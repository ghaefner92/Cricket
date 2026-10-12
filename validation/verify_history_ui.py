"""Navigate to existing history/explanation only; never start or confirm a trip."""
import subprocess,time,re,json
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.local-backups/history-repair/ui-04101';OUT.mkdir(parents=True,exist_ok=True)
ADB='C:/Users/equj44id/AppData/Local/Android/Sdk/platform-tools/adb.exe'
def adb(*args):return subprocess.run([ADB,'-s','RZCY70K63HV',*args],capture_output=True,check=True).stdout
def nodes():
 adb('shell','uiautomator','dump','/sdcard/history-repair-ui.xml')
 return list(ET.fromstring(adb('shell','cat','/sdcard/history-repair-ui.xml')).iter('node'))
def norm(s):return s.replace('’',"'").lstrip('▸▾ ')
def tap(labels,wait=12):
 until=time.monotonic()+wait
 while time.monotonic()<until:
  for n in nodes():
   if norm(n.get('text','')) in labels or norm(n.get('content-desc','')) in labels or any(norm(n.get('text','')).startswith(s) for s in labels if s in ["Let's go","Los geht's"]):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    if x2>x1 and y2>y1:
     adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.8);return True
  time.sleep(.5)
 return False
adb('shell','am','start','-n','de.ovgu.imiq.cricket/.MainActivity')
time.sleep(13)
tap(["Let's go","Los geht's"],wait=3)
assert tap(['Menu','Menü']), 'Menu unavailable; phone may be locked'
assert tap(['Journey history','Reiseverlauf']), 'History menu unavailable'
time.sleep(4)
(OUT/'history.png').write_bytes(adb('exec-out','screencap','-p'))
labels=[n.get('text','') for n in nodes()]
assert any('Companion explanation' in s or 'Erklärung des Begleiters' in s for s in labels), 'Saved-choice explanation action missing'
assert tap(['Companion explanation','Erklärung des Begleiters']), 'Explanation action unavailable'
until=time.monotonic()+35
while time.monotonic()<until:
 labels=[n.get('text','') for n in nodes()]
 if any('AI narration unavailable' in s or s=='AI narration' or 'KI-Erzählung' in s for s in labels):break
 time.sleep(1)
else:raise RuntimeError('Explanation did not finish loading')
(OUT/'explanation.png').write_bytes(adb('exec-out','screencap','-p'))
proof={'history_rendered':True,'explanation_action_visible':True,'explanation_rendered':True,
 'ai_narration':any(s=='AI narration' for s in labels),'fallback_label_visible':any('AI narration unavailable' in s or 'KI-Erzählung nicht verfügbar' in s for s in labels),
 'no_trip_started_or_experience_confirmed':True}
(OUT/'verification.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8')
print(json.dumps(proof))
