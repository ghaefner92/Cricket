"""Read-only ambient sampling and frozen trip-context evidence, not personal exposure."""
from copy import deepcopy
from datetime import datetime, timezone
from zoneinfo import ZoneInfo
import math
from flask import request, jsonify
from journey_contract import timestamp
from orion_query_list import OrionQueryListProvider
from orion_context import OrionProviderError
from .context_audit import audit_context, UNITS
from .model import WEATHER

VERSION = "d-day-context-sample-v1"


def prepare_reading(reading):
    r = deepcopy(reading)
    metadata = dict(r.get("source_metadata") or {})
    r["source_metadata"] = metadata
    original_unit = r.get("unit")
    name = r.get("variable")
    # Existing provider contracts can specify dimensions, never observation time.
    if name in WEATHER and name != "rain" and not original_unit and metadata.get("source_contract_confidence") in ("CONFIRMED", "STRONGLY_INFERRED"):
        r["unit"] = UNITS[name]
        metadata["pilot_unit_provenance"] = "provider_contract"
    else:
        metadata["pilot_unit_provenance"] = "sensor_or_provider_attribute" if original_unit else "unknown"
    metadata["pilot_original_unit"] = original_unit
    if r.get("unit") in ("°C", "celsius", "Celsius"): r["unit"] = "C"
    shared_clock = (r.get("source_entity_id") == "Sensor:Weather:Winfred" and
                    metadata.get("timestamp_provenance") == "PROVIDER_SHARED_CLOCK_CONFIRMED_BY_AUTHOR" and
                    metadata.get("timestamp_reference_entity") == "Sensor:Weather:Walter")
    if (r.get("source_entity_id") == "Sensor:Weather:Walter" or shared_clock) and r.get("timestamp"):
        raw_time = r["timestamp"]
        try: local = datetime.fromisoformat(raw_time.replace("Z", "+00:00"))
        except ValueError: local = None
        if local is not None and local.tzinfo is None:
            berlin = ZoneInfo("Europe/Berlin")
            candidates = [local.replace(tzinfo=berlin, fold=f) for f in (0,1)]
            valid = [c for c in candidates if c.astimezone(timezone.utc).astimezone(berlin).replace(tzinfo=None) == local]
            instants = {c.astimezone(timezone.utc) for c in valid}
            metadata.update(timestamp_raw=raw_time, timezone_provenance="AUTHOR_CONFIRMED_EUROPE_BERLIN")
            if len(instants) == 1:
                r["timestamp"] = valid[0].isoformat()
                metadata["timestamp_confidence"] = "SOURCE_LOCAL_TIME_WITH_CONFIRMED_ZONE"
            else:
                metadata["timestamp_confidence"] = "AMBIGUOUS_OR_NONEXISTENT_LOCAL_TIME"
    return r


def sample_context(data, *, provider=None, clock=None):
    from .weather_contract import LEGACY, INTENSITY, validate_version
    if not isinstance(data, dict) or set(data) not in ({'lat', 'lon'}, {'lat', 'lon', 'weather_model_version'}):
        raise ValueError('Expected coordinates and optional weather model version')
    version = validate_version(data.get('weather_model_version', LEGACY))
    lat, lon = data["lat"], data["lon"]
    if any(type(v) not in (int,float) or not math.isfinite(v) for v in (lat,lon)) or not -90 <= lat <= 90 or not -180 <= lon <= 180:
        raise ValueError("Invalid coordinates")
    clock = clock or (lambda:datetime.now(timezone.utc).isoformat())
    started = clock()
    # A single nearest station per sample, not an arbitrary mixture of stations.
    observations = (provider or OrionQueryListProvider()).get_weather(
        geometry="point", coords=f"{lat},{lon}", georel="near;maxDistance:5000", nearest_only=True)
    queried = clock()
    raw = [o.to_dict() for o in observations if o.variable in WEATHER or (version == INTENSITY and o.variable == 'cumulativeRainfall')]
    context = {"queried_at":queried,"measurements":[prepare_reading(r) for r in raw],
               "scope":"ambient_nearest_station_at_retained_gps_fix"}
    if version == INTENSITY: context['weather_model_version'] = version
    audit = audit_context(context,queried)
    return {"schema_version":VERSION,"request_started_at":started,"queried_at":queried,
            "query_location":{"lat":lat,"lon":lon},
            "raw_observations":raw,"context":context,"audit":audit,
            "status":"AVAILABLE" if raw else "EMPTY", "stored_on_server":False,
            "personal_exposure_measured":False}


def validate_capture(journey):
    capture = journey.get("context_capture")
    if capture is None: return
    if journey["capture_mode"] != "tracked": raise ValueError("Manual trip cannot contain sensor capture")
    if capture.get("schema_version") != "d-day-trip-context-v1": raise ValueError("Unknown capture version")
    points = {p["monotonic_ms"]:p for p in journey["tracking"]["points"]}
    last = None; ids = set()
    for sample in capture["samples"]:
        if sample["sample_id"] in ids: raise ValueError("Duplicate contextual sample")
        ids.add(sample["sample_id"])
        point = points.get(sample["point_monotonic_ms"])
        if point is None or sample["segment_index"] != point["segment_index"]:
            raise ValueError("Context sample is not linked to a retained GPS fix")
        requested = timestamp(sample["requested_at"])
        if requested != timestamp(point["received_at"]) or (last and requested <= last):
            raise ValueError("Invalid context sampling chronology")
        last = requested
        if journey["ended_at"] and requested > timestamp(journey["ended_at"]):
            raise ValueError("Query requested after trip")
        state = "recording"
        for event in journey["tracking"]["events"]:
            if timestamp(event["at"]) > requested: break
            if event["kind"] in ("pause","interrupted"): state = "paused"
            elif event["kind"] == "resume": state = "recording"
        if state != "recording": raise ValueError("Context queried while paused")
        if sample["status"] == "complete":
            received = timestamp(sample["received_at"])
            if received < requested or (journey["ended_at"] and received > timestamp(journey["ended_at"])):
                raise ValueError("Context response outside trip")
            result = sample["result"]
            if result.get("query_location") != {"lat":point["lat"],"lon":point["lon"]}:
                raise ValueError("Context query location mismatch")
            if result.get("schema_version") != VERSION or timestamp(result["queried_at"]) > received:
                raise ValueError("Invalid contextual response")
            if result.get("personal_exposure_measured") is not False or result.get("stored_on_server") is not False:
                raise ValueError("Unexpected contextual measurement claims")
            if result["context"]["queried_at"] != result["queried_at"]:
                raise ValueError("Query time mismatch")
            if audit_context(result["context"],sample["received_at"]) != result["audit"]:
                raise ValueError("Context audit mismatch")
            if result["context"]["measurements"] != [prepare_reading(r) for r in result["raw_observations"]]:
                raise ValueError("Context differs from preserved observations")
        elif sample["result"] is not None:
            raise ValueError("Incomplete sample cannot contain observations")
    if journey["ended_at"] and any(s["status"] == "pending" for s in capture["samples"]):
        raise ValueError("Finished trip has pending samples")


def register(app):
    @app.post("/api/dyconet/d-day/context/sample")
    def sample():
        if request.content_length and request.content_length > 1024: return jsonify(error="context_request_too_large"),413
        try:
            result = sample_context(request.get_json(silent=True))
            response = jsonify(result); response.headers["Cache-Control"] = "no-store"
            return response
        except OrionProviderError:
            return jsonify(error="context_provider_unavailable",message="Ambient sensor sampling unavailable"),503
        except (ValueError,KeyError,TypeError):
            return jsonify(error="invalid_context_sample",message="Invalid coordinates or sensor chronology"),422
