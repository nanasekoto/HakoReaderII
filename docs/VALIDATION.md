# Validation — 0.7.0 Lite

Successful workflow: https://github.com/nanasekoto/HakoReaderII/actions/runs/37504472355
Production source commit: 22b33e4a47dd62a1a4fb4bb1b4849f70a12e0c76. Later commits add only test diagnostics.

- ARM32 release compiled; 13 native libraries verified as ELF32/ARM, with no other ABI included.
- Delivered APK: 92,486,400 bytes; SHA256 4ae0b20873463bda60512a99ab7934d1c7b3605a6f2e7d74bada2a747e66bc93.
- APK v2 signature verified; signing certificate matches 0.6.4 for an in-place update.
- JVM parser, pagination/regression and hardware-hold tests passed.
- Actual Android 11/API30 emulator installation at 480×800, 2048MB RAM: 145 native checks at DPI219 and 145 at DPI220 passed. Emulator APK is x86_64; shipped APK is ARM32.
- 15 actual Gecko checks passed: HTTPS executor/IPC, shipped extraction script with delayed Vietnamese DOM, cookie shared by HTTP/rendering, cookie persistence after shutdown, actual visible page content through Gecko virtual accessibility nodes, browser handoff/return, and all engine child processes absent before instrumentation ends.
- Public HTTPS page and synthetic chapter fixture were used; no Hako account or credentials.
- Stationary native-reader sample: zero process CPU-time increase and no redraws over three seconds, both locked and unlocked. This is not a physical battery measurement.

## Limits
Live Hako/Cloudflare verification and RAM/battery consumption on the physical S4 remain unverified. Emulator display tests do not measure E-ink ghosting or prove physical key mapping. Existing website follow/read-all commands are preserved, not newly authenticated in this test.

The initial text-search assertion could not find Gecko content via the Android window text-search helper. Walking the virtual accessibility children confirmed the visible page text; the assertion was not removed.

## Historical 0.3.0 validation

### Original 0.3.0 record

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
