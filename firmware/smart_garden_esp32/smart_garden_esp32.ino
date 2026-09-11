#define ENABLE_USER_AUTH
#define ENABLE_DATABASE

#include <Arduino.h>
#include <ArduinoJson.h>
#include <BH1750.h>
#include <DHT.h>
#include <FirebaseClient.h>
#include <Preferences.h>
#include "PumpController.h"
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <Wire.h>

#include "Credentials.h"
#include "GoogleRootCa.h"

namespace {

constexpr char kZonePath[] = "/smart_garden/zones/zone_a";
constexpr uint32_t kSoilReadIntervalMs = 1'000;
constexpr uint32_t kControlPollIntervalMs = 2'000;
constexpr uint32_t kTelemetryIntervalMs = 10'000;
constexpr uint32_t kWifiRetryIntervalMs = 15'000;
constexpr time_t kMinimumTrustedUnixTime = 1'700'000'000;
constexpr uint32_t kMinimumCalibrationSpan = 200;

constexpr uint8_t kRelayOn = RELAY_ACTIVE_LOW ? LOW : HIGH;
constexpr uint8_t kRelayOff = RELAY_ACTIVE_LOW ? HIGH : LOW;

DHT dht(DHT_PIN, DHT22);
BH1750 lightMeter;
WiFiClientSecure sslClient;
AsyncClientClass firebaseClient(sslClient);
UserAuth userAuth(FIREBASE_API_KEY, FIREBASE_USER_EMAIL,
                  FIREBASE_USER_PASSWORD, 3000);
FirebaseApp firebaseApp;
RealtimeDatabase database;
Preferences preferences;
garden::PumpController pumpController;

garden::Control activeControl{};
bool controlValid = false;
uint32_t controlReceivedAtMs = 0;
uint32_t controlVersion = 0;
bool controlPollInFlight = false;
bool firebaseInitialized = false;
bool backendAuthorized = false;

int soilRaw = 0;
uint8_t soilPercent = 0;
bool soilValid = false;

uint32_t lastSoilReadMs = 0;
uint32_t lastControlPollMs = 0;
uint32_t lastTelemetryMs = 0;
uint32_t lastWifiAttemptMs = 0;

String consumedRequestIds;
String pendingAckRequestId;
uint64_t pendingAckExpiresAt = 0;
bool pendingAckMayActivate = false;
uint32_t pendingAckControlVersion = 0;
bool manualAckInFlight = false;
bool manualGrantPulse = false;
uint64_t manualGrantExpiresAt = 0;

bool elapsedAtLeast(uint32_t nowMs, uint32_t thenMs, uint32_t intervalMs) {
    return static_cast<uint32_t>(nowMs - thenMs) >= intervalMs;
}

bool timeTrusted() {
    return time(nullptr) >= kMinimumTrustedUnixTime;
}

uint64_t unixSeconds() {
    const time_t now = time(nullptr);
    return now < 0 ? 0 : static_cast<uint64_t>(now);
}

const char *stopReasonName(garden::StopReason reason) {
    switch (reason) {
    case garden::StopReason::WifiLost: return "wifi lost";
    case garden::StopReason::AuthLost: return "auth lost";
    case garden::StopReason::InvalidControl: return "invalid control";
    case garden::StopReason::ControlStale: return "control stale";
    case garden::StopReason::InvalidSoil: return "invalid soil";
    case garden::StopReason::ModeChanged: return "mode changed";
    case garden::StopReason::SoilWet: return "soil threshold";
    case garden::StopReason::ManualDurationComplete: return "manual duration";
    case garden::StopReason::HardRuntimeLimit: return "15-second limit";
    default: return "none";
    }
}

void logFirebaseResult(AsyncResult &result) {
    if (result.isError()) {
        Serial.printf("Firebase task %s failed: %s (%d)\n",
                      result.uid().c_str(), result.error().message().c_str(),
                      result.error().code());
    }
}

void queuePatch(const JsonDocument &document, const char *uid) {
    String payload;
    serializeJson(document, payload);
    database.update<object_t>(firebaseClient, kZonePath, object_t(payload),
                              logFirebaseResult, uid);
}

bool requestWasConsumed(const String &requestId) {
    const String needle = "\n" + requestId + "\n";
    return consumedRequestIds.indexOf(needle) >= 0;
}

bool storeConsumedRequestId(const String &requestId) {
    if (requestId.isEmpty()) {
        return false;
    }
    String updated = consumedRequestIds + requestId + "\n";
    constexpr size_t kMaximumHistoryBytes = 1024;
    while (updated.length() > kMaximumHistoryBytes) {
        const int nextRecord = updated.indexOf('\n', 1);
        if (nextRecord < 0) break;
        updated.remove(1, nextRecord);
    }
    const size_t written = preferences.putString("consumed", updated);
    if (written != updated.length()) {
        Serial.println("Could not persist manual request; rejecting activation");
        return false;
    }
    consumedRequestIds = updated;
    return true;
}

void queueManualAcknowledgement();

void processManualRequest(const String &requestId, bool requested,
                          uint64_t expiresAt) {
    if (!requested || !garden::isCanonicalUuid(requestId.c_str()) ||
        manualAckInFlight ||
        !pendingAckRequestId.isEmpty()) {
        return;
    }

    bool mayActivate = false;
    if (requestWasConsumed(requestId)) {
        // A prior acknowledgement may have been interrupted. Clear it again,
        // but never replay a consumed command.
        mayActivate = false;
    } else {
        if (!storeConsumedRequestId(requestId)) {
            return;
        }
        mayActivate = activeControl.mode == garden::Mode::Manual &&
                      timeTrusted() && unixSeconds() < expiresAt;
    }

    pendingAckRequestId = requestId;
    pendingAckExpiresAt = expiresAt;
    pendingAckMayActivate = mayActivate;
    pendingAckControlVersion = controlVersion;
    queueManualAcknowledgement();
}

void handleControlPayload(const char *payload) {
    JsonDocument json;
    if (deserializeJson(json, payload) != DeserializationError::Ok ||
        !json.is<JsonObject>()) {
        controlValid = false;
        Serial.println("Rejected malformed control snapshot");
        return;
    }

    const char *modeText = json["mode"] | "";
    garden::Control candidate{};
    if (strcmp(modeText, "auto") == 0) {
        candidate.mode = garden::Mode::Auto;
    } else if (strcmp(modeText, "manual") == 0) {
        candidate.mode = garden::Mode::Manual;
    }

    const int start = json["moisture_start"] | -1;
    const int stop = json["moisture_stop"] | -1;
    const int duration = json["manual_duration_ms"] | -1;
    garden::makeControl(candidate.mode, start, stop, duration, candidate);

    if (candidate.mode != activeControl.mode ||
        candidate.moistureStart != activeControl.moistureStart ||
        candidate.moistureStop != activeControl.moistureStop ||
        candidate.manualDurationMs != activeControl.manualDurationMs) {
        ++controlVersion;
    }
    activeControl = candidate;
    controlValid = candidate.valid();
    controlReceivedAtMs = millis();
    if (!controlValid) {
        Serial.println("Rejected unsafe control values");
        return;
    }

    const bool manualWater = json["manual_water"] | false;
    const String requestId = json["request_id"] | "";
    const uint64_t expiresAt = json["request_expires_at"] | 0ULL;
    processManualRequest(requestId, manualWater, expiresAt);
}

void firebaseResult(AsyncResult &result) {
    const String uid = result.uid();
    if (uid == "controlPoll") {
        if (result.isError()) {
            controlPollInFlight = false;
            backendAuthorized = false;
            logFirebaseResult(result);
            return;
        }
        if (result.available()) {
            controlPollInFlight = false;
            backendAuthorized = true;
            handleControlPayload(result.c_str());
        }
        return;
    }

    if (uid == "manualAck") {
        if (result.isError()) {
            manualAckInFlight = false;
            logFirebaseResult(result);
            return;
        }
        if (result.available()) {
            manualAckInFlight = false;
            if (pendingAckMayActivate &&
                pendingAckControlVersion == controlVersion &&
                activeControl.mode == garden::Mode::Manual) {
                manualGrantPulse = true;
                manualGrantExpiresAt = pendingAckExpiresAt;
            }
            pendingAckRequestId = "";
            pendingAckExpiresAt = 0;
            pendingAckMayActivate = false;
            pendingAckControlVersion = 0;
        }
        return;
    }

    logFirebaseResult(result);
}

void queueManualAcknowledgement() {
    if (pendingAckRequestId.isEmpty() || manualAckInFlight ||
        !firebaseInitialized || !firebaseApp.ready()) {
        return;
    }
    JsonDocument patch;
    patch["control/manual_water"] = false;
    patch["device/acknowledged_request_id"] = pendingAckRequestId;
    String payload;
    serializeJson(patch, payload);
    manualAckInFlight = true;
    database.update<object_t>(firebaseClient, kZonePath, object_t(payload),
                              firebaseResult, "manualAck");
}

void sampleSoil() {
    uint32_t total = 0;
    bool readingsPlausible = true;
    constexpr int kSamples = 8;
    for (int i = 0; i < kSamples; ++i) {
        const int reading = analogRead(SOIL_PIN);
        total += static_cast<uint32_t>(reading);
        readingsPlausible = readingsPlausible &&
                            reading >= SOIL_RAW_VALID_MIN &&
                            reading <= SOIL_RAW_VALID_MAX;
    }
    soilRaw = static_cast<int>(total / kSamples);

    const int span = abs(SOIL_DRY_RAW - SOIL_WET_RAW);
    soilValid = SOIL_CALIBRATION_CONFIRMED && readingsPlausible &&
                span >= static_cast<int>(kMinimumCalibrationSpan);
    if (!soilValid) {
        Serial.printf("Soil raw=%d INVALID (calibration/guard)\n", soilRaw);
        return;
    }

    const long numerator = static_cast<long>(soilRaw - SOIL_DRY_RAW) * 100L;
    const long denominator = static_cast<long>(SOIL_WET_RAW - SOIL_DRY_RAW);
    soilPercent = static_cast<uint8_t>(constrain(numerator / denominator, 0L, 100L));
    Serial.printf("Soil raw=%d moisture=%u%%\n", soilRaw, soilPercent);
}

garden::Inputs controllerInputs(uint32_t nowMs) {
    garden::Inputs input{};
    input.nowMs = nowMs;
    input.unixSeconds = unixSeconds();
    input.timeTrusted = timeTrusted();
    input.wifiConnected = WiFi.status() == WL_CONNECTED;
    input.authenticated = firebaseInitialized && firebaseApp.ready() &&
                          backendAuthorized;
    input.controlValid = controlValid;
    input.controlReceivedAtMs = controlReceivedAtMs;
    input.soilValid = soilValid;
    input.soilPercent = soilPercent;
    input.control = activeControl;
    input.manualGrant = manualGrantPulse;
    input.manualRequestExpiresAt = manualGrantExpiresAt;
    return input;
}

void publishPumpTransition(bool running) {
    if (!firebaseInitialized || !firebaseApp.ready() || !timeTrusted()) {
        return;
    }
    JsonDocument patch;
    patch["pump/status"] = running;
    if (running) {
        patch["pump/last_started_at"] = unixSeconds();
    } else {
        patch["pump/last_stopped_at"] = unixSeconds();
    }
    queuePatch(patch, "pumpTransition");
}

void applyController(uint32_t nowMs) {
    const garden::Result result = pumpController.update(controllerInputs(nowMs));
    manualGrantPulse = false;
    manualGrantExpiresAt = 0;
    if (!result.changed) {
        return;
    }

    digitalWrite(RELAY_PIN, result.running ? kRelayOn : kRelayOff);
    if (result.running) {
        Serial.println("PUMP ON");
    } else {
        Serial.printf("PUMP OFF (%s)\n", stopReasonName(result.stopReason));
    }
    publishPumpTransition(result.running);
}

void pollControl() {
    if (controlPollInFlight || !firebaseInitialized || !firebaseApp.ready()) {
        return;
    }
    controlPollInFlight = true;
    database.get(firebaseClient, String(kZonePath) + "/control", firebaseResult,
                 false, "controlPoll");
}

void publishTelemetry() {
    if (!firebaseInitialized || !firebaseApp.ready() || !timeTrusted()) {
        return;
    }

    const float temperature = dht.readTemperature();
    const float humidity = dht.readHumidity();
    const float light = lightMeter.readLightLevel();
    JsonDocument patch;
    JsonObject sensors = patch["sensors"].to<JsonObject>();
    sensors["soil_raw"] = soilRaw;
    if (soilValid) sensors["soil_moisture"] = soilPercent;
    else sensors["soil_moisture"] = nullptr;
    if (isfinite(temperature)) sensors["temperature"] = temperature;
    else sensors["temperature"] = nullptr;
    if (isfinite(humidity)) sensors["humidity"] = humidity;
    else sensors["humidity"] = nullptr;
    if (isfinite(light) && light >= 0) sensors["light"] = light;
    else sensors["light"] = nullptr;
    sensors["updated_at"] = unixSeconds();
    patch["device/online"] = true;
    patch["device/last_seen"] = unixSeconds();
    patch["pump/status"] = pumpController.running();
    queuePatch(patch, "telemetry");
}

void maintainWifi(uint32_t nowMs) {
    if (WiFi.status() == WL_CONNECTED ||
        !elapsedAtLeast(nowMs, lastWifiAttemptMs, kWifiRetryIntervalMs)) {
        return;
    }
    lastWifiAttemptMs = nowMs;
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    Serial.println("Wi-Fi reconnect started");
}

void initializeFirebaseWhenReady() {
    if (firebaseInitialized || WiFi.status() != WL_CONNECTED || !timeTrusted()) {
        return;
    }
    sslClient.setCACert(GOOGLE_ROOT_CA);
    sslClient.setConnectionTimeout(1000);
    sslClient.setHandshakeTimeout(5);
    initializeApp(firebaseClient, firebaseApp, getAuth(userAuth), firebaseResult,
                  "auth");
    firebaseApp.getApp<RealtimeDatabase>(database);
    database.url(FIREBASE_DATABASE_URL);
    firebaseInitialized = true;
    Serial.println("Firebase initialization started with verified TLS");
}

} // namespace

void setup() {
    Serial.begin(115200);
    pinMode(RELAY_PIN, OUTPUT);
    digitalWrite(RELAY_PIN, kRelayOff);

    analogReadResolution(12);
    dht.begin();
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    lightMeter.begin(BH1750::CONTINUOUS_HIGH_RES_MODE);
    preferences.begin("smart-garden", false);
    consumedRequestIds = preferences.getString("consumed", "\n");
    if (!consumedRequestIds.startsWith("\n")) consumedRequestIds = "\n";

    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    lastWifiAttemptMs = millis();
    configTime(0, 0, "time.google.com", "pool.ntp.org");

    sampleSoil();
    if (!SOIL_CALIBRATION_CONFIRMED) {
        Serial.println("PUMP LOCKED: confirm soil calibration in Credentials.h");
    }
}

void loop() {
    const uint32_t nowMs = millis();

    // Safety runs every loop and is never gated by network work.
    applyController(nowMs);
    maintainWifi(nowMs);
    initializeFirebaseWhenReady();
    if (firebaseInitialized) {
        firebaseApp.loop();
    }

    if (elapsedAtLeast(nowMs, lastSoilReadMs, kSoilReadIntervalMs)) {
        lastSoilReadMs = nowMs;
        sampleSoil();
    }
    if (elapsedAtLeast(nowMs, lastControlPollMs, kControlPollIntervalMs)) {
        lastControlPollMs = nowMs;
        pollControl();
    }
    if (elapsedAtLeast(nowMs, lastTelemetryMs, kTelemetryIntervalMs)) {
        lastTelemetryMs = nowMs;
        publishTelemetry();
    }
    queueManualAcknowledgement();
}
