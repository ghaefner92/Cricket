import unittest
from copy import deepcopy
from unittest.mock import patch
import numpy as np
from test_comfort_pilot import request_fixture
from test_prospective import fixture as prospective_fixture
from comfort_pilot.weather_contract import INTENSITY, LEGACY, RAW_V2, FEATURES_V2, specification
from comfort_pilot.context_audit import audit_context
from comfort_pilot.context_capture import prepare_reading, sample_context, validate_capture
from comfort_pilot.model import design_row, fit
from comfort_pilot.api import analyze
from comfort_pilot.evidence import _route_context, validate_request
from comfort_pilot.prospective import forecast
from comfort_pilot.prospective_evaluation import evaluate, validate_record
from comfort_pilot.sensor_time import attach_reception
from orion_context import OrionEntity
from orion_query_list import OrionQueryListProvider


STAMP = '2026-10-11T12:00:00Z'


def rain(value=6, report='2026-10-11T11:55:00Z'):
    wrapper = {'value': value}
    if report is not None: wrapper['receivedAt'] = report
    entity = OrionEntity.from_mapping({'id': 'Sensor:Weather:Winfred', 'type': 'Weather',
                                       'rainIntensity': wrapper, 'uplinkInterval': {'value': 60}})
    return attach_reception(OrionQueryListProvider().observations(entity, variables=('rain',))[0].to_dict(), STAMP)


def context(*readings):
    return {'queried_at': STAMP, 'weather_model_version': INTENSITY, 'measurements': list(readings)}


class WeatherIntensityV2Test(unittest.TestCase):
    def test_versioned_design_has_rate_and_distinguishes_zero_from_absence(self):
        x = design_row({'rainIntensity': 6}, weather_model_version=INTENSITY)
        self.assertAlmostEqual(x[1+RAW_V2.index('rainIntensity')], np.log(7))
        self.assertFalse(np.array_equal(design_row({}, weather_model_version=INTENSITY),
                                       design_row({'rainIntensity': 0}, weather_model_version=INTENSITY)))
        with self.assertRaises(ValueError): design_row({'rain': 6}, weather_model_version=INTENSITY)
        with self.assertRaises(ValueError): design_row({'cumulativeRainfall': 6}, weather_model_version=INTENSITY)
        with self.assertRaises(ValueError): design_row({'rainIntensity': 451}, weather_model_version=INTENSITY)
        self.assertAlmostEqual(design_row({'rain': 6})[1+RAW_V2.index('rainIntensity')], np.log(7))
        self.assertFalse(specification()['legacy_rain_coefficients_reused'])

    def test_report_recency_is_not_query_recency_and_unknown_zero_is_not_dry(self):
        for report, value, expected in (('2026-10-11T11:55:00Z', 0, 0),
                                        ('2026-10-11T11:50:00Z', 6, 6),
                                        ('2026-10-11T11:49:59Z', 6, None),
                                        (None, 0, None)):
            c = context(rain(value, report)); original = deepcopy(c)
            result = audit_context(c, STAMP)
            self.assertEqual(result['values']['rainIntensity'], expected)
            self.assertEqual(c, original)
            self.assertIsNone(result['readings'][0]['age_seconds_at_query'])
        unknown = audit_context(context(rain(report=None)), STAMP)
        self.assertEqual(unknown['fallback'], 'basic')
        self.assertFalse(unknown['contextual_prediction_allowed_for_research'])
        self.assertIn('rainIntensity: rain_report_time_unknown', unknown['warnings'])

    def test_future_query_or_report_rejected_and_legacy_clock_not_borrowed(self):
        with self.assertRaises(ValueError): audit_context(context(rain(report='2026-10-11T12:01:00Z')), STAMP)
        with self.assertRaises(ValueError): audit_context(context(rain()), '2026-10-11T11:59:00Z')
        r = rain(report=None); r['timestamp'] = STAMP
        r['source_metadata']['timestamp_reference_entity'] = 'Sensor:Weather:Walter'
        self.assertIsNone(audit_context(context(r), STAMP)['values']['rainIntensity'])
        with self.assertRaises(ValueError): audit_context(context(r), STAMP, weather_model_version=LEGACY)

    def test_wrong_station_unit_window_and_accumulation_are_excluded(self):
        for change in ({'unit': 'mm'}, {'source_entity_id': 'Sensor:Weather:Walter'},
                       {'variable': 'cumulativeRainfall'}):
            r = rain(); r.update(change)
            result = audit_context(context(r), STAMP)
            self.assertIsNone(result['values']['rainIntensity'])
            self.assertFalse(result['readings'][0]['included'])
        r = rain(); r['source_metadata']['measurement_window_seconds'] = 3600
        self.assertFalse(audit_context(context(r), STAMP)['readings'][0]['included'])

    def test_light_walter_requires_explicit_lux_and_new_provider_keeps_units_raw(self):
        p = OrionQueryListProvider()
        for unit, expected in ((None, None), ('W/m2', None), ('lux', 250), ('lx', 250)):
            a = {'value': 250}
            if unit: a['unit'] = unit
            e = OrionEntity.from_mapping({'id': 'Sensor:Weather:Walter', 'type': 'Weather', 'lightIntensity': a})
            r = p.observations(e, variables=('lightIntensity',))[0].to_dict(); r['timestamp'] = STAMP
            r = prepare_reading(r)
            self.assertEqual(audit_context(context(r), STAMP)['values']['lightIntensity'], expected)
            if unit is None: self.assertIsNone(r['unit'])
        historical = {'variable': 'lightIntensity', 'numeric_value': 250, 'unit': 'lux', 'timestamp': STAMP,
                      'status': 'OBSERVED', 'source': 'orion', 'source_entity_id': 'Sensor:Weather:Walter',
                      'source_metadata': {'pilot_original_unit': None, 'pilot_unit_provenance': 'provider_contract'}}
        self.assertFalse(audit_context(context(historical), STAMP)['readings'][0]['included'])

    def test_partial_context_and_no_weather_have_explicit_different_policies(self):
        temperature = {'variable': 'temperature', 'numeric_value': 18, 'unit': 'C', 'timestamp': STAMP,
                       'source': 'test', 'status': 'OBSERVED'}
        partial = audit_context(context(temperature, rain(report=None)), STAMP)
        self.assertEqual(partial['context_state'], 'partial')
        self.assertTrue(partial['contextual_prediction_allowed_for_research'])
        self.assertFalse(partial['rain_intensity_used'])
        missing = audit_context(context(), STAMP)
        self.assertEqual(missing['context_state'], 'insufficient')
        self.assertEqual(missing['fallback'], 'basic')
        self.assertFalse(missing['production_activation_allowed'])

    def test_route_uses_winfred_rate_without_walter_conflict_or_partial_coverage(self):
        r = rain(); walter = deepcopy(r); walter['source_entity_id'] = 'Sensor:Weather:Walter'
        d = prospective_fixture(); candidate = d['candidates'][0]
        candidate['snapshot'] = {'route_context': {'segments': [{'raw_observations': [walter, r]}, {'raw_observations': [r]}]}}
        j = {'selection': {'mode_key': 'walk', 'chosen_at': STAMP}, 'experience': {'completion': 'as_planned'},
             'plan': {'route': candidate['route'], 'context_at_selection': {'snapshot': candidate['snapshot']}}}
        c = _route_context({'completed_at': STAMP}, j, mode='walk', weather_model_version=INTENSITY)
        self.assertEqual(audit_context(c, STAMP)['values']['rainIntensity'], 6)
        candidate['snapshot']['route_context']['segments'][1]['raw_observations'] = []
        c = _route_context({'completed_at': STAMP}, j, mode='walk', weather_model_version=INTENSITY)
        self.assertIsNone(audit_context(c, STAMP)['values']['rainIntensity'])
        self.assertIn('rain: partial_segment_coverage', audit_context(c, STAMP)['warnings'])

    def test_sampler_has_separate_contract_and_does_not_transfer_literature_shape(self):
        with self.assertRaises(ValueError): fit([], 0, contextual=True, literature_shape=True, weather_model_version=INTENSITY)
        observations = [{'event_id': str(i), 'rating': 6 if i%2 == 0 else 2,
                         'context': {'rainIntensity': 0 if i%2 == 0 else 6}} for i in range(60)]
        posterior = fit(observations, 0, contextual=True, weather_model_version=INTENSITY, draws=4096, seed=812)
        dry = posterior.predict({'rainIntensity': 0})['signed_belief_mean']
        wet = posterior.predict({'rainIntensity': 6})['signed_belief_mean']
        self.assertGreater(dry-wet, .35)
        self.assertTrue(posterior.diagnostics['passed'])
        self.assertEqual(posterior.prior['mean'][1+RAW_V2.index('rainIntensity')], 0)
        self.assertEqual(posterior.prior['weather_contract']['weather_model_version'], INTENSITY)

    def test_api_forecast_versions_and_fallback_preserve_legacy_replay(self):
        data = request_fixture(); original = deepcopy(data)
        legacy = analyze(data)
        self.assertEqual(legacy['model_version'], 'd-day-comfort-ordinal-v1')
        self.assertNotIn('weather_contract', legacy)
        self.assertEqual(data, original)
        data['weather_model_version'] = INTENSITY
        result = analyze(data)
        self.assertEqual(result['model_version'], 'd-day-comfort-ordinal-intensity-v2')
        self.assertEqual(result['feature_columns'], ['intercept', *FEATURES_V2])
        self.assertEqual(result['predictions'][0]['models']['contextual']['context_policy']['fallback'], 'basic')
        self.assertFalse(result['predictions'][0]['models']['contextual']['prediction_usable_for_research'])
        # Old request and forecast remain identical with the default V1 route.
        self.assertEqual(analyze(original), legacy)
        d = prospective_fixture(); d['weather_model_version'] = INTENSITY
        f = forecast(d)
        self.assertEqual(f['targets']['affect']['model_version'], 'd-day-affect-ordinal-intensity-v2')
        self.assertFalse(f['hotco_modified'])
        self.assertFalse(f['learning_applied_to_active_passport'])
        j = deepcopy(d['records'][0]['journeys'][0]); j['selection'].update(chosen_at='2026-10-09T10:03:00Z', routing_rank=1)
        record = {'search_id': 'search', 'journeys': [j], 'd_day_forecast': {
            'status': 'complete', 'recorded_at': '2026-10-09T10:02:00Z', 'request': d, 'result': f}}
        scores = evaluate(record, j)
        self.assertEqual({s['model'] for s in scores['scores']}, {'static', 'basic'})
        self.assertTrue(all(s['weather_model_version'] == INTENSITY for s in scores['scores']))
        record['d_day_evaluations'] = [scores]; validate_record(record)

    def test_capture_v2_preserves_accumulation_without_learning_it(self):
        r = rain(); counter = deepcopy(r); counter.update(variable='cumulativeRainfall', numeric_value=110.49, unit='mm')
        counter['source_metadata'].update(source_attribute='cumulativeRainfall', normalization_eligible=False)
        class Provider:
            def get_weather(self, **query):
                from route_context import ContextObservation
                return tuple(ContextObservation.from_dict(o) for o in (r, counter))
        clock = iter([STAMP, STAMP])
        capture = sample_context({'lat': 52, 'lon': 11, 'weather_model_version': INTENSITY}, provider=Provider(), clock=lambda: next(clock))
        self.assertEqual(len(capture['raw_observations']), 2)
        self.assertEqual(capture['audit']['values']['rainIntensity'], 6)
        self.assertNotIn('cumulativeRainfall', capture['audit']['values'])
        self.assertEqual(capture['audit']['schema_version'], 'd-day-context-audit-v2')


if __name__ == '__main__': unittest.main()
