"""Assemble phase-4 evidence and explicit outstanding gates; never certify a model."""
import argparse
from hashlib import sha256
import json
from pathlib import Path


def run(output):
    output=Path(output);workspace=Path(__file__).resolve().parents[4]
    network=json.loads((output/"network-sensitivity/results.json").read_text(encoding="utf-8"))
    live=json.loads((output/"orion-contract-audit.json").read_text(encoding="utf-8"))
    runtime=json.loads((output/"runtime-checks.json").read_text(encoding="utf-8"))
    verification=json.loads((output/"verification.json").read_text(encoding="utf-8"))
    rows=network["results"]
    summary={**network["summary"],
        "near_tie_centers":sum(c["variants"][1]["near_tie_at_solver_margin"] for c in rows),
        "near_tie_initials":sum(c["variants"][0]["near_tie_at_solver_margin"] for c in rows),
        "center_changes_without_near_ties":sum(c["center_winner_differs_from_initial"] and not c["variants"][0]["near_tie_at_solver_margin"] and not c["variants"][1]["near_tie_at_solver_margin"] for c in rows),
        "endpoint_sensitive_without_near_ties":sum(not c["variants"][1]["near_tie_at_solver_margin"] and any(v["terminal_action_winner"] != c["variants"][1]["terminal_action_winner"] and not v["near_tie_at_solver_margin"] for v in c["variants"][2:]) for c in rows)}
    gates=[
        {"item":"runtime_sampling_and_shared_contract","status":"implemented_and_tested","requires_participants":False},
        {"item":"sensor_freshness","status":"pending_provider; stale_readings_excluded" if any("stale" in w for s in live["stations"] for w in s["audit"]["warnings"]) else "no_stale_readings_in_this_query; completeness_still_audited", "requires_participants":False},
        {"item":"rain_quantity_unit_and_window_comparability","status":"pending; mm/h not converted to mm","requires_participants":False},
        {"item":"temporal_stress_calibration","status":"undercoverage_in_previous_stress_tests; no_candidate_activated","requires_participants":False},
        {"item":"prospective_experience_prediction_validation","status":"pending_real_confirmed_responses","requires_participants":True},
        {"item":"instrument_comprehension","status":"pending_users","requires_participants":True},
        {"item":"precision_and_activation_rule","status":"pending_prespecification_and_validation; no_arbitrary_minimum_n","requires_participants":True},
        {"item":"visual_device_controls","status":"pending_unlock; UI_checks_could_not_access_controls" if verification["device_visual_pending"] else "verified", "requires_participants":False}]
    report={"schema_version":"d-day-phase4-closure-review-v1","app_version":"0.4.62",
        "technical_delivery":"implemented; remaining_sensor_and_validation_gates_explicit",
        "phase4_scientific_validation_complete":False,"active_passport_learning_applied":False,"hotco_production_modified":False,
        "temporal_adaptation_activated":False,"ui_changed":False,
        "tests":verification,
        "live_stations":[{"id":s["entity_id"],"usable_weather_count":s["audit"]["weather_used"],"warnings":s["audit"]["warnings"]} for s in live["stations"]],
        "runtime":runtime,"network_sensitivity":summary,"outstanding_gates":gates,
        "personal_exposure_measured":False,"participants_used_for_new_simulations":False}
    (output/"readiness.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    sources=["comfort_pilot/context_capture.py","comfort_pilot/context_audit.py","comfort_pilot/network_sensitivity.py","comfort_pilot/pilot_review.py","comfort_pilot/model.py","orion_query_list.py","hotco_ct_v4_3.py","input_mapping_v4_3.py"]
    files=[workspace/"app-backend/services/dyconet"/s for s in sources]
    files.extend([workspace/"contracts/journey-v1.schema.json"])
    files.extend(workspace/"android-native/app/src/main/java/de/ovgu/imiq/cricket"/s for s in ("JourneyContextCapture.kt","TrackingService.kt","TrackingEngine.kt","TripRepository.kt"))
    manifest={"source_sha256":{str(p.relative_to(workspace)):sha256(p.read_bytes()).hexdigest() for p in files},
              "artifact_sha256":{str(p.relative_to(output)):sha256(p.read_bytes()).hexdigest() for p in output.rglob("*.npz")}}
    (output/"implementation_manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
    return report


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--output",required=True,type=Path)
    r=run(p.parse_args().output);print(json.dumps({"scientific_validation_complete":False,"network":r["network_sensitivity"]}))
