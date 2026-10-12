"""Capture the welcome sequence without pressing Let's go or changing user data."""
import json
import subprocess
import time
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / '.local-backups/startup-04102'
out.mkdir(parents=True, exist_ok=True)
adb = 'C:/Users/equj44id/AppData/Local/Android/Sdk/platform-tools/adb.exe'
def device(*args):
    return subprocess.run([adb, '-s', 'RZCY70K63HV', *args], capture_output=True, check=True).stdout

device('reverse', 'tcp:8077', 'tcp:8077')
device('shell', 'am', 'start', '-n', 'de.ovgu.imiq.cricket/.MainActivity')
start = time.monotonic()
for target in [1, 2.8, 5, 7.5, 10.5, 14]:
    time.sleep(max(0, target - (time.monotonic() - start)))
    (out / f'startup-{target:04.1f}s.png').write_bytes(device('exec-out', 'screencap', '-p'))
device('shell', 'uiautomator', 'dump', '/sdcard/cricket-startup-04102.xml')
(out / 'hierarchy.xml').write_bytes(device('shell', 'cat', '/sdcard/cricket-startup-04102.xml'))
(out / 'package.txt').write_bytes(device('shell', 'dumpsys', 'package', 'de.ovgu.imiq.cricket'))
graphics = device('shell', 'dumpsys', 'gfxinfo', 'de.ovgu.imiq.cricket')
(out / 'gfxinfo.txt').write_bytes(graphics)
print(json.dumps({'captures': str(out), 'no_button_pressed': True}))
