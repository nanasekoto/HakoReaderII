"""Run one isolated QA journey; never install/clear the production package."""
import argparse
import io
import json
import re
import shutil
import subprocess
import tarfile
import time
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apks', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--serial', required=True)
parser.add_argument('--eink', action='store_true')
parser.add_argument('--no-install', action='store_true')
parser.add_argument('--adb', default=shutil.which('adb') or 'adb')
parser.add_argument('--aapt', default=shutil.which('aapt') or 'aapt')
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)


def adb(*command, timeout=180):
    return subprocess.run([args.adb, '-s', args.serial, *command], capture_output=True, timeout=timeout)


def version(raw):
    value = re.search(rb'versionCode=(\d+)', raw)
    return value.group(1).decode() if value else None


summary = {'serial': args.serial, 'pass': False, 'screenshots': 0, 'errors': []}
before = adb('shell', 'dumpsys', 'package', 'vn.nanase.hako').stdout
(args.output / 'production-before.txt').write_bytes(before)
try:
    for name, package in [('Hako-Pocket-Lab.apk', 'vn.nanase.hako.qa'),
                          ('Hako-Pocket-Lab-tests.apk', 'vn.nanase.hako.qa.tests')]:
        apk = args.apks / name
        badge = subprocess.check_output([args.aapt, 'dump', 'badging', str(apk)])
        if ("package: name='" + package + "'").encode() not in badge:
            raise ValueError('Only isolated QA packages are permitted')
        if not args.no_install:
            installed = adb('install', '--no-streaming', '-r', '-t', str(apk))
            (args.output / (name + '.install.txt')).write_bytes(installed.stdout + installed.stderr)
            if installed.returncode or b'Success' not in installed.stdout:
                raise RuntimeError('USB installation was not accepted: ' + name)
    # MIUI may block an instrumentation-launched activity from the background.
    # Bring the already-authorized QA launcher forward explicitly first.
    opened = adb('shell', 'am', 'start', '-n', 'vn.nanase.hako.qa/vn.nanase.hako.FixtureLabActivity')
    if opened.returncode:
        raise RuntimeError('Unable to foreground the QA application')
    for attempt in range(10):
        state = adb('shell', 'dumpsys', 'activity', 'activities').stdout
        resumed = b'\n'.join(line for line in state.splitlines() if b'ResumedActivity' in line or b'topResumedActivity' in line)
        if b'vn.nanase.hako.qa/' in resumed:
            break
        time.sleep(.5)
    else:
        raise RuntimeError('QA window is not foreground; unlock the device and leave it idle')
    result = adb('shell', 'am', 'instrument', '-w', '-e', 'eink', str(args.eink).lower(),
                 'vn.nanase.hako.qa.tests/vn.nanase.hako.tests.FixtureJourneyTest')
    (args.output / 'result.txt').write_bytes(result.stdout + result.stderr)
    summary['pass'] = result.returncode == 0 and b'PASS TOTAL' in result.stdout and b'CASE FAIL' not in result.stdout
    evidence = adb('exec-out', 'run-as', 'vn.nanase.hako.qa', 'tar', '-cf', '-', 'files/lab-evidence')
    (args.output / 'evidence.tar').write_bytes(evidence.stdout)
    try:
        with tarfile.open(fileobj=io.BytesIO(evidence.stdout)) as archive:
            for entry in archive.getmembers():
                if entry.issym() or entry.islnk() or not (args.output / entry.name).resolve().is_relative_to(args.output.resolve()):
                    raise ValueError('Unsafe evidence path')
            archive.extractall(args.output, filter='data')
        summary['screenshots'] = len(list(args.output.rglob('*.png')))
        if not summary['screenshots']:
            raise ValueError('No rendered-window evidence was saved')
    except (tarfile.TarError, ValueError) as error:
        summary['pass'] = False
        summary['errors'].append('Missing/invalid window evidence: ' + str(error))
except (subprocess.SubprocessError, OSError, ValueError, RuntimeError) as error:
    summary['errors'].append(str(error))
finally:
    after = adb('shell', 'dumpsys', 'package', 'vn.nanase.hako').stdout
    (args.output / 'production-after.txt').write_bytes(after)
    summary['production_version_unchanged'] = version(before) is not None and version(before) == version(after)
    if not summary['production_version_unchanged']:
        summary['pass'] = False
        summary['errors'].append('Production package identity could not be preserved/verified')
    (args.output / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
print(json.dumps(summary))
raise SystemExit(0 if summary['pass'] else 1)
