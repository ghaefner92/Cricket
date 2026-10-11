"""Traceable sensor checks; no unit guessing or exposure reconstruction."""
from copy import deepcopy
from datetime import timedelta
import math
from journey_contract import timestamp
from .model import RAW_FEATURES, WEATHER, FEATURES, design_row
from .sensor_time import reception_check

VERSION = "d-day-context-audit-v1"
UNITS = {"duration_seconds":"s", "temperature":"C", "humidity":"%", "rain":"mm",
         "windSpeed":"m/s", "windGust":"m/s", "windSpeed10Min":"m/s", "lightIntensity":"lux"}


def audit_context(context, as_of, *, weather_model_version=None):
    from .weather_contract import LEGACY, INTENSITY, validate_version
    version = validate_version(weather_model_version or context.get('weather_model_version', LEGACY))
    if context.get('weather_model_version', version) != version:
        raise ValueError('Context and request weather model versions differ')
    if version == INTENSITY:
        from .weather_audit_v2 import audit
        return audit(context, as_of)
    queried = timestamp(context["queried_at"])
    if queried > timestamp(as_of):
        raise ValueError("Context query is later than prediction")
    values = dict.fromkeys(RAW_FEATURES)
    readings = []; warnings = []
    for item in context.get("measurements", []):
        key = item.get("variable")
        reason = None; age = None
        reception = reception_check(item, queried)
        if key == "uvIndex": reason = "UV excluded by protocol"
        elif key not in RAW_FEATURES: reason = "Unsupported measurement excluded: " + str(key)
        elif reception is not None: reason = reception["reason"]
        elif not item.get("timestamp"): reason = key + ": observation time unknown"
        else:
            try: measured = timestamp(item["timestamp"])
            except (ValueError, TypeError):
                reason = key + ": explicit observation timezone unavailable"
            else:
                if measured > queried: raise ValueError("Observation is later than its query")
                age = (queried - measured).total_seconds()
                if queried - measured > timedelta(minutes=30): reason = key + ": stale measurement"
        value = item.get("numeric_value")
        if reason is None and (item.get("status") != "OBSERVED" or item.get("unit") != UNITS[key]
                or not item.get("source") or type(value) not in (int, float) or not math.isfinite(value)):
            reason = key + ": invalid value, status, source or unit"
        if reason is None:
            try: design_row({key:value})
            except ValueError: reason = key + ": outside measurement domain"
        if reason is None:
            if values[key] is not None: raise ValueError("Duplicate contextual variable")
            values[key] = value
        else: warnings.append(reason)
        metadata = item.get("source_metadata") or {}
        # Preserve provider metadata verbatim. Units alone never establish the
        # accumulation/averaging window or the user's actual exposure.
        issues = []
        if key in WEATHER and not isinstance(metadata.get("measurement_semantics"), str):
            issues.append("measurement_semantics_unverified")
        if key in ("rain", "windGust", "windSpeed10Min"):
            window = metadata.get("measurement_window_seconds")
            if type(window) not in (int, float) or not math.isfinite(window) or window <= 0:
                issues.append("measurement_window_unverified")
            elif key == "windSpeed10Min" and window != 600:
                issues.append("measurement_window_conflicts_with_attribute_name")
        if key == "duration_seconds" and item.get("source") == "routing_plan":
            issues.append("planned_duration_not_measured_trip_duration")
        readings.append({"measurement":deepcopy(item), "included":reason is None,
                         "exclusion_reason":reason, "age_seconds_at_query":age,
                         "expected_unit":UNITS.get(key), "unit_compatible":key in UNITS and item.get("unit") == UNITS.get(key),
                         "semantic_issues":issues})
        if reception is not None:
            readings[-1]["sensor_time_audit"] = {k:v for k,v in reception.items() if k != "reason"}
    route = deepcopy(context.get("route_sensor_audit", {}))
    for key, entry in route.get("variables", {}).items():
        if entry.get("exclusion_reason"):
            warnings.append(key + ": " + entry["exclusion_reason"])
    encoded = design_row(values).tolist()
    return {"schema_version":VERSION, "values":values, "warnings":sorted(set(warnings)),
            "readings":readings, "route_coverage":route,
            "weather_used":sum(values[k] is not None for k in WEATHER), "weather_expected":len(WEATHER),
            "scope":context.get("scope", "unspecified"),
            "actual_trip_exposure_reconstructed":False,
            "semantic_metadata_complete":all(not r["semantic_issues"] for r in readings if r["included"])
                and any(values[k] is not None for k in WEATHER),
            "feature_columns":["intercept", *FEATURES], "encoded_design_row":encoded}


def main():
    """Audit a pilot request offline without fitting a posterior or calling Orion."""
    import argparse
    import json
    from pathlib import Path
    from .evidence import validate_request
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--target", choices=("comfort", "affect"), default="comfort")
    args = parser.parse_args()
    data = json.loads(args.input.read_text(encoding="utf-8"))
    _, observations, predictions = validate_request(data, target=args.target)
    report = {"schema_version":VERSION, "target":args.target,
              "observations":[{"event_id":o["event_id"],"mode":o["mode"],"audit":o["context_audit"]} for o in observations],
              "predictions":[{"prediction_id":p["prediction_id"],"mode":p["mode"],"audit":p["context_audit"]} for p in predictions],
              "active_passport_modified":False, "live_sensor_validation_performed":False}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False)+"\n", encoding="utf-8")
    print(json.dumps({"output":str(args.output), "observations":len(observations), "predictions":len(predictions)}))


if __name__ == "__main__": main()
