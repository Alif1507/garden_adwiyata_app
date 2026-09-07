#pragma once

// Copy this file to Credentials.h and replace every placeholder.
#define WIFI_SSID "YOUR_WIFI_NAME"
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"
#define FIREBASE_API_KEY "YOUR_FIREBASE_WEB_API_KEY"
#define FIREBASE_USER_EMAIL "esp32@smartgarden.local"
#define FIREBASE_USER_PASSWORD "YOUR_DEVICE_USER_PASSWORD"
#define FIREBASE_DATABASE_URL \
    "https://YOUR_PROJECT-default-rtdb.asia-southeast1.firebasedatabase.app"

// Wiring from the project brief.
constexpr int SOIL_PIN = 34;
constexpr int DHT_PIN = 4;
constexpr int RELAY_PIN = 26;
constexpr int I2C_SDA_PIN = 21;
constexpr int I2C_SCL_PIN = 22;

// Most relay boards are active-low. Set false for an active-high board.
constexpr bool RELAY_ACTIVE_LOW = true;

// Replace these examples with dry/wet readings from the actual sensor, then
// explicitly set this flag true. Until then soil is invalid and pumping is
// fail-safe disabled.
constexpr int SOIL_DRY_RAW = 3100;
constexpr int SOIL_WET_RAW = 1350;
constexpr bool SOIL_CALIBRATION_CONFIRMED = false;

// Electrical plausibility guard. Tighten these after observing the installed
// sensor; rail values commonly indicate a short or disconnected probe.
constexpr int SOIL_RAW_VALID_MIN = 100;
constexpr int SOIL_RAW_VALID_MAX = 4000;
