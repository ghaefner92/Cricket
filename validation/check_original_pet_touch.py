"""Decorative pet gestures only: never edits goals, chooses trips or submits feedback."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
import re

adb = [str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'), '-s', 'RZCY70K63HV']
out = Path(__file__).resolve().parents[1] / '.local-backups/pet-0480-visual'
out.mkdir(exist_ok=True)
for action in ('joy', 'curiosity'):
    subprocess.run(adb + ['shell', 'uiautomator', 'dump', '/sdcard/pet-0480.xml'], capture_output=True, check=True)
    xml = subprocess.check_output(adb + ['exec-out', 'cat', '/sdcard/pet-0480.xml'])
    tree = ET.fromstring(xml)
    node = next(n for n in tree.iter('node') if 'Original full-body sprite' in n.get('content-desc', ''))
    left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
    x, y = (left + right) // 2, (top + bottom) // 2
    if action == 'joy':
        subprocess.run(adb + ['shell', 'input', 'tap', str(x), str(y)], check=True)
    else:
        subprocess.run(adb + ['shell', 'input', 'swipe', str(x), str(y), str(x), str(y), '700'], check=True)
    (out / f'{action}.png').write_bytes(subprocess.check_output(adb + ['exec-out', 'screencap', '-p']))
    print(f'{action}: decorative gesture and screenshot captured')
