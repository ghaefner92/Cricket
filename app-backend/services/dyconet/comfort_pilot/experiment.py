"""Reproducible phase-4 simulations; data-generating truth is not the Passport."""
import os
# This CLI does not alter the production service's thread settings.
os.environ.setdefault("OPENBLAS_NUM_THREADS","1")
import argparse
from copy import deepcopy
from pathlib import Path
import json
import platform
import numpy as np
import scipy
from .model import fit, probabilities, initial_location, design_row, FEATURES, MODES, RAW_FEATURES, MODEL_VERSION, identity

PROFILES = (
 ("aligned", "Expectativas acertadas y relación estable"),
 ("optimistic_wrong", "Optimismo inicial equivocado"),
 ("pessimistic_wrong", "Pesimismo inicial equivocado"),
 ("neutral_context", "Passport neutral y relación dependiente del contexto"),
 ("extreme_wrong", "Expectativas extremas equivocadas por modo"),
 ("noisy_correlated", "Experiencias ruidosas y contexto correlacionado"),
 ("missing_and_novel", "Datos faltantes y contexto futuro diferente"),
 ("temporal_shift", "Cambio de relación durante la secuencia"),
)
SIZES = (1,3,5,10,20,40)


def passport_fixture(index):
    signed = ([.15,.3,.1,.2], [.75,.65,.6,.7], [-.75,-.6,-.65,-.7], [0,0,0,0],
              [.95,-.95,.95,-.95], [.2,-.2,.1,.3], [.25,.35,.15,.3], [.5,.4,.2,.5])[index]
    needs = ("pro_env","physical","cost","speed","comfort","reliable","autonomy","safety_crime","health_infection","privacy","safety_accident")
    p = {"schema_version":"2.0","agent_id":"synthetic-"+PROFILES[index][0],
        "lineage":{"passport_id":"synthetic-passport-"+PROFILES[index][0],"revision":1,"parent_passport_id":None},
        "profile":{"needs":{n:((index+i)%7)/6 for i,n in enumerate(needs)},
            "beliefs":{m:{n:signed[j] if n=="comfort" else ((index+j+i)%7-3)/3 for i,n in enumerate(needs)} for j,m in enumerate(MODES)},
            "valences":{m:((index+j)%7-3)/3 for j,m in enumerate(MODES)},"availability":{m:True for m in MODES}},
        "observed_measurements":{"beliefs_raw_1_to_7":{m+"__comfort":int(round(4+3*signed[j])) for j,m in enumerate(MODES) if m in ("walk","pt")}}}
    # Raw declarations and their signed coding agree; Ridge cells remain estimates.
    for m in ("walk","pt"):p["profile"]["beliefs"][m]["comfort"]=(p["observed_measurements"]["beliefs_raw_1_to_7"][m+"__comfort"]-4)/3
    return p


def weather(rng, *, novel=False):
    temperature = float(rng.uniform(32,40) if novel else rng.uniform(-3,30))
    wind = float(rng.uniform(0,9));rain = float(rng.exponential(2) if rng.random()<.35 else 0)
    return {"duration_seconds":float(rng.uniform(300,3600)),"temperature":temperature,
        "humidity":float(np.clip(75-.7*temperature+10*rain+rng.normal(0,8),10,100)),"rain":rain,
        "windSpeed":wind,"windGust":wind+float(rng.uniform(0,5)),
        "windSpeed10Min":max(0.,wind+float(rng.normal(0,.7))),
        "lightIntensity":float(rng.uniform(0,1000) if rain else rng.uniform(500,20000))}


def truth(index, mode, context, step, initial, *, noise_state=0.):
    theta = np.zeros(1+len(FEATURES));theta[0]=initial_location(initial)
    if index==1:theta[0]=-1.5
    elif index==2:theta[0]=1.5
    elif index==3:theta[0]=.2
    elif index==4:theta[0]= -2 if initial>0 else 2
    elif index in (5,6):theta[0]=.5
    elif index==7:theta[0]=1. if step<=20 else -1.5
    if index in (1,2,3,6,7):
        exposure=1. if mode in ("walk","bike") else .15
        theta[1+RAW_FEATURES.index("duration_seconds")]=-.25
        theta[1+RAW_FEATURES.index("rain")]=-.7*exposure
        theta[1+RAW_FEATURES.index("windSpeed")]=-.35*exposure
        theta[1+len(RAW_FEATURES)]=-.6*exposure
    eta=float(design_row(context)@theta)+noise_state
    p=probabilities(eta)
    if index==5:p=.55*p+.45/7  # Measurement/process misspecification relative to the learner.
    return p


def _metrics(predictions,actual_probabilities,ratings):
    p=np.asarray(predictions);t=np.asarray(actual_probabilities);y=np.asarray(ratings)-1
    return {"log_score":float(np.log(np.maximum(p[np.arange(len(y)),y],1e-15)).mean()),
        "ranked_probability_score":float(((p.cumsum(axis=1)[:,:-1]-(y[:,None]<=np.arange(6))).astype(float)**2).sum(axis=1).mean()/6),
        "signed_prediction_rmse":float(np.sqrt(np.mean(((p-t)@((np.arange(1,8)-4)/3))**2)))}


def run(output, *, repetitions=3, draws=512, seed=20261009):
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    plan={"schema_version":"d-day-phase4-experiment-v1","seed":seed,"repetitions":repetitions,"draws_per_chain":draws,
        "warmup":256,"chains":4,"sizes":SIZES,"prior_profiles":["weak","moderate","strong"],
        "models":["basic","contextual"],"profiles":[{"id":key,"description":description} for key,description in PROFILES],
        "future_test_count_per_mode":24,"interval_level":.9,"exploratory_precision_halfwidths":[.1,.2,.3],
        "precision_thresholds_agreed_or_validated":False,"selection_policy":"balanced_synthetic_modes_not_observed_choices",
        "model_version":MODEL_VERSION,"numpy":np.__version__,"scipy":scipy.__version__,"python":platform.python_version()}
    # Freeze before generating any data. Analysis choices do not follow results.
    (output/"design.json").write_text(json.dumps(plan,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    profiles=[passport_fixture(i) for i in range(8)]
    (output/"passports.json").write_text(json.dumps(profiles,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    results=[];generation=[]
    for index,(name,description) in enumerate(PROFILES):
        for repeat in range(repetitions):
            rng=np.random.default_rng(seed+index*10000+repeat*100)
            for mode_i,mode in enumerate(MODES):
                initial=profiles[index]["profile"]["beliefs"][mode]["comfort"]
                provenance="declared" if mode in ("walk","pt") else "ridge_estimated"
                stream=[];full_contexts=[];noise=0.
                for step in range(1,65):
                    full=weather(rng,novel=index==6 and step>40)
                    noise=.65*noise+float(rng.normal(0,.35)) if index==5 else 0.
                    p=truth(index,mode,full,step,initial,noise_state=noise)
                    rating=int(rng.choice(np.arange(1,8),p=p))
                    observed=deepcopy(full)
                    if index==6:
                        observed={k:(None if rng.random()<.45 else v) for k,v in observed.items()}
                    stream.append({"event_id":f"{name}-{repeat}-{mode}-{step}","rating":rating,"context":observed,
                        "true_probabilities":p.tolist(),"sequence":step})
                    full_contexts.append(full)
                generation.append({"profile":name,"repeat":repeat,"mode":mode,"events":stream,
                                   "truth_contexts":full_contexts,"truth_separate_from_initial_passport":True})
                # Identical future test events across sample sizes and priors.
                future=stream[40:];truep=[o["true_probabilities"] for o in future];ratings=[o["rating"] for o in future]
                for size in SIZES:
                    training=stream[:size]
                    static=[probabilities(initial_location(initial)).tolist() for _ in future]
                    pseudo=np.ones(7);pseudo+=2*probabilities(initial_location(initial))
                    for o in training:pseudo[o["rating"]-1]+=1
                    simple=[(pseudo/pseudo.sum()).tolist() for _ in future]
                    for prior in ("weak","moderate","strong"):
                        for contextual in (False,True):
                            posterior=fit(training,initial,contextual=contextual,prior_profile=prior,provenance=provenance,
                                          seed=seed+index*100000+repeat*1000+mode_i*100+size,draws=draws)
                            predicted=[posterior.predict(o["context"]) for o in future]
                            true_signed=np.asarray(truep)@((np.arange(1,8)-4)/3)
                            intervals=np.array([p["signed_belief_interval_90"] for p in predicted])
                            halfwidth=(intervals[:,1]-intervals[:,0])/2
                            row={"profile":name,"repeat":repeat,"mode":mode,"provenance":provenance,"size":size,
                                "prior":prior,"model":"contextual" if contextual else "basic","diagnostics":posterior.diagnostics,
                                "scores":_metrics([p["category_probabilities"] for p in predicted],truep,ratings),
                                "static_scores":_metrics(static,truep,ratings),"simple_scores":_metrics(simple,truep,ratings),
                                "signed_expectation_coverage_90":float(((true_signed>=intervals[:,0])&(true_signed<=intervals[:,1])).mean()),
                                "interval_mean_halfwidth":float(halfwidth.mean()),
                                "precision_fractions":{str(limit):float((halfwidth<=limit).mean()) for limit in (.1,.2,.3)},
                                "context_observed_fraction":float(np.mean([v is not None for o in training for v in o["context"].values()])),
                                "active_passport_modified":False}
                            results.append(row)
        print(json.dumps({"completed_profile":name,"fits":len(results)}),flush=True)
    (output/"synthetic_evidence.json").write_text(json.dumps(generation,ensure_ascii=False,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    write_results(output,plan,results)
    return results


def write_results(output,plan,results):
    output=Path(output)
    aggregates=[]
    for size in SIZES:
        for prior in ("weak","moderate","strong"):
            for model in ("basic","contextual"):
                rows=[r for r in results if r["size"]==size and r["prior"]==prior and r["model"]==model]
                aggregates.append({"size":size,"prior":prior,"model":model,"fits":len(rows),
                    "valid_fits":sum(r["diagnostics"]["passed"] for r in rows),
                    "valid_only_mean_log_score":float(np.mean([r["scores"]["log_score"] for r in rows if r["diagnostics"]["passed"]])) if any(r["diagnostics"]["passed"] for r in rows) else None,
                    "diagnostics_pass_fraction":float(np.mean([r["diagnostics"]["passed"] for r in rows])),
                    "mean_log_score":float(np.mean([r["scores"]["log_score"] for r in rows])),
                    "mean_static_log_score":float(np.mean([r["static_scores"]["log_score"] for r in rows])),
                    "mean_simple_log_score":float(np.mean([r["simple_scores"]["log_score"] for r in rows])),
                    "coverage_90":float(np.mean([r["signed_expectation_coverage_90"] for r in rows])),
                    "mean_halfwidth":float(np.mean([r["interval_mean_halfwidth"] for r in rows]))})
    report={"design_digest":identity(plan),"fits":results,"aggregates":aggregates,
        "activation_conclusion":"No universal minimum established; precision cutoffs exploratory and no live activation authorized in phase4",
        "scientific_validation_claimed":False,"active_passport_modified":False}
    (output/"results.json").write_text(json.dumps(report,ensure_ascii=False,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig,axes=plt.subplots(1,3,figsize=(15,4),layout="constrained")
    for model in ("basic","contextual"):
        for prior in ("weak","moderate","strong"):
            rows=[r for r in aggregates if r["model"]==model and r["prior"]==prior]
            for ax,key in zip(axes,("mean_log_score","coverage_90","mean_halfwidth")):
                ax.plot([r["size"] for r in rows],[r[key] for r in rows],marker="o",label=model+" / "+prior)
                ax.set_xlabel("Experiences per mode–comfort relation");ax.grid(alpha=.2)
    axes[0].set_title("Future ordinal log score (higher better)")
    axes[1].set_title("Coverage of true expected coded response");axes[1].axhline(.9,color="black",linestyle="--")
    axes[2].set_title("Mean epistemic interval half-width");axes[2].legend(fontsize=7)
    fig.suptitle("D-Day phase 4 · synthetic stress tests · not psychological validation")
    fig.savefig(output/"comparison.png",dpi=160);fig.savefig(output/"comparison.svg");plt.close(fig)
    lines=["# D-Day phase 4 — simulated pilot results","",f"Fits: {len(results)}. Repetitions per profile: {plan['repetitions']}.",
        "", "Eight deliberately difficult scenarios; aggregate coverage includes misspecification and temporal change, not just correctly specified models.",
        "Diagnostics failures remain in the report and must not be silently treated as reliable posteriors. JSON aggregates also provide valid-only scores.","", "| N | Prior | Model | Diagnostics pass | Future log score | Static | Simple | Coverage 90% | Half-width |",
        "|---|---|---|---|---|---|---|---|---|"]
    for r in aggregates:lines.append(f"| {r['size']} | {r['prior']} | {r['model']} | {r['diagnostics_pass_fraction']:.2f} | {r['mean_log_score']:.3f} | {r['mean_static_log_score']:.3f} | {r['mean_simple_log_score']:.3f} | {r['coverage_90']:.2f} | {r['mean_halfwidth']:.3f} |")
    lines.extend(["", "## Per-profile comparison at N=40, moderate prior", "", "| Profile | Contextual log score | Basic | Static | Simple | Contextual coverage 90% |", "|---|---|---|---|---|---|"])
    for profile,_ in PROFILES:
        context=[r for r in results if r['profile']==profile and r['size']==40 and r['prior']=='moderate' and r['model']=='contextual' and r['diagnostics']['passed']]
        basic=[r for r in results if r['profile']==profile and r['size']==40 and r['prior']=='moderate' and r['model']=='basic' and r['diagnostics']['passed']]
        if context and basic:
            avg=lambda rows,key:float(np.mean([r[key]['log_score'] for r in rows]))
            lines.append(f"| {profile} | {avg(context,'scores'):.3f} | {avg(basic,'scores'):.3f} | {avg(context,'static_scores'):.3f} | {avg(context,'simple_scores'):.3f} | {np.mean([r['signed_expectation_coverage_90'] for r in context]):.2f} |")
    failures=[r for r in results if not r['diagnostics']['passed']]
    lines.extend(["",f"Unresolved diagnostic failures: {len(failures)}."])
    for r in failures:lines.append(f"- {r['profile']} / repeat {r['repeat']} / {r['mode']} / N={r['size']} / {r['prior']} / {r['model']}; excluded from valid-only aggregates.")
    lines.extend(["", "No sample count guarantees validity. Precision thresholds are exploratory; no activation rule has been validated or applied.",
                  "Synthetic experience generation is separate from the initial Passport and does not simulate observed human choices."])
    (output/"REPORT.md").write_text("\n".join(lines)+"\n",encoding="utf-8")


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output",type=Path,required=True);parser.add_argument("--repetitions",type=int,default=3)
    parser.add_argument("--draws",type=int,default=512);parser.add_argument("--seed",type=int,default=20261009)
    args=parser.parse_args()
    if not 1<=args.repetitions<=100:parser.error("repetitions must be 1–100")
    run(args.output,repetitions=args.repetitions,draws=args.draws,seed=args.seed)
