"""Full-weather offline temporal experiment; no live forecast changes."""
import os
os.environ['OPENBLAS_NUM_THREADS'] = '1'
os.environ['OMP_NUM_THREADS'] = '1'
import argparse
import json
from pathlib import Path
import numpy as np
from .model import MODES, WEATHER, FEATURES, design_row, probabilities, initial_location
from .temporal_lab import GridFilter
from .temporal_experiment import SCENARIOS, MODE_EXPOSURE, metrics
from .temporal_weather import WeatherFilter, average_forecast, VERSION


def stream(scenario, target, mode, rep, seed, steps=72):
    rng = np.random.default_rng(seed+SCENARIOS.index(scenario)*10000+rep*100+MODES.index(mode)*10+(target == 'affect'))
    initial = 2/3 if scenario == 'stable_wrong_prior' else 1/3
    events = []; day = 0.; exposure = MODE_EXPOSURE[mode]
    for step in range(1, steps+1):
        day += float(rng.choice([.5, 1., 3.])) if scenario == 'irregular_calendar' else 1.
        if scenario == 'irregular_calendar' and step == 37: day += 20
        storm_chance = (.1 if step <= 36 else .8) if scenario == 'context_shift_only' else .4
        storm = rng.random() < storm_chance
        rain = float(rng.uniform(.5, 5)) if storm else 0.
        wind = max(0., rng.normal(5 if storm else 2, 1.2))
        duration = {'walk':1500, 'bike':900, 'pt':1800, 'car':1100}[mode]*rng.uniform(.7, 1.5)
        context = {'duration_seconds': float(duration),
                   'temperature': float(17+7*np.sin(step/12)-5*storm+rng.normal(0, 2)),
                   'humidity': float(np.clip(55+25*storm+rng.normal(0, 7), 0, 100)),
                   'rain':rain, 'windSpeed':float(wind),
                   'windGust':float(wind+rng.uniform(0, 5)),
                   'windSpeed10Min':float(max(0, wind+rng.normal(0, .5))),
                   'lightIntensity':float(rng.uniform(100, 1500) if storm else rng.uniform(2000, 18000))}
        beta = np.zeros(len(FEATURES))
        coefficients = {'duration_seconds':-.12, 'temperature':.06*exposure,
                        'humidity':-.08*exposure, 'rain':-.7*exposure,
                        'windSpeed':-.10*exposure, 'windGust':-.06*exposure,
                        'windSpeed10Min':-.05*exposure, 'lightIntensity':.12*exposure,
                        'temperature_squared':-.18*exposure}
        if target == 'affect':
            coefficients.update(rain=-.91*exposure, lightIntensity=.25*exposure,
                                temperature_squared=-.06*exposure, duration_seconds=-.06)
        if scenario == 'rain_slope_change':
            coefficients['rain'] = (-.15 if step <= 36 else -1.2)*exposure*(1.3 if target == 'affect' else 1.)
        for key, value in coefficients.items(): beta[FEATURES.index(key)] = value
        intercept = .8
        if scenario == 'stable_wrong_prior': intercept = -1.5
        if scenario == 'abrupt_change' and step > 36: intercept = -1.5
        if scenario == 'gradual_change': intercept = .8-2.3*np.clip((step-24)/36, 0, 1)
        if scenario == 'transient_bad_trip' and 37 <= step <= 39: intercept = -2.5 if target == 'affect' else -.3
        if scenario == 'irregular_calendar': intercept = .8-2.3*np.clip((day-30)/60, 0, 1)
        p = probabilities(intercept+beta@design_row(context)[1:])
        rating = int(rng.choice(np.arange(1, 8), p=p))
        events.append({'event_id': f'weather-{target}-{scenario}-{mode}-{rep}-{step}',
                       'day':day, 'context':context, 'ordinal_category':rating,
                       'raw_response':rating-4 if target == 'affect' else rating,
                       'normalized_response':(rating-4)/3, 'truth_probabilities':p.tolist(),
                       'truth_intercept':float(intercept), 'truth_coefficients':beta.tolist()})
    return initial, events


def run(output, repetitions=2, particles=2048, seed=20261011):
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    names = ('static', 'basic_cumulative', 'rain_cumulative', 'weather_cumulative',
             'weather_dynamic_0.15', 'weather_dynamic_0.4', 'weather_averaged')
    design = {'version': VERSION, 'seed':seed, 'repetitions':repetitions, 'particles':particles,
              'moves_after_resampling':4, 'scenarios':SCENARIOS, 'targets':['comfort', 'affect'],
              'modes':MODES, 'steps':72, 'score_from_step':13, 'prior':'moderate',
              'weather':WEATHER, 'features':FEATURES, 'duration_included':True, 'uv_included':False,
              'missing_measurements_in_main_simulation':False, 'models':names,
              'diffusions':[0., .15, .4], 'only_intercept_dynamic':True,
              'all_history_retained':True, 'live_activation':False,
              'simulation_coefficients_empirically_validated':False,
              'replication_unit':'independent stream, not individual trip',
              'model_average':'equal prior; update with past conditional predictive evidence',
              'limitations':['finite-particle approximation', 'static correlated weather coefficients',
                             'moderate prior only', 'synthetic balanced mode usage; no human choice']}
    (output/'design.json').write_text(json.dumps(design, indent=2)+'\n', encoding='utf-8')
    streams = []; aggregates = {}; diagnostics = []; total = 0
    with (output/'predictions.jsonl').open('w', encoding='utf-8') as archive:
        for scenario in SCENARIOS:
            for target in ('comfort', 'affect'):
                for mode in MODES:
                    for rep in range(repetitions):
                        initial, events = stream(scenario, target, mode, rep, seed)
                        provenance = 'ridge_estimated' if target == 'comfort' and mode in ('bike', 'car') else 'declared'
                        stream_id = f'{scenario}-{target}-{mode}-{rep}'
                        streams.append({'id':stream_id, 'scenario':scenario, 'target':target, 'mode':mode,
                                        'replicate':rep, 'initial':initial, 'initial_source':provenance,
                                        'initial_raw':None if provenance == 'ridge_estimated' else round(3*initial)+(4 if target == 'comfort' else 0),
                                        'events':events})
                        filters = [WeatherFilter(initial, diffusion=q, particles=particles,
                                   seed=seed+len(streams)*100+j, provenance=provenance) for j, q in enumerate((0., .15, .4))]
                        controls = [GridFilter(initial, contextual=c, provenance=provenance) for c in (False, True)]
                        weights = np.ones(3)/3; fixed = probabilities(initial_location(initial))
                        for i, event in enumerate(events):
                            fs = [f.forecast(event['day'], event['context']) for f in filters]
                            forecasts = {'static':{'probabilities':fixed.tolist(), 'normalized_mean':initial,
                                         'interval_90':None, 'previous_observations':0},
                                         'basic_cumulative':controls[0].forecast(event['day'], 0.),
                                         'rain_cumulative':controls[1].forecast(event['day'], event['context']['rain']),
                                         **dict(zip(names[3:6], fs)),
                                         'weather_averaged':average_forecast(filters, fs, weights)}
                            if i >= 12:
                                for name, forecast in forecasts.items():
                                    scores = metrics(forecast, event)
                                    row = {'stream':stream_id, 'scenario':scenario, 'target':target, 'mode':mode,
                                           'replicate':rep, 'model':name, 'step':i+1, 'day':event['day'],
                                           'phase':'before_change' if i < 36 else 'after_change',
                                           'forecast':forecast, **scores}
                                    archive.write(json.dumps(row, allow_nan=False)+'\n'); total += 1
                                    key = (scenario, target, row['phase'], name, stream_id)
                                    aggregates.setdefault(key, []).append(scores)
                            # All candidates are scored before receiving this response.
                            evidence = np.array([f.observe(event['ordinal_category']) for f in filters])
                            weights *= evidence; weights /= weights.sum()
                            for f in controls: f.observe(event['ordinal_category'])
                        diagnostics.append({'stream':stream_id, 'filters':[f.diagnostics() for f in filters]})
            print(json.dumps({'completed_scenario':scenario, 'streams':len(streams)}), flush=True)
    groups = []
    for scenario in SCENARIOS:
        for target in ('comfort', 'affect'):
            for phase in ('before_change', 'after_change'):
                for name in names:
                    sequence_rows = [rows for key, rows in aggregates.items() if key[:4] == (scenario, target, phase, name)]
                    log_scores = [np.mean([r['log_score'] for r in rows]) for rows in sequence_rows]
                    flat = [r for rows in sequence_rows for r in rows]
                    groups.append({'scenario':scenario, 'target':target, 'phase':phase, 'model':name,
                                   'independent_streams':len(sequence_rows), 'mean_log_score':float(np.mean(log_scores)),
                                   'between_stream_log_score_sd':float(np.std(log_scores, ddof=1)),
                                   'mean_rps':float(np.mean([r['rps'] for r in flat])),
                                   'expectation_rmse':float(np.sqrt(np.mean([r['expected_response_squared_error'] for r in flat]))),
                                   'coverage_90':None if name == 'static' else float(np.mean([r['latent_expectation_covered_90'] for r in flat]))})
    (output/'streams.json').write_text(json.dumps(streams, indent=2)+'\n', encoding='utf-8')
    (output/'summary.json').write_text(json.dumps({'streams':len(streams), 'scored_predictions':total,
          'groups':groups, 'particle_diagnostics':diagnostics, 'live_activation':False}, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'complete':True, 'streams':len(streams), 'predictions':total}), flush=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--repetitions', type=int, default=2)
    p.add_argument('--particles', type=int, default=2048)
    args = p.parse_args()
    if not 2 <= args.repetitions <= 20 or not 512 <= args.particles <= 32768: p.error('Unsupported run size')
    run(args.output, args.repetitions, args.particles)
