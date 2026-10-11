"""Daily measurements and immutable Passport revisions; no longitudinal learning."""
from copy import deepcopy
from dataclasses import replace
from datetime import datetime, date
from hashlib import sha256
import json
import math
from uuid import uuid5, NAMESPACE_URL
from zoneinfo import ZoneInfo

from adaptive_profile_update import reconstruct_adaptive_participant
from adaptive_hotco_adapter import AdaptiveHOTCOInput
from adaptive_passport_xai import build_adaptive_cognitive_passport
from input_mapping_v4_3 import NEEDS, MODES


def validate_daily_goals(value):
    if not isinstance(value, dict) or set(value) != {"local_date", "timezone", "confirmed_at", "ratings"}:
        raise ValueError("Daily goals require date, timezone, confirmation time and eleven ratings")
    local_date = date.fromisoformat(value["local_date"])
    zone = ZoneInfo(value["timezone"])
    instant = datetime.fromisoformat(value["confirmed_at"].replace("Z", "+00:00"))
    if instant.tzinfo is None or instant.astimezone(zone).date() != local_date:
        raise ValueError("Daily date must match the confirmation instant in the declared timezone")
    ratings = value["ratings"]
    if not isinstance(ratings, dict) or set(ratings) != set(NEEDS) or any(type(r) is not int or not 1 <= r <= 7 for r in ratings.values()):
        raise ValueError("All daily importance ratings must be integers from 1 to 7")
    normalized = {n: (ratings[n] - 1) / 6 for n in NEEDS}
    digest = sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    return dict(deepcopy(value), schema_version="daily-goals-v1", daily_goals_id="daily-goals-"+digest,
                normalized=normalized, source="explicit_daily_confirmation")


def estimation_basis(passport):
    observed = passport.get("observed_measurements", {})
    if "needs_allocation" in observed:
        original_needs = {n: observed["needs_allocation"]["points"][n]/10 for n in NEEDS}
    elif "needs_raw_1_to_7" in observed:
        original_needs = {n: (observed["needs_raw_1_to_7"][n]-1)/6 for n in NEEDS}
    else:
        original_needs = passport["profile"]["needs"]
    original_valences = ({m: observed["valences_raw_minus3_to_3"][m]/3 for m in MODES}
                         if "valences_raw_minus3_to_3" in observed else passport["profile"]["valences"])
    basis = passport.get("belief_estimation_inputs", {"needs": original_needs, "valences": original_valences})
    for name, keys, lower in (("needs", NEEDS, 0), ("valences", MODES, -1)):
        values = basis.get(name)
        if not isinstance(values, dict) or set(values) != set(keys):
            raise ValueError("Invalid frozen belief estimation inputs")
        if any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) or not lower <= v <= 1 for v in values.values()):
            raise ValueError("Frozen belief estimation values are outside their bounds")
    return deepcopy(basis)


def attach_initial_daily(output, value):
    snapshot = validate_daily_goals(value)
    passport = output["cognitive_passport"]
    if any(abs(passport["profile"]["needs"][n] - snapshot["normalized"][n]) > 1e-8 for n in NEEDS):
        raise ValueError("Daily goals do not match the initial questionnaire")
    passport["daily_goals"] = snapshot
    passport["belief_estimation_inputs"] = estimation_basis(passport)
    passport["longitudinal_policy"] = "measurement_snapshots_only_no_state_carryover"
    return output


def confirm_daily_passport(payload, engine):
    if not isinstance(payload, dict) or set(payload) != {"schema_version", "previous_passport", "daily_goals"} or payload["schema_version"] != "daily-passport-confirm-v1":
        raise ValueError("Invalid daily Passport confirmation")
    previous = payload["previous_passport"]
    if not isinstance(previous, dict) or previous.get("schema_version") != "2.0":
        raise ValueError("Previous Cognitive Passport 2.0 is required")
    snapshot = validate_daily_goals(payload["daily_goals"])
    participant = reconstruct_adaptive_participant(previous)
    if previous.get("daily_goals", {}).get("daily_goals_id") == snapshot["daily_goals_id"]:
        return {"cognitive_passport": deepcopy(previous)}
    basis = estimation_basis(previous)
    participant = replace(participant, raw_needs=snapshot["ratings"], needs=[snapshot["normalized"][n] for n in NEEDS], needs_allocation=None)
    provenance = deepcopy(previous["measurement_provenance"])
    provenance["needs"] = {"source": "explicit_daily_confirmation", "scale": "ordinal_importance_1_7", "daily_goals_id": snapshot["daily_goals_id"]}
    parent = previous["lineage"]
    event = {"event_id": snapshot["daily_goals_id"], "kind": "daily_goals_confirmed", "at": snapshot["confirmed_at"], "learning_applied": False}
    lineage = dict(deepcopy(parent), passport_id=str(uuid5(NAMESPACE_URL, parent["passport_id"]+"|"+snapshot["daily_goals_id"])),
                   parent_passport_id=parent["passport_id"], revision=parent["revision"]+1,
                   origin="explicit_daily_confirmation", last_update_event=event)
    lineage["measurement_history"] = [*parent.get("measurement_history", []), event]
    adaptive = AdaptiveHOTCOInput(participant, provenance, previous["adaptive_passport"])
    output = build_adaptive_cognitive_passport(adaptive, engine.simulate(participant), engine, lineage=lineage)
    current = output["cognitive_passport"]
    current["daily_goals"] = snapshot
    current["belief_estimation_inputs"] = basis
    current["longitudinal_policy"] = "measurement_snapshots_only_no_state_carryover"
    current["evidence_history"] = deepcopy(previous.get("evidence_history", []))
    return output


def register_daily_passport(app, engine, lock):
    from flask import request, jsonify
    @app.post("/api/dyconet/daily-passport/confirm")
    def confirm_daily():
        try:
            with lock:
                output = confirm_daily_passport(request.get_json(force=True), engine)
            return jsonify(output)
        except (ValueError, KeyError, TypeError) as exc:
            return jsonify(error="invalid_daily_passport", message=str(exc)), 422
