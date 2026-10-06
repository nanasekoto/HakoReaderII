#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/evidence
export ANDROID_AVD_HOME="$PWD/build/avds"
mkdir -p "$ANDROID_AVD_HOME"
adb start-server
printf 'no\n' | avdmanager create avd -n hako-check -k 'system-images;android-30;default;x86_64' --force --path "$PWD/build/hako-check.avd"
printf '\nhw.lcd.width=480\nhw.lcd.height=800\nhw.lcd.density=220\nhw.ramSize=2048\n' >> "$PWD/build/hako-check.avd/config.ini"
"$ANDROID_HOME/emulator/emulator" -avd hako-check -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -skin 480x800 > build/evidence/emulator.log 2>&1 &
emulator_pid=$!
trap 'adb emu kill || true' EXIT
booted=false
for n in $(seq 1 120); do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then cat build/evidence/emulator.log; exit 1; fi
  if [ "$(timeout 5 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then booted=true; break; fi
  sleep 2
done
if [ "$booted" != true ]; then tail -80 build/evidence/emulator.log; exit 1; fi
adb shell input keyevent 82
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r build/test-android/tests.apk
failed=0
for density in 219 220; do
  adb shell am force-stop vn.nanase.hako
  adb shell pm clear vn.nanase.hako
  adb shell dumpsys battery unplug
  adb shell wm size 480x800
  adb shell wm density "$density"
  sleep 2
  mkdir -p "build/evidence/dpi-${density}"
  adb shell wm size > "build/evidence/dpi-${density}/display.txt"
  adb shell wm density >> "build/evidence/dpi-${density}/display.txt"
  timeout 120 adb shell am instrument -w vn.nanase.hako.tests/vn.nanase.hako.tests.SmokeTest | tee "build/evidence/dpi-${density}/instrumentation.txt" || failed=1
  if ! grep -q 'PASS TOTAL' "build/evidence/dpi-${density}/instrumentation.txt"; then failed=1; fi
  timeout 10 adb exec-out screencap -p > "build/evidence/dpi-${density}/last-screen.png" || true
  adb exec-out run-as vn.nanase.hako tar -C files/ui-evidence -cf - . > "build/evidence/dpi-${density}/screenshots.tar" || true
  adb logcat -d -s HakoKeys > "build/evidence/dpi-${density}/keys.txt"
  adb logcat -d -s AndroidRuntime > "build/evidence/dpi-${density}/crashes.txt"
done
adb shell am force-stop vn.nanase.hako
adb shell pm clear vn.nanase.hako
timeout 240 adb shell am instrument -w vn.nanase.hako.tests/vn.nanase.hako.tests.GeckoSmokeTest | tee build/evidence/gecko-engine.txt || failed=1
if ! grep -q 'PASS TOTAL' build/evidence/gecko-engine.txt; then failed=1; fi
adb logcat -d -s AndroidRuntime GeckoConsole GeckoView | tail -400 > build/evidence/gecko-log.txt
adb shell ps -A > build/evidence/processes-after-gecko.txt
if grep -q 'vn.nanase.hako:' build/evidence/processes-after-gecko.txt; then
  echo 'FAIL Gecko child process remains after engine shutdown' | tee -a build/evidence/gecko-engine.txt
  failed=1
fi
exit "$failed"
