import unittest
from copy import deepcopy
from unittest.mock import patch
from flask import Flask
from test_comfort_pilot import request_fixture
from comfort_pilot.prospective import forecast,register
from comfort_pilot.model import fit
from comfort_pilot.prospective_evaluation import evaluate,validate_record

def fixture():
    p=request_fixture()['initial_passport'];stamp='2026-10-09T10:00:00Z'
    j={'journey_id':'old','choice_event_id':'choice-old','selection':{'mode_key':'walk','chosen_at':stamp},
        'experience':{'event_id':'e-old','completion':'as_planned','confirmed_at':stamp,
            'comfort_observation':{'rating':4,'mode':'walk','question_version':'comfort-physical-bipolar-v1','scale':'helps-hinders-1-to-7'},
            'affect_scale_version':'mode-affect-ordinal-minus3-to3-v1','mode_valences_raw_minus3_to_3':{'walk':0},'mode_valences_normalized_minus1_to_1':{'walk':0}},
        'plan':{'route':{'summary':{'duration_seconds':600}},'context_at_selection':{'snapshot':None}}}
    return {'schema_version':'d-day-prospective-request-v1','search_id':'search','as_of':'2026-10-09T10:01:00Z','initial_passport':p,
        'records':[{'completed_at':stamp,'journeys':[j]}],
        'candidates':[{'prediction_id':'1-walk','mode':'walk','route':{'summary':{'duration_seconds':600}},'snapshot':None}]}

class ProspectiveTest(unittest.TestCase):
    def test_forecasts_both_targets_with_static_and_no_mutation(self):
        d=fixture();before=deepcopy(d);r=forecast(d);self.assertEqual(d,before)
        for target in ('comfort','affect'):
            p=r['targets'][target]['predictions'][0]
            self.assertEqual(set(p['models']),{'static','basic','contextual'})
            self.assertEqual(p['models']['basic']['observation_count'],1)
            self.assertEqual(p['context_audit']['schema_version'],'d-day-context-audit-v1')
            self.assertFalse(p['context_audit']['actual_trip_exposure_reconstructed'])
        self.assertFalse(r['hotco_modified']);self.assertFalse(r['learning_applied_to_active_passport'])

    def test_future_evidence_excluded_and_duplicate_candidates_rejected(self):
        d=fixture();d['records'][0]['journeys'][0]['experience']['confirmed_at']='2026-10-09T10:02:00Z'
        self.assertEqual(forecast(d)['targets']['affect']['predictions'][0]['models']['basic']['observation_count'],0)
        d['candidates']*=2
        with self.assertRaises(ValueError):forecast(d)

    def test_same_mode_alternatives_reuse_fit_but_predict_their_context(self):
        d=fixture();other=deepcopy(d['candidates'][0]);other['prediction_id']='2-walk';other['route']['summary']['duration_seconds']=3600;d['candidates'].append(other)
        with patch('comfort_pilot.api.fit',wraps=fit) as sampled:
            r=forecast(d);self.assertEqual(sampled.call_count,4)
        ps=r['targets']['comfort']['predictions']
        self.assertNotEqual(ps[0]['context_values']['duration_seconds'],ps[1]['context_values']['duration_seconds'])

    def test_live_contract_and_evaluation_tampering(self):
        app=Flask(__name__);register(app);client=app.test_client()
        self.assertEqual(client.post('/api/dyconet/d-day/prospective/forecast',json={}).status_code,422)
        d=fixture();r=client.post('/api/dyconet/d-day/prospective/forecast',json=d).get_json()
        j=deepcopy(d['records'][0]['journeys'][0]);j['selection'].update(chosen_at='2026-10-09T10:03:00Z',routing_rank=1)
        record={'search_id':'search','journeys':[j],'d_day_forecast':{'status':'complete','recorded_at':'2026-10-09T10:02:00Z','request':d,'result':r}}
        e=evaluate(record,j);self.assertEqual(e['status'],'scored');self.assertEqual(len(e['scores']),6)
        record['d_day_evaluations']=[e];validate_record(record)
        record['d_day_evaluations'][0]['scores'][0]['log_score']=123
        with self.assertRaises(ValueError):validate_record(record)

if __name__=='__main__':unittest.main()
