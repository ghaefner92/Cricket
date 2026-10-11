import os
os.environ.setdefault('OPENBLAS_NUM_THREADS','1')
import unittest
import json
from pathlib import Path
from copy import deepcopy
import numpy as np
from scipy.integrate import quad
from flask import Flask
from comfort_pilot.model import fit, probabilities, initial_location, ordinal_loglik, diagnostics, design_row, signed_expectation
from comfort_pilot.experiment import passport_fixture
from comfort_pilot.evidence import context_values, validate_request, request_from_backup
from comfort_pilot.api import analyze, register

def request_fixture():
    p=passport_fixture(0)
    context={'queried_at':'2026-10-09T10:00:00Z','measurements':[]}
    o={'event_id':'e1','subject_id':p['agent_id'],'journey_id':'j1','choice_event_id':'c1',
       'mode':'walk','rating':6,'question_version':'comfort-physical-bipolar-v1',
       'scale':'helps-hinders-1-to-7','attribution':'actual_mode','confirmed_at':'2026-10-09T10:10:00Z','context':context}
    return {'schema_version':'d-day-comfort-pilot-request-v1','subject_id':p['agent_id'],'initial_passport':p,
            'observations':[o], 'prediction_contexts':[{'prediction_id':'p1','mode':'walk','as_of':'2026-10-09T11:00:00Z','context':context}], 'draws':1024}

class ComfortPilotTest(unittest.TestCase):
    def test_ordinal_probability_and_stable_likelihood(self):
        np.testing.assert_allclose(probabilities(0),np.ones(7)/7)
        for eta in (-1000,0,1000):
            self.assertTrue(np.isfinite(ordinal_loglik(eta,np.arange(1,8))).all())
        for value in (-.9,0,.9):self.assertAlmostEqual(float(signed_expectation(initial_location(value))),value)

    def test_posterior_matches_independent_quadrature(self):
        # Integrate the full one-dimensional posterior; no optimizer or sampler reference.
        for ratings,initial in (([1,1,2,3],.6),([4]*8,0),([5,6,7]*5,-.3)):
            obs=[{'event_id':str(i),'rating':r,'context':{}} for i,r in enumerate(ratings)]
            posterior=fit(obs,initial,draws=4096,seed=712)
            mu=initial_location(initial);sd=1.2
            def density(t):return np.exp(-.5*((t-mu)/sd)**2+ordinal_loglik(t,np.array(ratings)).sum())
            z=quad(density,-15,15,epsabs=1e-13)[0]
            mean=quad(lambda t:float(signed_expectation(t))*density(t),-15,15,epsabs=1e-13)[0]/z
            self.assertAlmostEqual(posterior.predict({})['signed_belief_mean'],mean,delta=.025)
            self.assertTrue(posterior.diagnostics['passed'])

    def test_diagnostics_detect_disagreement(self):
        rng=np.random.default_rng(1); iid=rng.normal(size=(4,4096,2))
        self.assertTrue(diagnostics(iid)['passed'])
        iid[0]+=4;self.assertFalse(diagnostics(iid)['passed'])
        self.assertFalse(diagnostics(np.ones((4,512,1)))['passed'])

    def test_duplicate_and_invalid_sampler_settings(self):
        o={'event_id':'same','rating':4,'context':{}}
        with self.assertRaises(ValueError):fit([o,o],0)
        for kwargs in ({'cutpoint_scale':True},{'literature_shape':'false'},{'draws':True}):
            with self.assertRaises(ValueError):fit([],0,**kwargs)
        self.assertEqual(len(fit([o],0,draws=128).event_ids),1)

    def test_context_missing_uv_units_future_and_staleness(self):
        stamp='2026-10-09T10:00:00Z'
        def reading(key,value,unit,time=stamp):return {'variable':key,'numeric_value':value,'unit':unit,'timestamp':time,'source':'test','status':'OBSERVED'}
        c={'queried_at':stamp,'measurements':[reading('temperature',18,'C'),reading('uvIndex',7,'1'),reading('rain',2,'unknown'),reading('humidity',50,'%','2026-10-09T09:00:00Z')]}
        values,warnings=context_values(c,stamp)
        self.assertEqual(values['temperature'],18);self.assertIsNone(values['rain']);self.assertIsNone(values['humidity'])
        self.assertTrue(any('UV' in w for w in warnings));self.assertTrue(any('stale' in w for w in warnings))
        c['measurements']=[reading('temperature',18,'C','2026-10-09T10:01:00Z')]
        with self.assertRaises(ValueError):context_values(c,stamp)
        missing=design_row({});zero=design_row({'rain':0})
        self.assertFalse(np.array_equal(missing,zero))

    def test_individual_time_and_mode_attribution_without_mutation(self):
        data=request_fixture();before=deepcopy(data)
        other=deepcopy(data['observations'][0]);other.update(event_id='e2',journey_id='j2',mode='bike',rating=1)
        later=deepcopy(other);later.update(event_id='e3',journey_id='j3',mode='walk',confirmed_at='2026-10-09T12:00:00Z')
        data['observations'] += [other,later];before=deepcopy(data)
        result=analyze(data)
        for model in result['predictions'][0]['models'].values():self.assertEqual(model['event_ids'],['e1'])
        self.assertEqual(data,before);self.assertFalse(result['learning_applied_to_active_passport']);self.assertFalse(result['hotco_modified'])
        data['observations'][0]['subject_id']='another'
        with self.assertRaises(ValueError):validate_request(data)

    def test_readonly_endpoint_rejects_legacy_and_duplicate(self):
        app=Flask(__name__);register(app);client=app.test_client();data=request_fixture()
        self.assertEqual(client.post('/api/dyconet/d-day/comfort-pilot/analyze',json=data).status_code,200)
        data['observations']*=2
        self.assertEqual(client.post('/api/dyconet/d-day/comfort-pilot/analyze',json=data).status_code,422)
        data=request_fixture();data['observations'][0]['scale']='unipolar'
        self.assertEqual(client.post('/api/dyconet/d-day/comfort-pilot/analyze',json=data).status_code,422)

    def test_legacy_backup_is_not_relabelled(self):
        backup=json.loads((Path(__file__).resolve().parents[4]/'validation/fixtures/d_day_backup.json').read_text(encoding='utf-8'))
        # The minimal shared fixture omits agent_id; a real exported Passport has it.
        def subject(value):
            if isinstance(value,dict):
                if value.get('schema_version')=='2.0':value['agent_id']='legacy-fixture'
                for child in value.values():subject(child)
            elif isinstance(value,list):
                for child in value:subject(child)
        subject(backup['simulations'])
        for key,value in backup['storage'].items():
            wrapper=json.loads(value);subject(wrapper);backup['storage'][key]=json.dumps(wrapper)
        before=deepcopy(backup)
        data,excluded=request_from_backup(backup,prediction_contexts=request_fixture()['prediction_contexts'])
        self.assertEqual(data['observations'],[]);self.assertGreater(len(excluded),0);self.assertEqual(backup,before)

    def test_required_contract_and_actual_mode_checked_in_backend(self):
        from journey_contract import validate_journey
        case=json.loads((Path(__file__).resolve().parents[4]/'validation/fixtures/journey_cases.json').read_text(encoding='utf-8'))[1]
        record,journey=case['record'],case['journey'];record['comfort_pilot_required']=True
        with self.assertRaises(ValueError):validate_journey(record,journey)
        observation={'question_version':'comfort-physical-bipolar-v1','scale':'helps-hinders-1-to-7','mode':'walk',
                     'rating':4,'attribution':'actual_mode','language':'en','answered_at':journey['ended_at'],'mode_selection':'single_actual_mode'}
        journey['experience']['comfort_observation']=observation;validate_journey(record,journey)
        observation['mode']='car'
        with self.assertRaises(ValueError):validate_journey(record,journey)

if __name__=='__main__':unittest.main()
