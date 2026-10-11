"""V2 numerical audit with independent static MCMC and particle/seed sensitivity."""
import os
os.environ['OPENBLAS_NUM_THREADS']='1'
os.environ['OMP_NUM_THREADS']='1'
import argparse, json
from pathlib import Path
import numpy as np
from .model import fit
from .weather_contract import INTENSITY
from .temporal_weather import WeatherFilter
from .temporal_intensity_experiment import stream


def evaluate(events, initial, *, particles, diffusion, seed, rain_sd=.35):
    f=WeatherFilter(initial, particles=particles, diffusion=diffusion, seed=seed,
                    weather_model_version=INTENSITY, rain_prior_sd=rain_sd)
    forecasts=[]
    for e in events:
        forecasts.append(f.forecast(e['day'], e['context']))
        f.observe(e['ordinal_category'])
    return f, forecasts


def run(output):
    output=Path(output); output.mkdir(parents=True, exist_ok=True)
    cases=[('stable_aligned','comfort',.35), ('rain_slope_change','affect',.35), ('hourly_reporting','comfort',.7)]
    design={'version':'d-day-temporal-intensity-numerical-audit-v2','cases':cases,'mode':'walk',
            'particle_sizes':[1024,4096,16384], 'seeds':[91,92,93], 'diffusions':[0,.15,.4],
            'steps':48,'main_experiment_seed':20261012,'mcmc_draws':4096,'mcmc_warmup':1024,
            'scope':'selected numerical stress cases; not exhaustive convergence or empirical validation'}
    (output/'numerical_design.json').write_text(json.dumps(design,indent=2)+'\n',encoding='utf-8')
    rows=[]
    for scenario,target,rain_sd in cases:
        initial,events=stream(scenario,target,'walk',0,20261012,48)
        obs=[{'event_id':e['event_id'],'rating':e['ordinal_category'],'context':e['context']} for e in events]
        reference=fit(obs,initial,contextual=True,weather_model_version=INTENSITY,
                      rain_prior_sd=rain_sd,draws=4096,warmup=1024,seed=91)
        posterior=reference.predict(events[-1]['context'])
        for q in (0.,.15,.4):
            high,hp=evaluate(events,initial,particles=16384,diffusion=q,seed=93,rain_sd=rain_sd)
            others=[evaluate(events,initial,particles=n,diffusion=q,seed=seed,rain_sd=rain_sd)
                    for n in (1024,4096) for seed in (91,92)]
            def score(ps):
                eligible=[np.log(max(p['probabilities'][e['ordinal_category']-1],1e-15))
                          for i,(p,e) in enumerate(zip(ps,events)) if i>=8 and e['context_audit']['contextual_prediction_allowed_for_research']]
                return float(np.mean(eligible))
            comparisons=[]
            for (f,ps),n,seed in zip(others,(1024,1024,4096,4096),(91,92,91,92)):
                delta=[abs(a['normalized_mean']-b['normalized_mean']) for a,b in zip(ps,hp)]
                endpoints=[float(np.max(abs(np.array(a['interval_90'])-b['interval_90']))) for a,b in zip(ps,hp)]
                comparisons.append({'particles':n,'seed':seed,'max_mean_difference':max(delta),
                                    'rms_mean_difference':float(np.sqrt(np.mean(np.square(delta)))),
                                    'max_interval_endpoint_difference':max(endpoints),
                                    'mean_log_score_difference':score(ps)-score(hp),'diagnostics':f.diagnostics()})
            row={'scenario':scenario,'target':target,'rain_prior_sd':rain_sd,'diffusion':q,
                 'reference_particle_diagnostics':high.diagnostics(),'comparisons':comparisons,
                 'reference_mean_log_score':score(hp)}
            if q==0:
                full=high.forecast(events[-1]['day'],events[-1]['context'])
                row.update(mcmc_diagnostics=reference.diagnostics,
                           particle_mcmc_mean_difference=abs(full['normalized_mean']-posterior['signed_belief_mean']),
                           particle_mcmc_probability_l1=float(np.abs(np.array(full['probabilities'])-posterior['category_probabilities']).sum()))
            rows.append(row)
        print(json.dumps({'numerical_case_complete':scenario,'target':target}),flush=True)
    result={'version':design['version'],'rows':rows,'all_mcmc_diagnostics_passed':all(r['mcmc_diagnostics']['passed'] for r in rows if 'mcmc_diagnostics' in r),
            'max_particle_mcmc_mean_difference':max(r.get('particle_mcmc_mean_difference',0) for r in rows),
            'max_1024_log_score_sensitivity':max(abs(c['mean_log_score_difference']) for r in rows for c in r['comparisons'] if c['particles']==1024),
            'max_4096_log_score_sensitivity':max(abs(c['mean_log_score_difference']) for r in rows for c in r['comparisons'] if c['particles']==4096),
            'live_activation':False,'scientific_validation_claimed':False}
    (output/'numerical_audit.json').write_text(json.dumps(result,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k!='rows'}),flush=True)
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True)
    run(parser.parse_args().output)
