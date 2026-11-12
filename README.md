# LocalSub — YouTube Local Subtitle (Android)

**Kotlin + Jetpack Compose + whisper.cpp (ARM64 / native C++)**. Mobile-only Android 10+ (API 29), no root, no Shizuku required.

## What this app actually does

1. Paste a permitted YouTube link (`youtube.com/watch`, `youtu.be`, `shorts`, `live`, `embed`).
2. Play the original YouTube video **inside YouTube's embedded player** (the app doesn't scrape or download videos).
3. Choose a Whisper model (Tiny Q5 about 32 MB / Base Q5 about 60 MB) and download it once. The download is SHA-256 verified.
4. Tap **Mulai subtitle lokal**. Approve Android's audio recording permission and MediaProjection consent. **Pick whole-screen sharing when offered.**
5. The foreground service tries to capture Android `USAGE_MEDIA` output, collects mono PCM, and runs `whisper.cpp` inference locally. Captions appear below the video with a few seconds latency.
6. If audio can't be captured, choose **Mikrofon** and use the phone speaker. Headphones generally won't work in microphone fallback mode.
7. Copy the transcript or save a rough SRT. Timecodes are relative to the **recording session**, and are not video-synchronized after seeking/pausing.

## Known limitations (please read)

- **Not a live caption guarantee for every YouTube video.** Android's `AudioPlaybackCapture` can only capture a player when the app, player and output policy permit. Some YouTube/WebView versions, DRM content, or OEM firmware may block it, returning silence even with permission.
- YouTube embeds can disallow video playback because of owner restrictions, region or WebView integrity/identity requirements.
- This is **on-device speech-to-text**, NOT offline YouTube playback. You need internet for watching YouTube and the initial model download.
- Speech recognition latency is CPU-dependent; 4-second chunks with 0.5-second overlap are used. It can lag behind on entry-level phones. Whisper models can mistranscribe music, silence and loud background sounds.
- Captions **transcribe** the original language, not auto-translate to Indonesian. Choose one of the supported recognition languages.
- No monetization, analytics, tracker, API token, or server-side ASR. Audio samples are not uploaded.
- Only ARM64 builds are configured. This keeps the APK smaller on modern Android phones.

## GitHub Actions build — recommended for Android-only workflow

**You push the source.** No GitHub repository has been modified by this package.

1. Extract ZIP and push its **contents** to the ROOT of an empty GitHub repository; hidden `.github` must be included.
2. Go to **Actions -> Build YouTube Local Subtitles**. It also runs automatically after push to `main`/`master`.
3. Wait for the native build (it downloads pinned `whisper.cpp` source v1.9.5 during CI).
4. Open the successful run -> Artifacts -> **YTLocalSubtitles-arm64-debug-APK**.
5. Unzip to get `app-debug.apk` and install on the device.

There is no Gradle wrapper binary in this source package. Actions installs Gradle `8.10.2` via the official setup action. For local development use Android Studio + Gradle 8.10.2 and run `bash scripts/fetch-whisper.sh` first.

### Build implementation

- Android Gradle Plugin: `8.7.3`; Kotlin & Compose compiler: `2.0.21`.
- `compileSdk / targetSdk`: `35`; `minSdk`: `29`.
- NDK: `27.2.12479018`; CMake: `3.22.1`; ABI: `arm64-v8a`.
- `whisper.cpp` v1.9.5 source builds as CMake subproject, JNI via `native_bridge.cpp`.
- Language auto-detect or forced `id`, `en`, `ja`, `zh`, `ko`, `es`.

## Privacy and handling

- Pasted URLs are parsed to YouTube video IDs; arbitrary websites are rejected.
- YouTube video playback uses its own servers and policies.
- Hugging Face model download is pinned by SHA-256. Models are stored in app-private storage.
- Capture requires Android consent; stopping or revoking MediaProjection stops the audio service.
- No audio stream is saved by default; transcript is stored in process memory, with explicit manual export only.

## Tests

`gradle :app:testDebugUnitTest` covers YouTube URL validation, PCM buffer behavior, chunk assembly, and caption deduplication. CI runs tests followed by `assembleDebug`.

## Upstream references & licenses

- https://github.com/ggml-org/whisper.cpp (MIT)
- https://huggingface.co/ggerganov/whisper.cpp (Whisper GGML models)
- https://developer.android.com/media/platform/av-capture (playback capture rules)
- https://developers.google.com/youtube/iframe_api_reference (YouTube embedded player)

The ZIP contains our own source and build config, not the upstream Whisper source or model binary. The pinned upstream source and selected model are obtained as described above.

For replacing the old **Ihsan113/Dolby** ClearSound app, see [docs/REPLACING_CLEARSOUND.md](docs/REPLACING_CLEARSOUND.md).
