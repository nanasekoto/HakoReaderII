#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/evidence build/avds
export ANDROID_AVD_HOME="$PWD/build/avds"
export ANDROID_SERIAL=emulator-5554
adb start-server
printf 'no\n' | avdmanager create avd -n hako-reading -k 'system-images;android-30;default;x86_64' --force --path "$PWD/build/hako-reading.avd"
printf '\nhw.lcd.width=480\nhw.lcd.height=800\nhw.lcd.density=220\nhw.ramSize=2048\n' >> build/hako-reading.avd/config.ini
"$ANDROID_HOME/emulator/emulator" -avd hako-reading -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -skin 480x800 > build/evidence/emulator.log 2>&1 &
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
adb shell svc wifi disable
adb shell svc data disable
for test in ImageCacheTest ReadingStateTest UnreadStateTest SequentialReadTest LibraryStateTest; do
  adb shell am force-stop vn.nanase.hako
  adb shell pm clear vn.nanase.hako
  timeout 180 adb shell am instrument -w "vn.nanase.hako.tests/vn.nanase.hako.tests.$test" | tee "build/evidence/$test.txt"
  grep -q 'PASS TOTAL' "build/evidence/$test.txt"
done
adb logcat -d -s AndroidRuntime > build/evidence/crashes.txt
