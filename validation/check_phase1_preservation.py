"""Compare private device snapshots without printing any profile or location data."""
import json
import sqlite3
import tarfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/".local-backups"
def read_snapshot(name,label):
    directory=ROOT/"phase1-preservation"/label
    directory.mkdir(parents=True,exist_ok=True)
    with tarfile.open(ROOT/name) as archive:
        for suffix in ("","-wal","-shm"):
            source=f"databases/cricket-native-v1.db{suffix}"
            try:
                stream=archive.extractfile(source)
                if stream:(directory/f"cricket-native-v1.db{suffix}").write_bytes(stream.read())
            except KeyError:pass
    with sqlite3.connect(directory/"cricket-native-v1.db") as db:
        storage=dict(db.execute("SELECT key,value FROM kv"))
        records={key:json.loads(value) for key,value in db.execute("SELECT id,value FROM searches")}
    return storage,records
before,old=read_snapshot("before-phase1-validation.tar","before")
after,new=read_snapshot("after-phase1-validation.tar","after")
for key in ("imiq.experimental.cognitive-passport.v1","imiq.experimental.companion.v1"):
    assert before.get(key)==after.get(key),f"Profile changed: {key}"
for identity,record in old.items():
    assert identity in new,"Original history missing"
    assert record["choices"]==new[identity]["choices"],"Original choice changed"
    assert record.get("result")==new[identity].get("result"),"Original simulation changed"
    assert record.get("journeys")==new[identity].get("journeys"),"Test experience leaked into user history"
assert not any(identity.startswith("phase1-device-") for identity in new),"Temporary test history not removed"
assert "cricket.active-trip.v1" not in after,"Test tracking still active"
print(f"Profile preserved; {len(old)} original searches retained; temporary records removed; no active tracking")
