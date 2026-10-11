"""Grid, particle-size/seed and prior-generative audits of rain dynamics."""
import os
os.environ['OPENBLAS_NUM_THREADS'] = '1'
os.environ['OMP_NUM_THREADS'] = '1'
import argparse, json
from pathlib import Path
import numpy as np
from scipy.stats import binomtest
from .model import probabilities, design_row
from .temporal_attribution import AttributionFilter, RAIN
from .attribution_reference import GridReference, null_context
from .attribution_experiment import stream


def evaluate(initial, events, q, rq, particles, seed):
    f = AttributionFilter(initial, diffusion=q, rain_diffusion=rq, particles=particles, seed=seed)
    ps = []
    for e in events:
        ps.append(f.forecast(e['day'], e['context']))
        f.observe(e['ordinal_category'])
    return ps, f.diagnostics()


def run(output):
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    design = {'grid_sizes':[241, 481], 'grid_days':24, 'grid_coefficients':[-5, 5],
              'grid_intercepts':[-12, 12], 'particles':[4096, 16384], 'seeds':[91, 93],
              'scenarios':['context_only', 'intercept_only', 'rain_only', 'both'],
              'targets':['comfort', 'affect'], 'mode':'walk', 'schedule':'correlated_weather',
              'dynamic_candidates':[[0., .08], [.15, .08]],
              'prior_generative_calibration_sequences':64, 'calibration_steps':24,
              'no_psychological_or_live_precision_thresholds':True}
    (output/'audit_design.json').write_text(json.dumps(design, indent=2)+'\n', encoding='utf-8')
    grid_rows = []
    for q, rq in ((.15, 0.), (0., .08), (.15, .08)):
        f = AttributionFilter(1/3, diffusion=q, rain_diffusion=rq, particles=16384, seed=12)
        refs = [GridReference(q=q, rain_q=rq, size=size) for size in (241, 481)]
        for day, rating in enumerate([5, 4, 6, 1, 2, 3, 5, 2]*3, 1):
            rain = float([0., .5, 2., 5.][day % 4])
            p = f.forecast(day, null_context(rain)); r, fine = [g.forecast(day, rain) for g in refs]
            grid_rows.append({'diffusion':q, 'rain_diffusion':rq, 'day':day,
                             'particle_vs_fine_grid_mean_difference':abs(p['normalized_mean']-fine['mean']),
                             'particle_vs_fine_grid_rain_coefficient_difference':abs(p['rain_coefficient_mean']-fine['b']),
                             'coarse_vs_fine_grid_mean_difference':abs(r['mean']-fine['mean']),
                             'max_boundary_mass':max(r['boundary_mass'], fine['boundary_mass'])})
            f.observe(rating)
            for g in refs: g.observe(rating)
    sensitivity = []
    for scenario in design['scenarios']:
        for target in design['targets']:
            initial, events = stream(scenario, 'correlated_weather', target, 'walk', 0)
            for q, rq in design['dynamic_candidates']:
                ps, diag = evaluate(initial, events, q, rq, 4096, 91)
                high, high_diag = evaluate(initial, events, q, rq, 16384, 93)
                def logscore(predictions):
                    return float(np.mean([np.log(p['probabilities'][e['ordinal_category']-1]) for p, e in zip(predictions[12:], events[12:])]))
                sensitivity.append({'scenario':scenario, 'target':target, 'diffusion':q, 'rain_diffusion':rq,
                    'max_mean_difference':max(abs(a['normalized_mean']-b['normalized_mean']) for a, b in zip(ps, high)),
                    'max_rain_coefficient_difference':max(abs(a['rain_coefficient_mean']-b['rain_coefficient_mean']) for a, b in zip(ps, high)),
                    'mean_log_score_difference':abs(logscore(ps)-logscore(high)),
                    'max_expectation_interval_endpoint_difference':max(float(max(abs(np.array(a['interval_90'])-b['interval_90']))) for a, b in zip(ps, high)),
                    'diagnostics':[diag, high_diag]})
            print(json.dumps({'audited_scenario':scenario, 'target':target}), flush=True)
    calibration = []
    for rep in range(64):
        rng = np.random.default_rng(20261014+rep)
        f = AttributionFilter(1/3, diffusion=.15, rain_diffusion=.08, particles=4096, seed=1000+rep)
        beta = rng.normal(size=len(f.beta_sd))*f.beta_sd
        a = float(rng.normal(f.center, f.sd)); delta = 0.
        _, events = stream('stable', 'correlated_weather', 'comfort', 'walk', rep, seed=20261014, steps=24)
        for e in events:
            a += rng.normal(0, .15); delta += rng.normal(0, .08)
            x = design_row(e['context'])[1:]
            p = probabilities(a+beta@x+delta*x[RAIN]); truth = float(p@((np.arange(7)-3)/3))
            forecast = f.forecast(e['day'], e['context'])
            f.observe(int(rng.choice(np.arange(1, 8), p=p)))
        item = {'sequence':rep, 'previous_observations':forecast['previous_observations']}
        for name, value, key in (('expectation', truth, 'interval_90'), ('intercept', a, 'intercept_interval_90'),
                                 ('rain_coefficient', beta[RAIN]+delta, 'rain_coefficient_interval_90')):
            lo, hi = forecast[key]
            item[name] = {'truth':float(value), 'interval_90':[lo, hi], 'covered':bool(lo <= value <= hi)}
        calibration.append(item)
    coverage = {}
    for name in ('expectation', 'intercept', 'rain_coefficient'):
        count = sum(r[name]['covered'] for r in calibration)
        ci = binomtest(count, 64).proportion_ci(confidence_level=.95)
        coverage[name] = {'covered':count, 'sequences':64, 'coverage':count/64, 'binomial_interval_95':[float(ci.low), float(ci.high)]}
    result = {'grid_rows':grid_rows, 'particle_sensitivity':sensitivity,
              'calibration_coverage':coverage, 'calibration_sequences':calibration,
              'max_particle_vs_grid_mean_difference':max(r['particle_vs_fine_grid_mean_difference'] for r in grid_rows),
              'max_grid_resolution_mean_difference':max(r['coarse_vs_fine_grid_mean_difference'] for r in grid_rows),
              'max_grid_boundary_mass':max(r['max_boundary_mass'] for r in grid_rows),
              'max_full_weather_mean_sensitivity':max(r['max_mean_difference'] for r in sensitivity),
              'max_full_weather_log_score_sensitivity':max(r['mean_log_score_difference'] for r in sensitivity),
              'live_activation':False}
    (output/'numerical_audit.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k:v for k, v in result.items() if k.startswith('max_') or k == 'calibration_coverage'}), flush=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--output', type=Path, required=True)
    run(p.parse_args().output)
