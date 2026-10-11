"""Preserve the current phone SQLite and preferences before an APK replacement."""
from pathlib import Path
from datetime import datetime
import subprocess
import tarfile
import shutil
import sqlite3
import json
import gzip

root = Path(__file__).resolve().parents[1]
adb = [str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'), '-s', 'RZCY70K63HV']
out = root / '.local-backups/pet-0480-preservation' / datetime.now().strftime('%Y%m%d-%H%M%S')
out.mkdir(parents=True)
services = subprocess.check_output(adb + ['shell', 'dumpsys', 'activity', 'services', 'de.ovgu.imiq.cricket']).decode()
assert '(nothing)' in services, 'Do not interrupt a running service'
subprocess.run(adb + ['shell', 'am', 'force-stop', 'de.ovgu.imiq.cricket'], check=True)
archive = out / 'before-apk.tar.gz'
with gzip.open(archive,'wb',compresslevel=1) as stream:
    with subprocess.Popen(adb + ['exec-out', 'run-as', 'de.ovgu.imiq.cricket', 'tar', '-cf', '-', 'databases', 'shared_prefs'], stdout=subprocess.PIPE) as process:
        shutil.copyfileobj(process.stdout,stream)
        if process.wait():raise RuntimeError('Phone backup failed')
with tarfile.open(archive) as tar:
    for filename in ('cricket-native-v1.db', 'cricket-native-v1.db-wal', 'cricket-native-v1.db-shm'):
        name = f'databases/{filename}'
        if name in tar.getnames():
            with tar.extractfile(name) as source, (out / filename).open('wb') as dest:
                shutil.copyfileobj(source, dest)
db = sqlite3.connect((out / 'cricket-native-v1.db').as_uri() + '?mode=ro', uri=True)
assert db.execute('pragma quick_check').fetchone()[0] == 'ok'
keys = dict(db.execute('select * from kv'))
passport = json.loads(keys['imiq.experimental.cognitive-passport.v1'])
report = {'backup':str(out), 'searches':db.execute('select count(*) from searches').fetchone()[0],
          'passport_id':passport['response']['cognitive_passport']['lineage']['passport_id'],
          'revision':passport['response']['cognitive_passport']['lineage']['revision']}
(out / 'report.json').write_text(json.dumps(report, indent=2))
db.close()
print(json.dumps(report))
