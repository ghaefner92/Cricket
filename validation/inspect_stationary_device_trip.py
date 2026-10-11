"""Read-only device snapshot and private trip audit; does not infer a real journey."""
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import sqlite3
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app-backend/services/dyconet"))
from journey_contract import validate_journey

ADB = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT = ROOT / ".local-backups/d-day-stationary-2026-10-11" / datetime.now(timezone.utc).strftime("%H%M%S")
OUT.mkdir(parents=True, exist_ok=True)

def read(name, optional=False):
    r = subprocess.run([str(ADB), "-s", "RZCY70K63HV", "exec-out", "run-as", "de.ovgu.imiq.cricket", "cat", "databases/" + name], capture_output=True)
    if r.returncode:
        if optional and b"No such file" in r.stderr:
            return b""
        raise RuntimeError(r.stderr.decode(errors="replace"))
    return r.stdout

name = "cricket-native-v1.db"
for attempt in range(3):
    first_db, first_wal = read(name), read(name + "-wal", True)
    journal = read(name + "-journal", True)
    last_wal, last_db = read(name + "-wal", True), read(name)
    if first_db == last_db and first_wal == last_wal:
        break
else:
    raise RuntimeError("Database changed during snapshot; try after stopping recording")

db = OUT / name
db.write_bytes(last_db)
for suffix, data in (("-wal", last_wal), ("-journal", journal)):
    if data:
        Path(str(db) + suffix).write_bytes(data)
with sqlite3.connect(db) as conn:
    conn.execute("PRAGMA query_only=ON")
    integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
    if integrity != "ok":
        raise RuntimeError("Snapshot integrity failed")
    records = [json.loads(row[0]) for row in conn.execute("SELECT value FROM searches ORDER BY created")]

trips = sorted([(r, j) for r in records for j in r.get("journeys", [])], key=lambda item: item[1].get("created_at", ""))
rows = []
for index, (record, trip) in enumerate(trips[-5:]):
    tracking = trip.get("tracking") or {}
    samples = (trip.get("context_capture") or {}).get("samples", [])
    experience = trip.get("experience")
    try:
        validate_journey(record, trip)
        contract = "valid"
    except Exception as e:
        contract = str(e)
    rows.append({"created_at": trip.get("created_at"), "started_at": trip.get("started_at"),
        "ended_at": trip.get("ended_at"), "status": trip.get("status"), "capture_mode": trip.get("capture_mode"),
        "retained_points": len(tracking.get("points", [])), "distance_m": tracking.get("distance_m"),
        "elapsed_seconds": tracking.get("elapsed_seconds"), "rejected_fixes": tracking.get("rejected_fixes"),
        "segment_index": tracking.get("segment_index"), "events": tracking.get("events"),
        "context_statuses": dict(Counter(s.get("status") for s in samples)),
        "context_failure_reasons": dict(Counter(s.get("failure_reason") for s in samples if s.get("failure_reason"))),
        "usable_weather_per_complete_sample": [s["result"]["audit"]["weather_used"] for s in samples if s.get("status") == "complete"],
        "experience_present": bool(experience), "experience_keys": sorted(experience) if experience else [],
        "forecast_status": (record.get("d_day_forecast") or {}).get("status"), "contract": contract})
    if index == len(trips[-5:]) - 1:
        (OUT / "latest-record.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (OUT / "latest-journey.json").write_text(json.dumps(trip, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

report = {"source": "stable read-only SQLite snapshot; individual JSON extracts, not app backup export",
    "user_report": "stationary in bed, no real route performed", "integrity": integrity,
    "total_journeys": len(trips), "recent_journeys": rows, "full_real_journey_verified": False}
(OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, ensure_ascii=True, indent=2))
print("Private snapshot:", OUT)
