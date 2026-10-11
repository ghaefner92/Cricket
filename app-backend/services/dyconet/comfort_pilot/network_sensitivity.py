"""Offline 11–4–4 HOTCO sensitivity; marginal endpoint sweeps are not joint credible sets."""
import os
os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
import argparse
from copy import deepcopy
from dataclasses import asdict
import json
from pathlib import Path
import numpy as np
from hotco_ct_v4_3 import HOTCOCTv43, HOTCOCTParameters
from input_mapping_v4_3 import MODES as NETWORK_MODES, NEEDS
from .model import MODES, fit, initial_location, identity
from .experiment import passport_fixture, weather, truth, PROFILES
from .affect_experiment import true_probs

CONTEXTS = {
    "mild_dry":{"duration_seconds":1200,"temperature":20,"humidity":50,"rain":0,"windSpeed":1,"windGust":2,"windSpeed10Min":1,"lightIntensity":10000},
    "cold_wet_windy":{"duration_seconds":1200,"temperature":5,"humidity":90,"rain":6,"windSpeed":7,"windGust":12,"windSpeed10Min":6,"lightIntensity":200}}


def simulate_batch(needs, beliefs, valences, availability, parameters=None):
    class LaboratoryBatch(HOTCOCTv43):
        def arrays_from_input(self, participant):
            b=np.asarray(beliefs,dtype=float);v=np.asarray(valences,dtype=float)
            a=np.broadcast_to(np.asarray(availability,dtype=float),(len(b),4)).copy()
            n=np.broadcast_to(np.asarray(needs,dtype=float),(len(b),11))
            return np.c_[n,np.zeros((len(b),4)),v],b,a
    return LaboratoryBatch(parameters=parameters).simulate(None)


def run(output, seed=20261014):
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    design={"schema_version":"d-day-network-sensitivity-v1","seed":seed,"profiles":8,"history_size_per_mode":40,
            "priors":["weak","moderate","strong"],"contexts":CONTEXTS,"topology":[11,4,4],
            "hotco_parameters":asdict(HOTCOCTParameters()),"perturbations":"one target/mode endpoint at a time around posterior means",
            "posterior_interval_level":.9,"joint_posterior_or_joint_credible_set_claimed":False,
            "rain_units":"synthetic mm; no conversion of live mm/h", "ui_modified":False,
            "scientific_validation_claimed":False,"active_passport_modified":False}
    (output/"design.json").write_text(json.dumps(design,indent=2)+"\n",encoding="utf-8")
    rows=[]; histories=[];profiles=[passport_fixture(i) for i in range(8)]
    for index,passport in enumerate(profiles):
        original=deepcopy(passport);fitted={}
        for mode_index,mode in enumerate(MODES):
            rng=np.random.default_rng(seed+1000*index+mode_index*10)
            initial=passport["profile"]["beliefs"][mode]["comfort"]
            contexts=[weather(rng) for _ in range(40)]
            for target in ("comfort","affect"):
                response_rng=np.random.default_rng(seed+50000*(target=="affect")+index*1000+mode_index)
                observations=[]
                for step,context in enumerate(contexts,1):
                    probs=truth(index,mode,context,step,initial) if target=="comfort" else true_probs(index,mode,context,step)
                    rating=int(response_rng.choice(np.arange(1,8),p=probs))
                    observations.append({"event_id":f"synthetic-{index}-{mode}-{target}-{step}","rating":rating,"context":context,
                        **({"rating_raw_minus3_to_3":rating-4,"valence_normalized":(rating-4)/3} if target=="affect" else {})})
                histories.append({"profile":index,"mode":mode,"target":target,"observations":observations})
                reference=initial if target=="comfort" else passport["profile"]["valences"][mode]
                for prior in design["priors"]:
                    options=dict(contextual=True,prior_profile=prior,seed=seed+index*100+mode_index+(10000 if target=="affect" else 0),draws=512)
                    provenance="ridge_estimated" if target=="comfort" and mode not in ("walk","pt") else "declared"
                    posterior=fit(observations,reference,provenance=provenance,**options)
                    if not posterior.diagnostics["passed"]:posterior=fit(observations,reference,provenance=provenance,**{**options,"draws":4096})
                    fitted[(target,mode,prior)]=posterior
        needs=[passport["profile"]["needs"][n] for n in NEEDS]
        base_beliefs=np.array([[passport["profile"]["beliefs"][m][n] for n in NEEDS] for m in NETWORK_MODES])
        base_valences=np.array([passport["profile"]["valences"][m] for m in NETWORK_MODES])
        for prior in design["priors"]:
            for context_name,context in CONTEXTS.items():
                center_b=base_beliefs.copy();center_v=base_valences.copy();predictions={};diagnostics={}
                for target in ("comfort","affect"):
                    for mi,mode in enumerate(NETWORK_MODES):
                        posterior=fitted[(target,mode,prior)];p=posterior.predict(context)
                        predictions[f"{target}:{mode}"]=p;diagnostics[f"{target}:{mode}"]=posterior.diagnostics
                        if target=="comfort":center_b[mi,NEEDS.index("comfort")]=p["signed_belief_mean"]
                        else:center_v[mi]=p["signed_belief_mean"]
                labels=["initial_passport","posterior_means"]
                beliefs=[base_beliefs.copy(),center_b.copy()];valences=[base_valences.copy(),center_v.copy()]
                for target in ("comfort","affect"):
                    for mi,mode in enumerate(NETWORK_MODES):
                        for side,value in zip(("lower","upper"),predictions[f"{target}:{mode}"]["signed_belief_interval_90"]):
                            b=center_b.copy();v=center_v.copy()
                            if target=="comfort":b[mi,NEEDS.index("comfort")]=value
                            else:v[mi]=value
                            labels.append(f"{target}:{mode}:{side}");beliefs.append(b);valences.append(v)
                result=simulate_batch(needs,beliefs,valences,[True]*4)
                terminal=result.final_state
                actions=terminal[:,11:15];winner=np.argmax(actions,axis=1)
                margins=np.sort(actions,axis=1)[:,-1]-np.sort(actions,axis=1)[:,-2]
                path=f"trajectory-{index}-{prior}-{context_name}.npz"
                np.savez_compressed(output/path,time=np.arange(result.trajectory.shape[1])*HOTCOCTParameters().dt,
                                    trajectory=result.trajectory,topology=result.topology)
                valid=all(d["passed"] for d in diagnostics.values())
                rows.append({"profile":PROFILES[index][0],"prior":prior,"context":context_name,"diagnostics_passed":valid,
                    "posterior_diagnostics":diagnostics,"predictions":predictions,"trajectory_file":path,
                    "variants":[{"label":label,"terminal_action_winner":NETWORK_MODES[int(winner[i])],
                        "action_margin":float(margins[i]),"terminal_state":terminal[i].tolist(),
                        "near_tie_at_solver_margin":bool(margins[i]<=HOTCOCTParameters().practical_margin_threshold)} for i,label in enumerate(labels)],
                    "endpoint_sweeps_change_center_winner":bool(np.any(winner[2:]!=winner[1])),
                    "center_winner_differs_from_initial":bool(winner[1]!=winner[0]),
                    "remaining_40_belief_weights_unchanged":True,"goals_unchanged":True})
        assert passport==original
        print(f"profile {index+1}/8 complete",flush=True)
    valid=[r for r in rows if r["diagnostics_passed"]]
    summary={"cases":len(rows),"trajectories":sum(len(r["variants"]) for r in rows),"usable_cases":len(valid),
             "usable_center_winner_changes":sum(r["center_winner_differs_from_initial"] for r in valid),
             "usable_endpoint_sensitive_cases":sum(r["endpoint_sweeps_change_center_winner"] for r in valid),
             "winner_frequencies_are_not_posterior_probabilities":True,"activation_authorized_by_results":False}
    summary["by_prior"]={prior:{"cases":sum(r["prior"]==prior for r in valid),
        "center_changes":sum(r["prior"]==prior and r["center_winner_differs_from_initial"] for r in valid),
        "endpoint_sensitive":sum(r["prior"]==prior and r["endpoint_sweeps_change_center_winner"] for r in valid)} for prior in design["priors"]}
    report={"design":design,"summary":summary,"results":rows,"synthetic_histories":histories,"passports":profiles}
    (output/"results.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    (output/"summary.json").write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")
    return summary


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--output",required=True,type=Path)
    print(json.dumps(run(p.parse_args().output)))
