# Android QA checklist

## Functional

- Fresh install: 3-page onboarding appears once, then shows main page.
- Paste valid watch/short/live/youtu.be URL -> video loads in embedded YouTube player.
- Paste malicious/fake URL -> error and no external browsing.
- Select Tiny or Base, download -> progress, SHA-256 verification, ready state.
- Disconnect internet *after model download* and feed microphone sound -> ASR still runs offline.
- Start Audio perangkat -> permission prompt -> service shows a persistent notification.
- On a permissive player/ROM, see "Audio terdeteksi" and actual local captions.
- Blocked audio: after 12s no audio, informative message; manually stop, switch mic.
- Try microphone through speaker; play and recognize speech.
- Change language preset; listen for output; listen through headphones (mic fallback limitations).
- Stop -> foreground notification disappears; mic/audio recording ends.
- Deny record permission and cancel projection -> no bogus active state.
- Switch video while recording -> action blocked rather than stale subtitles.
- Save SRT and read as plain text; note timestamps are session-relative.

## Android compatibility

- Android 10, 13 and 15; primary test is POCO M5 Android 13 custom ROM.
- Verify Youtube embed errors (YouTube may reject embedded video/DRM).
- Verify MediaProjection consent and usage captures actual nonzero signal.
- Test foreground-service behavior under battery optimization.
- Try Android system theme dark and light, 360dp viewport, large font scaling.

## Quality gates

- Verify native engine sample on a WAV-fed test harness if using Android Studio; no claim that model accuracy is proven by JVM-only unit tests.
- Subtitle latency from playback is variable; do not call the system "zero latency".
- If inference is slower than the capture window, show delay to user and choose Tiny over Base.
- Tune energy threshold only after comparing silence, speech and music recordings.

## Privacy

- All audio analyzed locally. Capturing YouTube playback can be restricted by its media capture policy.
- Do not add bypass/DRM defeat, unauthorized video extraction or downloading, or private interception.
