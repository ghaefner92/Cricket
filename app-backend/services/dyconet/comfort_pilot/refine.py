"""Computational refinement only; unchanged priors, evidence and future test sets."""
import os
os.environ.setdefault('OPENBLAS_NUM_THREADS','1')
import argparse,json
from pathlib import Path
import numpy as np
from .model import fit, MODES, diagnostics
from .experiment import write_results, _metrics, PROFILES

def run(output):
    output=Path(output);report=json.loads((output/'results.json').read_text(encoding='utf-8'))
    plan=json.loads((output/'design.json').read_text(encoding='utf-8'))
    originals=output/'initial_sampling_results.json'
    if not originals.exists():originals.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    passports=json.loads((output/'passports.json').read_text(encoding='utf-8'))
    generation=json.loads((output/'synthetic_evidence.json').read_text(encoding='utf-8'))
    streams={(r['profile'],r['repeat'],r['mode']):r['events'] for r in generation}
    names=[p[0] for p in PROFILES];count=0
    for r in report['fits']:
        index=names.index(r['profile']);mode=r['mode'];stream=streams[(r['profile'],r['repeat'],mode)]
        initial=passports[index]['profile']['beliefs'][mode]['comfort']
        seed=plan['seed']+index*100000+r['repeat']*1000+MODES.index(mode)*100+r['size']
        # Recheck initial chain diagnostics including tail ESS. Reproducible same seed.
        posterior=fit(stream[:r['size']],initial,contextual=r['model']=='contextual',prior_profile=r['prior'],
                      provenance=r['provenance'],seed=seed,draws=plan['draws_per_chain'])
        r['sampling_attempts']=[{'draws':plan['draws_per_chain'],'diagnostics':posterior.diagnostics}]
        if not posterior.diagnostics['passed']:
            posterior=fit(stream[:r['size']],initial,contextual=r['model']=='contextual',prior_profile=r['prior'],
                          provenance=r['provenance'],seed=seed,draws=4096)
            r['sampling_attempts'].append({'draws':4096,'diagnostics':posterior.diagnostics});count+=1
        future=stream[40:];pred=[posterior.predict(o['context']) for o in future]
        truep=[o['true_probabilities'] for o in future];signed=np.array(truep)@((np.arange(1,8)-4)/3)
        intervals=np.array([p['signed_belief_interval_90'] for p in pred]);half=(intervals[:,1]-intervals[:,0])/2
        r.update(diagnostics=posterior.diagnostics,scores=_metrics([p['category_probabilities'] for p in pred],truep,[o['rating'] for o in future]),
                 signed_expectation_coverage_90=float(((signed>=intervals[:,0])&(signed<=intervals[:,1])).mean()),
                 interval_mean_halfwidth=float(half.mean()),precision_fractions={str(t):float((half<=t).mean()) for t in (.1,.2,.3)})
    plan['computational_refinement']={'reason':'Initial 512-draw chains failed diagnostics in some contextual fits; rerun for convergence, no predictive model selection',
        'retry_max_draws':4096,'original_results_preserved':'initial_sampling_results.json','tail_ess_required':100,'rhat_limit':1.01,'retry_fits':count}
    (output/'design.json').write_text(json.dumps(plan,indent=2)+'\n',encoding='utf-8');write_results(output,plan,report['fits'])
    print(json.dumps({'refined':count,'remaining_failed':sum(not r['diagnostics']['passed'] for r in report['fits'])}),flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True,type=Path)
    run(parser.parse_args().output)
