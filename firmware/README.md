# ESP32 firmware

This firmware controls one ESP32 zone at
`/smart_garden/zones/zone_a`. Networking is asynchronous; the pump safety
state machine runs on every `loop()` iteration. The relay boots OFF and is
also forced OFF when Wi-Fi/auth is lost, control is invalid or 10 seconds old,
soil is invalid, mode changes, the configured stop threshold is reached, or
the 15-second hard limit is reached. Every stop starts a 60-second cooldown
for automatic watering.

Manual requests are accepted only in manual mode with trusted NTP time, a
future `request_expires_at`, and a duration from 1,000 through 15,000 ms. The
UUID is written to ESP32 NVS before the firmware sends one atomic Firebase
patch that clears `control/manual_water` and sets
`device/acknowledged_request_id`. The relay can turn on only after that patch
succeeds and the firmware rechecks expiry, connectivity, auth, sensor safety,
control freshness, and that control settings did not change during the async
acknowledgement. Recent consumed UUIDs are retained in NVS; older entries can
only fall out of the bounded history after their 10-second validity window.

## Configure and upload

Install these Arduino components:

- ESP32 by Espressif Systems
- FirebaseClient by Mobizt
- ArduinoJson
- DHT sensor library by Adafruit
- Adafruit Unified Sensor
- BH1750 by Christopher Laws

The verified build used ESP32 core 3.3.11, FirebaseClient 2.2.13,
ArduinoJson 7.4.3, DHT 1.4.7, Adafruit Unified Sensor 1.1.15, and BH1750
1.3.0.

Copy `smart_garden_esp32/Credentials.example.h` to
`smart_garden_esp32/Credentials.h`. Fill in Wi-Fi, Firebase Web API key,
Realtime Database URL, and a dedicated Firebase Authentication email/password
device account. Provision that account's UID at
`/smart_garden/access/devices/<uid> = true` with the Firebase console or Admin
SDK. Never put an admin user's credentials in the sketch.

The default pins from the project brief are:

| Device | ESP32 pin |
|---|---:|
| Capacitive soil ADC | GPIO 34 |
| DHT22 data | GPIO 4 |
| BH1750 SDA | GPIO 21 |
| BH1750 SCL | GPIO 22 |
| Relay input | GPIO 26 |

The relay default is active-low. Change `RELAY_ACTIVE_LOW` only after checking
the relay board. The TLS client validates the server chain with Google's GTS
Root R1 embedded in `GoogleRootCa.h`; it never calls `setInsecure()`. Review
Google Trust Services' repository before long-lived deployments and update the
trust anchor if Google changes the serving chain.

## Calibrate the soil sensor

Calibration is a mandatory pump interlock. With
`SOIL_CALIBRATION_CONFIRMED = false`, the serial monitor reports raw ADC values
but soil remains invalid and the pump cannot run.

1. Keep the pump disconnected. Upload the sketch and record stable `Soil raw`
   readings with the probe dry; use their representative value for
   `SOIL_DRY_RAW`.
2. Put only the probe section in fully wet soil, wait for readings to settle,
   and use the representative value for `SOIL_WET_RAW`.
3. Confirm the two readings differ by at least 200 ADC counts. Adjust
   `SOIL_RAW_VALID_MIN` and `SOIL_RAW_VALID_MAX` so open-circuit and rail values
   are rejected while both calibrated states remain valid.
4. Set `SOIL_CALIBRATION_CONFIRMED = true`, upload again, and verify dry/wet
   percentages move in the expected direction and stay within 0–100%.

## Staged physical checks

Run these stages in order. Keep the 12 V pump supply separate from the ESP32
3.3 V rail and wire grounds/relay isolation according to the relay module's
datasheet.

1. **Sensors only:** leave relay and pump disconnected. Confirm soil once per
   second and DHT22/BH1750 values in the coherent Firebase `sensors` snapshot
   every 10 seconds. Invalid sensor values must appear as `null`.
2. **Relay only:** connect the relay without the pump. On boot and reset its
   output must remain OFF. Issue one unexpired UUID request in manual mode and
   check acknowledgement occurs before the relay turns on.
3. **Safety interruption:** while the relay is on, disconnect Wi-Fi, disable
   the device user, change mode, and unplug the soil probe in separate runs.
   Each condition must release the relay. Confirm both manual and auto runs
   release it no later than 15 seconds.
4. **Automatic behavior:** use safe low-voltage relay testing first. Confirm
   soil at/below `moisture_start` starts watering, soil at/above
   `moisture_stop` stops it, and no automatic restart occurs for 60 seconds
   after any stop.
5. **Pump last:** connect the externally powered pump, begin with a short
   `manual_duration_ms`, watch the water path continuously, and keep a physical
   power cutoff within reach. Repeat the disconnect and hard-stop checks.

Hardware and live Firebase credentials were unavailable during the repository
build, so these physical checks are intentionally not marked as completed.

## Local verification

The repository scripts use tools cached below `firmware/.tools` and keep all
generated files ignored. From the repository root:

```powershell
firmware\run-host-tests.ps1
firmware\compile-esp32.ps1
```

`compile-esp32.ps1` copies the credentials example into an ignored temporary
sketch and compiles `esp32:esp32:esp32`; it does not create a real credentials
file or upload anything. For Arduino IDE, open
`smart_garden_esp32/smart_garden_esp32.ino` after creating the real
`Credentials.h`, select the matching ESP32 board, then build and upload.

The controller tests cover relay-off initialization, automatic thresholds,
manual acknowledgement gating and expiry, both-mode hard stops, the cooldown,
millis wraparound, invalid settings, and all immediate safety stop inputs.
