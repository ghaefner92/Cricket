"""Inference calibration and declared prior/cutpoint sensitivity, not construct validation."""
import os
os.environ.setdefault('OPENBLAS_NUM_THREADS','1')
import argparse
import json
from pathlib import Path
import numpy as np
from scipy.stats import kstest, binomtest
from .model import fit, probabilities, design_row, FEATURES
from .experiment import passport_fixture, weather, truth

def run(output,replicates=64,seed=20261010):
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    design={'schema_version':'d-day-comfort-calibration-v1','seed':seed,'replicates_per_model':replicates,
            'draws':1024,'chains':4,'observations':20,'rank_draws':128,
            'rank_thinning_stride':32,'prior':'moderate','initial_signed':0,
            'cutpoint_scales':[.75,1.,1.5],'literature_shape_candidates':[False,True],
            'inference_check_not_psychological_validation':True}
    (output/'calibration_design.json').write_text(json.dumps(design,indent=2)+'\n',encoding='utf-8')
    rng=np.random.default_rng(seed);rows=[];prior_checks=[];sensitivity=[]
    for contextual in (False,True):
        template=fit([],0,contextual=contextual,draws=1024)
        mu=np.array(template.prior['mean']);sd=np.array(template.prior['sd'])
        for rep in range(replicates):
            theta=rng.normal(mu,sd);contexts=[weather(rng) for _ in range(20)]
            x=np.array([design_row(c) if contextual else np.ones(1) for c in contexts])
            obs=[{'event_id':str(i),'context':c,'rating':int(rng.choice(np.arange(1,8),p=probabilities(x[i]@theta)))} for i,c in enumerate(contexts)]
            posterior=fit(obs,0,contextual=contextual,draws=1024,seed=seed+rep)
            # Widely spaced samples reduce MCMC correlation in rank checks.
            draws=posterior.chains[:,::32,:].reshape(-1,len(mu))
            intervals=np.quantile(posterior.chains,[.05,.95],axis=(0,1))
            replicated=[]
            for parameter in draws:
                replicated.append([int(rng.choice(np.arange(1,8),p=probabilities(row@parameter))) for row in x])
            replicated=np.array(replicated);actual=np.array([o['rating'] for o in obs])
            ppc={}
            for name,observed,simulated in (('mean_coded_response',float(((actual-4)/3).mean()),((replicated-4)/3).mean(axis=1)),
                                          ('extreme_fraction',float(((actual==1)|(actual==7)).mean()),((replicated==1)|(replicated==7)).mean(axis=1))):
                ppc[name]={'observed':observed,'replicated_interval_90':np.quantile(simulated,[.05,.95]).tolist(),
                           'replicated_exceedance_probability':float((simulated>=observed).mean())}
            rows.append({'model':'contextual' if contextual else 'basic','replicate':rep,
                         'ranks':(draws<theta).sum(axis=0).tolist(),
                         'parameter_covered_90':((intervals[0]<=theta)&(theta<=intervals[1])).tolist(),
                         'diagnostics':posterior.diagnostics,'posterior_predictive_checks':ppc})
    for index in range(8):
        passport=passport_fixture(index)
        for mode in ('walk','bike','pt','car'):
            initial=passport['profile']['beliefs'][mode]['comfort'];source='declared' if mode in ('walk','pt') else 'ridge_estimated'
            for strength in ('weak','moderate','strong'):
                posterior=fit([],initial,contextual=False,provenance=source,prior_profile=strength,draws=1024)
                prediction=posterior.predict({})
                prior_checks.append({'profile':index,'mode':mode,'prior':strength,'initial':initial,
                    'prior_predictive':prediction,'extreme_category_probability':prediction['category_probabilities'][0]+prediction['category_probabilities'][-1],
                    'mean_coding_drift_from_declared':prediction['signed_belief_mean']-initial})
            contexts=[weather(rng) for _ in range(20)]
            obs=[{'event_id':str(i),'context':c,'rating':int(rng.choice(np.arange(1,8),p=truth(index,mode,c,i+1,initial)))} for i,c in enumerate(contexts)]
            for scale in (.75,1.,1.5):
                for shape in (False,True):
                    posterior=fit(obs,initial,contextual=True,provenance=source,cutpoint_scale=scale,literature_shape=shape,draws=1024,seed=seed+index)
                    sensitivity.append({'profile':index,'mode':mode,'cutpoint_scale':scale,'literature_shape':shape,
                                        'diagnostics':posterior.diagnostics,'prediction':posterior.predict(contexts[-1])})
    aggregate=[]
    for model in ('basic','contextual'):
        group=[r for r in rows if r['model']==model];dimension=len(group[0]['ranks'])
        parameters=[]
        for j in range(dimension):
            ranks=np.array([r['ranks'][j] for r in group]);covered=sum(r['parameter_covered_90'][j] for r in group)
            ci=binomtest(covered,len(group)).proportion_ci()
            # Randomized ranks handle discrete ranks; diagnostic only, no significance-based gate.
            uniform=(ranks+rng.random(len(ranks)))/129
            parameters.append({'parameter':j,'coverage_90':covered/len(group),'coverage_binomial_interval_95':[ci.low,ci.high],
                               'rank_histogram':np.histogram(ranks,bins=np.linspace(0,129,11))[0].tolist(),
                               'rank_ks_pvalue_exploratory':float(kstest(uniform,'uniform').pvalue)})
        aggregate.append({'model':model,'diagnostics_pass_fraction':sum(r['diagnostics']['passed'] for r in group)/len(group),'parameters':parameters})
    result={'design':design,'sbc':rows,'sbc_summary':aggregate,'prior_predictive':prior_checks,
            'sensitivity':sensitivity,'active_passport_modified':False,'scientific_validation_claimed':False,
            'limitations':['64 replicates per model is exploratory, not a guarantee','Rank tests rely on thinned MCMC draws','Fixed cutpoints and reference-temperature prior location are assumptions','Literature-shaped numeric coefficients are candidate choices, not published estimates']}
    (output/'calibration.json').write_text(json.dumps(result,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    print(json.dumps({'status':'complete','sbc_fits':len(rows),'sensitivity_fits':len(sensitivity),'prior_predictive_checks':len(prior_checks),'diagnostics_pass':[r['diagnostics_pass_fraction'] for r in aggregate]}),flush=True)
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--replicates',type=int,default=64);args=parser.parse_args()
    if not 8<=args.replicates<=256:parser.error('replicates must be 8–256')
    run(args.output,args.replicates)
