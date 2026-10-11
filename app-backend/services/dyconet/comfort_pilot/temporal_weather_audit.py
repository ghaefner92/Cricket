"""Independent static MCMC and particle-size/seed sensitivity for weather lab."""
import os
os.environ['OPENBLAS_NUM_THREADS'] = '1'
os.environ['OMP_NUM_THREADS'] = '1'
import argparse, json
from pathlib import Path
import numpy as np
from .model import fit
from .temporal_weather import WeatherFilter
from .temporal_weather_experiment import stream


def evaluate(events, initial, n, q, seed):
    f = WeatherFilter(initial, particles=n, diffusion=q, seed=seed)
    predictions = []
    for e in events:
        predictions.append(f.forecast(e['day'], e['context']))
        f.observe(e['ordinal_category'])
    return f, predictions


def run(output):
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    design = {'audit_version':1, 'scenarios':['stable_aligned', 'abrupt_change', 'context_shift_only', 'rain_slope_change'],
              'targets':['comfort', 'affect'], 'mode':'walk', 'diffusions':[0., .15],
              'particle_sizes':[4096, 16384], 'independent_seeds':[91, 92, 93],
              'mcmc_draws':4096, 'warmup':1024,
              'purpose':'numerical sensitivity, not psychological or live activation thresholds'}
    (output/'audit_design.json').write_text(json.dumps(design, indent=2)+'\n', encoding='utf-8')
    rows = []
    for scenario in design['scenarios']:
        for target in design['targets']:
            initial, events = stream(scenario, target, 'walk', 0, 20261011)
            observations = [{'event_id':e['event_id'], 'rating':e['ordinal_category'], 'context':e['context']} for e in events]
            reference = fit(observations, initial, contextual=True, draws=4096, warmup=1024, seed=91)
            ref = reference.predict(events[-1]['context'])
            for q in (0., .15):
                high, hp = evaluate(events, initial, 16384, q, 93)
                small = [evaluate(events, initial, 4096, q, seed) for seed in (91, 92)]
                diffs = [abs(a['normalized_mean']-b['normalized_mean']) for _, ps in small for a, b in zip(ps, hp)]
                endpoints = [max(abs(np.array(a['interval_90'])-np.array(b['interval_90']))) for _, ps in small for a, b in zip(ps, hp)]
                def log_score(ps):
                    return float(np.mean([np.log(p['probabilities'][e['ordinal_category']-1]) for p, e in zip(ps[12:], events[12:])]))
                row = {'scenario':scenario, 'target':target, 'diffusion':q,
                       'max_mean_difference_4096_vs_16384':float(max(diffs)),
                       'rms_mean_difference':float(np.sqrt(np.mean(np.square(diffs)))),
                       'max_interval_endpoint_difference':float(max(endpoints)),
                       'mean_log_score_16384':log_score(hp),
                       'mean_log_score_4096_seeds':[log_score(ps) for _, ps in small],
                       'max_mean_log_score_difference':max(abs(log_score(ps)-log_score(hp)) for _, ps in small),
                       'particle_diagnostics':[high.diagnostics(), *[f.diagnostics() for f, _ in small]]}
                if q == 0.:
                    # Posterior after all 72 responses, same context and no time advance.
                    posterior_prediction = high.forecast(events[-1]['day'], events[-1]['context'])
                    row.update(mcmc_diagnostics=reference.diagnostics,
                               full_static_particle_vs_mcmc_mean_difference=abs(posterior_prediction['normalized_mean']-ref['signed_belief_mean']),
                               full_static_particle_vs_mcmc_probability_l1=float(np.abs(np.array(posterior_prediction['probabilities'])-ref['category_probabilities']).sum()))
                    samples = reference.chains.reshape(-1, reference.chains.shape[-1])
                    row['static_mcmc_parameter_mean'] = samples.mean(axis=0).tolist()
                    row['static_mcmc_parameter_interval_90'] = np.quantile(samples, [.05, .95], axis=0).T.tolist()
                    row['final_generating_coefficients'] = events[-1]['truth_coefficients']
                rows.append(row)
            print(json.dumps({'audited_scenario':scenario, 'target':target}), flush=True)
    result = {'rows':rows, 'max_mean_sensitivity':max(r['max_mean_difference_4096_vs_16384'] for r in rows),
              'max_mean_difference_mcmc':max(r.get('full_static_particle_vs_mcmc_mean_difference', 0) for r in rows),
              'max_mean_log_score_sensitivity':max(r['max_mean_log_score_difference'] for r in rows),
              'all_mcmc_diagnostics_passed':all(r['mcmc_diagnostics']['passed'] for r in rows if 'mcmc_diagnostics' in r),
              'live_activation':False}
    (output/'numerical_audit.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k:v for k, v in result.items() if k != 'rows'}), flush=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--output', type=Path, required=True)
    run(p.parse_args().output)
