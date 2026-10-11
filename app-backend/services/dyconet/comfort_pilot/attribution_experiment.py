"""Controlled climate/time attribution study. Frozen design, no live changes."""
import os
os.environ['OPENBLAS_NUM_THREADS'] = '1'
os.environ['OMP_NUM_THREADS'] = '1'
import argparse, json
from pathlib import Path
import numpy as np
from .model import FEATURES, WEATHER, MODES, design_row, probabilities
from .temporal_experiment import metrics, MODE_EXPOSURE
from .temporal_attribution import AttributionFilter, VERSION, RAIN

SCENARIOS = ('stable', 'context_only', 'intercept_only', 'rain_only', 'both', 'transient')
SCHEDULES = ('independent_weather', 'correlated_weather')
CANDIDATES = {'cumulative':(0., 0.), 'intercept_dynamic':(.15, 0.),
              'rain_dynamic':(0., .08), 'both_dynamic':(.15, .08)}


def stream(scenario, schedule, target, mode, rep, seed=20261013, steps=72):
    # Shared climate innovations across targets/scenarios; independent response
    # RNG. This is a paired design, NOT 192 independent profile replications.
    context_rng = np.random.default_rng(seed+SCHEDULES.index(schedule)*10000+MODES.index(mode)*100+rep)
    response_rng = np.random.default_rng(seed+50000+SCENARIOS.index(scenario)*10000+SCHEDULES.index(schedule)*1000+MODES.index(mode)*100+rep*10+(target == 'affect'))
    events = []; exposure = MODE_EXPOSURE[mode]
    for step in range(1, steps+1):
        after = step > steps//2
        chance = (.2 if not after else .8) if scenario == 'context_only' else .5
        wet = context_rng.random() < chance
        rain_draw = context_rng.uniform(.2, 5)
        rain = float(rain_draw if wet else 0.)
        # No smooth temperature trend: weather innovations do not encode time.
        linked = wet if schedule == 'correlated_weather' else False
        wind = max(0., context_rng.normal(5 if linked else 3, 1.3))
        context = {'duration_seconds':float({'walk':1500, 'bike':900, 'pt':1800, 'car':1100}[mode]*context_rng.uniform(.7, 1.5)),
                   'temperature':float(context_rng.uniform(2, 32)-5*linked),
                   'humidity':float(np.clip(context_rng.normal(80 if linked else 55, 12), 0, 100)),
                   'rain':rain, 'windSpeed':float(wind),
                   'windGust':float(wind+context_rng.uniform(0, 4)),
                   'windSpeed10Min':float(max(0., wind+context_rng.normal(0, .5))),
                   'lightIntensity':float(context_rng.uniform(100, 1500) if linked else context_rng.uniform(2000, 18000))}
        beta = np.zeros(len(FEATURES))
        coeffs = {'duration_seconds':-.12, 'temperature':.06*exposure, 'humidity':-.08*exposure,
                  'rain':-.3*exposure, 'windSpeed':-.10*exposure, 'windGust':-.06*exposure,
                  'windSpeed10Min':-.05*exposure, 'lightIntensity':.12*exposure,
                  'temperature_squared':-.18*exposure}
        if target == 'affect': coeffs.update(rain=-.39*exposure, lightIntensity=.25*exposure,
                                          temperature_squared=-.06*exposure, duration_seconds=-.06)
        a = .8
        if after and scenario in ('intercept_only', 'both'): a = -.8
        if after and scenario in ('rain_only', 'both'): coeffs['rain'] = -1.3*exposure*(1.3 if target == 'affect' else 1.)
        if scenario == 'transient' and steps//2 < step <= steps//2+3: a = -2.5 if target == 'affect' else -.3
        for name, value in coeffs.items(): beta[FEATURES.index(name)] = value
        p = probabilities(a+beta@design_row(context)[1:])
        rating = int(response_rng.choice(np.arange(1, 8), p=p))
        contrast = float((probabilities(a+beta[RAIN]*np.log(3))-probabilities(a))@((np.arange(7)-3)/3))
        events.append({'event_id':f'{scenario}-{schedule}-{target}-{mode}-{rep}-{step}',
                       'day':float(step), 'context':context, 'ordinal_category':rating,
                       'raw_response':rating-4 if target == 'affect' else rating,
                       'normalized_response':(rating-4)/3, 'truth_probabilities':p.tolist(),
                       'truth_intercept':a, 'truth_coefficients':beta.tolist(),
                       'truth_rain_coefficient':float(beta[RAIN]), 'truth_controlled_rain_contrast':contrast})
    return 1/3, events


def attribution_scores(f, e):
    out = metrics(f, e)
    for name in ('intercept', 'rain_coefficient', 'controlled_rain_contrast'):
        truth = e['truth_'+name]; lo, hi = f[name+'_interval_90']
        out[name+'_squared_error'] = (f[name+'_mean']-truth)**2
        out[name+'_covered_90'] = bool(lo <= truth <= hi)
    return out


def run(output, repetitions=2, particles=4096, seed=20261013):
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    design = {'version':VERSION, 'seed':seed, 'repetitions':repetitions, 'particles':particles,
              'scenarios':SCENARIOS, 'schedules':SCHEDULES, 'targets':['comfort', 'affect'],
              'modes':MODES, 'steps':72, 'score_from_step':13, 'change_step':37,
              'candidates':CANDIDATES, 'prior':'moderate', 'weather':WEATHER,
              'features':FEATURES, 'uv_included':False, 'ui_changed':False,
              'climate_has_no_smooth_time_trend':True,
              'shared_context_innovations_across_scenarios_and_targets':True,
              'independent_response_rng':True,
              'replication_unit':'schedule/mode/replicate block; targets and scenarios share weather',
              'diffusions_are_sensitivity_candidates_not_validated':True,
              'controlled_rain_probe':'all encoded other predictors zero; rain 0 versus 2; synthetic diagnostic not causal human estimate',
              'all_history_retained':True, 'live_activation':False, 'scientific_validation_claimed':False}
    (output/'design.json').write_text(json.dumps(design, indent=2)+'\n', encoding='utf-8')
    streams = []; rows = []; diagnostics = []
    with (output/'predictions.jsonl').open('w', encoding='utf-8') as archive:
        for schedule in SCHEDULES:
            for scenario in SCENARIOS:
                for target in ('comfort', 'affect'):
                    for mode in MODES:
                        for rep in range(repetitions):
                            initial, events = stream(scenario, schedule, target, mode, rep, seed)
                            provenance = 'ridge_estimated' if target == 'comfort' and mode in ('bike', 'car') else 'declared'
                            stream_id = f'{schedule}-{scenario}-{target}-{mode}-{rep}'
                            block_id = f'{schedule}-{mode}-{rep}'
                            streams.append({'id':stream_id, 'block':block_id, 'scenario':scenario, 'schedule':schedule,
                                            'target':target, 'mode':mode, 'replicate':rep, 'initial':initial,
                                            'initial_source':provenance, 'initial_raw':None if provenance == 'ridge_estimated' else (5 if target == 'comfort' else 1),
                                            'events':events})
                            filters = {name:AttributionFilter(initial, diffusion=q, rain_diffusion=rq,
                                      particles=particles, seed=seed+len(streams)*100+j, provenance=provenance)
                                       for j, (name, (q, rq)) in enumerate(CANDIDATES.items())}
                            sums = {}
                            for i, e in enumerate(events):
                                fs = {name:f.forecast(e['day'], e['context']) for name, f in filters.items()}
                                if i >= 12:
                                    for name, f in fs.items():
                                        phase = 'before_change' if i < 36 else 'after_change'
                                        scores = attribution_scores(f, e)
                                        archive.write(json.dumps({'stream':stream_id, 'block':block_id, 'model':name,
                                              'step':i+1, 'forecast':f, 'scores':scores}, allow_nan=False)+'\n')
                                        sums.setdefault((name, phase), []).append(scores)
                                # No candidate sees a response until all forecasts are archived.
                                for f in filters.values(): f.observe(e['ordinal_category'])
                            for (name, phase), values in sums.items():
                                rows.append({'stream':stream_id, 'block':block_id, 'schedule':schedule,
                                             'scenario':scenario, 'target':target, 'mode':mode, 'replicate':rep,
                                             'model':name, 'phase':phase, 'events':len(values),
                                             **{k:float(np.mean([v[k] for v in values])) for k in values[0]}})
                            diagnostics.append({'stream':stream_id, 'filters':{name:f.diagnostics() for name, f in filters.items()}})
                print(json.dumps({'schedule':schedule, 'completed_scenario':scenario, 'streams':len(streams)}), flush=True)
    (output/'streams.json').write_text(json.dumps(streams, indent=2)+'\n', encoding='utf-8')
    (output/'summary.json').write_text(json.dumps({'streams':len(streams), 'weather_blocks':len(SCHEDULES)*len(MODES)*repetitions,
          'scored_predictions':len(streams)*60*len(CANDIDATES), 'per_stream_scores':rows,
          'particle_diagnostics':diagnostics, 'live_activation':False}, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'complete':True, 'streams':len(streams), 'predictions':len(streams)*240}), flush=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--output', type=Path, required=True)
    p.add_argument('--repetitions', type=int, default=2); p.add_argument('--particles', type=int, default=4096)
    args = p.parse_args()
    # Response seed blocks allocate ten replicate slots per mode.
    if not 2 <= args.repetitions <= 9 or not 512 <= args.particles <= 32768: p.error('Use 2–9 repetitions and 512–32768 particles')
    run(args.output, args.repetitions, args.particles)
