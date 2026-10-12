"""Check the origin picker on the real phone without starting a journey."""
from pathlib import Path
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
out = root / '.local-backups/current-origin-04106'
out.mkdir(parents=True, exist_ok=True)
adb = 'C:/Users/equj44id/AppData/Local/Android/Sdk/platform-tools/adb.exe'

def device(*args):
    return subprocess.run([adb, '-s', 'RZCY70K63HV', *args], capture_output=True, check=True).stdout

def nodes(name):
    device('shell', 'uiautomator', 'dump', '/sdcard/cricket-origin.xml')
    raw = device('shell', 'cat', '/sdcard/cricket-origin.xml')
    (out / (name + '.xml')).write_bytes(raw)
    return list(ET.fromstring(raw).iter('node'))

def tap(node):
    left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds', '')))
    device('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

device('shell', 'am', 'start', '-n', 'de.ovgu.imiq.cricket/.MainActivity')
time.sleep(2)
ui = nodes('launch')
start = next((n for n in ui if n.get('text', '').startswith(("Let's go", "Los geht's"))), None)
if start is not None:
    tap(start)
elif any('magical spirit circles the start button' in n.get('content-desc', '') for n in ui):
    width, height = map(int, re.findall(r'(\d+)x(\d+)', device('shell', 'wm', 'size').decode())[-1])
    device('shell', 'input', 'tap', str(width // 2), str(round(height * .618)))
ui = nodes('navigation')
tap(next(n for n in ui if n.get('text') in ('Travel', 'Reise')))
time.sleep(1)
ui = nodes('search')
# The origin label is the first text button to the right of the A control.
origin = next(n for n in ui if n.get('class') == 'android.widget.TextView'
              and n.get('text') and 100 < int(re.findall(r'\d+', n.get('bounds'))[1]) < 260
              and int(re.findall(r'\d+', n.get('bounds'))[0]) > 140)
tap(origin)
ui = nodes('origin-sheet')
clear = next((n for n in ui if n.get('content-desc') in ('Clear address', 'Adresse löschen')), None)
if clear is not None:
    tap(clear)
time.sleep(3)
ui = nodes('empty-origin')
field = next(n for n in ui if n.get('class') == 'android.widget.EditText')
assert not field.get('text'), 'GPS replaced the empty manual origin'
tap(field)
device('shell', 'input', 'text', 'Universitatsplatz%s2%sMagdeburg')
time.sleep(3)
ui = nodes('manual-origin')
field = next(n for n in ui if n.get('class') == 'android.widget.EditText')
assert field.get('text') == 'Universitatsplatz 2 Magdeburg', 'Manual origin was overwritten'
(out / 'manual-origin.png').write_bytes(device('exec-out', 'screencap', '-p'))
choice = next(n for n in ui if n.get('text') in ('My current location', 'Mein aktueller Standort'))
(out / 'origin-sheet.png').write_bytes(device('exec-out', 'screencap', '-p'))
tap(choice)
deadline = time.monotonic() + 30
while time.monotonic() < deadline:
    ui = nodes('after-choice')
    if not any(n.get('text') in ('Origin', 'Start') for n in ui):
        break
    time.sleep(.5)
else:
    raise AssertionError('Origin sheet did not close after a fresh position')
assert any(n.get('text') in ('My current location', 'Mein aktueller Standort') for n in ui)
(out / 'selected-origin.png').write_bytes(device('exec-out', 'screencap', '-p'))
result = {'empty_manual_origin_preserved_during_gps': True, 'manual_address_typing_preserved': True,
          'origin_choice_visible': True, 'fresh_location_selected_as_origin': True, 'sheet_closed': True}
(out / 'verification.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result))
