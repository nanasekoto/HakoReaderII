#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
bash test.sh
gradle --no-daemon assembleRelease -Pabi="${HAKO_BUILD_ABI:-armeabi-v7a}" "$@"
version=$(sed -n 's/^VERSION_NAME=//p' version.properties | tr -d '\r')
mkdir -p build/deliver
cp app/build/outputs/apk/release/app-release.apk "build/deliver/Hako-Pocket-${version}.apk"
echo "APK: build/deliver/Hako-Pocket-${version}.apk"
