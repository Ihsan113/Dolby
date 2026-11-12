# Test plan • ClearSound v0.1.0

## Build & static checks
- [ ] Run GitHub Actions `testDebugUnitTest`; 3 EqCurve tests pass.
- [ ] Run `assembleDebug`; APK generated; install succeeds.
- [ ] Launch Android 13/14/15 with Shizuku **not installed**: app must open without crash.
- [ ] Launch in both system light and dark modes; test 320dp, 360dp, 393dp, 480dp width.
- [ ] Confirm onboarding scrolls on short screens and text/buttons aren't clipped.

## Setup/onboarding
- [ ] Fresh install shows step 1, next 2, then step 3.
- [ ] Skip optional Shizuku, app still reaches home.
- [ ] With Shizuku running, ask for permission; accept and verify state changes.
- [ ] With permission denied, show prompt/info without crash.
- [ ] Test whitelist request (granted/rejected) and non-root ADB identity behavior.
- [ ] Test notification permission grant/deny.
- [ ] Test add-tile prompt Android 13+, and manual instruction for older Android.

## Audio functionality
- [ ] With ROM supporting session 0, enable/disable from Home and Quick Settings.
- [ ] Verify original equalizer values restored/released on stop (not guaranteed globally for other apps' own EQs).
- [ ] Verify on headphone, speaker and Bluetooth routes; compare at same volume.
- [ ] Change preset while active; hear audible change if effect supported.
- [ ] Move 10 logical band sliders and verify mapping when hardware exposes 5 bands.
- [ ] Simulate two equalizer apps conflicting; ensure ClearSound reports error instead of displaying success.
- [ ] Deny notifications, force stop, reboot, low battery saver; ensure UI doesn't persist fictitious enabled state.
- [ ] Check no harsh clipping on Bass with mid/high volumes.

## Store readiness (not part of v0.1 acceptance)
- [ ] Investigate FGS `specialUse` policy with Play Console.
- [ ] Privacy policy and Data Safety form.
- [ ] Upload store assets and conduct closed testing.
- [ ] Test against manufacturer-specific firmware restrictions.
