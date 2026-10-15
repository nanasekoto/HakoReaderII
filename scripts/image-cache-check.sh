#!/usr/bin/env bash
set -euo pipefail
export ANDROID_SERIAL=emulator-5556
export ANDROID_AVD_HOME="$PWD/build/image-cache-avds"
mkdir -p build/image-cache-evidence "$ANDROID_AVD_HOME"
adb start-server
printf 'no\n' | avdmanager create avd -n image-cache -k 'system-images;android-30;default;x86_64' --force --path "$PWD/build/image-cache.avd"
"$ANDROID_HOME/emulator/emulator" -avd image-cache -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > build/image-cache-evidence/emulator.log 2>&1 &
emulator_pid=$!
trap 'adb -s emulator-5556 emu kill || true' EXIT
booted=false
for n in $(seq 1 120); do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then cat build/image-cache-evidence/emulator.log; exit 1; fi
  if [ "$(timeout 5 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then booted=true; break; fi
  sleep 2
done
[ "$booted" = true ]
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r build/test-android/tests.apk
timeout 90 adb shell am instrument -w vn.nanase.hako.tests/vn.nanase.hako.tests.ImageCacheTest | tee build/image-cache-evidence/test.txt
grep -q 'PASS TOTAL' build/image-cache-evidence/test.txt
