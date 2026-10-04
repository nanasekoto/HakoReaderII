# Validation — 0.3.0

## Executed
- 18 JVM parser assertions: source order, URL boundaries, Vietnamese text, sanitization, notes and image hosts.
- 3215 regression assertions: empty/unrendered protected chapter rejection, Hako data-unread count, rolling window, progressive delay, 200 deterministic variable-line pagination cases with resume boundaries.
- JavaScript syntax check of extract.js.
- Android application compilation / dex / resource packaging.
- Android instrumentation compilation and APK packaging (not execution).
- APK signature verification v2/v3; development signing certificate compared with prior 0.1 APK.

## Not executed
- Android instrumentation: no attached device (adb devices empty); no installed runnable emulator/system image; /dev/kvm absent.
- Real chapter loading inside this APK on Android, WebView login, follow, mark-read, renderer history restoration.
- S4 physical key mapping, E Ink ghosting/full refresh, memory/battery/performance measurements.

Tests under tests/android/ are executable instructions for future device validation, not evidence of a passed Android test.

## Website evidence used to implement behavior
- Authenticated shelf labels count as chapters since last mark-read.
- Website .mark-read uses POST /action/series/usermarkread with series_id and CSRF; app operates website control and waits for success UI.
- Viewing Rebuild World latest chapter left unread shelf count at 17 in live browser test.
- Series CSS uses a:visited for gray links. History page reads localStorage reading_series.
- These observations establish website behavior, not runtime validation of the new APK.

## Remaining risks
Website selectors or markup may change. Native HTML conversion simplifies advanced layout. Renderer shares normal WebView origin storage, so conditional history restoration still requires concurrency testing. Prefetch pause is cooperative between chapters. No claim of spam avoidance or measured resource savings.
