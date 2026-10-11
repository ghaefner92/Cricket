import unittest
from copy import deepcopy
import numpy as np
from comfort_pilot.temporal_weather import WeatherFilter, average_forecast
from comfort_pilot.temporal_intensity_experiment import stream
from comfort_pilot.weather_contract import INTENSITY, FEATURES_V2
from comfort_pilot.model import fit
from comfort_pilot.temporal_intensity_report import contrast


class TemporalIntensityTest(unittest.TestCase):
    def test_report_compares_only_common_steps_and_weights_sequences_equally(self):
        rows=[]
        for sid,step,model,score in [('a',9,'weather_cumulative',2),('a',9,'basic_cumulative',0),
                                    ('a',10,'basic_cumulative',100),('b',9,'weather_cumulative',4),
                                    ('b',9,'basic_cumulative',0),('b',10,'weather_cumulative',4),
                                    ('b',10,'basic_cumulative',0)]:
            rows.append({'stream':sid,'step':step,'model':model,'prior':'reference','target':'comfort',
                         'scenario':'weather_dropout','log_score':score,'rps':0,'expected_response_squared_error':0})
        result=contrast(rows,'weather_cumulative','basic_cumulative')[0]
        self.assertEqual(result['common_forecasts'],3)
        self.assertEqual(result['sequences'],2)
        self.assertEqual(result['mean_delta_log_score'],3)

    def test_stream_uses_rate_and_keeps_truth_unavailable_to_filters(self):
        _,events=stream('hourly_reporting','affect','walk',0,123,16)
        for e in events:
            self.assertNotIn('rain',e['context'])
            self.assertEqual(e['normalized_response'],e['raw_response']/3)
            self.assertFalse(e['context_audit']['actual_trip_exposure_reconstructed'])
        self.assertIsNotNone(events[1]['context']['rainIntensity'])
        self.assertIsNone(events[2]['context']['rainIntensity'])
        self.assertIsNone(events[4]['context']['rainIntensity'])
        self.assertIsNotNone(events[2]['truth_context_not_available_to_filter']['rainIntensity'])

    def test_context_shift_has_fixed_relation_and_negative_episode_is_separate(self):
        _,events=stream('context_shift_only','comfort','bike',0,123,48)
        self.assertEqual(events[0]['truth_coefficients'],events[-1]['truth_coefficients'])
        self.assertEqual(events[0]['truth_intercept'],events[-1]['truth_intercept'])
        _,events=stream('rain_slope_change','affect','walk',0,123,48)
        self.assertNotEqual(events[0]['truth_coefficients'],events[-1]['truth_coefficients'])
        self.assertEqual(events[0]['truth_intercept'],events[-1]['truth_intercept'])

    def test_v2_priors_match_static_mcmc_and_legacy_rejects_sensitivity_option(self):
        for sd in (.175,.35,.7):
            f=WeatherFilter(0,particles=512,weather_model_version=INTENSITY,rain_prior_sd=sd)
            posterior=fit([],0,contextual=True,weather_model_version=INTENSITY,rain_prior_sd=sd,draws=128)
            self.assertEqual(f.beta_sd[FEATURES_V2.index('rainIntensity')],sd)
            self.assertEqual(posterior.prior['sd'][1+FEATURES_V2.index('rainIntensity')],sd)
        with self.assertRaises(ValueError): WeatherFilter(0,rain_prior_sd=.7)
        with self.assertRaises(ValueError): fit([],0,rain_prior_sd=.7)

    def test_calendar_and_response_order_and_model_weights_use_only_past(self):
        filters=[WeatherFilter(0,particles=512,weather_model_version=INTENSITY,diffusion=q) for q in (0.,.15,.4)]
        forecasts=[f.forecast(1,{'rainIntensity':0}) for f in filters]
        before=deepcopy(forecasts);weights=np.ones(3)/3
        mixture=average_forecast(filters,forecasts,weights)
        self.assertEqual(mixture['candidate_weights_before_response'],[1/3]*3)
        with self.assertRaises(ValueError): filters[0].forecast(2,{})
        likelihood=np.array([f.observe(1) for f in filters]);weights*=likelihood;weights/=weights.sum()
        self.assertEqual(forecasts,before)
        self.assertEqual(filters[0].diagnostics()['all_history_retained'],True)
        with self.assertRaises(ValueError):filters[0].forecast(.5,{})
        with self.assertRaises(ValueError):filters[0].observe(1)

    def test_dropout_is_explicit_basic_fallback_and_time_gap_is_real_days(self):
        _,events=stream('weather_dropout','comfort','car',0,321,48)
        self.assertEqual(events[1]['context_audit']['fallback'],'basic')
        self.assertTrue(all(events[1]['context'][k] is None for k in events[1]['context'] if k!='duration_seconds'))
        _,events=stream('irregular_calendar','affect','walk',0,321,48)
        self.assertGreater(events[24]['day']-events[23]['day'],20)


if __name__=='__main__':unittest.main()
