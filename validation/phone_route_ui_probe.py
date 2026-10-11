"""Inspect presentation; taps are limited to navigation/search, never GPS/start."""
import json
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ADB = 'C:/Users/equj44id/AppData/Local/Android/Sdk/platform-tools/adb.exe'
BASE = [ADB, '-s', 'RZCY70K63HV']
target = sys.argv[1] if len(sys.argv) > 1 else None
allowed = ["Let's go", 'Travel', 'Find routes', 'Car']
if target is not None and target not in allowed:
    raise ValueError('Only navigation and search may be tapped')
subprocess.run(BASE + ['shell', 'uiautomator', 'dump', '/sdcard/cricket-ui-audit.xml'], check=True, capture_output=True)
xml = subprocess.check_output(BASE + ['exec-out', 'cat', '/sdcard/cricket-ui-audit.xml'])
tree = ET.fromstring(xml)
nodes = list(tree.iter('node'))
if target:
    matches = [node for node in nodes if target in node.get('text', '') or node.get('content-desc', '') == target]
    if not matches:
        print(json.dumps({'tapped': False, 'reason': 'Target not on screen', 'target': target}))
    else:
        numbers = list(map(int, re.findall(r'\d+', matches[0].get('bounds', ''))))
        if len(numbers) != 4: raise ValueError('Missing bounds')
        subprocess.run(BASE + ['shell', 'input', 'tap', str((numbers[0]+numbers[2])//2), str((numbers[1]+numbers[3])//2)], check=True)
        print(json.dumps({'tapped': True, 'target': target}))
else:
    print(json.dumps([{'text': node.get('text', ''), 'description': node.get('content-desc', '')}
                      for node in nodes if any(value in (node.get('text', '') + node.get('content-desc', ''))
                                              for value in allowed + ['geometry', 'walking', 'access', 'min', 'Simulated'])]))
path = pathlib.Path(__file__).resolve().parents[1] / '.local-backups/auto-goals-audit/ui-0465.png'
path.write_bytes(subprocess.check_output(BASE + ['exec-out', 'screencap', '-p']))
