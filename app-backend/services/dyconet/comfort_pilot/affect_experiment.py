"""Reproducible synthetic affect stress checks; not psychological validation."""
import os
os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
import argparse
import json
from pathlib import Path
import numpy as np
from .experiment import weather
from .model import MODES, fit, probabilities

PROFILES = (
    ("aligned", .3, .8), ("optimistic_wrong", .7, -1.5),
    ("pessimistic_wrong", -.7, 1.5), ("neutral_rain_sensitive", 0., 1.5),
    ("extreme_wrong", 1., -2.), ("noisy", .2, .5),
    ("novel_context", .2, 1.), ("temporal_shift", .5, 1.5),
)

def true_probs(index, mode, context, step):
    eta = PROFILES[index][2]
    if index == 7 and step > 20: eta = -1.5
    if index in (3, 6, 7):
        eta -= (2. if mode in ("walk", "bike") else .2) * np.log1p(context["rain"])
    p = probabilities(eta)
    return .6*p + .4/7 if index == 5 else p

def run(output, *, seed=20261009):
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    design = {"schema_version":"d-day-affect-experiment-v1", "seed":seed,
        "profiles":[{"id":name,"initial_normalized_valence":initial,"initial_truth_eta":eta} for name,initial,eta in PROFILES],
        "sizes":[3,40], "modes":MODES, "priors":["weak","moderate","strong"],
        "models":["basic","contextual"], "repetitions":1, "draws_per_chain":512,
        "sampler_retry_draws":4096, "held_out_per_mode":24,
        "measurement":"ordinal raw -3..3; normalized raw/3",
        "temporal_policy":"cumulative; shift scenario tests an unimplemented temporal adaptation",
        "scientific_validation_claimed":False, "selection":"balanced synthetic modes, not user choices"}
    (output/"design.json").write_text(json.dumps(design,indent=2)+"\n",encoding="utf-8")
    rows=[]; streams=[]
    for index,(name,initial,_) in enumerate(PROFILES):
        for mi,mode in enumerate(MODES):
            rng=np.random.default_rng(seed+index*1000+mi*100)
            observations=[];contexts=[];ratings=[]
            for step in range(1,65):
                c=weather(rng,novel=index==6 and step>40)
                if index in (3,7): c["rain"]=4. if step%2==0 else 0.
                raw=int(rng.choice(np.arange(-3,4),p=true_probs(index,mode,c,step)))
                contexts.append(c);ratings.append(raw)
                observations.append({"event_id":f"affect-{name}-{mode}-{step}","rating":raw+4,"context":c})
            streams.append({"profile":name,"mode":mode,"raw_minus3_to_3":ratings,"normalized_minus1_to_1":[r/3 for r in ratings],"contexts":contexts})
            test_contexts=contexts[40:];y=np.array(ratings[40:])+3
            truth=np.array([true_probs(index,mode,c,41+j) for j,c in enumerate(test_contexts)])
            for n in (3,40):
                for prior in ("weak","moderate","strong"):
                    for contextual in (False,True):
                        kwargs=dict(contextual=contextual,prior_profile=prior,provenance="declared",seed=seed+index*100+mi,draws=512)
                        posterior=fit(observations[:n],initial,**kwargs)
                        if not posterior.diagnostics["passed"]:
                            posterior=fit(observations[:n],initial,**{**kwargs,"draws":4096})
                        predicted=[posterior.predict(c) for c in test_contexts]
                        p=np.array([v["category_probabilities"] for v in predicted]);coding=np.arange(-3,4)/3
                        intervals=np.array([v["signed_belief_interval_90"] for v in predicted]);expected=truth@coding
                        dry=dict(contexts[0],rain=0.);rain=dict(dry,rain=4.)
                        rows.append({"profile":name,"mode":mode,"n":n,"prior":prior,"model":"contextual" if contextual else "basic",
                            "diagnostics_passed":posterior.diagnostics["passed"],
                            "normalized_expectation_rmse":float(np.sqrt(np.mean(((p-truth)@coding)**2))),
                            "log_score":float(np.log(np.maximum(p[np.arange(len(y)),y],1e-15)).mean()),
                            "expected_affect_interval_coverage":float(np.mean((intervals[:,0]<=expected)&(expected<=intervals[:,1]))),
                            "dry_normalized_mean":posterior.predict(dry)["signed_belief_mean"],
                            "rain_normalized_mean":posterior.predict(rain)["signed_belief_mean"]})
    for filename,data in (("streams.json",streams),("results.json",rows)):
        (output/filename).write_text(json.dumps(data,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    usable=[r for r in rows if r["diagnostics_passed"]]
    summary={"fits":len(rows),"diagnostics_passed":len(usable),"scientific_validation_claimed":False,
        "groups":[{"profile":name,"n":n,"model":model,"fits":len(group),
            "mean_normalized_expectation_rmse":float(np.mean([r["normalized_expectation_rmse"] for r in group])) if group else None}
            for name,_,_ in PROFILES for n in (3,40) for model in ("basic","contextual")
            for group in [[r for r in usable if r["profile"]==name and r["n"]==n and r["model"]==model]]]}
    (output/"summary.json").write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({"output":str(output),"fits":len(rows),"diagnostics_passed":len(usable)}))

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--output",type=Path,required=True)
    run(parser.parse_args().output)
