"""Remove only Hako QA packages from one explicitly named Android device."""
import argparse
import shutil
import subprocess

PACKAGES = ('vn.nanase.hako.qa.tests', 'vn.nanase.hako.qa')
parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default=shutil.which('adb') or 'adb')
parser.add_argument('--data-only', action='store_true')
args = parser.parse_args()
for package in PACKAGES:
    assert package.startswith('vn.nanase.hako.qa') and package != 'vn.nanase.hako'
    installed = subprocess.check_output([args.adb, '-s', args.serial, 'shell', 'pm', 'list', 'packages', package], text=True)
    if 'package:' + package not in installed.splitlines():
        print(package + ': absent')
        continue
    command = ['shell', 'pm', 'clear', package] if args.data_only else ['uninstall', package]
    result = subprocess.run([args.adb, '-s', args.serial, *command], text=True, capture_output=True, check=True)
    if 'Success' not in result.stdout:
        raise RuntimeError(package + ': cleanup failed')
    print(package + ': ' + ('data cleared' if args.data_only else 'uninstalled'))
