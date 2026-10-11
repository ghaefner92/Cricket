"""Read the rendered UI and open existing choice tracking controls; never start a trip."""
import re
import subprocess
import time
from pathlib import Path
import xml.etree.ElementTree as ET

ADB="C:/Users/equj44id/AppData/Local/Android/Sdk/platform-tools/adb.exe"
DEVICE="RZCY70K63HV"
OUT=Path(__file__).resolve().parents[1]/".local-backups"
OUT.mkdir(exist_ok=True)
def adb(*args):return subprocess.run([ADB,"-s",DEVICE,*args],capture_output=True,check=True).stdout
def nodes():
    adb("shell","uiautomator","dump","/sdcard/cricket-phase1-ui.xml")
    return list(ET.fromstring(adb("shell","cat","/sdcard/cricket-phase1-ui.xml")).iter("node"))
def tap(labels):
    for node in nodes():
        if node.get("text") in labels:
            x1,y1,x2,y2=map(int,re.findall(r"\d+",node.get("bounds")))
            adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2));time.sleep(1);return True
    return False
adb("shell","am","start","-n","de.ovgu.imiq.cricket/.MainActivity")
time.sleep(1)
assert tap(["Menu","Menü"]),"Menu unavailable (phone may be locked)"
assert tap(["History","Verlauf","Journey history","Reiseverlauf"]),"History unavailable"
assert tap(["Open journey","Reise öffnen"]),"No journey available"
assert tap(["Start trip / confirm experience →","Reise starten / Erfahrung bestätigen →"]),"Choice tracking action unavailable"
labels=[node.get("text") for node in nodes()]
assert any(label in labels for label in ("Start journey with GPS","Reise mit GPS starten")),"GPS start control missing"
assert any(label in labels for label in ("Confirm without GPS","Ohne GPS bestätigen")),"Manual confirmation control missing"
(OUT/"phase1-tracking-controls.png").write_bytes(adb("exec-out","screencap","-p"))
print("Tracking and manual confirmation controls rendered; no journey started")
