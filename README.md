# Smart Garden Adiwiyata

Aplikasi Android Kotlin + Jetpack Compose untuk memantau dan menyiram kebun sekolah melalui ESP32 dan Firebase Realtime Database. Antarmuka berbahasa Indonesia, satu zona (`zone_a`).

## Coba tanpa perangkat

Pilih build variant **demoDebug** di Android Studio, lalu Run. Atau:

```powershell
./gradlew.bat :app:assembleDemoDebug
```

APK: `app/build/outputs/apk/demo/debug/app-demo-debug.apk`.

Tekan **Jelajahi kebun demo**. Data sensor berubah secara simulasi. Pilih **Manual** lalu **Siram sekarang** untuk mencoba penyiraman. Buka **Atur batas & durasi** untuk mengubah pengaturan. Demo tidak mengakses Firebase atau perangkat fisik. Keluar akan mereset simulasi.

## Fitur

- Login administrator dengan email/password, pemulihan sesi, dan logout (live).
- Kelembapan tanah, suhu udara, kelembapan udara, dan cahaya secara realtime.
- Indikator koneksi, waktu pembaruan, status perangkat dan status pompa.
- Mode otomatis/manual; batas kelembapan dan durasi manual dapat diubah.
- Konfirmasi perangkat untuk perintah penyiraman; perintah berumur maksimal 10 detik.
- Firmware dengan hysteresis, batas pompa 15 detik, cooldown otomatis 60 detik, dan pencegahan pemutaran ulang perintah.

## Android live dengan Firebase

1. Ikuti [panduan Firebase](firebase/README.md) untuk membuat project, Realtime Database, akun admin/perangkat, dan izin.
2. Daftarkan Android package **`com.maw.smart_adwiyata`** di Firebase. Demo menggunakan suffix `.demo` dan tidak perlu didaftarkan.
3. Salin [config/firebase.properties.example](config/firebase.properties.example) ke `app/firebase.properties`.
4. Isi `API_KEY`, `APP_ID`, `PROJECT_ID`, dan `DATABASE_URL`. Dari `google-services.json`: API key = `client[].api_key[].current_key`, APP_ID = `client[].client_info.mobilesdk_app_id`, PROJECT_ID = `project_info.project_id`. Gunakan client Android dengan package yang benar; URL database diambil dari halaman Realtime Database.
5. Pilih **liveDebug** dan jalankan, atau:

```powershell
./gradlew.bat :app:assembleLiveDebug
```

Firebase diinisialisasi dengan `FirebaseOptions` dari konfigurasi lokal tersebut; plugin Google Services dan penyalinan `google-services.json` tidak diperlukan. Tidak ada kredensial admin/perangkat di APK. Akun admin dimasukkan melalui layar login. Akun disediakan administrator, tanpa pendaftaran publik.

Build live tanpa konfigurasi akan berhenti dengan pesan yang jelas. Live tidak pernah mengganti data yang hilang dengan data demo. Untuk data yang belum tersedia, aplikasi menampilkan `—`.

## Uji Firebase lokal

Jalankan Firebase Emulator Suite dan seed sesuai [firebase/README.md](firebase/README.md), kemudian:

```powershell
./gradlew.bat :app:assembleLiveDebug -PfirebaseEmulator=true
```

Build ini khusus pengujian lokal, bukan koneksi cloud. Android Emulator mengakses host `10.0.2.2`, Auth port 9099 dan Database port 9000. Project ID `demo-smart-garden`. Build release menolak flag emulator.

Login emulator: `admin@garden.test` / `GardenDemo123!`. Akun ini hanya untuk emulator. Jangan membuat akun produksi dengan password tersebut.

Untuk uji integrasi Android, emulator Firebase harus berjalan dan sudah di-seed:

```powershell
./gradlew.bat :app:connectedLiveDebugAndroidTest -PfirebaseEmulator=true
```

## ESP32

Kode dan petunjuk ada di [firmware/README.md](firmware/README.md). Gunakan ESP32 Dev Module, sensor soil capacitive pada GPIO34, DHT22 pada GPIO4, BH1750 SDA21/SCL22, dan relay GPIO26. Polaritas relay dan kalibrasi dapat dikonfigurasi.

Firmware tidak mengizinkan penyiraman sebelum kalibrasi soil diaktifkan. Konfigurasikan akun Firebase khusus perangkat. Uji pembacaan sensor terlebih dahulu, lalu relay tanpa pompa, baru sambungkan pompa dan jalankan pengujian menyeluruh.

## Perilaku koneksi dan penyiraman

- Status online memerlukan heartbeat berumur kurang dari 30 detik; boolean `online` saja tidak cukup.
- App menyimpan pembacaan terakhir dalam memori, memberi label ketika offline, dan menonaktifkan penyiraman.
- Pengaturan membutuhkan `0 ≤ mulai < berhenti ≤ 100`. Default 30% dan 45%. Durasi manual 1–15 detik, default 5 detik.
- ESP32 berhenti menyiram saat mode berubah, soil tidak valid, Wi-Fi/auth hilang, atau kontrol tidak diperbarui selama 10 detik.
- Perintah manual memakai ID unik dan expiry. ESP32 menyimpan ID yang dikonsumsi, mengirim acknowledgement, lalu memeriksa kembali kondisi sebelum menyalakan relay.
- Pesan "diterima perangkat" adalah acknowledgement. Status pompa ditampilkan dari telemetry perangkat, bukan hasil klik tombol.
- Android membatalkan antrean write saat koneksi putus atau logout; rules juga menolak perintah kedaluwarsa.
- Timestamp database menggunakan Unix **detik**. Durasi pompa menggunakan **milidetik**. Lihat [kontrak bersama](docs/implementation-plan.md).

## Pengujian

Gunakan JDK bawaan Android Studio (JDK 17+ yang kompatibel dengan AGP 9.1.1; build diverifikasi dengan JBR Android Studio).

```powershell
./gradlew.bat :app:testDemoDebugUnitTest :app:lintDemoDebug :app:assembleDemoDebug
./gradlew.bat :app:connectedDemoDebugAndroidTest
```

Pengujian rules berada di `firebase/`, pengujian controller C++ di `firmware/`. Laporan hasil lokal dicatat di [docs/verification.md](docs/verification.md).

Android minimum API23 (Android 6.0), target API36. Grafik, history, notifikasi, dan multi-zone belum termasuk MVP.

## Batas verifikasi

Demo dan emulator membuktikan alur software, bukan ketepatan sensor atau pompa fisik. Definition of Done untuk perangkat nyata tetap memerlukan kalibrasi, polaritas relay, pembacaan sensor, dan pengujian pemutusan jaringan/daya pada hardware sebenarnya.
