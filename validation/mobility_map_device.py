"""Navigation-only check; never starts GPS, chooses a trip or submits feedback."""
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

BASE = [str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'), '-s', 'RZCY70K63HV']
OUT = Path(__file__).resolve().parents[1] / '.local-backups/mobility-map-0472'
OUT.mkdir(exist_ok=True)
action = sys.argv[1] if len(sys.argv) > 1 else 'inspect'
allowed = {'inspect', 'intro', 'travel', 'recent', 'origin', 'destination', 'search', 'car', 'pt', 'change', 'use_origin', 'close', 'settings', 'apply', 'stop', 'plan', 'stop_cluster', 'destination_cluster', 'radius500', 'radius200', 'menu', 'history', 'open_history'}
if action not in allowed:
    raise ValueError('Only navigation and search are allowed')
subprocess.run(BASE + ['shell', 'uiautomator', 'dump', '/sdcard/cricket-mobility-audit.xml'], capture_output=True, check=True)
xml = subprocess.check_output(BASE + ['exec-out', 'cat', '/sdcard/cricket-mobility-audit.xml'])
tree = ET.fromstring(xml)
nodes = list(tree.iter('node'))
matches = []
target = {'travel':'Travel', 'recent':'Recent places', 'search':'Find routes', 'car':'Travelling by car', 'pt':'Public transport','change':'Change journey','use_origin':'Use at origin · recalculate','close':'Close','settings':'Map settings · Places and radius','apply':'Apply','stop':'H · Hauptbahnhof/Willy-Brandt-Platz','plan':'Plan this itinerary','stop_cluster':'H · 4 · Zoom to explore','destination_cluster':'H · 2 · Zoom to explore'}.get(action)
if action=='radius500':target='500 m'
if action=='radius200':target='200 m'
if action=='menu':target='Menu'
if action=='history':target='Journey history'
if action=='open_history':target='Open journey'
if target:
    matches = [n for n in nodes if n.get('content-desc') == target or n.get('text') == target or action == 'search' and n.get('text','').startswith(target) or action in ('pt','car','stop') and n.get('content-desc','').startswith(target)]
    if action=='stop' and not matches:matches=[n for n in nodes if n.get('content-desc')=='H · Universitätsbibliothek']
elif action in ('origin', 'destination'):
    label = 'Magdeburg Central Station' if action == 'origin' else 'Otto-von-Guericke Business School'
    button = 'Origin' if action == 'origin' else 'Destination'
    indices = [i for i,n in enumerate(nodes) if n.get('text','').startswith(label)]
    if indices:
        matches = [n for n in nodes[indices[-1]+1:] if n.get('text') == button]
if action == 'intro':
    if any(n.get('text') == 'Travel' for n in nodes):
        raise ValueError('Intro is not visible')
    subprocess.run(BASE + ['shell','input','tap','540','1450'], check=True)
elif action != 'inspect':
    if not matches:
        raise ValueError('Navigation target absent: '+action)
    n = matches[0]
    if n.get('enabled') == 'false':
        raise ValueError('Navigation target disabled')
    b = list(map(int,re.findall(r'\d+',n.get('bounds',''))))
    subprocess.run(BASE + ['shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2)],check=True)
    print(json.dumps({'action':action,'tapped':True}))
else:
    print(json.dumps([{'text':n.get('text'),'description':n.get('content-desc'),'bounds':n.get('bounds')} for n in nodes if n.get('text') or n.get('content-desc')]))
(OUT / (action+'.png')).write_bytes(subprocess.check_output(BASE + ['exec-out','screencap','-p']))
(OUT / 'latest.xml').write_bytes(xml)
