"""Resolve newest stable ARMv7 Gecko supporting the installed non-preview SDK.
Run only when gecko-version.txt is absent; successful version is then committed.
"""
import re, urllib.request, xml.etree.ElementTree as ET, zipfile, pathlib, tempfile
p = pathlib.Path('gecko-version.txt')
if p.exists():
    print('Pinned Gecko:', p.read_text().strip())
    raise SystemExit(0)
base = 'https://maven.mozilla.org/maven2/org/mozilla/geckoview/'
meta = ET.fromstring(urllib.request.urlopen(base+'geckoview-armeabi-v7a/maven-metadata.xml', timeout=60).read())
versions = sorted((v.text for v in meta.findall('.//version') if re.fullmatch(r'\d+\.0\.\d+', v.text)), key=lambda s:tuple(map(int,s.split('.'))), reverse=True)
seen=set()
for v in versions:
    major=v.split('.')[0]
    if major in seen: continue
    seen.add(major)
    if int(major)<140: raise RuntimeError('No recent compatible Gecko stable found')
    artifact='geckoview-armeabi-v7a'
    url=f'{base}{artifact}/{v}/{artifact}-{v}.aar'
    with tempfile.TemporaryFile() as f:
        with urllib.request.urlopen(url,timeout=120) as r:
            while chunk:=r.read(1024*1024): f.write(chunk)
        f.seek(0)
        with zipfile.ZipFile(f) as z:
            info=z.read('META-INF/com/android/build/gradle/aar-metadata.properties').decode()
            sdk=int(re.search(r'^minCompileSdk=(\d+)',info,re.M).group(1))
            print('Candidate',v,'minCompileSdk',sdk,flush=True)
            assert any(n=='jni/armeabi-v7a/libxul.so' for n in z.namelist()),'Missing ARM32 engine'
    if sdk<=36:
        p.write_text(v+'\n');print('Selected',v,flush=True);break
else: raise RuntimeError('No compatible stable version')
