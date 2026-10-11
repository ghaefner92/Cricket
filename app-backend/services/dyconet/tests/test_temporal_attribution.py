import unittest
import numpy as np
from comfort_pilot.temporal_weather import WeatherFilter
from comfort_pilot.temporal_attribution import AttributionFilter
from comfort_pilot.attribution_reference import GridReference, null_context
from comfort_pilot.attribution_experiment import stream


class AttributionTest(unittest.TestCase):
    def test_zero_rain_diffusion_preserves_previous_filter(self):
        a = WeatherFilter(1/3, particles=512, seed=21, diffusion=.15)
        b = AttributionFilter(1/3, particles=512, seed=21, diffusion=.15)
        for day, rating in enumerate([1, 1, 2, 7, 4, 6, 7, 6]*3, 1):
            context = null_context(day % 3)
            p, q = a.forecast(day, context), b.forecast(day, context)
            np.testing.assert_allclose(p['probabilities'], q['probabilities'], atol=1e-12)
            a.observe(rating); b.observe(rating)

    def test_dynamic_rain_and_joint_states_match_independent_grid(self):
        for q in (0., .15):
            f = AttributionFilter(1/3, particles=16384, seed=12, diffusion=q, rain_diffusion=.08)
            reference = GridReference(q=q)
            errors = []
            for day, rating in enumerate([5, 4, 6, 1, 2, 3, 5, 2]*3, 1):
                rain = float([0., .5, 2., 5.][day % 4])
                p = f.forecast(day, null_context(rain)); r = reference.forecast(day, rain)
                errors.append(abs(p['normalized_mean']-r['mean']))
                self.assertLess(abs(p['rain_coefficient_mean']-r['b']), .06)
                self.assertLess(r['boundary_mass'], 1e-5)
                f.observe(rating); reference.observe(rating)
            self.assertLess(max(errors), .04)

    def test_context_is_paired_and_changes_are_separated(self):
        streams = {s:stream(s, 'independent_weather', 'comfort', 'walk', 0)[1]
                   for s in ('stable', 'intercept_only', 'rain_only', 'both', 'context_only')}
        for s in ('intercept_only', 'rain_only', 'both'):
            self.assertEqual([e['context'] for e in streams[s]], [e['context'] for e in streams['stable']])
        for s in ('stable', 'context_only', 'rain_only'):
            self.assertEqual(streams[s][0]['truth_intercept'], streams[s][-1]['truth_intercept'])
        for s in ('stable', 'context_only', 'intercept_only'):
            self.assertEqual(streams[s][0]['truth_rain_coefficient'], streams[s][-1]['truth_rain_coefficient'])
        _, affect = stream('stable', 'independent_weather', 'affect', 'walk', 0)
        self.assertEqual([e['context'] for e in affect], [e['context'] for e in streams['stable']])
        self.assertNotEqual([e['raw_response'] for e in affect], [e['raw_response'] for e in streams['stable']])

    def test_forecast_order_and_invalid_diffusion(self):
        self.assertRaises(ValueError, AttributionFilter, 1/3, rain_diffusion=-.08)
        f = AttributionFilter(1/3, particles=512, rain_diffusion=.08)
        self.assertRaises(ValueError, f.observe, 4)
        p = f.forecast(1., null_context(2))
        self.assertEqual(p['previous_observations'], 0)
        self.assertRaises(ValueError, f.forecast, 2., null_context(2))
        f.observe(4)
        self.assertRaises(ValueError, f.observe, 4)
        self.assertEqual(len(f.y), 1)


if __name__ == '__main__': unittest.main()
