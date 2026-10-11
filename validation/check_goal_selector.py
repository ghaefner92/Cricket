"""Navigation-only pet selector check. Never rate or save a goal."""
from pathlib import Path
import subprocess
import time
import re
import sys
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
adb=[str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'),'-s','RZCY70K63HV']
out=root/'.local-backups/goal-selector-0481'
out.mkdir(exist_ok=True)
de='--de' in sys.argv
save='Speichern' if de else 'Save'
cancel_label='Abbrechen' if de else 'Cancel'
def run(*args):return subprocess.check_output(adb+list(args))
def nodes():
    run('shell','uiautomator','dump','/sdcard/goal-selector.xml')
    data=run('exec-out','cat','/sdcard/goal-selector.xml')
    (out/'latest.xml').write_bytes(data)
    return list(ET.fromstring(data).iter('node'))
def centre(n):
    b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
    return ((b[0]+b[2])//2,(b[1]+b[3])//2)
def motion(kind,p):run('shell','input','motionevent',kind,*map(str,p))
def cancel():
    current=nodes()
    button=next(n for n in current if n.get('text')==cancel_label)
    run('shell','input','tap',*map(str,centre(button)))
    assert not any(n.get('text')==save for n in nodes())
current=nodes()
pet=next(n for n in current if 'Original full-body sprite' in n.get('content-desc','') or 'Originalsprite' in n.get('content-desc',''))
goals=[n for n in current if ' · Priority ' in n.get('content-desc','') or ' · Rang ' in n.get('content-desc','')]
assert len(goals)==11,'All goal buttons must be visible'
eco=goals[0]
p=centre(pet);target=centre(eco)
try:
    motion('DOWN',p);motion('MOVE',target);time.sleep(.15)
    (out/'aim-eco.png').write_bytes(run('exec-out','screencap','-p'))
    assert any(n.get('text')==('Öko-Modus' if de else 'Eco-mode') for n in nodes()),'Target name did not appear without long press'
finally:
    motion('UP',target)
assert any(n.get('text')==save for n in nodes()),'Releasing target did not open editor'
cancel()
activity=goals[1]
activity_target=centre(activity)
try:
    motion('DOWN',p);motion('MOVE',activity_target);time.sleep(.15)
    (out/'aim-activity.png').write_bytes(run('exec-out','screencap','-p'))
    assert any(n.get('text')==('Körperliche Aktivität' if de else 'Physical Activity') for n in nodes()),'Activity label did not appear'
finally:
    motion('UP',activity_target)
assert any(n.get('text')==save for n in nodes()),'Activity release did not open editor'
cancel()
run('shell','input','draganddrop',*map(str,p),*map(str,p),'900')
assert not any(n.get('text')==save for n in nodes()),'Centre release should cancel'
run('shell','input','tap',*map(str,target))
assert any(n.get('text')==save for n in nodes()),'Direct goal tap should open editor'
cancel()
(out/'resting.png').write_bytes(run('exec-out','screencap','-p'))
print('PASS: target highlight/name, release opens editor, centre cancels, direct tap opens; all editors cancelled without saving')
