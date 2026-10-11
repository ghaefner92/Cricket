import json
from pathlib import Path
import unittest
from unittest.mock import patch
from comfort_pilot.pilot_review import predictive_interval, review


class PilotReviewTest(unittest.TestCase):
    def test_discrete_predictive_mass_and_latent_intervals_are_distinct(self):
        lower,upper,mass=predictive_interval([1/7]*7)
        self.assertEqual((lower,upper),(0,6));self.assertAlmostEqual(mass,1)
        self.assertEqual(predictive_interval([0,0,0,1,0,0,0]),(3,3,1))
        with self.assertRaises(ValueError):predictive_interval([.1]*7)

    def test_legacy_backup_never_creates_prospective_scores(self):
        backup=json.loads((Path(__file__).resolve().parents[4]/"validation/fixtures/d_day_backup.json").read_text(encoding="utf-8"))
        r=review(backup)
        self.assertEqual(r["matched_scores"],[])
        self.assertFalse(r["activation_eligible"])
        self.assertFalse(r["latent_expectation_coverage_claimed"])

    def test_incomplete_model_comparison_is_not_counted_and_days_are_berlin(self):
        prediction={"prediction_id":"p","mode":"walk","models":{m:{"category_probabilities":[1/7]*7} for m in ("static","basic","contextual")}}
        record={"search_id":"r","d_day_forecast":{"result":{"targets":{"affect":{"predictions":[prediction]}}}},
            "journeys":[{"journey_id":"j","experience":{"event_id":"e","actual_modes":["walk"],"confirmed_at":"2026-10-10T23:30:00Z"}}]}
        score={"target":"affect","mode":"walk","raw_response":0,"log_score":-1.9,"ranked_probability_score":.1,"normalized_absolute_error":0}
        evaluation={"event_id":"e","status":"scored","prediction_id":"p","scores":[{**score,"model":m} for m in ("static","basic","contextual")]}
        with patch("comfort_pilot.pilot_review.report",return_value={"evaluations":[evaluation]}):
            r=review({"simulations":[record]})
            self.assertEqual(len(r["matched_scores"]),3)
            self.assertEqual(r["temporal_daily_blocks"][0]["confirmation_calendar_date"],"2026-10-11")
            evaluation["scores"].pop()
            self.assertEqual(review({"simulations":[record]})["matched_scores"],[])

    def test_weather_versions_are_not_pooled_in_metrics_or_daily_blocks(self):
        from copy import deepcopy
        prediction = {'prediction_id': 'p', 'mode': 'walk', 'models': {
            m: {'category_probabilities': [1/7]*7} for m in ('static', 'basic', 'contextual')}}
        base = {'search_id': 'r0', 'd_day_forecast': {'result': {'targets': {'affect': {'predictions': [prediction]}}}},
                'journeys': [{'journey_id': 'j0', 'experience': {'event_id': 'e0', 'actual_modes': ['walk'], 'confirmed_at': '2026-10-11T10:00:00Z'}}]}
        new = deepcopy(base); new.update(search_id='r1'); new['journeys'][0]['journey_id'] = 'j1'; new['journeys'][0]['experience']['event_id'] = 'e1'
        rows = []
        for i in range(2):
            scores = [{'target': 'affect', 'mode': 'walk', 'model': m, 'raw_response': 0, 'log_score': -1-i,
                       'ranked_probability_score': .1, 'normalized_absolute_error': 0,
                       **({'weather_model_version': 'd-day-weather-intensity-v2'} if i else {})}
                      for m in ('static', 'basic', 'contextual')]
            rows.append({'event_id': 'e'+str(i), 'status': 'scored', 'prediction_id': 'p', 'scores': scores})
        with patch('comfort_pilot.pilot_review.report', return_value={'evaluations': rows}):
            result = review({'simulations': [base, new]})
        self.assertEqual(len(result['group_metrics']), 6)
        self.assertEqual(len(result['temporal_daily_blocks']), 2)
        self.assertTrue(all(g['matched_event_count'] == 1 for g in result['group_metrics']))
        self.assertEqual({g['log_score'] for g in result['group_metrics']}, {-1, -2})


if __name__=="__main__":unittest.main()
