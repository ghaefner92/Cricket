"""Restore a verified private SQLite snapshot after the test-runner uninstall incident.

Never deletes the original backup. Refuses to overwrite a newly populated history.
Preserves the newly installed app's state separately before any replacement.
"""
from pathlib import Path
import subprocess
import sqlite3
import json

root = Path(__file__).resolve().parents[1]
adb = [str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'), '-s', 'RZCY70K63HV']
out = root / '.local-backups/pet-0479-recovery'
out.mkdir(exist_ok=True)
source = root / '.local-backups/car-time-0473/cricket-native-v1.db'
target = out / 'restored.db'
source_db = sqlite3.connect(source.as_uri() + '?mode=ro', uri=True)
assert source_db.execute('pragma quick_check').fetchone()[0] == 'ok'
assert source_db.execute('select count(*) from searches').fetchone()[0] == 25
keys = dict(source_db.execute('select * from kv'))
passport = json.loads(keys['imiq.experimental.cognitive-passport.v1'])
assert passport['response']['cognitive_passport']['lineage']['revision'] == 3
dest_db = sqlite3.connect(target)
source_db.backup(dest_db)
source_db.close()
assert not dest_db.execute("select key from kv where key like '%active%trip%'").fetchall(), 'Snapshot may activate an old trip'
dest_db.close()
# Stop only the freshly reinstalled app, which cannot have the old running service.
services = subprocess.check_output(adb + ['shell', 'dumpsys', 'activity', 'services', 'de.ovgu.imiq.cricket']).decode()
assert '(nothing)' in services, 'Do not interrupt a running service'
subprocess.run(adb + ['shell', 'am', 'force-stop', 'de.ovgu.imiq.cricket'], check=True)
listing = subprocess.run(adb + ['shell', 'run-as', 'de.ovgu.imiq.cricket', 'ls', 'databases'], capture_output=True)
if listing.returncode == 0:
    blob = subprocess.check_output(adb + ['exec-out', 'run-as', 'de.ovgu.imiq.cricket', 'tar', '-cf', '-', 'databases', 'shared_prefs'])
    (out / 'new-install-before-restore.tar').write_bytes(blob)
    current = subprocess.check_output(adb + ['exec-out', 'run-as', 'de.ovgu.imiq.cricket', 'cat', 'databases/cricket-native-v1.db'])
    current_path = out / 'new-install-before-restore.db'
    current_path.write_bytes(current)
    fresh = sqlite3.connect(current_path.as_uri() + '?mode=ro', uri=True)
    assert fresh.execute('select count(*) from searches').fetchone()[0] == 0, 'New history exists: review before replacing'
    assert not fresh.execute("select key from kv where key='imiq.experimental.cognitive-passport.v1'").fetchall(), 'New Passport exists: review before replacing'
    fresh.close()
subprocess.run(adb + ['push', str(target), '/data/local/tmp/cricket-recovery-0479.db'], check=True)
subprocess.run(adb + ['shell', 'run-as', 'de.ovgu.imiq.cricket', 'mkdir', '-p', 'databases'], check=True)
# One native shell; fixed paths inside the private app directory only.
subprocess.run(adb + ['shell', 'run-as', 'de.ovgu.imiq.cricket', 'sh', '-c',
                     "'cp /data/local/tmp/cricket-recovery-0479.db databases/cricket-native-v1.db'"], check=True)
print(json.dumps({'restored_searches':25, 'passport_revision':3, 'backup_local_time':'2026-10-11 16:05 Europe/Berlin',
                  'new_install_preserved_at':str(out)}))
