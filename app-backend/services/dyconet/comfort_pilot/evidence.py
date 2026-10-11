"""Versioned pilot evidence, time-safe context and export adapter."""
from copy import deepcopy
import json
import math
from journey_contract import timestamp
from d_day_evidence import validate_backup, PASSPORT
from .model import MODES, WEATHER, identity
from .context_audit import UNITS, audit_context
from .eligibility import exclusion_reason, validate_exclusions, backup_exclusions

QUESTION_VERSION = "comfort-physical-bipolar-v1"
SCALE = "helps-hinders-1-to-7"


def context_values(context, as_of):
    """Compatibility wrapper; structured diagnostics live in audit_context."""
    audit = audit_context(context, as_of)
    return audit["values"], audit["warnings"]


def validate_request(data, *, target="comfort"):
    if target not in ("comfort","affect"):raise ValueError("Unknown learning target")
    affect=target=="affect"
    from .weather_contract import LEGACY, validate_version
    if not isinstance(data, dict) or data.get("schema_version") != f"d-day-{target}-pilot-request-v1":
        raise ValueError("Unsupported pilot request")
    weather_version = validate_version(data.get('weather_model_version', LEGACY))
    allowed = {"schema_version","subject_id","initial_passport","observations","prediction_contexts","prior_profile",
               "seed","draws","warmup","cutpoint_scale","literature_shape","weather_model_version"}
    if set(data) - allowed: raise ValueError("Unknown request fields")
    if not isinstance(data.get("subject_id"), str) or not data["subject_id"]: raise ValueError("subject_id required")
    passport = deepcopy(data["initial_passport"])
    if passport.get("schema_version") != "2.0" or passport.get("agent_id") != data["subject_id"]:
        raise ValueError("Initial Passport belongs to another individual")
    if (passport.get("lineage", {}).get("parent_passport_id") is not None
        or passport.get("evidence_history")):
        raise ValueError("Choose the original Passport, not an evidence-derived revision")
    observations = []
    seen = set(); journeys = set()
    for o in data.get("observations", []):
        rating=o.get("rating_raw_minus3_to_3") if affect else o.get("rating")
        if (o.get("question_version") != ("mode-affect-ordinal-minus3-to3-v1" if affect else QUESTION_VERSION) or o.get("scale") != ("minus3-to-3" if affect else SCALE)
            or o.get("subject_id") != data["subject_id"] or o.get("mode") not in MODES
            or type(rating) is not int or not (-3 <= rating <= 3 if affect else 1 <= rating <= 7)
            or o.get("attribution") != "actual_mode" or not o.get("journey_id")
            or not o.get("choice_event_id") or not o.get("event_id")):
            raise ValueError("Invalid or legacy comfort observation")
        event=(o["event_id"],o["mode"]) if affect else o["event_id"]
        journey=(o["journey_id"],o["mode"]) if affect else o["journey_id"]
        if event in seen: raise ValueError("Duplicate pilot event")
        if journey in journeys: raise ValueError("Duplicate observation for journey/mode")
        seen.add(event);journeys.add(journey)
        if affect and (type(o.get("valence_normalized")) not in (int,float) or not math.isfinite(o["valence_normalized"]) or abs(o["valence_normalized"]-rating/3)>1e-9):
            raise ValueError("Affective normalization must be raw/3")
        stamp = timestamp(o["confirmed_at"])
        audit = audit_context(o["context"], o["confirmed_at"], weather_model_version=weather_version)
        values, warnings = audit["values"], audit["warnings"]
        observations.append({**deepcopy(o), "rating":rating+4 if affect else rating, "context_raw":deepcopy(o["context"]), "context":values, "warnings":warnings, "context_audit":audit})
    observations.sort(key=lambda o: (timestamp(o["confirmed_at"]), o["event_id"]))
    predictions = []
    for p in data["prediction_contexts"]:
        if p.get("mode") not in MODES or not p.get("prediction_id"): raise ValueError("Invalid prediction context")
        audit = audit_context(p["context"], p["as_of"], weather_model_version=weather_version)
        values, warnings = audit["values"], audit["warnings"]
        predictions.append({**deepcopy(p),"context":values,"warnings":warnings,"context_audit":audit})
    if len({p["prediction_id"] for p in predictions}) != len(predictions): raise ValueError("Duplicate prediction ID")
    for mode in MODES:
        b = passport["profile"]["valences"][mode] if affect else passport["profile"]["beliefs"][mode]["comfort"]
        if type(b) not in (int,float) or not math.isfinite(b) or not -1 <= b <= 1:
            raise ValueError("Invalid initial comfort belief")
    return passport, observations, predictions


def _route_context(record, journey, *, mode=None, weather_model_version=None):
    from .weather_contract import LEGACY, INTENSITY, validate_version
    version = validate_version(weather_model_version or LEGACY)
    version_tag = {'weather_model_version': version} if version == INTENSITY else {}
    queried = record.get("completed_at") or journey["selection"]["chosen_at"]
    mode = mode or journey["experience"]["comfort_observation"]["mode"]
    planned_mode = {"foot":"walk","pt_bus_tram":"pt","car_driver":"car"}.get(journey["selection"]["mode_key"], journey["selection"]["mode_key"])
    # A changed mode is not assigned the selected route's weather or duration.
    if mode != planned_mode or journey["experience"]["completion"] == "changed":
        return {"queried_at":queried,"measurements":[],"scope":"actual_mode_context_unknown", **version_tag}
    route = journey["plan"]["route"]
    measurements = []
    duration = route.get("summary",{}).get("duration_seconds")
    if duration is not None:
        measurements.append({"variable":"duration_seconds","numeric_value":duration,"unit":"s",
                             "timestamp":queried,"status":"OBSERVED","source":"routing_plan"})
    snapshot = journey["plan"]["context_at_selection"].get("snapshot") or {}
    # Use only the exact candidate frozen at selection, not today's Orion data.
    segments = (snapshot.get("route_context") or {}).get("segments",[])
    grouped = {}; coverage = {}
    for index, segment in enumerate(segments):
        for reading in segment.get("raw_observations",[]):
            name = reading.get("variable")
            if name not in WEATHER: continue
            if version == INTENSITY and name == 'rain' and (
                reading.get('source_entity_id') != 'Sensor:Weather:Winfred'
                or (reading.get('source_metadata') or {}).get('source_attribute') != 'rainIntensity'):
                continue
            if version == INTENSITY and name == 'lightIntensity' and reading.get('source_entity_id') == 'Sensor:Weather:Walter':
                m = reading.get('source_metadata') or {}
                if (reading.get('unit') not in ('lux', 'lx') or m.get('pilot_unit_provenance') == 'provider_contract'
                    or m.get('pilot_original_unit', reading.get('unit')) not in ('lux', 'lx')
                    or m.get('unit_provenance') == 'INFERRED'):
                    continue
            # Keep the spatial evidence separately. If segments disagree, do not
            # manufacture a route-wide average from unknown exposure weights.
            grouped.setdefault(name, []).append(reading)
            coverage.setdefault(name, set()).add(index)
    sensor_audit = {"segment_count":len(segments), "variables":{}}
    for name in WEATHER:
        covered = coverage.get(name, set())
        sensor_audit["variables"][name] = {
            "segments_with_reading":len(covered), "segment_fraction":len(covered)/len(segments) if segments else None,
            "coverage_basis":"segment_count_not_time_exposure",
            "exclusion_reason":"no_segment_readings" if not covered else None,
            "raw_readings":deepcopy(grouped.get(name, []))}
    for name, readings in grouped.items():
        signatures = {(r.get("numeric_value"),r.get("timestamp"),r.get("unit"),r.get("source_entity_id")) for r in readings}
        if version == INTENSITY:
            signatures = {(r.get('numeric_value'), r.get('timestamp'), r.get('unit'), r.get('source_entity_id'),
                           identity(r.get('source_metadata') or {})) for r in readings}
        entry = sensor_audit["variables"][name]
        if len(coverage[name]) != len(segments):
            entry["exclusion_reason"] = "partial_segment_coverage"
            continue
        if len(signatures) != 1:
            entry["exclusion_reason"] = "conflicting_segment_readings_no_exposure_weights"
            continue
        from .context_capture import prepare_reading
        r = prepare_reading(readings[0])
        entry["unit_provenance"] = "sensor_attribute" if readings[0].get("unit") else (
            "provider_contract" if r["unit"] else "unknown")
        measurements.append(r)
    return {"queried_at":queried,"measurements":measurements,"scope":"selected_route_search_query","query_time_provenance":"response_completion_or_selection_upper_bound",
            "spatial_source":"frozen_candidate_segments","context_snapshot_digest":identity(snapshot),
            "route_sensor_audit":sensor_audit, **version_tag}


def request_from_backup(backup, *, prediction_contexts, prior_profile="moderate", target="comfort", exclusions=None, weather_model_version=None):
    validate_backup(backup)
    exclusions = backup_exclusions(backup, exclusions)
    wrappers = [json.loads(v) for k,v in backup["storage"].items() if k == PASSPORT or k.startswith(PASSPORT+".archive.")]
    wrappers += [r["confirmed_snapshot"] for r in backup["simulations"] if r.get("confirmed_snapshot")]
    originals = {p["lineage"]["passport_id"]:p for w in wrappers
                 if (p:=w.get("response",{}).get("cognitive_passport")) and p.get("lineage",{}).get("parent_passport_id") is None}
    if len(originals) != 1: raise ValueError("Select a backup containing exactly one original Passport lineage")
    passport = next(iter(originals.values()))
    subject = passport["agent_id"]
    observations, excluded = observations_from_records(backup["simulations"], subject=subject, target=target, exclusions=exclusions, weather_model_version=weather_model_version)
    data = {"schema_version":f"d-day-{target}-pilot-request-v1","subject_id":subject,"initial_passport":passport,
            "observations":observations,"prediction_contexts":prediction_contexts,"prior_profile":prior_profile}
    if weather_model_version is not None: data['weather_model_version'] = weather_model_version
    validate_request(data,target=target)
    return data, excluded


def observations_from_records(records, *, subject, target, exclusions=None, weather_model_version=None):
    if target not in ("comfort", "affect"): raise ValueError("Unknown target")
    validate_exclusions(exclusions)
    observations, excluded = [], []
    for record in records:
        for journey in record.get("journeys",[]):
            from .trip_context_review import capture_summary
            captured = capture_summary(journey)
            experience = journey.get("experience") or {}
            reason = exclusion_reason(record, journey, target=target, exclusions=exclusions)
            if reason:
                excluded.append({"event_id":experience.get("event_id"),"journey_id":journey.get("journey_id"),"reason":reason})
                continue
            if target=="affect":
                if experience.get("affect_scale_version")!="mode-affect-ordinal-minus3-to3-v1":
                    if experience:excluded.append({"event_id":experience["event_id"],"reason":"legacy_or_no_versioned_affective_observation"})
                    continue
                for mode,rating in experience.get("mode_valences_raw_minus3_to_3",{}).items():
                    observations.append({"event_id":experience["event_id"],"subject_id":subject,"journey_id":journey["journey_id"],"choice_event_id":journey["choice_event_id"],
                        "confirmed_at":experience["confirmed_at"],"mode":mode,"rating_raw_minus3_to_3":rating,
                        "valence_normalized":experience["mode_valences_normalized_minus1_to_1"][mode],"question_version":experience["affect_scale_version"],
                        "scale":"minus3-to-3","attribution":"actual_mode","context":_route_context(record,journey,mode=mode,weather_model_version=weather_model_version),"trip_context_capture_summary":captured})
                continue
            o = experience.get("comfort_observation")
            if o is None:
                if experience: excluded.append({"event_id":experience["event_id"],"reason":"legacy_or_no_bipolar_comfort_observation"})
                continue
            observations.append({"event_id":experience["event_id"],"subject_id":subject,
                "journey_id":journey["journey_id"],"choice_event_id":journey["choice_event_id"],
                "confirmed_at":experience["confirmed_at"],"mode":o["mode"],"rating":o["rating"],
                "question_version":o["question_version"],"scale":o["scale"],"attribution":"actual_mode",
                "context":_route_context(record,journey,weather_model_version=weather_model_version),"trip_context_capture_summary":captured})
    return observations, excluded
