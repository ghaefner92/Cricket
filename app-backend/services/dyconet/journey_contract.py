"""Phase 1 evidence validation. No learning, profile mutations or server storage."""
from datetime import datetime
from hashlib import sha256
import json
import math
from pathlib import Path

from jsonschema import Draft202012Validator

SCHEMA_PATH = Path(__file__).resolve().parents[3] / "contracts" / "journey-v1.schema.json"
SCHEMA = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
Draft202012Validator.check_schema(SCHEMA)
VALIDATOR = Draft202012Validator(SCHEMA)
LIVE_STATES = {"recording", "paused"}


def timestamp(value):
    if not isinstance(value, str):
        raise ValueError("Expected ISO timestamp")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("Timestamps must include a time zone")
    return parsed


def choice_event_id(record, index):
    choice = record["choices"][index]
    return choice.get("choice_event_id") or "choice-" + sha256(
        f'{record["search_id"]}|{index}|{choice["chosen_at"]}'.encode()).hexdigest()


def distance_m(a, b):
    lat1, lat2 = math.radians(a["lat"]), math.radians(b["lat"])
    delta = math.radians(b["lon"] - a["lon"])
    h = math.sin((lat2-lat1)/2)**2 + math.cos(lat1)*math.cos(lat2)*math.sin(delta/2)**2
    return 6371008.8 * 2 * math.asin(math.sqrt(min(1, max(0, h))))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_journey(record, journey):
    errors = list(VALIDATOR.iter_errors(journey))
    if errors:
        error = errors[0]
        # Report paths, never potentially sensitive coordinates or user answers.
        raise ValueError("Invalid contract at " + ".".join(map(str, error.absolute_path)))
    from comfort_pilot.context_capture import validate_capture
    validate_capture(journey)
    require(isinstance(record, dict) and record.get("version") == 1 and record.get("status") == "complete", "Expected completed search")
    require(journey["search_id"] == record["search_id"] == record["result"]["search_id"] == record["request"]["search_id"], "Search identity mismatch")
    require(record["result"].get("schema_version") == "routed-contextual-deliberation-v1", "Search schema mismatch")
    index = journey["choice_index"]
    require(index < len(record["choices"]), "Choice index out of range")
    choice = record["choices"][index]
    require(journey["choice_event_id"] == choice_event_id(record, index), "Choice event mismatch")
    selection = journey["selection"]
    require(all(selection[key] == choice.get(key) for key in selection), "Selection mismatch")
    require(journey["passport_reference"] == record["passport_reference"], "Passport mismatch")
    routes = record["result"]["routing"]["routes"]
    matches = [r for r in routes if r.get("rank") == selection["routing_rank"] and r.get("mode_key") == selection["mode_key"]]
    require(len(matches) == 1 and matches[0].get("available") is True, "Itinerary identity mismatch")
    plan = journey["plan"]
    require(plan["route"] == matches[0], "Itinerary snapshot mismatch")
    require(plan["recommendation"] == record.get("recommendation"), "Recommendation mismatch")
    require(plan["alternatives"] == [dict(routing_rank=r["rank"], mode_key=r["mode_key"], available=r.get("available", False)) for r in routes], "Alternatives mismatch")
    context = plan["context_at_selection"]
    require(context["queried_at"] == record["request"]["datetime"], "Context timestamp mismatch")
    audit = next((a for a in record["result"].get("route_audit", []) if a.get("routing_rank") == selection["routing_rank"] and a.get("mode_key") == selection["mode_key"]), None)
    candidate = next((c for c in record["result"].get("contextual_deliberation", {}).get("candidate_results", []) if audit is not None and c.get("route_id") == audit.get("route_id")), None)
    require(context["snapshot"] == candidate, "Context snapshot mismatch")
    timestamp(context["queried_at"])
    created = timestamp(journey["created_at"])
    require(created >= timestamp(selection["chosen_at"]), "Creation precedes choice")
    start = timestamp(journey["started_at"]) if journey["started_at"] else None
    end = timestamp(journey["ended_at"]) if journey["ended_at"] else None
    require(start is None or start >= created, "Invalid start time")
    require(end is None or end >= created and (start is None or end >= start), "Invalid end time")
    status = journey["status"]
    manual = journey["capture_mode"] == "manual"
    t = journey["tracking"]
    require(t["gap_count"] == t["segment_index"], "Gap/segment mismatch")
    if status in LIVE_STATES:
        require(not manual and start is not None and end is None, "Invalid active trip")
    else:
        require(end is not None, "Missing trip end")
    if manual:
        require(start is None and not t["points"] and not t["events"] and t["distance_m"] is None and t["elapsed_seconds"] is None and t["started_monotonic_ms"] is None and t["boot_count"] is None and t["gap_count"] == 0 and t["rejected_fixes"] == 0, "Manual trip cannot contain measured tracking")
    else:
        require(start is not None and t["started_monotonic_ms"] is not None, "Missing tracking start")
    distance = 0.0
    previous = None
    for point in t["points"]:
        require(all(math.isfinite(point[key]) for key in ("lat", "lon", "accuracy_m")), "Non-finite fix")
        stamp, received = timestamp(point["recorded_at"]), timestamp(point["received_at"])
        require(start is not None and stamp >= start and received >= stamp and (received-stamp).total_seconds() < 31 and (end is None or received <= end), "Invalid fix time")
        require(point["mock"] is False, "Mock fixes are not experience evidence")
        require(point["segment_index"] <= t["segment_index"], "Invalid segment")
        if previous is not None:
            require(point["segment_index"] >= previous["segment_index"], "Segment order")
            if point["segment_index"] == previous["segment_index"]:
                seconds = (point["monotonic_ms"] - previous["monotonic_ms"])/1000
                require(0 < seconds <= 120, "Invalid fix interval")
                step = distance_m(previous, point)
                require(step/seconds <= 70, "Implausible speed")
                distance += step
        previous = point
    if len(t["points"]) < 2:
        require(t["distance_m"] is None, "Unknown distance must stay null")
    else:
        require(t["distance_m"] is not None and math.isfinite(t["distance_m"]) and abs(t["distance_m"]-distance) <= 0.01, "Distance inconsistent with fixes")
    last_event = created
    for event in t["events"]:
        at = timestamp(event["at"])
        require(at >= last_event and (end is None or at <= end), "Invalid event time")
        last_event = at
    if not manual:
        require(bool(t["events"]) and t["events"][0]["kind"] == "start", "Missing tracking start event")
    require((t["elapsed_seconds"] is not None) == (not manual and end is not None), "Invalid elapsed time state")
    require(t["elapsed_seconds"] is None or math.isfinite(t["elapsed_seconds"]), "Non-finite duration")
    experience = journey["experience"]
    require((status == "confirmed") == (experience is not None), "Experience requires explicit confirmation")
    if experience is not None:
        require(experience["event_id"] == "experience-" + journey["journey_id"], "Experience identity mismatch")
        require(timestamp(experience["confirmed_at"]) >= end, "Confirmation precedes finish")
        completion, actual = experience["completion"], experience["actual_modes"]
        require(set(experience.get("mode_valences_raw_minus3_to_3", {})).issubset(set(actual)), "Emotion must refer to an actual mode")
        emotions = experience.get("mode_valences_raw_minus3_to_3", {})
        normalized = experience.get("mode_valences_normalized_minus1_to_1")
        require(not record.get("affect_pilot_required") or completion == "not_taken" or (normalized is not None and set(emotions)==set(actual) and experience.get("affect_scale_version")=="mode-affect-ordinal-minus3-to3-v1"), "Affective experience is required for each actual mode")
        if normalized is not None:
            require(set(normalized)==set(emotions), "Valence normalization keys mismatch")
            require(all(abs(normalized[k]-emotions[k]/3)<1e-9 for k in emotions), "Valence normalization must be raw/3")
        comfort = experience.get("comfort_observation")
        require(not record.get("comfort_pilot_required") or completion == "not_taken" or comfort is not None, "Physical comfort is required in this pilot")
        if comfort is not None:
            require(completion != "not_taken" and comfort["mode"] in actual, "Comfort must refer to an actual mode")
            require(timestamp(journey["ended_at"]) <= timestamp(comfort["answered_at"]) <= timestamp(experience["confirmed_at"]), "Comfort answer outside experience time")
            require((comfort["mode_selection"] == "single_actual_mode") == (len(actual) == 1), "Incorrect mode attribution method")
        if completion == "not_taken":
            require(not experience.get("need_satisfaction_raw_1_to_7") and not experience.get("mode_valences_raw_minus3_to_3"), "An unperformed trip cannot be rated")
            require(manual and not actual and not experience["ratings"], "An unperformed trip cannot be rated")
        else:
            require(bool(actual), "Confirm actual transport")
        if completion == "as_planned":
            canonical_mode = {"foot": "walk", "car_driver": "car", "pt_bus_tram": "pt"}.get(selection["mode_key"], selection["mode_key"])
            require(set(actual) == {canonical_mode}, "As-planned mode mismatch")
    return journey


def evidence_id(journey):
    return "journey-evidence-" + sha256(json.dumps(journey, sort_keys=True, separators=(",", ":"), ensure_ascii=False, allow_nan=False).encode()).hexdigest()
