# GPU Vulkan + persiapan sebelum pemutaran

## Alur yang benar-benar dilakukan

1. Tempel URL YouTube. Pemutar **belum dimuat** sehingga video tidak berjalan mendahului subtitle.
2. Unduh model Whisper bila belum ada; pilih sumber audio.
3. Tekan **Mulai subtitle lokal**, setujui izin RECORD_AUDIO / MediaProjection.
4. Layanan foreground memuat model Whisper dalam worker thread, mencoba Vulkan bila APK GPU tersedia, lalu melakukan satu inferensi senyap untuk pemanasan kernel/cache.
5. Barulah AudioRecord dimulai dan UI mengubah status menjadi READY. YouTube WebView baru dimuat pada tahap ini.
6. Pengguna menekan Play pada player YouTube (aturan gesture/autoplay). Audio 16 kHz diproses sebagai potongan 2,5 detik, overlap 250 ms; subtitle tampil segera setelah inferensi selesai.

**Penting:** warmup mempercepat langkah awal *setelah* video berjalan, tetapi tidak mungkin menyiapkan transkrip ucapan yang belum pernah diputar. Ini bukan subtitle bebas latensi. YouTube error 152-4 / pembatasan embed maupun kebijakan AudioPlaybackCapture tidak diperbaiki oleh GPU.

## APK yang dihasilkan

- `LocalSub-cpu-arm64-debug`: CPU stabil; opsi GPU otomatis akan memakai CPU.
- `LocalSub-vulkan-arm64-debug`: mencoba Vulkan, fallback CPU jika backend tidak ditemukan / model tidak dapat dimuat. Driver Vulkan, panas, dan performa sangat bergantung pada ROM/GPU.
- Keduanya applicationId dan tanda tangan debug dari job yang sama; instal **salah satu**, bukan dua APK bersamaan. Penggantian antar APK dengan tanda tangan berbeda dari run lain bisa membutuhkan uninstall (model terunduh dapat terhapus).

## Tes nyata sebelum diklaim real-time

- Bandingkan waktu pemanasan (`warmupMs`) dan inferensi per potongan pada lagu/dialog yang sama (CPU vs Vulkan) dan suhu/volume sama.
- Konfirmasi audio terdeteksi dari pemutar; bila hanya diam, coba mikrofon atas izin pengguna.
- Bila waktu inferensi per potongan lebih besar dari 2250 ms (laju potongan), backlog bisa muncul. Queue dibatasi untuk mencegah subtitle tertinggal selamanya.
- Jangan mengasumsikan GPU lebih cepat: GPU Mali kelas menengah mungkin kalah cepat dari ARM NEON, dan dapat mengalami thermal throttling.
- Pengguna tetap harus menekan tombol Play di YouTube: autoplay tersemat tidak dapat dijamin.
