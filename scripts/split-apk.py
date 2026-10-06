"""Small authorized transfer artifacts; they reassemble the identical signed APK."""
from pathlib import Path
import hashlib
source=Path('build/lite/Hako-Pocket-0.7.0-Lite-arm32.apk')
out=source.parent/'parts';out.mkdir(exist_ok=True)
digest=hashlib.sha256()
with source.open('rb') as f:
    n=1
    while chunk:=f.read(24*1024*1024):
        (out/f'part-{n:02d}').write_bytes(chunk);digest.update(chunk);n+=1
assert n<=17, 'Unexpected APK size'
(source.parent/'SHA256.txt').write_text(digest.hexdigest()+'  '+source.name+'\n')
print('APK bytes:',source.stat().st_size,'transfer parts:',n-1,'SHA256:',digest.hexdigest())
