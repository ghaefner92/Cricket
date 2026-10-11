"""Prior-generative latent-coverage check; not construct validation."""
import os
os.environ['OPENBLAS_NUM_THREADS'] = '1'
import argparse, json
from pathlib import Path
import numpy as np
from scipy.stats import binomtest
from .model import probabilities, design_row
from .temporal_weather import WeatherFilter
from .temporal_weather_experiment import stream


def run(output):
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    design = {'sequences':64, 'steps':24, 'diffusion':.15, 'particles':4096,
              'initial_normalized':1/3, 'prior':'moderate', 'mode':'walk',
              'seed':20261012, 'coverage_unit':'final pre-response forecast per independent sequence',
              'truth_generated_from_candidate_prior':True,
              'not_psychological_validation':True}
    (output/'calibration_design.json').write_text(json.dumps(design, indent=2)+'\n', encoding='utf-8')
    rows = []
    for rep in range(64):
        rng = np.random.default_rng(design['seed']+rep)
        f = WeatherFilter(1/3, particles=4096, diffusion=.15, seed=rep+1000)
        _, contexts = stream('irregular_calendar' if rep % 2 else 'stable_aligned', 'comfort', 'walk', rep, 20261012, steps=24)
        beta = rng.normal(size=len(f.beta_sd))*f.beta_sd
        intercept = float(rng.normal(f.center, f.sd)); day = 0.
        for e in contexts:
            intercept += rng.normal(0, .15*np.sqrt(e['day']-day)); day = e['day']
            p = probabilities(intercept+beta@design_row(e['context'])[1:])
            truth = float(p@((np.arange(7)-3)/3))
            forecast = f.forecast(day, e['context'])
            rating = int(rng.choice(np.arange(1, 8), p=p))
            f.observe(rating)
        lo, hi = forecast['interval_90']
        rows.append({'sequence':rep, 'truth_normalized_expectation':truth,
                     'interval_90':forecast['interval_90'], 'covered':bool(lo <= truth <= hi),
                     'previous_observations':forecast['previous_observations']})
        if (rep+1) % 16 == 0: print(json.dumps({'calibrated_sequences':rep+1}), flush=True)
    successes = sum(r['covered'] for r in rows)
    ci = binomtest(successes, len(rows)).proportion_ci(confidence_level=.95)
    result = {'sequences':len(rows), 'covered':successes, 'coverage':successes/len(rows),
              'binomial_interval_95':[float(ci.low), float(ci.high)], 'rows':rows,
              'live_activation':False, 'scientific_validation_claimed':False}
    (output/'calibration.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k:v for k, v in result.items() if k != 'rows'}), flush=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--output', type=Path, required=True)
    run(p.parse_args().output)
