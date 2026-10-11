"""Read-only snapshot of current route search; no journey, GPS or feedback writes."""
import json
from pathlib import Path
import sqlite3
import subprocess

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.local-backups/car-time-0473';OUT.mkdir(exist_ok=True)
ADB=[str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'),'-s','RZCY70K63HV']
name='cricket-native-v1.db'
def read(suffix=''):
    r=subprocess.run(ADB+['exec-out','run-as','de.ovgu.imiq.cricket','cat','databases/'+name+suffix],capture_output=True)
    if r.returncode:
        if suffix and b'No such file' in r.stderr:return b''
        raise RuntimeError(r.stderr.decode(errors='replace'))
    return r.stdout
for _ in range(3):
    db,wal=read(),read('-wal')
    if wal==read('-wal') and db==read():break
else:raise RuntimeError('Snapshot changed; retry')
(OUT/name).write_bytes(db)
if wal:(OUT/(name+'-wal')).write_bytes(wal)
with sqlite3.connect(OUT/name) as conn:
    conn.execute('PRAGMA query_only=ON')
    assert conn.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    record=json.loads(conn.execute('SELECT value FROM searches ORDER BY created DESC LIMIT 1').fetchone()[0])
(OUT/'latest-search.json').write_text(json.dumps(record),encoding='utf-8')
result=record.get('result',{})
print(json.dumps({'created':record['created_at'],'car_parking':record['request'].get('car_parking'),
 'routes':[{'mode':r['mode_key'],'available':r.get('available'),'summary':r.get('summary'),'reasons':r.get('reasons')} for r in result.get('routing',{}).get('routes',[])],
 'provider_audit':result.get('routing',{}).get('provider_audit'),'warnings':result.get('warnings')},ensure_ascii=True))
