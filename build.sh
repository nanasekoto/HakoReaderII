#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-../android-sdk}}"
SDK="$(cd "$SDK" && pwd)"
BT="$SDK/build-tools/35.0.0"
AJ="$SDK/platforms/android-35/android.jar"
mkdir -p build/classes build/gen build/dex build/apk
source version.properties
python3 - <<'MANIFEST'
from pathlib import Path
import xml.etree.ElementTree as ET
v = dict(line.split('=', 1) for line in Path('version.properties').read_text().splitlines() if '=' in line)
ns = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', ns)
r = ET.parse('app/src/main/AndroidManifest.xml').getroot()
r.set('package', 'vn.nanase.hako')
r.set('{'+ns+'}versionCode', v['VERSION_CODE'])
r.set('{'+ns+'}versionName', v['VERSION_NAME'])
ET.SubElement(r, 'uses-sdk', {'{'+ns+'}minSdkVersion': '26', '{'+ns+'}targetSdkVersion': '34'})
ET.ElementTree(r).write('build/AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
MANIFEST
APK="build/Hako-Pocket-${VERSION_NAME}.apk"
"$BT/aapt" package -f -m -J build/gen -M build/AndroidManifest.xml -S app/src/main/res -A app/src/main/assets -I "$AJ" -F build/resources.apk
find app/src/main/java build/gen -name '*.java' > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -classpath "$AJ:libs/jsoup.jar" -d build/classes @build/sources.txt
jar cf build/classes.jar -C build/classes .
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('libs/jsoup.jar') as src,zipfile.ZipFile('build/jsoup-android.jar','w') as out:
 for n in src.namelist():
  if n.endswith('.class') and not n.startswith('META-INF/') and n != 'module-info.class':out.writestr(n,src.read(n))
PY
"$BT/d8" --lib "$AJ" --min-api 26 --output build/dex build/classes.jar build/jsoup-android.jar
cp build/resources.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes*.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
# Local testing key, NOT suitable for publishing. Keep the same key to install updates.
if [ ! -f test-signing.p12 ]; then
 keytool -genkeypair -keystore test-signing.p12 -storetype PKCS12 -storepass android -keypass android -alias hako-test -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Hako Pocket Local Test' >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks test-signing.p12 --ks-key-alias hako-test --ks-pass pass:android --key-pass pass:android --out "$APK" build/aligned.apk
"$BT/apksigner" verify --verbose "$APK"
ls -lh "$APK"

