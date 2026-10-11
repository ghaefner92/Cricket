"""D-Day structural validation. No persistence, inference or scientific validation."""
import json
import math
from pathlib import Path
from flask import jsonify, request
from journey_contract import validate_journey
from jsonschema import Draft202012Validator
SCHEMA = json.loads((Path(__file__).resolve().parents[3] / "contracts/d-day-longitudinal-v1.schema.json").read_text(encoding="utf-8"))
VALIDATOR = Draft202012Validator(SCHEMA)

PASSPORT = "imiq.experimental.cognitive-passport.v1"


def longitudinal_index(storage, records):
    passports, warnings, searches, events, seen = {}, set(), [], [], set()

    def add(wrapper, source):
        if not isinstance(wrapper, dict):
            return
        p = wrapper.get("response", {}).get("cognitive_passport", {})
        lineage = p.get("lineage", {})
        identifier = lineage.get("passport_id")
        if not identifier:
            return
        if identifier in passports:
            if passports[identifier][0] != p:
                raise ValueError("Conflicting immutable Passport")
        else:
            passports[identifier] = p, source

    for key in sorted(storage):
        if key == PASSPORT or key.startswith(PASSPORT + ".archive."):
            add(json.loads(storage[key]), {"storage_key": key})
    for record in sorted(records, key=lambda r: r["search_id"]):
        from comfort_pilot.prospective_evaluation import validate_record
        validate_record(record)
        identifier = record["search_id"]
        add(record.get("confirmed_snapshot"), {"search_id": identifier, "field": "confirmed_snapshot"})
        reference = record.get("passport_reference")
        simulation = (record.get("result") or {}).get("contextual_deliberation")
        if reference is None:
            warnings.add(f"{identifier}: decision Passport reference unavailable")
        if simulation is None:
            warnings.add(f"{identifier}: simulation unavailable ({record['status']})")
        else:
            if simulation["search_id"] != identifier:
                raise ValueError("Simulation belongs to another search")
            summaries = [simulation["baseline"]] + [c["hotco"] for c in simulation.get("candidate_results", [])]
            for summary in summaries:
                evidence = summary.get("simulation_evidence") or {}
                trajectory = evidence.get("trajectory_id")
                if not trajectory:
                    warnings.add(f"{identifier}: full trajectory unavailable")
                    continue
                states = simulation["trajectory_archive"][trajectory]
                columns = evidence["state_columns"]
                if (evidence["state_kind"] != "model_prediction"
                    or evidence["time_reference"] != "model_units_not_elapsed_travel_time"
                    or evidence["dt"] <= 0 or len(columns) != 19 or len(states) != evidence["sample_count"]
                    or any(len(row) != len(columns) or any(isinstance(x, bool) or not isinstance(x, (float, int))
                        or not math.isfinite(x) for x in row) for row in states)):
                    raise ValueError("Invalid simulation evidence")
        policy = record.get("request", {}).get("context_policy", "legacy_context_drive")
        if policy != "observations_only":
            warnings.add(f"{identifier}: legacy contextual policy; excluded from D-Day causal assumptions")
        searches.append({"search_id": identifier, "created_at": record.get("created_at"),
            "completed_at": record.get("completed_at"), "status": record["status"],
            "decision_passport": reference, "context_policy": policy, "source_record": identifier,
            "recommendation_presentation": "not_instrumented",
            "trajectory_ids": sorted((simulation or {}).get("trajectory_archive", {}))})
        for journey in record.get("journeys", []):
            validate_journey(record, journey)
            experience = journey.get("experience")
            if experience is None:
                continue
            event = experience["event_id"]
            if event in seen:
                raise ValueError("Duplicate Experience event")
            seen.add(event)
            transitions = [t for t in record.get("passport_transitions", []) if t["event_id"] == event]
            if len(transitions) > 1:
                raise ValueError("Duplicate Passport transition")
            if not transitions:
                warnings.add(f"{event}: before/after Passport transition unavailable (legacy or no Passport)")
            events.append({"event_id": event, "at": experience["confirmed_at"], "search_id": identifier,
                "journey_id": journey["journey_id"], "choice_event_id": journey["choice_event_id"],
                "passport_transition": transitions[0] if transitions else None,
                "learning_applied": False, "state_carryover_applied": False})
        if any(not any((j.get("experience") or {}).get("event_id") == t["event_id"]
                       for j in record.get("journeys", [])) for t in record.get("passport_transitions", [])):
            raise ValueError("Orphan Passport transition")
    for record in records:
        reference = record.get("passport_reference", {})
        identifier = reference.get("passport_id")
        if identifier and identifier not in passports:
            warnings.add(f"{record['search_id']}: decision Passport snapshot unavailable ({identifier})")
        for t in record.get("passport_transitions", []):
            before = passports[t["before"]["passport_id"]][0]
            after = passports[t["after"]["passport_id"]][0]
            b, a = before["lineage"], after["lineage"]
            if (a["parent_passport_id"] != b["passport_id"] or a["revision"] != b["revision"] + 1
                or t["before"]["revision"] != b["revision"] or t["after"]["revision"] != a["revision"]
                or before["profile"] != after["profile"]
                or not any(e["event_id"] == t["event_id"] for e in after.get("evidence_history", []))
                or t.get("learning_applied") is not False or t.get("state_carryover_applied") is not False):
                raise ValueError("Invalid evidence-only Passport transition")
            journey = next(j for j in record["journeys"] if (j.get("experience") or {}).get("event_id") == t["event_id"])
            entries = [e for e in after["evidence_history"] if e["event_id"] == t["event_id"]]
            if len(entries) != 1:
                raise ValueError("Duplicate Passport evidence event")
            entry = entries[0]
            if (entry["search_id"] != record["search_id"] or entry["journey_id"] != journey["journey_id"]
                or entry["choice_event_id"] != journey["choice_event_id"] or entry["experience"] != journey["experience"]
                or before.get("evidence_history", []) != [e for e in after["evidence_history"] if e["event_id"] != t["event_id"]]):
                raise ValueError("Passport evidence does not match journey or prior history")
    versions = []
    for identifier, (p, source) in sorted(passports.items()):
        lineage = p["lineage"]
        parent = lineage.get("parent_passport_id") or None
        if parent and parent not in passports:
            warnings.add(f"{identifier}: parent snapshot unavailable ({parent})")
        versions.append({"passport_id": identifier, "revision": lineage["revision"],
                         "parent_passport_id": parent, "source": source})
    return {"schema_version": "d-day-longitudinal-v1", "learning_applied": False,
        "state_carryover_applied": False, "model_time_reference": "model_units_not_elapsed_travel_time",
        "observation_time_reference": "recorded_wall_clock_timestamps", "passport_versions": versions,
        "searches": searches, "experience_events": events, "warnings": sorted(warnings)}


def validate_backup(backup):
    if not isinstance(backup, dict) or backup.get("schema") != "cricket-backup-v1":
        raise ValueError("Unsupported backup")
    from comfort_pilot.learned_passport import validate_storage
    validate_storage(backup['storage'])
    records = backup["simulations"]
    if len({r["search_id"] for r in records}) != len(records):
        raise ValueError("Duplicate search ID")
    if not isinstance(backup["storage"], dict) or not isinstance(records, list) or not all(isinstance(r, dict) for r in records):
        raise ValueError("Invalid backup sources")
    expected = longitudinal_index(backup["storage"], records)
    if any(VALIDATOR.iter_errors(expected)):
        raise ValueError("Invalid D-Day index schema")
    if backup.get("longitudinal") != expected:
        raise ValueError("Inconsistent D-Day evidence index")
    return expected


def register_d_day(app):
    @app.get("/api/dyconet/d-day/contract")
    def d_day_contract():
        return jsonify(SCHEMA)

    @app.post("/api/dyconet/d-day/validate")
    def validate_d_day():
        if request.content_length is not None and request.content_length > 64 * 1024 * 1024:
            return jsonify(error="d_day_too_large", message="Use offline validation for exports over 64 MiB"), 413
        try:
            payload = request.get_json(silent=True)
            if not isinstance(payload, dict):
                raise ValueError("Expected a D-Day backup")
            index = validate_backup(payload)
        except (ValueError, KeyError, TypeError, IndexError, OverflowError, AttributeError):
            return jsonify(error="invalid_d_day_evidence", message="D-Day sources, links or index are inconsistent"), 422
        return jsonify(schema_version="d-day-validation-v1", status="valid", stored_on_server=False,
                       learning_applied=False, state_carryover_applied=False,
                       passport_versions=len(index["passport_versions"]), searches=len(index["searches"]),
                       experience_events=len(index["experience_events"]), warnings=index["warnings"])


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("backup", type=Path)
    args = parser.parse_args()
    index = validate_backup(json.loads(args.backup.read_text(encoding="utf-8")))
    print(json.dumps({"status": "valid", "searches": len(index["searches"]),
                      "experience_events": len(index["experience_events"]), "warnings": index["warnings"]}))
