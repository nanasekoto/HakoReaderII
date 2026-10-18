#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-../android-sdk}}";SDK="$(cd "$SDK" && pwd)";BT="$SDK/build-tools/35.0.0";AJ="$SDK/platforms/android-35/android.jar"
mkdir -p build/test-android/classes build/test-android/dex
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp "$AJ:build/classes.jar:libs/jsoup.jar" -d build/test-android/classes tests/android/*.java
jar cf build/test-android/classes.jar -C build/test-android/classes .
"$BT/d8" --lib "$AJ" --classpath build/classes.jar --min-api 26 --output build/test-android/dex build/test-android/classes.jar
TARGET_PACKAGE="${HAKO_TEST_PACKAGE:-vn.nanase.hako}"
if [ "$TARGET_PACKAGE" != vn.nanase.hako ] && [ "$TARGET_PACKAGE" != vn.nanase.hako.qa ]; then echo 'Unsupported test target'; exit 1; fi
mkdir -p build/test-android/manifest
python3 - "$TARGET_PACKAGE" <<'PY'
from pathlib import Path
import sys
s=Path('tests/android/AndroidManifest.xml').read_text()
if sys.argv[1]=='vn.nanase.hako.qa':
 s=s.replace('package="vn.nanase.hako.tests"','package="vn.nanase.hako.qa.tests"').replace('android:targetPackage="vn.nanase.hako"','android:targetPackage="vn.nanase.hako.qa"')
Path('build/test-android/manifest/AndroidManifest.xml').write_text(s)
PY
"$BT/aapt" package -f -M build/test-android/manifest/AndroidManifest.xml -I "$AJ" -F build/test-android/unsigned.apk
(cd build/test-android/dex && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/test-android/unsigned.apk build/test-android/aligned.apk
"$BT/apksigner" sign --ks test-signing.p12 --ks-key-alias hako-test --ks-pass pass:android --out build/test-android/tests.apk build/test-android/aligned.apk

