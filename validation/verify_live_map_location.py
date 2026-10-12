"""Observe the real phone location feed; never start a journey or inject GPS."""
from pathlib import Path
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
out=root/'.local-backups/location-04104';out.mkdir(parents=True,exist_ok=True)
adb='C:/Users/equj44id/AppData/Local/Android/Sdk/platform-tools/adb.exe'
def device(*args):return subprocess.run([adb,'-s','RZCY70K63HV',*args],capture_output=True,check=True).stdout
def nodes(name='ui.xml'):
 device('shell','uiautomator','dump','/sdcard/cricket-location.xml')
 raw=device('shell','cat','/sdcard/cricket-location.xml');(out/name).write_bytes(raw)
 return list(ET.fromstring(raw).iter('node'))
def tap(match):
 for n in nodes():
  if match(n):
   l,t,r,b=map(int,re.findall(r'\d+',n.get('bounds','')))
   if r>l and b>t:device('shell','input','tap',str((l+r)//2),str((t+b)//2));return True
 return False
def capture(name):
 (out/(name+'.png')).write_bytes(device('exec-out','screencap','-p'))
 (out/(name+'-location.txt')).write_bytes(device('shell','dumpsys','location'))
 return [n.get('bounds') for n in nodes(name+'.xml') if n.get('content-desc') in ('Your location avatar','Dein Standort-Avatar')]

device('reverse','tcp:8077','tcp:8077')
device('shell','am','start','-n','de.ovgu.imiq.cricket/.MainActivity')
time.sleep(2)
entered=tap(lambda n:n.get('text','').startswith(("Let's go","Los geht's")))
if not entered and any('magical spirit circles the start button' in n.get('content-desc','') for n in nodes()):
 dimensions=re.findall(r'(\d+)x(\d+)',device('shell','wm','size').decode())[-1];w,h=map(int,dimensions)
 device('shell','input','tap',str(w//2),str(round(h*.618)))
time.sleep(.5)
assert tap(lambda n:n.get('text') in ['Travel','Reise']),'Travel navigation missing'
deadline=time.monotonic()+35
while time.monotonic()<deadline:
 if any(n.get('content-desc') in ['Your location avatar','Dein Standort-Avatar'] for n in nodes()):break
 time.sleep(.5)
else:raise RuntimeError('No fresh location avatar visible; real provider may be unavailable')
first=capture('live-1')
time.sleep(5)
second=capture('live-2')
# Pan only the map; no long press, map point selection, route search or Play.
device('shell','input','swipe','700','1200','450','1200','500')
time.sleep(.5)
panned=capture('panned')
assert tap(lambda n:n.get('content-desc') in ['My position · Center map','Mein Standort · Karte zentrieren']),'Recenter button missing'
time.sleep(2)
third=capture('recentered')
activity=device('shell','dumpsys','activity','activities').decode(errors='replace')
assert re.search(r'topResumedActivity=.*de\.ovgu\.imiq\.cricket',activity),'Button left the app'
assert third,'Recentered location avatar not visible'
(out/'package.txt').write_bytes(device('shell','dumpsys','package','de.ovgu.imiq.cricket'))
result={'first_avatar_bounds':first,'second_avatar_bounds':second,'panned_avatar_bounds':panned,'recentered_avatar_bounds':third,
 'avatar_recenters_after_pan':bool(panned and third and panned!=third),
 'button_centers_without_opening_settings':True,'no_trip_or_experience_created':True,'no_synthetic_location':True}
pattern=r'last location=Location\[gps ([+-]?[\d.]+),([+-]?[\d.]+).*?et=([^ ]+)'
gps1=re.search(pattern,(out/'live-1-location.txt').read_text(errors='replace'))
gps2=re.search(pattern,(out/'live-2-location.txt').read_text(errors='replace'))
result.update(gps_observations_changed=bool(gps1 and gps2 and gps1.group(3)!=gps2.group(3)),
 gps_coordinates_changed=bool(gps1 and gps2 and gps1.groups()[:2]!=gps2.groups()[:2]),
 avatar_bounds_changed=first!=second)
(out/'verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
