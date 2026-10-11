"""Read-only laboratory endpoint and offline CLI. No HOTCO/Passport mutation."""
import argparse
import json
from pathlib import Path
from flask import request, jsonify
from .evidence import validate_request, request_from_backup
from .model import fit, identity, MODEL_VERSION, FEATURES, MODES


def analyze(data, *, target="comfort"):
    from .weather_contract import LEGACY, INTENSITY, FEATURES_V2, validate_version, specification
    version = validate_version(data.get('weather_model_version', LEGACY))
    features = FEATURES_V2 if version == INTENSITY else FEATURES
    passport, observations, predictions = validate_request(data,target=target)
    affect=target=="affect"
    if affect and data.get("literature_shape",False):raise ValueError("Comfort literature-shaped prior must not be reused for affect")
    options = {k:data[k] for k in ("prior_profile","draws","warmup","cutpoint_scale","literature_shape") if k in data}
    if version == INTENSITY: options['weather_model_version'] = version
    base_seed = data.get("seed",20261009)
    if type(base_seed) is not int or not 0 <= base_seed < 2**32: raise ValueError("Invalid seed")
    outputs = []; fitted = {}
    raw = passport.get("observed_measurements",{}).get("beliefs_raw_1_to_7",{})
    for p in predictions:
        # Forecast-specific cutoff; later confirmations cannot train an earlier prediction.
        from journey_contract import timestamp
        evidence = [o for o in observations if o["mode"]==p["mode"] and timestamp(o["confirmed_at"]) < timestamp(p["as_of"])]
        initial = passport["profile"]["valences"][p["mode"]] if affect else passport["profile"]["beliefs"][p["mode"]]["comfort"]
        provenance = "declared" if affect or p["mode"]+"__comfort" in raw else "ridge_estimated"
        comparisons = {}
        for contextual in (False,True):
            seed = (base_seed + int(identity({"mode":p["mode"],"events":[o["event_id"] for o in evidence],"contextual":contextual})[:8],16)) % 2**32
            key=(p["mode"],tuple(o["event_id"] for o in evidence),contextual)
            if key not in fitted:
                posterior = fit(evidence,initial,contextual=contextual,provenance=provenance,seed=seed,**options)
                attempts=[{"draws":options.get("draws",1024),"diagnostics":posterior.diagnostics}]
                if not posterior.diagnostics["passed"] and options.get("draws",1024)<4096:
                    posterior=fit(evidence,initial,contextual=contextual,provenance=provenance,seed=seed,**{**options,"draws":4096})
                    attempts.append({"draws":4096,"diagnostics":posterior.diagnostics})
                fitted[key]=(posterior,attempts)
            posterior,attempts=fitted[key]
            prediction = posterior.predict(p["context"])
            if affect:
                prediction["category_labels_raw_minus3_to_3"]=list(range(-3,4))
                prediction["normalized_valence_mean"]=prediction.pop("signed_belief_mean")
                prediction["normalized_valence_median"]=prediction.pop("signed_belief_median")
                prediction["normalized_valence_interval_90"]=prediction.pop("signed_belief_interval_90")
                prediction["expected_raw_valence"]=3*prediction["normalized_valence_mean"]
                prediction.pop("expected_ordinal_response")
                prediction["interval_kind"]="epistemic_interval_for_expected_normalized_affect_not_single_emotion"
            unseen = [k for k in features if not k.startswith("missing:") and k!="temperature_squared"
                      and p["context"].get(k) is not None and (not evidence or all(o["context"].get(k) is None for o in evidence))]
            outside = {}
            for key,value in p["context"].items():
                history = [o["context"][key] for o in evidence if o["context"].get(key) is not None]
                if value is not None and history and not min(history)<=value<=max(history):
                    outside[key] = {"observed_min":min(history),"observed_max":max(history),"prediction_value":value}
            comparisons["contextual" if contextual else "basic"] = {**prediction,"diagnostics":posterior.diagnostics,
                "prior":posterior.prior,"event_ids":list(posterior.event_ids),"observation_count":len(evidence),"sampling_attempts":attempts,
                "prediction_usable_for_research":posterior.diagnostics["passed"] and (
                    not contextual or p['context_audit'].get('contextual_prediction_allowed_for_research', True)),
                "unseen_features":unseen if contextual else [],"outside_observed_ranges":outside if contextual else {},"activation":{"applied":False,
                    "reason":"phase4_shadow_only_no_validated_precision_rule"},("initial_normalized_valence" if affect else "initial_signed_belief"):initial}
            if version == INTENSITY:
                comparisons['contextual' if contextual else 'basic']['weather_model_version'] = version
                comparisons['contextual' if contextual else 'basic']['context_policy'] = {
                    'state': p['context_audit']['context_state'] if contextual else 'not_required',
                    'fallback': p['context_audit']['fallback'] if contextual else None,
                    'rain_intensity_used': p['context_audit']['rain_intensity_used'] if contextual else False,
                    'empirical_precision_validated': False,
                }
        outputs.append({"prediction_id":p["prediction_id"],"mode":p["mode"],"as_of":p["as_of"],
                        "excluded_after_as_of_event_ids":[o["event_id"] for o in observations if o["mode"]==p["mode"] and timestamp(o["confirmed_at"])>=timestamp(p["as_of"])],
                        "context_values":p["context"],"context_warnings":p["warnings"],"context_audit":p["context_audit"],"models":comparisons})
    result = {"schema_version":f"d-day-{target}-pilot-result-v1","model_version":(
        f'd-day-{target}-ordinal-intensity-v2' if version == INTENSITY else 'd-day-affect-ordinal-v1' if affect else MODEL_VERSION),"learning_target":target,
        "subject_id":data["subject_id"],"request_digest":identity(data),"initial_passport_id":passport["lineage"]["passport_id"],
        "predictions":outputs,"feature_columns":["intercept",*features],"uv_excluded":True,
        "sampler":"four_chain_independence_metropolis_student_t_map_proposal_full_posterior_correction",
        "learned_in_sandbox":any(p["models"]["basic"]["observation_count"]>0 for p in outputs),"learning_applied_to_active_passport":False,
        "state_carryover_applied":False,"hotco_modified":False,"stored_on_server":False,"scientific_validation_claimed":False,
        "observation_context_audits":[{"event_id":o["event_id"],"mode":o["mode"],"audit":o["context_audit"],"trip_capture":o.get("trip_context_capture_summary")} for o in observations],
        "observation_warnings":[{"event_id":o["event_id"],"warnings":o["warnings"]} for o in observations if o["warnings"]]}
    if version == INTENSITY: result['weather_contract'] = specification()
    return result


def register(app):
    @app.post("/api/dyconet/d-day/comfort-pilot/analyze")
    def comfort_pilot_analyze():
        if request.content_length is not None and request.content_length > 4*1024*1024:
            return jsonify(error="pilot_request_too_large"),413
        data = request.get_json(silent=True)
        try:
            if not isinstance(data,dict) or len(data.get("observations",[]))>512 or not 1<=len(data.get("prediction_contexts",[]))<=8:
                raise ValueError("Use offline analysis for larger datasets")
            return jsonify(analyze(data))
        except (ValueError,KeyError,TypeError,IndexError,AttributeError):
            return jsonify(error="invalid_comfort_pilot_request",message="Invalid pilot evidence, context, prior or sampler settings"),422


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input",type=Path);parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--backup",action="store_true");parser.add_argument("--contexts",type=Path)
    parser.add_argument("--exclusions",type=Path)
    from .weather_contract import LEGACY, INTENSITY
    parser.add_argument('--weather-model-version', choices=(LEGACY, INTENSITY))
    args=parser.parse_args();data=json.loads(args.input.read_text(encoding="utf-8"));excluded=[]
    if args.backup:
        if not args.contexts: parser.error("--backup requires --contexts with prediction contexts")
        exclusions=json.loads(args.exclusions.read_text(encoding="utf-8")) if args.exclusions else None
        data,excluded=request_from_backup(data,prediction_contexts=json.loads(args.contexts.read_text(encoding="utf-8")),exclusions=exclusions,weather_model_version=args.weather_model_version)
    elif args.exclusions:
        parser.error("--exclusions requires --backup")
    if args.weather_model_version:
        if data.get('weather_model_version', args.weather_model_version) != args.weather_model_version:
            parser.error('Input and command weather model versions differ')
        data['weather_model_version'] = args.weather_model_version
    result=analyze(data);result["excluded_legacy_events"]=excluded
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    print(json.dumps({"status":"complete","output":str(args.output),"predictions":len(result["predictions"]),"active_passport_modified":False}))


if __name__=="__main__":main()
