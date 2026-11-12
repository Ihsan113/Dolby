# Architecture • ClearSound

```
MainActivity / Compose UI
  ├─ OnboardingWizard (3 steps)
  ├─ Home (one-tap engine toggle)
  ├─ Equalizer (10-band logical curve & 5 presets)
  └─ Settings (Shizuku / battery / tile)
         │
         ├── SoundStore → SharedPreferences (preset, band gains, onboarding)
         │
         ├── AudioEngineService → SystemAudioEngine → Equalizer(session = 0)
         │                       ↘ AudioRuntime (active/error/native band count)
         │
         ├── SoundQuickTile ↔ AudioEngineService
         │
         └── ShizukuBridge → ShellToolsService (ADB shell UserService)
                                 └─ cmd deviceidle whitelist +package
```

Important design limits:

1. `AudioRuntime` is **process-local**; when process dies, active defaults to false. `START_NOT_STICKY` is used so engine isn't silently shown as active after a restart.
2. `SystemAudioEngine` owns exactly one `Equalizer` instance for entire life of the foreground service; always releases it in `onDestroy` and explicit STOP.
3. `EqCurve` is pure Kotlin logic: points are mapped logarithmically from 10 UI handles to whatever hardware band centers are enumerated by AudioEffect. Frequencies in the Android API use milliHertz.
4. Shizuku **is not required to toggle Session 0**. Its optional role is to invoke `deviceidle` to request a background whitelist with ADB shell identity and only after user taps the action. Actual success depends on vendor restrictions.
5. No audio buffer interception or audio recording, so no raw signal compressor/limiter. Do not market it as lossless, Dolby-certified or guaranteed global.
6. User-triggered foreground service uses the `specialUse` type for a persistent equalizer; Play Store review still necessary.
