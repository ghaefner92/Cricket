"""Read existing local recovery snapshots without changing their records."""
from pathlib import Path
import sqlite3
import json
from datetime import datetime

root = Path(__file__).resolve().parents[1]
for path in sorted((root / '.local-backups').rglob('*')):
    if path.suffix not in ('.db', '.sqlite'):
        continue
    try:
        db = sqlite3.connect(path.as_uri() + '?mode=ro', uri=True)
        tables = [r[0] for r in db.execute("select name from sqlite_master where type='table'")]
        if 'kv' not in tables:
            db.close()
            continue
        passports = []
        for key, value in db.execute('select * from kv'):
            if key == 'imiq.experimental.cognitive-passport.v1' or key.startswith('imiq.experimental.cognitive-passport.v1.archive.'):
                item = json.loads(value)
                p = item.get('response', {}).get('cognitive_passport', {})
                if p.get('lineage'):
                    passports.append({'key':key,'created':item.get('createdAt'),'revision':p['lineage'].get('revision')})
        rows = db.execute('select count(*),max(created) from searches').fetchone()
        print(json.dumps({'path':str(path.relative_to(root)),'modified':datetime.fromtimestamp(path.stat().st_mtime).isoformat(),
                          'integrity':db.execute('pragma quick_check').fetchone()[0], 'searches':rows[0], 'last_search':rows[1],
                          'passports':passports[-4:]},ensure_ascii=False))
        db.close()
    except (sqlite3.Error, ValueError) as error:
        print(json.dumps({'path':str(path.relative_to(root)),'error':str(error)}))
