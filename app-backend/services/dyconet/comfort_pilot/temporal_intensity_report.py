"""Sequence-paired report for the isolated V2 temporal experiment."""
import argparse
from collections import defaultdict
from hashlib import sha256
import json
from pathlib import Path
import numpy as np


def paired_rows(rows, left, right, *, left_prior='reference', right_prior='reference'):
    """Only identical sequence/step pairs contribute, including weather dropout."""
    a = {(r['stream'], r['step']): r for r in rows if r['model']==left and r['prior']==left_prior}
    b = {(r['stream'], r['step']): r for r in rows if r['model']==right and r['prior']==right_prior}
    return [(a[k], b[k]) for k in sorted(a.keys() & b.keys())]


def contrast(rows, left, right, *, left_prior='reference', right_prior='reference'):
    pairs=paired_rows(rows,left,right,left_prior=left_prior,right_prior=right_prior)
    groups=defaultdict(list)
    for a,b in pairs: groups[(a['target'],a['scenario'],a['stream'])].append((a,b))
    aggregates=defaultdict(list)
    for (target,scenario,sid), ps in groups.items():
        aggregates[(target,scenario)].append({'stream':sid,'n':len(ps),
            'delta_log_score':float(np.mean([a['log_score']-b['log_score'] for a,b in ps])),
            'delta_rps':float(np.mean([a['rps']-b['rps'] for a,b in ps])),
            'delta_expectation_mse':float(np.mean([a['expected_response_squared_error']-b['expected_response_squared_error'] for a,b in ps]))})
    result=[]
    for (target,scenario), seqs in sorted(aggregates.items()):
        values=np.array([s['delta_log_score'] for s in seqs])
        result.append({'target':target,'scenario':scenario,'left':left,'right':right,
            'left_prior':left_prior,'right_prior':right_prior,'sequences':len(seqs),
            'common_forecasts':sum(s['n'] for s in seqs),
            'mean_delta_log_score':float(values.mean()),
            'sd_across_sequences':float(values.std(ddof=1)) if len(values)>1 else None,
            'min_sequence_delta':float(values.min()),'max_sequence_delta':float(values.max()),
            'mean_delta_rps':float(np.mean([s['delta_rps'] for s in seqs])),
            'mean_delta_expectation_mse':float(np.mean([s['delta_expectation_mse'] for s in seqs])),
            'sequence_deltas':seqs})
    return result


def run(output):
    output=Path(output)
    design=json.loads((output/'design.json').read_text(encoding='utf-8'))
    streams=json.loads((output/'streams.json').read_text(encoding='utf-8'))
    rows=[json.loads(line) for line in (output/'predictions.jsonl').read_text(encoding='utf-8').splitlines()]
    audit=json.loads((output/'numerical_audit.json').read_text(encoding='utf-8'))
    events={(s['id'],i+1):e for s in streams for i,e in enumerate(s['events'])}
    keys=set(); errors=[]
    for r in rows:
        k=(r['stream'],r['step'],r['prior'],r['model'])
        if k in keys: errors.append('duplicate forecast')
        keys.add(k); e=events[(r['stream'],r['step'])]; f=r['forecast']
        p=np.array(f['probabilities'])
        if len(p)!=7 or not np.all(np.isfinite(p)) or np.any(p<0) or abs(p.sum()-1)>1e-8: errors.append('invalid probabilities')
        if r['model']!='static' and f['previous_observations']!=r['step']-1: errors.append('response leakage/history mismatch')
        if 'candidate_weights_before_response' in f:
            w=np.array(f['candidate_weights_before_response'])
            if len(w)!=3 or np.any(w<0) or not np.all(np.isfinite(w)) or abs(w.sum()-1)>1e-8: errors.append('invalid candidate weights')
        if r['model'].startswith('weather_') and not e['context_audit']['contextual_prediction_allowed_for_research']: errors.append('ineligible weather score')
        if r['rain_intensity_used']!=e['context_audit']['rain_intensity_used']: errors.append('rain mismatch')
    expected=sum(1 for s in streams for i,e in enumerate(s['events']) if i>=8
                 for _ in design['prior_configurations'] for m in design['models']
                 if not m.startswith('weather_') or e['context_audit']['contextual_prediction_allowed_for_research'])
    if len(rows)!=expected: errors.append('missing predictions')
    for e in events.values():
        if not e['context_audit']['rain_intensity_used'] and e['context']['rainIntensity'] is not None: errors.append('unusable rain treated as observed')
    if errors: raise ValueError(sorted(set(errors)))
    series=defaultdict(list)
    for r in rows: series[(r['target'],r['scenario'],r['prior'],r['model'],r['stream'])].append(r)
    summaries=defaultdict(list)
    for (target,scenario,prior,model,sid), values in series.items():
        values.sort(key=lambda r:r['step'])
        jumps=[abs(b['forecast']['normalized_mean']-a['forecast']['normalized_mean'])
               for a,b in zip(values,values[1:]) if b['step']==a['step']+1]
        coverage=[r['latent_expectation_covered_90'] for r in values if r['latent_expectation_covered_90'] is not None]
        widths=[r['interval_halfwidth'] for r in values if r['interval_halfwidth'] is not None]
        summaries[(target,scenario,prior,model)].append({
            'coverage':None if not coverage else float(np.mean(coverage)),
            'halfwidth':None if not widths else float(np.mean(widths)),
            'absolute_expectation_change':None if not jumps else float(np.mean(jumps))})
    descriptive=[]
    for (target,scenario,prior,model), ss in sorted(summaries.items()):
        record={'target':target,'scenario':scenario,'prior':prior,'model':model,'sequences':len(ss)}
        for key in ('coverage','halfwidth','absolute_expectation_change'):
            available=[s[key] for s in ss if s[key] is not None]
            record[key]=None if not available else float(np.mean(available))
        descriptive.append(record)
    comparisons=[]
    for left,right in [('weather_dynamic_0.15','weather_cumulative'),('weather_dynamic_0.4','weather_cumulative'),
                       ('weather_averaged','weather_cumulative'),('weather_cumulative','basic_cumulative'),
                       ('basic_averaged','basic_cumulative'),('basic_cumulative','static')]:
        comparisons.extend(contrast(rows,left,right))
    for prior in ('initial_weak','initial_strong','rain_weak','rain_strong'):
        for model in ('weather_cumulative','weather_averaged'):
            comparisons.extend(contrast(rows,model,model,left_prior=prior))
    policy={'version':'d-day-temporal-intensity-use-policy-candidate-v2',
        'scope':'offline shadow candidates; not app activation policy',
        'reference':{'initial_prior':'moderate','rain_coefficient_sd':.35,'diffusion':0},
        'diffusion_candidates_latent_sd_per_sqrt_day':[0,.15,.4],
        'averaging':'predictive likelihood updates after response; weights frozen for prediction',
        'history':'retain all original events; no forgetting or deletion',
        'insufficient_weather':'basic fallback; no contextual research score',
        'unknown_or_stale_rain':'missing, never dry or current by query time',
        'model_selection':'no universal winner or automatic activation from synthetic results',
        'near_numerical_differences':'differences within selected-case sensitivity are inconclusive; not a universal error bound',
        'changing_rain_relationship':'intercept diffusion cannot identify it; requires separate model and evaluation',
        'comfort_affect':'separate priors, evidence and versions',
        'live_activation':False}
    result={'version':'d-day-temporal-intensity-paired-report-v2','sequences':len(streams),
        'synthetic_events':len(events),'scored_predictions':len(rows),'integrity_checks_passed':True,
        'comparison_unit':'equal-weight independent sequence; paired common steps; modes pooled descriptively',
        'uncertainty':'sequence dispersion descriptive; only two replicates per mode; no scientific confidence claim',
        'numerical_audit':{k:v for k,v in audit.items() if k!='rows'},'comparisons':comparisons,
        'uncertainty_and_stability':descriptive,
        'stability_scope':'absolute changes between consecutive eligible forecasts; includes context changes; not recommendation accuracy or personal change attribution',
        'policy':policy,'live_activation':False,'scientific_validation_claimed':False}
    (output/'paired_report.json').write_text(json.dumps(result,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    (output/'temporal_use_policy.json').write_text(json.dumps(policy,indent=2)+'\n',encoding='utf-8')
    text=['# Comparación temporal V2 — datos sintéticos','',
          'Diferencia de log score frente al clima acumulativo, prior de referencia. Positivo favorece al candidato.',
          'Cada celda promedia ocho secuencias independientes; mismas experiencias en cada contraste. No es validación empírica.','',
          '| Objetivo / escenario | q=0.15 | q=0.4 | Promedio |','|---|---:|---:|---:|']
    for target in ('comfort','affect'):
        for scenario in design['scenarios']:
            cs=[next(c for c in comparisons if c['target']==target and c['scenario']==scenario and c['left']==m and c['right']=='weather_cumulative')
                for m in ('weather_dynamic_0.15','weather_dynamic_0.4','weather_averaged')]
            text.append('| '+target+' / '+scenario+' | '+' | '.join(f"{c['mean_delta_log_score']:+.4f}" for c in cs)+' |')
    text.extend(['','La sensibilidad numérica máxima seleccionada con 1.024 partículas es '+f"{audit['max_1024_log_score_sensitivity']:.4f}"+
                 ' en log score. No constituye una cota global. Métricas y contrastes de priors completos: paired_report.json.',''])
    (output/'report.md').write_text('\n'.join(text),encoding='utf-8')
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig,axes=plt.subplots(2,1,figsize=(12,8),sharex=True,layout='constrained')
    for ax,target in zip(axes,('comfort','affect')):
        for model,label in [('weather_dynamic_0.15','q=0.15'),('weather_dynamic_0.4','q=0.4'),('weather_averaged','Promedio')]:
            values=[next(c['mean_delta_log_score'] for c in comparisons if c['target']==target and c['scenario']==s and c['left']==model and c['right']=='weather_cumulative') for s in design['scenarios']]
            ax.plot(range(len(values)),values,marker='o',label=label)
        ax.axhline(0,color='black',linewidth=.7)
        sensitivity=audit['max_1024_log_score_sensitivity']
        ax.axhspan(-sensitivity,sensitivity,color='gray',alpha=.15,label='Sensibilidad numérica seleccionada')
        ax.set_ylabel(('Comodidad' if target=='comfort' else 'Afecto')+'\nDelta log score');ax.legend(fontsize=8)
    axes[-1].set_xticks(range(len(design['scenarios'])),design['scenarios'],rotation=30,ha='right',fontsize=8)
    fig.suptitle('V2: comparación emparejada por secuencia sintética frente a clima acumulativo\nOcho secuencias por escenario y objetivo; resultados descriptivos')
    fig.savefig(output/'comparison.png',dpi=160);plt.close(fig)
    hashes={}
    for name in ('design.json','streams.json','predictions.jsonl','summary.json','numerical_design.json',
                 'numerical_audit.json','paired_report.json','temporal_use_policy.json','report.md','comparison.png'):
        digest=sha256()
        with (output/name).open('rb') as handle:
            for block in iter(lambda:handle.read(1024*1024),b''):digest.update(block)
        hashes[name]=digest.hexdigest()
    test_path=output/'tests.json'
    verification={'version':'d-day-temporal-intensity-verification-v2',
        'tests':json.loads(test_path.read_text(encoding='utf-8')) if test_path.exists() else None,
        'integrity_checks_passed':True,'scored_predictions':len(rows),'sequences':len(streams),
        'selected_mcmc_diagnostics_passed':audit['all_mcmc_diagnostics_passed'],
        'artifact_sha256':hashes,'live_activation':False,'empirical_validation':False}
    (output/'verification.json').write_text(json.dumps(verification,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('comparisons','policy','numerical_audit','uncertainty_and_stability')}))
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True)
    run(parser.parse_args().output)
