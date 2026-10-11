"""Prospective pilot review on common events; no latent coverage claims from ratings."""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
from zoneinfo import ZoneInfo
import numpy as np
from journey_contract import timestamp
from .prospective_evaluation import report
from .trip_context_review import capture_summary


def predictive_interval(probabilities):
    p=np.asarray(probabilities,dtype=float)
    if p.shape != (7,) or not np.isfinite(p).all() or (p<0).any() or abs(p.sum()-1)>1e-6:
        raise ValueError("Invalid predictive distribution")
    cdf=np.cumsum(p)
    lower=min(int(np.searchsorted(cdf,.05)),6);upper=min(int(np.searchsorted(cdf,.95)),6)
    return lower,upper,float(p[lower:upper+1].sum())


def review(backup, *, exclusions=None):
    scored=report(backup, exclusions=exclusions)
    records={r["search_id"]:r for r in backup["simulations"]}
    event_records={(j.get("experience") or {}).get("event_id"):(r,j) for r in records.values() for j in r.get("journeys",[]) if j.get("experience")}
    rows=[]; paired=[]; captures=[]
    for r,j in event_records.values():captures.append({"journey_id":j["journey_id"],"summary":capture_summary(j)})
    for e in scored["evaluations"]:
        if e["status"] != "scored":continue
        r,j=event_records[e["event_id"]]
        for target in ("comfort","affect"):
            candidates=[s for s in e["scores"] if s["target"]==target]
            if {s["model"] for s in candidates} != {"static","basic","contextual"}:continue
            # All three distributions were frozen before this same choice.
            pred=next(p for p in r["d_day_forecast"]["result"]["targets"][target]["predictions"] if p["prediction_id"]==e["prediction_id"] and p["mode"]==candidates[0]["mode"])
            model_scores={s["model"]:s for s in candidates}
            paired.append({"event_id":e["event_id"],"target":target,"mode":candidates[0]["mode"],
                "confirmed_at":j["experience"]["confirmed_at"],
                "contextual_minus_basic_log_score":model_scores["contextual"]["log_score"]-model_scores["basic"]["log_score"],
                "contextual_minus_static_log_score":model_scores["contextual"]["log_score"]-model_scores["static"]["log_score"],
                "context_warnings":pred.get("context_warnings",[]),"context_values":pred.get("context_values",{})})
            version = candidates[0].get('weather_model_version')
            if version: paired[-1]['weather_model_version'] = version
            for s in candidates:
                model=pred["models"][s["model"]]
                lower,upper,mass=predictive_interval(model["category_probabilities"])
                index=s["raw_response"]-1 if target=="comfort" else s["raw_response"]+3
                interval=model.get("signed_belief_interval_90") if target=="comfort" else model.get("normalized_valence_interval_90")
                rows.append({**s,"event_id":e["event_id"],"predictive_response_interval_indices":[lower,upper],
                    "predictive_response_interval_mass":mass,"response_inside_predictive_interval":lower<=index<=upper,
                    "expected_response_epistemic_halfwidth":(interval[1]-interval[0])/2 if interval else None})
    groups=[]
    for target in ("comfort","affect"):
        for mode in ("walk","bike","pt","car"):
            for model in ("static","basic","contextual"):
                data=[s for s in rows if (s["target"],s["mode"],s["model"])==(target,mode,model)]
                if not data:continue
                for version in sorted({s.get('weather_model_version', '') for s in data}):
                    subset = [s for s in data if s.get('weather_model_version', '') == version]
                    groups.append({"target":target,"mode":mode,"model":model,"matched_event_count":len(subset),
                        **({'weather_model_version': version} if version else {}),
                        **{key:sum(s[key] for s in subset)/len(subset) for key in ("log_score","ranked_probability_score","normalized_absolute_error","predictive_response_interval_mass","response_inside_predictive_interval")}})
    days=defaultdict(list)
    for p in paired:days[(p["target"],p["mode"],timestamp(p["confirmed_at"]).astimezone(ZoneInfo("Europe/Berlin")).date().isoformat(),p.get('weather_model_version',''))].append(p["contextual_minus_basic_log_score"])
    return {"schema_version":"d-day-pilot-review-v1","prospective_evaluation":scored,"matched_scores":rows,"group_metrics":groups,
        "paired_differences":paired,"calendar_timezone":"Europe/Berlin","temporal_daily_blocks":[{"target":k[0],"mode":k[1],"confirmation_calendar_date":k[2],"n":len(v),"mean_log_score_difference":float(np.mean(v)),**({'weather_model_version':k[3]} if k[3] else {})} for k,v in sorted(days.items())],
        "unscored_reasons":dict(Counter(e.get("reason") for e in scored["evaluations"] if e["status"]!="scored")),
        "trip_context_capture":captures,"activation_eligible":False,
        "precision_rule_status":"not_predefined_or_validated; report widths without arbitrary activation threshold",
        "temporal_candidate_selection":"not_selected; diffusion candidates remain offline",
        "latent_expectation_coverage_claimed":False,"independent_trip_sampling_assumed":False,
        "scientific_validation_claimed":False,"active_passport_modified":False}


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("backup",type=Path);p.add_argument("--output",required=True,type=Path)
    p.add_argument("--exclusions",type=Path)
    args=p.parse_args();exclusions=json.loads(args.exclusions.read_text(encoding="utf-8")) if args.exclusions else None
    r=review(json.loads(args.backup.read_text(encoding="utf-8")),exclusions=exclusions)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(r,ensure_ascii=False,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    print(json.dumps({"matched_target_events":len(r["paired_differences"]),"activation_eligible":False}))
