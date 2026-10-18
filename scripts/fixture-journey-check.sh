#!/usr/bin/env bash
set -euo pipefail
# This script only operates an isolated QA package in emulators, never the user's library.
export ANDROID_SERIAL=emulator-5554
export ANDROID_AVD_HOME="$PWD/build/avds"
mkdir -p build/evidence/fixture build/avds
emulator_pid=''
finish_emulator() {
  if [ -n "$emulator_pid" ]; then adb emu kill >/dev/null 2>&1 || true; wait "$emulator_pid" || true; emulator_pid=''; fi
}
trap finish_emulator EXIT
failed=0
for profile in s4 phone; do
  if [ "$profile" = s4 ]; then api=30; variant=default; width=480; height=800; density=220; eink=true
  else api=34; variant=google_apis; width=1080; height=2400; density=420; eink=false; fi
  printf 'no\n' | avdmanager create avd -n "hako-fixture-$profile" -k "system-images;android-$api;$variant;x86_64" --force --path "$PWD/build/hako-fixture-$profile.avd"
  printf '\nhw.lcd.width=%s\nhw.lcd.height=%s\nhw.lcd.density=%s\nhw.ramSize=2048\n' "$width" "$height" "$density" >> "build/hako-fixture-$profile.avd/config.ini"
  "$ANDROID_HOME/emulator/emulator" -avd "hako-fixture-$profile" -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -skin "${width}x${height}" > "build/evidence/fixture/$profile-emulator.log" 2>&1 &
  emulator_pid=$!
  booted=false
  for n in $(seq 1 120); do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then break; fi
    if [ "$(timeout 5 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then booted=true; break; fi
    sleep 2
  done
  if [ "$booted" != true ]; then failed=$((failed+1)); finish_emulator; continue; fi
  adb shell input keyevent 82
  adb shell settings put global window_animation_scale 0
  adb shell settings put global transition_animation_scale 0
  adb shell settings put global animator_duration_scale 0
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb install -r build/test-android/tests.apk
  adb shell svc wifi disable
  adb shell svc data disable
  for scale in 1.0 1.3; do
    adb shell am force-stop vn.nanase.hako.qa
    adb shell pm clear vn.nanase.hako.qa
    adb shell settings put system font_scale "$scale"
    if timeout 180 adb shell am instrument -w -e eink "$eink" vn.nanase.hako.qa.tests/vn.nanase.hako.tests.FixtureJourneyTest | tee "build/evidence/fixture/$profile-font$scale.txt"; then
      if ! grep -q 'PASS TOTAL' "build/evidence/fixture/$profile-font$scale.txt"; then failed=$((failed+1)); fi
    else failed=$((failed+1)); fi
    adb exec-out run-as vn.nanase.hako.qa tar -cf - files/lab-evidence > "build/evidence/fixture/$profile-font$scale.tar" || true
  done
  adb logcat -d -s AndroidRuntime > "build/evidence/fixture/$profile-crashes.txt"
  python3 scripts/cleanup-lab.py --serial emulator-5554
  finish_emulator
done
if [ "$failed" -ne 0 ]; then echo "Fixture configurations failed: $failed"; exit 1; fi
