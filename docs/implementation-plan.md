# Smart Garden Adiwiyata

Approved source: `context/markdown/garden_adyiwata_app.md` and the implementation plan in the conversation.

## Deliverables
- [x] Android demo/live variants, Indonesian dashboard, login and controls
- [x] Firebase seed, role rules, emulator tests and configuration
- [x] ESP32 firmware, pure controller tests and compile verification
- [x] Setup guide, demo APK, build/lint/tests and integration review

## Shared wire contract
Root: `/smart_garden/zones/zone_a`. Existing timestamps are Unix seconds.

`sensors`: soil_moisture (0..100 or absent), soil_raw, temperature, humidity, light, updated_at. Invalid sensors are null/absent; publish coherent snapshots.

`control`: mode (`auto` or `manual`), moisture_start (default 30), moisture_stop (45), manual_duration_ms (5000), manual_water (false), request_id (empty string initially), request_expires_at (Unix seconds, 0 initially).

`pump`: status (false), last_started_at (0), last_stopped_at (0).

`device`: online (false), last_seen (0), acknowledged_request_id (empty initially).

Admin writes control only; device writes sensors/pump/device and may only clear control/manual_water. Provision roles at `/smart_garden/access/admins/<uid> = true` and `/smart_garden/access/devices/<uid> = true`, using console/Admin SDK only. Authenticated authorized admins and devices can read zone_a. Client writes to access forbidden.

Admin submits manual_water=true plus UUID request_id and server-adjusted now +10 seconds request_expires_at atomically, in manual mode. Request duration 1000..15000ms. Only one outstanding request. Device persists ID, atomically clears manual_water and sets acknowledged_request_id before starting, rechecks expiry and safety after acknowledgement success. Never replay consumed or expired commands; reject without trustworthy time. Android shows pending until matching acknowledgement, expires pending after deadline; telemetry alone reports actual pump status. New IDs and expiry required for each request. SDK disk persistence disabled; purge pending writes on disconnect/logout and deny stale writes in rules.

Settings updates are atomic and require 0 <= start < stop <= 100. Heartbeat online only when connected and age in [0,30) seconds. Time fields always seconds, Firebase rules `now` milliseconds divided by 1000. Android uses .info/serverTimeOffset.

Pump: 15s hard runtime both modes; 60s auto cooldown after every stop. Mode changes, bad soil, lost WiFi/auth or controls stale >=10s stop pump. Boot relay OFF, calibrated soil and fresh valid control mandatory; soil read every second, publish every 10s, control poll every 2s. Async networking cannot gate safety loop.

## Decisions and progress
- Work in place: no Git repository exists, so worktree/commit steps do not apply.
- Keep application ID com.example.home_garden_system, lower minSdk to 23.
- Demo is a separate build flavor and explicitly simulated; live never falls back to fake data.
- Firmware and Firebase tasks share only this contract, Android consumes it; separate file ownership prevents conflicts.
- Firebase credentials and actual hardware unavailable: emulator/live compile tests and staged hardware checklist replace claims of physical validation.
