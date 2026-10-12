"""Compressed private snapshot; original phone data are read only."""
import subprocess,gzip,json
from pathlib import Path
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.local-backups/history-repair'/datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-backup')
OUT.mkdir(parents=True,exist_ok=True)
ADB='C:/Users/equj44id/AppData/Local/Android/Sdk/platform-tools/adb.exe'
base=[ADB,'-s','RZCY70K63HV']
names=['cricket-native-v1.db','cricket-native-v1.db-wal','cricket-native-v1.db-shm']
for name in names:
 with subprocess.Popen(base+['exec-out','run-as','de.ovgu.imiq.cricket','cat','databases/'+name],stdout=subprocess.PIPE,stderr=subprocess.PIPE) as p:
  with gzip.open(OUT/(name+'.gz'),'wb',compresslevel=3) as zipped:
   while chunk:=p.stdout.read(1024*1024):zipped.write(chunk)
  if p.wait()!=0:raise RuntimeError(p.stderr.read().decode(errors='replace'))
result={'private_backup':str(OUT),'files':[{'name':p.name,'bytes':p.stat().st_size} for p in OUT.glob('*.gz')]}
(OUT/'manifest.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
