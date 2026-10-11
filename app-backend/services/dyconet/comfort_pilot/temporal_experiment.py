"""Frozen-design sequential synthetic evaluation of offline temporal candidates."""
import os
os.environ.setdefault('OPENBLAS_NUM_THREADS','1')
import argparse,json
from pathlib import Path
import numpy as np
from .temporal_lab import GridFilter,AveragedFilter,DIFFUSIONS,VERSION
from .model import MODES,probabilities,initial_location,fit

SCENARIOS=('stable_aligned','stable_wrong_prior','abrupt_change','gradual_change',
    'context_shift_only','transient_bad_trip','irregular_calendar','rain_slope_change')
MODE_EXPOSURE={'walk':1.,'bike':1.,'pt':.3,'car':.1}

def stream(scenario,target,mode,rep,seed,steps=72):
    index=SCENARIOS.index(scenario);rng=np.random.default_rng(seed+index*10000+rep*100+MODES.index(mode)*10+(target=='affect'))
    day=0.;initial=2/3 if scenario=='stable_wrong_prior' else 1/3
    out=[]
    for step in range(1,steps+1):
        day+=float(rng.choice([.5,1.,3.])) if scenario=='irregular_calendar' else 1.
        if scenario=='irregular_calendar' and step==37:day+=20.
        rain=float(rng.uniform(.5,5)) if rng.random() < (.1 if scenario=='context_shift_only' and step<=36 else .8 if scenario=='context_shift_only' else .4) else 0.
        intercept=.8;slope=-.7*MODE_EXPOSURE[mode]
        if scenario=='stable_wrong_prior':intercept=-1.5
        if scenario=='abrupt_change' and step>36:intercept=-1.5
        if scenario=='gradual_change':intercept=.8-2.3*np.clip((step-24)/36,0,1)
        if scenario=='transient_bad_trip' and 37<=step<=39:intercept=-2.5 if target=='affect' else -.3
        if scenario=='irregular_calendar':intercept=.8-2.3*np.clip((day-30)/60,0,1)
        if scenario=='rain_slope_change':slope=(-.15 if step<=36 else -1.2)*MODE_EXPOSURE[mode]
        # Affect has a distinct context response; never derive it from comfort.
        if target=='affect':slope*=1.3
        p=probabilities(intercept+slope*np.log1p(rain))
        rating=int(rng.choice(np.arange(1,8),p=p))
        out.append({'event_id':f'{target}-{scenario}-{mode}-{rep}-{step}','day':day,'rain':rain,
            'ordinal_category':rating,'raw_response':rating-4 if target=='affect' else rating,
            'normalized_response':(rating-4)/3,'truth_probabilities':p.tolist(),
            'truth_intercept':float(intercept),'truth_rain_slope':float(slope)})
    return initial,out

def metrics(forecast,event):
    p=np.array(forecast['probabilities']);truth=np.array(event['truth_probabilities']);y=event['ordinal_category']-1
    actual=np.arange(6)>=y;mean=float(truth@((np.arange(7)-3)/3));interval=forecast['interval_90']
    return {'log_score':float(np.log(max(p[y],1e-15))),
        'rps':float(np.mean((p.cumsum()[:6]-actual)**2)),
        'expected_response_squared_error':float((forecast['normalized_mean']-mean)**2),
        'latent_expectation_covered_90':None if interval is None else bool(interval[0]<=mean<=interval[1]),
        'interval_halfwidth':None if interval is None else float((interval[1]-interval[0])/2)}

def run(output,repetitions=3,seed=20261010):
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    design={'schema_version':'d-day-temporal-design-v1','model_version':VERSION,'seed':seed,'repetitions':repetitions,
        'scenarios':SCENARIOS,'targets':['comfort','affect'],'modes':MODES,'priors':['moderate','strong'],
        'steps':72,'warm_start_excluded_steps':12,'diffusions_latent_sd_per_sqrt_day':DIFFUSIONS,
        'grid_intercept':[-12,12,241],'grid_rain_slope':[-3,3,41],
        'models':['static','cumulative','dynamic_0.15','dynamic_0.4','averaged'],
        'context_designs':['basic','rain_only'],'prediction_before_each_observation':True,
        'all_events_retained':True,'live_activation':False,'parameter_choices_validated':False,
        'primary_scores':['log_score','rps'],'coverage_is_of_known_synthetic_latent_expectation':True,
        'scope':'matched rain-only candidates; not a replacement for the production full-weather model',
        'model_average':'equal discrete prior; past marginal predictive likelihood updates; not causal change detection'}
    design['initial_declarations']={'comfort_raw_1_to_7':[5,6],'affect_raw_minus3_to_3':[1,2],'normalized':[1/3,2/3]}
    (output/'design.json').write_text(json.dumps(design,indent=2)+'\n',encoding='utf-8')
    rows=[];streams=[];numerical=[]
    progress=0
    for scenario in SCENARIOS:
        for target in ('comfort','affect'):
            for mode in MODES:
                for rep in range(repetitions):
                    initial,events=stream(scenario,target,mode,rep,seed)
                    provenance='ridge_estimated' if target=='comfort' and mode in ('bike','car') else 'declared'
                    streams.append({'scenario':scenario,'target':target,'mode':mode,'replicate':rep,'initial':initial,
                        'initial_source':provenance,
                        'initial_raw':None if provenance=='ridge_estimated' else round(3*initial)+(4 if target=='comfort' else 0),'events':events})
                    for prior in ('moderate','strong'):
                        for contextual in (False,True):
                            kwargs=dict(contextual=contextual,prior_profile=prior,provenance=provenance)
                            models={'cumulative':GridFilter(initial,**kwargs),
                                'dynamic_0.15':GridFilter(initial,diffusion=.15,**kwargs),
                                'dynamic_0.4':GridFilter(initial,diffusion=.4,**kwargs),
                                'averaged':AveragedFilter(initial,**kwargs)}
                            # Fixed initial Passport baseline has no rain learning.
                            fixed=probabilities(initial_location(initial));coding=(np.arange(7)-3)/3
                            for i,event in enumerate(events):
                                forecasts={'static':{'probabilities':fixed.tolist(),'normalized_mean':float(fixed@coding),
                                    'interval_90':None,'boundary_mass':0.,'previous_observations':0}}
                                forecasts.update({name:f.forecast(event['day'],event['rain']) for name,f in models.items()})
                                if i>=12:
                                    for name,f in forecasts.items():
                                        rows.append({'scenario':scenario,'target':target,'mode':mode,'replicate':rep,'prior':prior,
                                            'context_design':'rain_only' if contextual else 'basic','model':name,'step':i+1,'day':event['day'],
                                            'phase':'before_change' if i<36 else 'after_change','forecast':f,**metrics(f,event)})
                                # Nobody sees this answer until all forecasts have been archived/scored.
                                for f in models.values():f.observe(event['ordinal_category'])
                            if rep==0 and mode=='walk' and target=='comfort' and prior=='moderate' and not contextual:
                                # Independent corrected-MCMC reference to the q=0 grid posterior.
                                obs=[{'event_id':e['event_id'],'rating':e['ordinal_category'],'context':{}} for e in events]
                                mcmc=fit(obs,initial,draws=4096,seed=seed)
                                gr=models['cumulative'].forecast(events[-1]['day'],0.)
                                diff=abs(gr['normalized_mean']-mcmc.predict({})['signed_belief_mean'])
                                numerical.append({'scenario':scenario,'basic_grid_vs_mcmc_absolute_difference':diff,'mcmc_diagnostics':mcmc.diagnostics})
                    progress+=1
        print(json.dumps({'completed_scenario':scenario,'streams':progress}),flush=True)
    with (output/'predictions.jsonl').open('w',encoding='utf-8') as f:
        for row in rows:f.write(json.dumps(row,allow_nan=False)+'\n')
    (output/'streams.json').write_text(json.dumps(streams,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    summaries=[]
    for scenario in SCENARIOS:
        for target in ('comfort','affect'):
            for context in ('basic','rain_only'):
                for prior in ('moderate','strong'):
                    for phase in ('before_change','after_change'):
                        for model in ('static','cumulative','dynamic_0.15','dynamic_0.4','averaged'):
                            group=[r for r in rows if r['scenario']==scenario and r['target']==target and r['context_design']==context and r['prior']==prior and r['phase']==phase and r['model']==model]
                            # Per-stream averages are the replication unit, not independent trips.
                            per_stream=[np.mean([r['log_score'] for r in group if r['mode']==mode and r['replicate']==rep]) for mode in MODES for rep in range(repetitions)]
                            summaries.append({'scenario':scenario,'target':target,'context_design':context,'prior':prior,'phase':phase,'model':model,
                                'events':len(group),'independent_streams':len(per_stream),'mean_log_score':float(np.mean(per_stream)),
                                'between_stream_log_score_sd':float(np.std(per_stream,ddof=1)),
                                'mean_rps':float(np.mean([r['rps'] for r in group])),
                                'expectation_rmse':float(np.sqrt(np.mean([r['expected_response_squared_error'] for r in group]))),
                                'latent_expectation_coverage_90':None if model=='static' else float(np.mean([r['latent_expectation_covered_90'] for r in group])),
                                'max_grid_boundary_mass':max(r['forecast']['boundary_mass'] for r in group)})
    (output/'summary.json').write_text(json.dumps({'groups':summaries,'numerical_reference':numerical,
        'streams':len(streams),'scored_predictions':len(rows),'live_activation':False,'scientific_validation_claimed':False},indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'complete':True,'streams':len(streams),'scored_predictions':len(rows)}),flush=True)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);p.add_argument('--repetitions',type=int,default=3);args=p.parse_args()
    if not 2<=args.repetitions<=20:p.error('Use 2–20 independent repetitions')
    run(args.output,args.repetitions)
