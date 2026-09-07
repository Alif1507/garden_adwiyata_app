# Verifikasi Sistem Smart Garden Adiwiyata

Dokumen ini merangkum hasil pengujian dan verifikasi lokal untuk seluruh komponen proyek Smart Garden Adiwiyata IoT.

---

## 1. Android Application (`app`)

### Konfigurasi JDK
- Ditautkan ke Android Studio JBR (`C:\Program Files\Android\Android Studio\jbr`) melalui `gradle.properties` (`org.gradle.java.home`), mengatasi isu resolusi toolchain JDK/`jlink`.

### Unit Tests
- **Demo Variant**:
  ```powershell
  ./gradlew.bat :app:testDemoDebugUnitTest --console=plain
  ```
  - `GardenPolicyTest`: Pengujian validasi rentang ambang kelembapan (hysteresis), durasi maksimal pompa, pemutusan status online (heartbeat < 30 detik), pencegahan nilai palsu dari data Firebase numerik campuran, dan status permintaan penyiraman. (Semua lolos)
  - `GardenViewModelTest`: Pengujian kegagalan autentikasi tanpa membuka dashboard, penolakan penulisan aturan yang gagal, dan pelarangan penyiraman saat perangkat offline/stale. (Semua lolos)
  - **Hasil**: `BUILD SUCCESSFUL`

- **Live Variant**:
  ```powershell
  ./gradlew.bat :app:testLiveDebugUnitTest -PfirebaseEmulator=true --console=plain
  ```
  - **Hasil**: `BUILD SUCCESSFUL`

### Lint
- **Lint Check**:
  ```powershell
  ./gradlew.bat :app:lintDemoDebug --console=plain
  ```
  - **Hasil**: `BUILD SUCCESSFUL` (0 error, 0 fatal issues)

### Build APK
- **Demo APK**:
  ```powershell
  ./gradlew.bat :app:assembleDemoDebug --console=plain
  ```
  - Output: `app/build/outputs/apk/demo/debug/app-demo-debug.apk`
- **Live Debug APK**:
  ```powershell
  ./gradlew.bat :app:assembleLiveDebug -PfirebaseEmulator=true --console=plain
  ```
  - Output: `app/build/outputs/apk/live/debug/app-live-debug.apk`

---

## 2. Firebase Database Rules & Authentication (`firebase`)

### Test Suite
- Menggunakan `@firebase/rules-unit-testing`, local Auth emulator (port 9099), dan Realtime Database emulator (port 9000).
- Perintah eksekusi:
  ```powershell
  npm test
  ```
  (dijalankan di direktori `firebase/` dengan `JAVA_HOME` mengarah ke Android Studio JBR).

### Hasil Pengujian Rules (`database.rules.test.mjs`)
- `✔ seeded admin and device can read zone_a`
- `✔ anonymous and unassigned authenticated users cannot read zone_a`
- `✔ clients cannot read or change role assignments`
- `✔ admin can atomically update valid mode, duration, and strict thresholds`
- `✔ admin threshold writes reject equal, reversed, out-of-range, and malformed values`
- `✔ admin settings reject invalid modes, duration bounds, unknown fields, and deletion`
- `✔ admin cannot write device-owned telemetry`
- `✔ admin can submit a fresh UUID manual request only in safe manual conditions`
- `✔ manual request rejects malformed, expired, overlong, replayed, and outstanding commands`
- `✔ admin can replace an expired unacknowledged request with a new UUID and deadline`
- `✔ manual request rejects auto mode, running pump, and stale device`
- `✔ device can publish a coherent validated sensor snapshot`
- `✔ sensor writes reject malformed values, unknown fields, stale timestamps, and partial leaf writes`
- `✔ device can report pump transitions and heartbeat but malformed telemetry is rejected`
- `✔ device acknowledgement requires one atomic multi-location clear with the matching request ID`
- `✔ device cannot alter admin control fields or create manual commands`
- **Total**: 16 lulus, 0 gagal.

---

## 3. ESP32 Firmware (`firmware`)

### Host Unit Tests (C++)
- Menguji logika murni `PumpController` (state machine, hysteresis, batas runtime 15 detik, cooldown otomatis 60 detik, dan acknowledgement perintah penyiraman).
- Perintah:
  ```powershell
  powershell -Command "firmware\run-host-tests.ps1"
  ```
- **Hasil**: `All pump controller tests passed`

### Kompilasi Hardware ESP32
- Menggunakan `arduino-cli` yang dipaketkan di `firmware/.tools/arduino-cli/arduino-cli.exe` dengan target board `esp32:esp32:esp32`.
- Perintah:
  ```powershell
  powershell -Command "firmware\compile-esp32.ps1"
  ```
- **Hasil**:
  - `Sketch uses 1099546 bytes (83%) of program storage space.`
  - `Global variables use 51428 bytes (15%) of dynamic memory.`
  - Kompilasi berhasil tanpa error.
