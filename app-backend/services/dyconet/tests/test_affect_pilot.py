import unittest,json
from copy import deepcopy
from pathlib import Path
from flask import Flask
from test_comfort_pilot import request_fixture
from comfort_pilot.affect import analyze_affect,register_affect
from comfort_pilot.evidence import validate_request,request_from_backup
from journey_contract import validate_journey

def affect_fixture():
    data=request_fixture();data['schema_version']='d-day-affect-pilot-request-v1'
    for o in data['observations']:
        o.pop('rating');o.update(rating_raw_minus3_to_3=-2,valence_normalized=-2/3,
            question_version='mode-affect-ordinal-minus3-to3-v1',scale='minus3-to-3')
    return data

class AffectPilotTest(unittest.TestCase):
    def test_all_seven_values_normalize_without_mutating_passport(self):
        for raw in range(-3,4):
            data=affect_fixture();data['observations'][0].update(rating_raw_minus3_to_3=raw,valence_normalized=raw/3)
            before=deepcopy(data);_,obs,_=validate_request(data,target='affect')
            self.assertEqual(obs[0]['rating'],raw+4);self.assertEqual(obs[0]['valence_normalized'],raw/3);self.assertEqual(data,before)

    def test_affect_uses_initial_valence_not_comfort_and_can_learn_negative(self):
        data=affect_fixture();data['initial_passport']['profile']['valences']['walk']=.5
        data['initial_passport']['profile']['beliefs']['walk']['comfort']=1
        base=data['observations'][0];data['observations']=[dict(deepcopy(base),event_id=f'e{i}',journey_id=f'j{i}',rating_raw_minus3_to_3=-3,valence_normalized=-1) for i in range(30)]
        before=deepcopy(data);result=analyze_affect(data);model=result['predictions'][0]['models']['basic']
        self.assertEqual(model['initial_normalized_valence'],.5);self.assertLess(model['normalized_valence_mean'],0)
        self.assertEqual(model['category_labels_raw_minus3_to_3'],list(range(-3,4)))
        self.assertEqual(model['observation_count'],30);self.assertEqual(data,before)
        self.assertFalse(result['learning_applied_to_active_passport']);self.assertFalse(result['hotco_modified'])

    def test_individual_mode_time_and_duplicate_guards(self):
        data=affect_fixture();o=deepcopy(data['observations'][0]);o['mode']='bike';data['observations'].append(o)
        # Two real modes in the same experience are two distinct observations.
        self.assertEqual(len(validate_request(data,target='affect')[1]),2)
        result=analyze_affect(data);self.assertEqual(result['predictions'][0]['models']['basic']['observation_count'],1)
        data['observations'].append(deepcopy(o))
        with self.assertRaises(ValueError):validate_request(data,target='affect')
        for field,value in (('rating_raw_minus3_to_3',True),('valence_normalized',0),('subject_id','other')):
            data=affect_fixture();data['observations'][0][field]=value
            with self.assertRaises(ValueError):validate_request(data,target='affect')
        data=affect_fixture();data['prediction_contexts'][0]['as_of']='2026-10-09T10:05:00Z'
        self.assertEqual(analyze_affect(data)['predictions'][0]['models']['basic']['observation_count'],0)

    def test_affect_and_comfort_contracts_cannot_be_interchanged(self):
        app=Flask(__name__);register_affect(app);client=app.test_client();path='/api/dyconet/d-day/affect-pilot/analyze'
        self.assertEqual(client.post(path,json=affect_fixture()).status_code,200)
        self.assertEqual(client.post(path,json=request_fixture()).status_code,422)
        data=affect_fixture();data['literature_shape']=True
        self.assertEqual(client.post(path,json=data).status_code,422)

    def test_required_affect_neutral_and_inconsistent_normalization(self):
        case=json.loads((Path(__file__).resolve().parents[4]/'validation/fixtures/journey_cases.json').read_text(encoding='utf-8'))[1]
        record,journey=case['record'],case['journey'];record['affect_pilot_required']=True
        with self.assertRaises(ValueError):validate_journey(record,journey)
        e=journey['experience'];e['mode_valences_raw_minus3_to_3']={'walk':0};e['mode_valences_normalized_minus1_to_1']={'walk':0};e['affect_scale_version']='mode-affect-ordinal-minus3-to3-v1'
        validate_journey(record,journey)
        e['mode_valences_normalized_minus1_to_1']['walk']=.5
        with self.assertRaises(ValueError):validate_journey(record,journey)

if __name__=='__main__':unittest.main()
