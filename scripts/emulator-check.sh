#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/evidence
adb start-server
printf 'no\n' | avdmanager create avd -n hako-check -k 'system-images;android-30;default;x86_64' --force
printf '\nhw.lcd.width=480\nhw.lcd.height=800\nhw.lcd.density=220\nhw.ramSize=2048\n' >> "$HOME/.android/avd/hako-check.avd/config.ini"
"$ANDROID_HOME/emulator/emulator" -avd hako-check -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -skin 480x800 > build/evidence/emulator.log 2>&1 &
trap 'adb emu kill || true' EXIT
booted=false
for n in $(seq 1 120); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then booted=true; break; fi
  sleep 2
done
if [ "$booted" != true ]; then tail -80 build/evidence/emulator.log; exit 1; fi
adb shell input keyevent 82
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
source version.properties
adb install -r "build/Hako-Pocket-${VERSION_NAME}.apk"
adb install -r build/test-android/tests.apk
failed=0
for density in 160 220 240; do
  adb shell am force-stop vn.nanase.hako
  adb shell pm clear vn.nanase.hako
  adb shell wm size 480x800
  adb shell wm density "$density"
  sleep 2
  mkdir -p "build/evidence/dpi-${density}"
  adb shell wm size > "build/evidence/dpi-${density}/display.txt"
  adb shell wm density >> "build/evidence/dpi-${density}/display.txt"
  adb shell am instrument -w vn.nanase.hako.tests/vn.nanase.hako.tests.SmokeTest | tee "build/evidence/dpi-${density}/instrumentation.txt"
  if ! grep -q 'PASS TOTAL' "build/evidence/dpi-${density}/instrumentation.txt"; then failed=1; fi
  adb pull /sdcard/Android/data/vn.nanase.hako/files/ui-evidence "build/evidence/dpi-${density}/" || true
  adb logcat -d -s AndroidRuntime > "build/evidence/dpi-${density}/crashes.txt"
done
exit "$failed"
