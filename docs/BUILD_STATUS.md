# Status validasi source v1.1.0-beta2

## Pemeriksaan yang dilakukan

- File Kotlin inti diparse oleh kompiler Kotlin lokal (tanpa Android SDK; unresolved Android imports adalah keterbatasan alat lokal).
- Kelas pure Kotlin (`VideoUrl`, `CaptionText`, `PcmChunker`, `PcmRing`) dikompilasi dan diuji dengan test harness lokal.
- XML/manifest dan workflow YAML diperiksa dengan parser.
- Referensi JNI baru (`loadModel(path, requestGpu)`, `backendLabel()`, `warmup()`, `transcribe`, `unload`) diperiksa konsistensi penamaannya.
- ZIP diverifikasi via `ZipFile.testzip()` dan daftar path.

## Belum diverifikasi

- APK `assembleDebug` lengkap; Android SDK/NDK dan dependency jar tidak tersedia di lingkungan penyusun.
- Vulkan backend (`GGML_VULKAN=ON`) pada whisper.cpp v1.9.5 di GitHub Actions atau GPU Mali POCO M5.
- Kinerja GPU / thermal throttling / konsumsi memori model terhadap playback.
- Kompatibilitas YouTube embed `152-4` dan pembatasan AudioPlaybackCapture pada video sesungguhnya.

## Saat build di GitHub Actions

Workflow membangun dua varian terpisah: `LocalSub-cpu-arm64-debug` dan `LocalSub-vulkan-arm64-debug`. Jika Vulkan gagal dikompilasi atau crash di device, gunakan varian CPU. Repo Anda **tidak** diubah oleh pembuatan ZIP ini.
