#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
gradle --no-daemon assembleRelease -Pabi="${HAKO_BUILD_ABI:-armeabi-v7a}"
mkdir -p build
cp app/build/outputs/apk/release/app-release.apk build/Hako-Pocket-0.7.0-Lite-arm32.apk
jar cf build/classes.jar -C app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes .
