"""Capture Experience; optionally navigate or restore the user's visible draft choice.

Never selects a rating, starts GPS, or confirms/saves an experience.
"""
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding='utf-8')

base = ['C:/Users/equj44id/AppData/Local/Android/Sdk/platform-tools/adb.exe', '-s', 'RZCY70K63HV']
subprocess.run(base + ['shell', 'uiautomator', 'dump', '/sdcard/cricket-experience-ui.xml'], check=True, capture_output=True)
nodes = list(ET.fromstring(subprocess.check_output(base + ['exec-out', 'cat', '/sdcard/cricket-experience-ui.xml'])).iter('node'))
target = sys.argv[1] if len(sys.argv) > 1 else None
if target:
    if target not in ['Open', 'Yes, as planned']:
        raise ValueError('Only navigation or the previously observed draft choice is allowed')
    matches = [n for n in nodes if n.get('text', '').removeprefix('✓ ') == target]
    if len(matches) != 1:
        raise ValueError('Expected a unique visible target')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', matches[0].get('bounds', '')))
    subprocess.run(base + ['shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)], check=True)
else:
    print('\n'.join(n.get('text') or n.get('content-desc', '') for n in nodes if n.get('text') or n.get('content-desc')))
out = pathlib.Path('.local-backups/experience-0468')
out.mkdir(exist_ok=True)
(out / 'screen.png').write_bytes(subprocess.check_output(base + ['exec-out', 'screencap', '-p']))
