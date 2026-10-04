#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-../android-sdk}";SDK="$(cd "$SDK" && pwd)";BT="$SDK/build-tools/35.0.0";AJ="$SDK/platforms/android-35/android.jar"
mkdir -p build/test-android/classes build/test-android/dex
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp "$AJ:build/classes.jar:libs/jsoup.jar" -d build/test-android/classes tests/android/SmokeTest.java
jar cf build/test-android/classes.jar -C build/test-android/classes .
"$BT/d8" --lib "$AJ" --classpath build/classes.jar --min-api 26 --output build/test-android/dex build/test-android/classes.jar
"$BT/aapt" package -f -M tests/android/AndroidManifest.xml -I "$AJ" -F build/test-android/unsigned.apk
(cd build/test-android/dex && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/test-android/unsigned.apk build/test-android/aligned.apk
"$BT/apksigner" sign --ks test-signing.p12 --ks-key-alias hako-test --ks-pass pass:android --out build/test-android/tests.apk build/test-android/aligned.apk
