"""Private, read-only device history snapshot and bounded metadata diagnosis."""
from pathlib import Path
import subprocess,sqlite3,json
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.local-backups/history-repair'/datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')
OUT.mkdir(parents=True,exist_ok=True)
ADB='C:/Users/equj44id/AppData/Local/Android/Sdk/platform-tools/adb.exe'
def adb(*args):return subprocess.run([ADB,'-s','RZCY70K63HV',*args],capture_output=True,check=True).stdout
def read(name,optional=False):
 r=subprocess.run([ADB,'-s','RZCY70K63HV','exec-out','run-as','de.ovgu.imiq.cricket','cat','databases/'+name],capture_output=True)
 if r.returncode:
  if optional and b'No such file' in r.stderr:return b''
  raise RuntimeError(r.stderr.decode(errors='replace'))
 return r.stdout
name='cricket-native-v1.db'
for attempt in range(3):
 first=read(name);wal=read(name+'-wal',True);second_wal=read(name+'-wal',True);second=read(name)
 if first==second and wal==second_wal:break
else:raise RuntimeError('History changed during snapshot; retry without modifying it')
(OUT/name).write_bytes(second)
if wal:(OUT/(name+'-wal')).write_bytes(wal)
with sqlite3.connect(OUT/name) as db:
 assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
 rows=db.execute("""SELECT id,length(CAST(value AS BLOB)),
  json_array_length(value,'$.choices'),json_array_length(value,'$.journeys'),
  json_array_length(value,'$.explanations'),length(json_extract(value,'$.d_day_forecast.request')),
  json_extract(value,'$.status') FROM searches ORDER BY created DESC""").fetchall()
summary={'private_snapshot':str(OUT),'searches':len(rows),'total_bytes':sum(r[1] for r in rows),
 'records':[{'id':r[0],'bytes':r[1],'choices':r[2] or 0,'journeys':r[3] or 0,'explanations':r[4] or 0,'forecast_request_bytes':r[5] or 0,'status':r[6]} for r in rows]}
(OUT/'summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print(json.dumps(summary,indent=2))
logs=adb('logcat','-d','-t','2500').decode(errors='replace')
matching=[line for line in logs.splitlines() if any(t in line for t in ['OutOfMemoryError','FATAL EXCEPTION','Unverified explanation','choice-explanation','CursorWindow'])]
(OUT/'diagnostic-log-lines.txt').write_text('\n'.join(matching),encoding='utf-8')
print('Relevant error lines:',len(matching))
