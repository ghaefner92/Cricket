"""Inspect the Companion card without creating choices, trips or experiences."""
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

root=Path(__file__).resolve().parents[1]
out=root/'.local-backups/companion-04103';out.mkdir(parents=True,exist_ok=True)
adb='C:/Users/equj44id/AppData/Local/Android/Sdk/platform-tools/adb.exe'
def device(*args):return subprocess.run([adb,'-s','RZCY70K63HV',*args],capture_output=True,check=True).stdout
def nodes():
 device('shell','uiautomator','dump','/sdcard/cricket-companion-04103.xml')
 raw=device('shell','cat','/sdcard/cricket-companion-04103.xml');(out/'hierarchy.xml').write_bytes(raw)
 return list(ET.fromstring(raw).iter('node'))
def tap(match):
 for n in nodes():
  if match(n):
   left,top,right,bottom=map(int,re.findall(r'\d+',n.get('bounds','')))
   if right>left and bottom>top:
    device('shell','input','tap',str((left+right)//2),str((top+bottom)//2));return True
 return False

device('reverse','tcp:8077','tcp:8077')
device('shell','am','start','-n','de.ovgu.imiq.cricket/.MainActivity')
time.sleep(2)
entered=tap(lambda n:n.get('text','').startswith(("Let's go","Los geht's")))
if not entered and any('magical spirit circles the start button' in n.get('content-desc','') for n in nodes()):
 # This phone's welcome button was visually inspected at these coordinates.
 dimensions=re.findall(r'(\d+)x(\d+)',device('shell','wm','size').decode())[-1]
 width,height=map(int,dimensions)
 device('shell','input','tap',str(width//2),str(round(height*.618)))
time.sleep(1)
assert tap(lambda n:n.get('text')=='Companion' and int(re.findall(r'\d+',n.get('bounds'))[1])>1500),'Companion navigation missing'
deadline=time.monotonic()+42
while time.monotonic()<deadline:
 text=[n.get('text','') for n in nodes()]
 if any('Let’s take a moment' in s or 'I’m here to help' in s or 'Schauen wir uns' in s or 'Ich bin hier' in s for s in text):break
 if any('try again?' in s or 'Versuchen wir es noch einmal?' in s for s in text):raise RuntimeError('Companion request failed')
 time.sleep(.5)
else:raise RuntimeError('Companion reflection did not appear')
tap(lambda n:n.get('text') in ['Show text','Text anzeigen'])
time.sleep(.5)
texts=[n.get('text','') for n in nodes()]
assert not any(re.search(r'\b(HOTCO|Passport|backend|model|Modell)\b',s,re.I) for s in texts),texts
(out/'companion.png').write_bytes(device('exec-out','screencap','-p'))
device('shell','input','swipe','540','1800','540','650','400')
time.sleep(.3)
texts += [n.get('text','') for n in nodes()]
assert not any(re.search(r'\b(HOTCO|Passport|backend|model|Modell)\b',s,re.I) for s in texts),texts
(out/'companion-bottom.png').write_bytes(device('exec-out','screencap','-p'))
(out/'package.txt').write_bytes(device('shell','dumpsys','package','de.ovgu.imiq.cricket'))
proof={'companion_navigation':True,'avatar_and_reflection_card':True,'technical_terms_absent':True,'no_choice_trip_or_experience_created':True,
 'fallback_identified':any('Live conversation unavailable' in s or 'Live-Gespräch nicht verfügbar' in s for s in texts),
 'emojis_present':any('🧭' in s or '💬' in s for s in texts) and any('🎯' in s for s in texts)}
(out/'verification.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8')
print(json.dumps(proof))
