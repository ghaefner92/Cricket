"""Independent grid sensitivity and prior-generative filtering calibration."""
import os
os.environ.setdefault('OPENBLAS_NUM_THREADS','1')
import argparse,json
from pathlib import Path
import numpy as np
from scipy.stats import binomtest
from .temporal_lab import GridFilter
from .temporal_experiment import stream,SCENARIOS
from .model import probabilities,initial_location

def run(output,replicates=64,seed=20261011):
    output=Path(output);rng=np.random.default_rng(seed);sensitivity=[];calibration=[]
    design={'seed':seed,'prior_generative_replicates_per_context_design':replicates,'observations':24,
        'diffusion':.15,'context_designs':['basic','rain_only'],'grid_sizes':[[241,41],[481,81]],
        'sensitivity_scenarios':SCENARIOS,'sensitivity_targets':['comfort','affect'],'initial':.3,
        'construct_validation_claimed':False,'target_codings_share_numerical_likelihood':True}
    (output/'calibration_design.json').write_text(json.dumps(design,indent=2)+'\n',encoding='utf-8')
    for scenario in SCENARIOS:
        for target in ('comfort','affect'):
            initial,events=stream(scenario,target,'walk',0,20261010)
            filters=[GridFilter(initial,contextual=True,diffusion=.15,grid_size=n,slope_size=s) for n,s in ((241,41),(481,81))]
            errors=[];interval_errors=[]
            for event in events:
                p=[f.forecast(event['day'],event['rain']) for f in filters]
                errors.append(abs(p[0]['normalized_mean']-p[1]['normalized_mean']))
                interval_errors.append(max(abs(a-b) for a,b in zip(p[0]['interval_90'],p[1]['interval_90'])))
                for f in filters:f.observe(event['ordinal_category'])
            sensitivity.append({'scenario':scenario,'target':target,'max_normalized_mean_difference':max(errors),
                'max_interval_endpoint_difference':max(interval_errors),'max_boundary_mass':max(f.max_boundary_mass for f in filters)})
    print(json.dumps({'grid_sensitivity_sequences':len(sensitivity)}),flush=True)
    for contextual in (False,True):
        rows=[]
        for rep in range(replicates):
            eta=float(rng.normal(initial_location(.3),1.2));slope=float(rng.normal(0,.35)) if contextual else 0.
            f=GridFilter(.3,contextual=contextual,diffusion=.15);day=0.
            covered=[]
            for step in range(24):
                dt=float(rng.choice([.5,1.,3.]));day+=dt;eta+=float(rng.normal(0,.15*np.sqrt(dt)))
                rain=float(rng.exponential(2.)) if rng.random()<.4 else 0.
                p=probabilities(eta+slope*np.log1p(rain));expected=float(p@((np.arange(7)-3)/3))
                forecast=f.forecast(day,rain);lo,hi=forecast['interval_90'];covered.append(bool(lo<=expected<=hi))
                f.observe(int(rng.choice(np.arange(1,8),p=p)))
            # Last forecast: one calibration outcome per independent sequence.
            rows.append({'replicate':rep,'last_expectation_covered':covered[-1],'all_steps_covered':covered,'boundary_mass':f.max_boundary_mass})
        count=sum(r['last_expectation_covered'] for r in rows);ci=binomtest(count,replicates).proportion_ci()
        calibration.append({'context_design':'rain_only' if contextual else 'basic','replicates':replicates,
            'last_forecast_latent_expectation_coverage_90':count/replicates,
            'binomial_interval_95':[ci.low,ci.high],'rows':rows})
    result={'design':design,'sensitivity':sensitivity,'prior_generative_calibration':calibration,
        'live_activation':False,'scientific_validation_claimed':False,
        'limitations':['64 sequences per design gives exploratory coverage estimates','Matches the candidate generative model, not user psychology','No drift-rate selection based on this calibration']}
    (output/'sensitivity.json').write_text(json.dumps(result,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    print(json.dumps({'grid_sensitivity_sequences':len(sensitivity),'calibration_sequences':2*replicates,
        'coverage':[r['last_forecast_latent_expectation_coverage_90'] for r in calibration]}),flush=True)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);args=p.parse_args();run(args.output)
