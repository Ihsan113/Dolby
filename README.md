# ClearSound • Android Audio Enhancer

**Versi:** `0.1.0` (source / beta percobaan)  
**Platform:** Android 10+ (minSdk 29) · Fokus ponsel  
**Teknologi:** Kotlin 2.0.21 · Jetpack Compose Material 3 · Shizuku 13.1.5  
**Paket:** `id.danzku.clearsound`  
**Internet:** Tidak meminta izin internet; offline.

## Tujuan

Aplikasi equalizer dengan UI clean, mobile-first, yang mencoba menerapkan efek Android ke audio mix global melalui `android.media.audiofx.Equalizer(priority=0, audioSession=0)`. Audio Session 0 **deprecated**: setiap ROM bisa mengizinkan, menolak, atau membuat efeknya tidak terdengar pada sebagian aplikasi/output. Ini **bukan Dolby Atmos asli**, tidak mendistribusikan DSP berlisensi Dolby, dan tidak memodifikasi Audio HAL. Keberhasilan membuat engine **bukan bukti** bahwa semua aplikasi terdengar berubah.

## Implementasi (bukan mockup)

- Home: saklar mengaktifkan foreground service yang menyimpan instance `Equalizer` aktif; kurva grafik live; info band fisik dan pesan error.
- Equalizer: 10 band logaritmik (31 Hz–16 kHz); disesuaikan secara interpolasi ke 1–32 band fisik dari perangkat; lima preset konservatif + kustom; nilai disimpan.
- Quick Settings: TileService untuk ON/OFF, tombol tambah tile Android 13+; di Android 12 ke bawah tambahkan manual melalui panel Edit.
- Onboarding pertama: tiga halaman, edukasi batasan Session 0, setup Shizuku, notifikasi dan tile; bisa diulang dari Pengaturan.
- Shizuku: meminta izin dengan API 13.1.5. **Penggunaan spesifik**: layanan `UserService` menjalankan `cmd deviceidle whitelist +<package>` sebagai identitas ADB shell (jika ROM mengizinkan) setelah pengguna mengetuk tombol whitelist.
- Background: foreground service bertipe `specialUse`, notifikasi persisten + action matikan, tidak start diam-diam setelah reboot.
- Tidak ada perekaman audio, MediaProjection, analytic SDK, tracking, atau request jaringan.
- Material 3 responsif, dark/light mengikuti sistem, navigasi 3 tab dan kontras untuk ukuran HP kecil.

## Build APK memakai GitHub Actions

1. Buat **repository pribadi** (atau repo publik) baru di GitHub; upload **seluruh isi** folder ini ke root repo termasuk `.github/workflows/android.yml` (folder `.github` tersembunyi, jangan sampai terlewat).
2. Buka **Actions → ClearSound Android CI → Run workflow**. Workflow juga otomatis berjalan jika push ke `main`/`master`.
3. Setelah status workflow hijau, buka job dan download artifact `ClearSound-debug-apk`.
4. Ekstrak artifact dan install `app-debug.apk` pada HP Android 10+.

CI memakai Java 17, Gradle 8.10.2 dan Android SDK 35. *Gradle Wrapper tidak disertakan* karena lingkungan pembuatan ini tidak memiliki binary wrapper tepercaya untuk dikemas. GitHub Actions mengunduh Gradle melalui `gradle/actions/setup-gradle@v4`. Di Android Studio, impor folder ini dan pilih Gradle 8.10.2/JDK 17 jika diminta. Perintah build bila Gradle sudah terpasang:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Langkah penggunaan pertama

1. Install ClearSound dari APK debug.
2. Lalui onboarding. **Shizuku opsional untuk audio engine**, tetapi diperlukan untuk tombol whitelist baterai otomatis. Install dan jalankan Shizuku via Wireless Debugging, lalu ketuk **Beri izin**.
3. Izinkan notifikasi supaya status pemrosesan terlihat.
4. Tambahkan ClearSound ke Quick Settings melalui langkah setup atau edit panel manual.
5. Putar musik/video lalu pilih **Clear Voice** / **Music** / **Cinema** / **Bass** dan aktifkan ClearSound.
6. Bandingkan ON/OFF **pada volume sama**. Jika tidak ada perbedaan, Audio Session 0 bisa diabaikan oleh ROM atau output tersebut; aplikasi tidak mampu memaksa jalur sistem tanpa dukungan firmware.

## Troubleshooting

| Kondisi | Langkah |
|---|---|
| “EQ global tidak didukung” | Matikan equalizer bawaan, restart app, coba lagi; ROM mungkin melarang session 0. |
| Status aktif tetapi tidak terdengar beda | Uji dengan playlist sama melalui speaker/headset; gunakan preset Clear Voice. Efek tidak dijamin berlaku global. |
| Berhenti setelah layar mati | Buka Pengaturan → Manajemen baterai; whitelist via Shizuku bisa dicoba jika ROM mendukung. |
| Shizuku tidak berjalan | Setelah restart HP, jalankan ulang Shizuku via Wireless Debugging. Ini normal pada Shizuku non-root. |
| Tile tidak terlihat | Edit panel Quick Settings dan cari ClearSound; dukungan permintaan otomatis hanya Android 13+. |
| Suara pecah | Kurangi gain band positif; di versi ini belum ada limiter brickwall native. |

## Catatan Play Store

Versi ini adalah **prototype fungsional dan belum divalidasi untuk publikasi**. Foreground Service bertipe `specialUse` harus dijelaskan di Play Console dan dapat memerlukan review. Untuk publikasi, verifikasi manual terhadap kompatibilitas, kebijakan foreground service, aset listing, privacy policy, aksesibilitas, dan pengujian perangkat wajib dilakukan. Tidak ada klaim sertifikasi Dolby.

## Status verifikasi

- ✅ XML, YAML workflow, struktur source dan algoritme interpolasi diverifikasi offline.
- ✅ Fungsi EqCurve dikompilasi lewat Kotlin CLI dan uji midpoint dilalui.
- ⚠️ Android APK **belum** dikompilasi di lingkungan ini (Android SDK/Gradle tidak tersedia). Workflow CI sudah disediakan untuk membangun dan mengungkap error jika ada.
- ⚠️ Efek audio **belum** diuji langsung pada POCO M5 / ROM spesifik.

## Referensi API (bukan kode yang disalin)

- Android Equalizer: https://developer.android.com/reference/android/media/audiofx/Equalizer
- AudioEffect: https://developer.android.com/reference/android/media/audiofx/AudioEffect
- Shizuku API: https://github.com/RikkaApps/Shizuku-API
- Android foreground service types: https://developer.android.com/about/versions/14/changes/fgs-types-required

Kode ClearSound ditulis untuk proyek ini. Bukan fork dari allEQ atau produk Dolby.
