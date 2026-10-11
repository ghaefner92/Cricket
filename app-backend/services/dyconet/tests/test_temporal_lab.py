import unittest
from copy import deepcopy
import numpy as np
from scipy.integrate import quad
from comfort_pilot.temporal_lab import GridFilter,AveragedFilter
from comfort_pilot.model import initial_location,probabilities
from comfort_pilot.temporal_experiment import stream,metrics

class TemporalLabTest(unittest.TestCase):
    def test_cumulative_grid_matches_independent_quadrature(self):
        f=GridFilter(.3,grid_size=481);answers=[1,2,3,4,5,6,7,2,2]
        for day,rating in enumerate(answers,1):f.forecast(day,0.);f.observe(rating)
        result=f.forecast(len(answers),0.)
        mu=initial_location(.3)
        def density(x):return np.exp(-.5*((x-mu)/1.2)**2)*np.prod([probabilities(x)[r-1] for r in answers])
        denominator=quad(density,-12,12,epsabs=1e-14)[0]
        expected=quad(lambda x:density(x)*float(probabilities(x)@((np.arange(7)-3)/3)),-12,12,epsabs=1e-14)[0]/denominator
        self.assertAlmostEqual(result['normalized_mean'],expected,places=5)

    def test_calendar_uncertainty_increases_with_gap(self):
        a=GridFilter(0.,diffusion=.4,grid_size=481);b=GridFilter(0.,diffusion=.4,grid_size=481)
        a.forecast(1,0.);b.forecast(4,0.)
        variance=lambda f:float((f.mass[:,0]*(f.axis-(f.mass[:,0]@f.axis))**2).sum())
        self.assertAlmostEqual(variance(b)-variance(a),.4**2*3,delta=.003)

    def test_predictions_precede_responses_and_weights_use_past_only(self):
        f=AveragedFilter(.3);before=f.weights.copy();p=f.forecast(1,0.)
        np.testing.assert_array_equal(before,f.weights)
        self.assertEqual(p['previous_observations'],0)
        expected=before*np.array([c.pending[2][0] for c in f.components]);expected/=expected.sum()
        f.observe(1);np.testing.assert_allclose(f.weights,expected)
        with self.assertRaises(ValueError):f.observe(1)
        with self.assertRaises(ValueError):f.forecast(-1,0.)

    def test_raw_codings_preserved_and_context_shift_is_not_personal_drift(self):
        initial_c,comfort=stream('context_shift_only','comfort','walk',0,20261010)
        initial_a,affect=stream('context_shift_only','affect','walk',0,20261010)
        self.assertEqual(initial_c,(5-4)/3);self.assertEqual(initial_a,1/3)
        self.assertEqual(stream('stable_wrong_prior','affect','walk',0,20261010)[0],2/3)
        for e in comfort:self.assertEqual(e['normalized_response'],(e['raw_response']-4)/3)
        for e in affect:self.assertEqual(e['normalized_response'],e['raw_response']/3)
        self.assertEqual(len(set(e['truth_intercept'] for e in affect)),1)
        self.assertEqual(len(set(e['truth_rain_slope'] for e in affect)),1)

    def test_fine_grid_convergence_for_dynamic_contextual_filter(self):
        _,events=stream('gradual_change','affect','bike',0,20261010,steps=45)
        filters=[GridFilter(.3,contextual=True,diffusion=.15,grid_size=n,slope_size=s) for n,s in ((241,41),(481,81))]
        errors=[]
        for e in events:
            predictions=[f.forecast(e['day'],e['rain']) for f in filters]
            errors.append(abs(predictions[0]['normalized_mean']-predictions[1]['normalized_mean']))
            for f in filters:f.observe(e['ordinal_category'])
        self.assertLess(max(errors),.015)
        self.assertLess(max(f.max_boundary_mass for f in filters),1e-4)

if __name__=='__main__':unittest.main()
