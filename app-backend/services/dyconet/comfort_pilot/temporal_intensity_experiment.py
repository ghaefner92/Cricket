"""Paired synthetic V2 prior/diffusion experiment, isolated from app state."""
import os
os.environ['OPENBLAS_NUM_THREADS'] = '1'
os.environ['OMP_NUM_THREADS'] = '1'
import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import numpy as np
from .weather_contract import INTENSITY, RAW_V2, FEATURES_V2, UNITS_V2, specification
from .context_audit import audit_context
from .model import MODES, probabilities, initial_location, design_row
from .temporal_weather import WeatherFilter, average_forecast
from .temporal_lab import GridFilter, AveragedFilter
from .temporal_experiment import metrics, MODE_EXPOSURE

VERSION = 'd-day-temporal-intensity-prior-lab-v2'
SCENARIOS = ('stable_aligned', 'stable_wrong_prior', 'abrupt_change', 'gradual_change',
             'context_shift_only', 'transient_bad_trip', 'irregular_calendar', 'rain_slope_change',
             'hourly_reporting', 'unknown_rain_clock', 'weather_dropout')
PRIORS = {
    'reference': ('moderate', .35), 'initial_weak': ('weak', .35),
    'initial_strong': ('strong', .35), 'rain_weak': ('moderate', .7),
    'rain_strong': ('moderate', .175),
}
MODELS = ('static', 'basic_cumulative', 'basic_averaged', 'weather_cumulative',
          'weather_dynamic_0.15', 'weather_dynamic_0.4', 'weather_averaged')


def frozen_context(truth, instant, *, rain_age_seconds=300, omit_weather=False):
    """Synthetic NGSI-shaped fixture. Timestamp/window do not assert live measurements."""
    stamp = instant.isoformat()
    measurements = []
    for key, value in truth.items():
        if omit_weather and key != 'duration_seconds': continue
        metadata = {'measurement_semantics': 'SYNTHETIC_TEST_FIXTURE_NOT_LIVE_SENSOR',
                    'measurement_window_seconds': 600 if key == 'windSpeed10Min' else 60,
                    'normalization_eligible': True, 'simulation_only': True}
        variable = key
        if key == 'rainIntensity':
            variable = 'rain'
            metadata.update(source_attribute='rainIntensity', rain_contract_version='winfred-rain-intensity-v1',
                            measurement_semantics='TEN_MINUTE_ACCUMULATION_SCALED_TO_HOURLY_RATE',
                            measurement_window_seconds=600, reporting_interval_minutes=60,
                            last_report_time_provenance='EXPLICIT_ATTRIBUTE_RECEIVED_AT' if rain_age_seconds is not None else 'UNKNOWN',
                            last_report_at=None if rain_age_seconds is None else (instant-timedelta(seconds=rain_age_seconds)).isoformat())
        measurements.append({'variable': variable, 'numeric_value': value, 'raw_value': value,
                             'unit': UNITS_V2[key], 'timestamp': stamp, 'status': 'OBSERVED',
                             'source': 'routing_plan' if key == 'duration_seconds' else 'orion',
                             'source_entity_id': None if key == 'duration_seconds' else 'Sensor:Weather:Winfred',
                             'source_metadata': metadata})
    return {'weather_model_version': INTENSITY, 'queried_at': stamp,
            'scope': 'synthetic_temporal_fixture_not_personal_exposure', 'measurements': measurements}


def stream(scenario, target, mode, rep, seed, steps=48):
    if scenario not in SCENARIOS or target not in ('comfort', 'affect') or mode not in MODES or steps < 16:
        raise ValueError('Invalid synthetic stream configuration')
    rng = np.random.default_rng(seed+SCENARIOS.index(scenario)*10000+rep*100+MODES.index(mode)*10+(target == 'affect'))
    initial = 2/3 if scenario == 'stable_wrong_prior' else 1/3
    events = []; day = 0.; exposure = MODE_EXPOSURE[mode]; change = steps//2
    anchor = datetime(2026, 1, 1, tzinfo=timezone.utc)
    for index in range(steps):
        day += float(rng.choice([.5, 1., 3.])) if scenario == 'irregular_calendar' else 1.
        if scenario == 'irregular_calendar' and index == change: day += 20
        chance = (.1 if index < change else .8) if scenario == 'context_shift_only' else .4
        storm = rng.random() < chance
        intensity = float(rng.uniform(.5, 6)) if storm else 0.
        wind = max(0., rng.normal(5 if storm else 2, 1.2))
        truth = {'duration_seconds': float({'walk':1500, 'bike':900, 'pt':1800, 'car':1100}[mode]*rng.uniform(.7, 1.5)),
                 'temperature': float(17+7*np.sin(index/10)-5*storm+rng.normal(0, 2)),
                 'humidity': float(np.clip(55+25*storm+rng.normal(0, 7), 0, 100)),
                 'rainIntensity': intensity, 'windSpeed': float(wind),
                 'windGust': float(wind+rng.uniform(0, 5)), 'windSpeed10Min': float(max(0, wind+rng.normal(0, .5))),
                 'lightIntensity': float(rng.uniform(100,1500) if storm else rng.uniform(2000,18000))}
        coefficients = {'duration_seconds': -.12, 'temperature': .06*exposure, 'humidity': -.08*exposure,
                        'rainIntensity': -.7*exposure, 'windSpeed': -.10*exposure, 'windGust': -.06*exposure,
                        'windSpeed10Min': -.05*exposure, 'lightIntensity': .12*exposure, 'temperature_squared': -.18*exposure}
        if target == 'affect': coefficients.update(rainIntensity=-.91*exposure, lightIntensity=.25*exposure,
                                                  temperature_squared=-.06*exposure, duration_seconds=-.06)
        if scenario == 'rain_slope_change':
            coefficients['rainIntensity'] = (-.15 if index < change else -1.2)*exposure*(1.3 if target == 'affect' else 1)
        beta = np.zeros(len(FEATURES_V2))
        for key, value in coefficients.items(): beta[FEATURES_V2.index(key)] = value
        intercept = .8
        if scenario == 'stable_wrong_prior': intercept = -1.5
        if scenario == 'abrupt_change' and index >= change: intercept = -1.5
        if scenario == 'gradual_change': intercept = .8-2.3*np.clip((index-steps/3)/(steps/2), 0, 1)
        if scenario == 'transient_bad_trip' and change <= index < change+3: intercept = -2.5 if target == 'affect' else -.3
        if scenario == 'irregular_calendar': intercept = .8-2.3*np.clip((day-steps/3)/steps, 0, 1)
        p = probabilities(intercept+beta@design_row(truth, weather_model_version=INTENSITY)[1:])
        category = int(rng.choice(np.arange(1,8), p=p))
        age = (0, 300, 900, 2700, None)[index%5] if scenario == 'hourly_reporting' else None if scenario == 'unknown_rain_clock' else 300
        raw = frozen_context(truth, anchor+timedelta(days=day), rain_age_seconds=age,
                             omit_weather=scenario == 'weather_dropout' and index%4 != 0)
        audit = audit_context(raw, raw['queried_at'])
        events.append({'event_id': f'synthetic-v2-{scenario}-{target}-{mode}-{rep}-{index+1}',
                       'day': day, 'confirmed_at': (anchor+timedelta(days=day, minutes=45)).isoformat(),
                       'context': audit['values'], 'frozen_context': raw, 'context_audit': audit,
                       'truth_context_not_available_to_filter': truth, 'ordinal_category': category,
                       'raw_response': category-4 if target == 'affect' else category,
                       'normalized_response': (category-4)/3, 'truth_probabilities': p.tolist(),
                       'truth_intercept': float(intercept), 'truth_coefficients': beta.tolist(),
                       'phase': 'before_change' if index < change else 'after_change'})
    return initial, events


def run(output, *, repetitions=2, particles=1024, steps=48, seed=20261012, scenarios=SCENARIOS, modes=MODES, priors=None):
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    priors = priors or PRIORS
    design = {'version': VERSION, 'seed': seed, 'particles': particles, 'steps': steps, 'score_from_step': 9,
              'repetitions': repetitions, 'scenarios': list(scenarios), 'targets': ['comfort','affect'], 'modes': list(modes),
              'prior_configurations': priors, 'models': MODELS, 'diffusions_latent_sd_per_sqrt_day': [0,.15,.4],
              'weather_contract': specification(), 'prediction_before_response': True, 'all_history_retained': True,
              'replication_unit': 'independent sequence; priors and candidates paired within sequence',
              'data_kind': 'synthetic_not_observed', 'only_intercept_dynamic': True, 'rain_coefficient_dynamic': False,
              'prior_or_diffusion_empirically_validated': False, 'live_activation': False,
              'unknown_or_stale_rain_never_replaced_with_zero': True,
              'limitations': ['synthetic coefficients and weather relationships', 'finite-particle error',
                              'fixed contextual coefficients cannot identify changing rain relationships',
                              'partial-weather latent coverage is stress testing, not generative calibration']}
    (output/'design.json').write_text(json.dumps(design, indent=2)+'\n', encoding='utf-8')
    sequences, summaries, numerical = [], [], []; predictions = 0
    with (output/'predictions.jsonl').open('w', encoding='utf-8') as archive:
        for scenario in scenarios:
            for target in ('comfort','affect'):
                for mode in modes:
                    for rep in range(repetitions):
                        initial, events = stream(scenario, target, mode, rep, seed, steps)
                        source = 'ridge_estimated' if target == 'comfort' and mode in ('bike','car') else 'declared'
                        sid = f'{scenario}-{target}-{mode}-{rep}'
                        sequences.append({'id': sid, 'scenario': scenario, 'target': target, 'mode': mode, 'replicate': rep,
                                          'initial': initial, 'initial_source': source, 'events': events})
                        fixed = probabilities(initial_location(initial))
                        for prior_name, (profile, rain_sd) in priors.items():
                            filters = [WeatherFilter(initial, diffusion=q, particles=particles, seed=seed+len(sequences)*100+j,
                                       provenance=source, prior_profile=profile, rain_prior_sd=rain_sd,
                                       weather_model_version=INTENSITY) for j,q in enumerate((0.,.15,.4))]
                            basic = GridFilter(initial, prior_profile=profile, provenance=source)
                            basic_average = AveragedFilter(initial, prior_profile=profile, provenance=source)
                            weights = np.full(3, 1/3); groups = {}
                            for i,event in enumerate(events):
                                weather = [f.forecast(event['day'], event['context']) for f in filters]
                                fs = {'static': {'probabilities': fixed.tolist(), 'normalized_mean': float(fixed@((np.arange(7)-3)/3)),
                                                 'interval_90': None, 'previous_observations': 0},
                                      'basic_cumulative': basic.forecast(event['day'], 0),
                                      'basic_averaged': basic_average.forecast(event['day'], 0),
                                      **dict(zip(MODELS[3:6], weather)), 'weather_averaged': average_forecast(filters, weather, weights)}
                                for name, forecast in fs.items():
                                    usable = not name.startswith('weather_') or event['context_audit']['contextual_prediction_allowed_for_research']
                                    if i >= 8 and usable:
                                        scores = metrics(forecast, event)
                                        row = {'stream': sid, 'scenario': scenario, 'target': target, 'mode': mode, 'replicate': rep,
                                               'prior': prior_name, 'model': name, 'step': i+1, 'day': event['day'], 'phase': event['phase'],
                                               'weather_model_version': INTENSITY, 'forecast': forecast,
                                               'rain_intensity_used': event['context_audit']['rain_intensity_used'], **scores}
                                        archive.write(json.dumps(row, allow_nan=False)+'\n'); predictions += 1
                                        groups.setdefault((name, event['phase']), []).append(scores)
                                # Full history is retained; unknown context is missing, never an observed dry report.
                                evidence = np.array([f.observe(event['ordinal_category']) for f in filters])
                                weights *= evidence; weights /= weights.sum()
                                basic.observe(event['ordinal_category']); basic_average.observe(event['ordinal_category'])
                            for (name,phase), scores in groups.items():
                                summaries.append({'stream': sid, 'scenario': scenario, 'target': target, 'mode': mode,
                                                  'replicate': rep, 'prior': prior_name, 'model': name, 'phase': phase,
                                                  'n': len(scores), 'mean_log_score': float(np.mean([s['log_score'] for s in scores])),
                                                  'mean_rps': float(np.mean([s['rps'] for s in scores])),
                                                  'expectation_rmse': float(np.sqrt(np.mean([s['expected_response_squared_error'] for s in scores]))),
                                                  'coverage_90': None if name=='static' else float(np.mean([s['latent_expectation_covered_90'] for s in scores])),
                                                  'interval_halfwidth': None if name=='static' else float(np.mean([s['interval_halfwidth'] for s in scores]))})
                            numerical.append({'stream': sid, 'prior': prior_name, 'filters': [f.diagnostics() for f in filters],
                                              'basic_grid_boundary_mass': max(basic.max_boundary_mass, *(f.max_boundary_mass for f in basic_average.components))})
            print(json.dumps({'scenario_complete': scenario, 'sequences': len(sequences)}), flush=True)
    (output/'streams.json').write_text(json.dumps(sequences, indent=2, allow_nan=False)+'\n', encoding='utf-8')
    summary = {'version': VERSION, 'sequences': len(sequences), 'synthetic_events': len(sequences)*steps,
               'scored_predictions': predictions, 'sequence_metrics': summaries, 'numerical_diagnostics': numerical,
               'live_activation': False, 'scientific_validation_claimed': False}
    (output/'summary.json').write_text(json.dumps(summary, indent=2, allow_nan=False)+'\n', encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k not in ('sequence_metrics','numerical_diagnostics')}), flush=True)
    return summary


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--particles', type=int, default=1024); parser.add_argument('--repetitions', type=int, default=2)
    parser.add_argument('--steps', type=int, default=48); args=parser.parse_args()
    if not 512<=args.particles<=32768 or not 2<=args.repetitions<=20 or not 16<=args.steps<=144:
        parser.error('Unsupported laboratory size')
    run(args.output, particles=args.particles, repetitions=args.repetitions, steps=args.steps)
