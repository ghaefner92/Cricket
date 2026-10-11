"""Describe captured ambient queries; do not estimate unobserved exposure."""
from collections import Counter
from .model import WEATHER, identity


def capture_summary(journey):
    capture = journey.get("context_capture")
    if capture is None:
        return {"available":False,"reason":"legacy_or_manual_no_capture"}
    samples = capture["samples"]
    complete = [s for s in samples if s["status"] == "complete"]
    variables = {}
    for key in WEATHER:
        readings = [s['result']['audit']['values'].get(key) for s in complete
                    if s['result']['audit']['schema_version'] == 'd-day-context-audit-v1'
                    and s['result']['audit']['values'].get(key) is not None]
        variables[key] = {"usable_query_count":len(readings),"minimum":min(readings) if readings else None,
                          "maximum":max(readings) if readings else None}
    v2 = [s for s in complete if s['result']['audit']['schema_version'] == 'd-day-context-audit-v2']
    if v2:
        from .weather_contract import RAW_V2, INTENSITY
        # Separate versions: never pool old mm and new mm/h under "rain".
        versions = {}
        for key in RAW_V2[1:]:
            values = [s['result']['audit']['values'][key] for s in v2 if s['result']['audit']['values'].get(key) is not None]
            versions[key] = {'usable_query_count': len(values), 'minimum': min(values) if values else None,
                             'maximum': max(values) if values else None}
        variables[INTENSITY] = versions
    actual = (journey.get("experience") or {}).get("actual_modes", [])
    return {"available":True,"capture_digest":identity(capture),"sample_count":len(samples),
            "sample_status_counts":dict(Counter(s["status"] for s in samples)),"variables":variables,
            "sampling_limit_reached":len(samples) >= capture["sample_limit"],
            "planned_sampling_interval_seconds":capture["sampling_interval_seconds"],
            "actual_modes_confirmed":actual,"mode_or_leg_exposure_attribution":"not_inferred",
            "continuous_exposure_reconstructed":False,"exposure_weighted_average_computed":False,
            "used_to_replace_frozen_prediction_context":False,
            "all_complete_queries_have_semantic_metadata":bool(complete) and
                all(s["result"]["audit"]["semantic_metadata_complete"] for s in complete)}
