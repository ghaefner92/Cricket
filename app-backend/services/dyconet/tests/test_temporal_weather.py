import unittest
import numpy as np
from comfort_pilot.model import WEATHER, design_row
from comfort_pilot.temporal_weather import WeatherFilter
from comfort_pilot.temporal_lab import GridFilter
from comfort_pilot.temporal_weather_experiment import stream


class TemporalWeatherTest(unittest.TestCase):
    def test_production_encoding_all_weather_without_uv(self):
        _, events = stream('stable_aligned', 'comfort', 'walk', 0, 72)
        context = events[0]['context']
        self.assertEqual(set(context), set(WEATHER)|{'duration_seconds'})
        self.assertNotIn('UV', context)
        self.assertTrue(np.isfinite(design_row(context)).all())
        self.assertEqual(events[0]['normalized_response'], (events[0]['raw_response']-4)/3)

    def test_order_duplicate_and_future_response_guards(self):
        f = WeatherFilter(1/3, particles=512)
        self.assertRaises(ValueError, f.observe, 5)
        prediction = f.forecast(1, {})
        self.assertEqual(prediction['previous_observations'], 0)
        self.assertAlmostEqual(sum(prediction['probabilities']), 1)
        self.assertRaises(ValueError, f.forecast, 2, {})
        f.observe(5)
        self.assertRaises(ValueError, f.observe, 5)
        self.assertRaises(ValueError, f.forecast, .5, {})
        self.assertEqual(len(f.y), 1)

    def test_full_particle_filter_reduces_to_exact_scalar_control(self):
        # All encoded predictors, including missing indicators, equal zero.
        context = dict(duration_seconds=1200, temperature=20, humidity=50, rain=0,
                       windSpeed=0, windGust=0, windSpeed10Min=0, lightIntensity=np.sqrt(10001)-1)
        self.assertLess(abs(design_row(context)[1:]).max(), 1e-14)
        for q in (0., .15):
            particle = WeatherFilter(1/3, particles=8192, diffusion=q, seed=3)
            exact = GridFilter(1/3, diffusion=q)
            errors = []
            for day, rating in enumerate([5, 4, 3, 6, 2, 1, 3, 2, 6, 4]*3, 1):
                a = particle.forecast(day, context); b = exact.forecast(day, 0)
                errors.append(abs(a['normalized_mean']-b['normalized_mean']))
                particle.observe(rating); exact.observe(rating)
            self.assertLess(max(errors), .04)
            self.assertEqual(len(particle.y), 30)

    def test_weather_shift_does_not_change_generating_relationship(self):
        _, events = stream('context_shift_only', 'affect', 'bike', 0, 55)
        self.assertEqual(events[0]['truth_coefficients'], events[-1]['truth_coefficients'])
        self.assertEqual(events[0]['truth_intercept'], events[-1]['truth_intercept'])
        self.assertEqual(events[0]['normalized_response'], events[0]['raw_response']/3)


if __name__ == '__main__': unittest.main()
