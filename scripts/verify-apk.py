"""Verify the shipped S4 APK contains the complete ARM32 Gecko engine."""
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as apk:
    libraries = [name for name in apk.namelist() if name.startswith("lib/") and name.endswith(".so")]
    assert libraries and any(name.endswith("/libxul.so") for name in libraries), "Missing Gecko engine"
    for name in libraries:
        assert name.startswith("lib/armeabi-v7a/"), f"Unexpected ABI: {name}"
        header = apk.read(name)[:20]
        assert header[:4] == b"\x7fELF" and header[4] == 1, f"Not ELF32: {name}"
        assert int.from_bytes(header[18:20], "little") == 40, f"Not ARM: {name}"
    print(f"PASS: {len(libraries)} ELF32 ARM libraries")
