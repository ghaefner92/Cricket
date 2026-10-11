import json
from copy import deepcopy
from pathlib import Path
import tempfile
import unittest
from flask import Flask
from test_prospective import fixture
from comfort_pilot.learned_passport import build,predict,validate_version,validate_storage,Archive,REQUEST,PREFIX,register,identity


def request_fixture():
    d=fixture();d.pop('search_id');d.pop('candidates');d['schema_version']=REQUEST
    d['as_of']='2026-10-09T12:00:00Z'
    for record in d['records']:
        record.update(original_passport_id=d['initial_passport']['lineage']['passport_id'],subject_id=d['initial_passport']['agent_id'])
    return d


def second(d,rating=1):
    d=deepcopy(d);j=deepcopy(d['records'][0]['journeys'][0])
    j.update(journey_id='j2',choice_event_id='c2')
    j['experience'].update(event_id='e2',confirmed_at='2026-10-09T11:00:00Z')
    j['experience']['comfort_observation']['rating']=rating
    j['experience']['mode_valences_raw_minus3_to_3']['walk']=-3
    j['experience']['mode_valences_normalized_minus1_to_1']['walk']=-1
    d['records'].append({'completed_at':'2026-10-09T10:30:00Z','journeys':[j],
                        'original_passport_id':d['initial_passport']['lineage']['passport_id'],'subject_id':d['initial_passport']['agent_id']})
    return d


class LearnedPassportTest(unittest.TestCase):
    def test_semantic_digest_preserves_types_unicode_and_numeric_roundtrip(self):
        self.assertEqual(identity({'lluvia ☔':[1.0,-0.0]}),identity({'lluvia ☔':[1,0]}))
        self.assertNotEqual(identity(True),identity(1))
        for bad in (float('nan'),float('inf'),2**53):
            with self.assertRaises(ValueError):identity(bad)
    @classmethod
    def setUpClass(cls):
        cls.data=request_fixture();cls.result=build(cls.data);cls.v1=cls.result['new_versions'][0]
        cls.next=second(cls.data);cls.next['previous_version']=cls.v1
        cls.result2=build(cls.next);cls.v2=cls.result2['new_versions'][0]

    def test_original_immutable_targets_separate_and_no_activation(self):
        d=request_fixture();before=deepcopy(d);r=build(d)
        self.assertEqual(d,before);self.assertEqual(r,self.result)
        v=self.v1;validate_version(v)
        self.assertEqual(v['parent_passport_id'],d['initial_passport']['lineage']['passport_id'])
        self.assertEqual(v['revision'],1)
        self.assertEqual(len(v['evidence_ledger']),2)
        self.assertFalse(r['learning_applied_to_active_passport']);self.assertFalse(r['hotco_modified'])
        self.assertNotEqual(v['targets']['comfort']['walk']['basic']['prior']['mean'],
                            v['targets']['affect']['walk']['basic']['prior']['mean'])

    def test_sequential_equals_original_prior_reconstruction_and_once_per_event(self):
        r=build(second(self.data))
        self.assertEqual(r['new_versions'],[self.v1,self.v2])
        self.assertEqual(self.v2['parent_passport_id'],self.v1['passport_id'])
        self.assertEqual(self.v2['revision'],2)
        self.assertEqual(self.v2['targets']['comfort']['walk']['basic']['event_ids'],['e-old','e2'])
        retry=deepcopy(self.next);retry['previous_version']=self.v2
        self.assertEqual(build(retry)['new_versions'],[])
        with self.assertRaises(ValueError):build(second(self.data,7)|{'previous_version':self.v2})

    def test_exclusions_missing_and_future_never_enter_as_neutral(self):
        d=second(self.data);d['records'][1]['journeys'][0]['evidence_classification']='stationary_test'
        r=build(d);self.assertEqual(r['new_versions'],[self.v1])
        self.assertTrue(all(x['reason']=='explicit_stationary_test' for x in r['excluded_evidence']['comfort']))
        d=second(self.data);d['as_of']='2026-10-09T10:15:00Z'
        self.assertEqual(build(d)['new_versions'],[self.v1])
        d=request_fixture();e=d['records'][0]['journeys'][0]['experience'];e.pop('affect_scale_version')
        self.assertEqual(build(d)['new_versions'][0]['targets']['affect'],{})
        d=request_fixture();d['records'][0]['journeys']*=2
        with self.assertRaises(ValueError):build(d)

    def test_contextual_prediction_fallback_and_no_future_evidence(self):
        c={'queried_at':'2026-10-09T13:00:00Z','measurements':[]}
        p=predict(self.v2,'affect','walk',c,'2026-10-09T13:00:00Z')
        self.assertEqual(p['model'],'basic');self.assertIn('normalized_valence_interval_90',p['prediction'])
        self.assertFalse(p['activation_applied'])
        self.assertEqual(predict(self.v2,'comfort','car',c,'2026-10-09T13:00:00Z')['status'],'original_prior_required')
        with self.assertRaises(ValueError):predict(self.v2,'comfort','walk',c,self.v2['evidence_cutoff'])

    def test_immutable_samples_and_android_integral_number_roundtrip(self):
        v=deepcopy(self.v1);v['revision']=9
        with self.assertRaises(ValueError):validate_version(v)
        def android(value):
            if isinstance(value,dict):return {k:android(v) for k,v in value.items()}
            if isinstance(value,list):return [android(v) for v in value]
            return int(value) if type(value)is float and value.is_integer() else value
        validate_version(android(self.v1))
        v=deepcopy(self.v1);v['targets']['comfort']['walk']['basic']['samples']['digest']='wrong'
        body={k:x for k,x in v.items() if k not in ('passport_id','content_digest')}
        v['content_digest']=identity(body);v['passport_id']='learned-'+v['content_digest']
        with self.assertRaises(ValueError):validate_version(v)

    def test_durable_atomic_archive_retry_reproduction_and_reversible_selection(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'versions.sqlite';archive=Archive(path)
            archive.commit(self.data,self.result);archive.commit(self.data,self.result)
            root=self.result['original_passport_id'];archive.select(root,self.v1['passport_id'])
            archive.commit(self.next,self.result2)
            self.assertEqual(archive.state(root),(self.v2['passport_id'],self.v1['passport_id']))
            archive.select(root,self.v2['passport_id']);archive.select(root,self.v1['passport_id'])
            self.assertIsNotNone(archive.version(self.v2['passport_id']))
            self.assertEqual(archive.reproduce(second(self.data),self.v2)['new_versions'][-1],self.v2)
            archive.close();archive=Archive(path)
            self.assertEqual(archive.state(root)[1],self.v1['passport_id'])
            self.assertEqual(archive.reproduce_archived(self.v2['passport_id'])['new_versions'][-1],self.v2)
            bad=deepcopy(self.result2);bad['request_digest']=identity(self.next|{'as_of':'2026-10-09T14:00:00Z'})
            with self.assertRaises(ValueError):archive.commit(self.next|{'as_of':'2026-10-09T14:00:00Z'},bad)
            archive.close()

    def test_failed_commit_rolls_back_all_archives_and_requests(self):
        with tempfile.TemporaryDirectory() as folder:
            archive=Archive(Path(folder)/'atomic.sqlite')
            bad=deepcopy(self.result);bad['tip_passport_id']='wrong'
            with self.assertRaises(ValueError):archive.commit(self.data,bad)
            self.assertIsNone(archive.version(self.v1['passport_id']))
            self.assertEqual(archive.db.execute('SELECT count(*) FROM requests').fetchone()[0],0)
            archive.commit(self.data,self.result);archive.close()

    def test_backup_graph_requires_original_ancestors_and_correct_pointers(self):
        root=self.result['original_passport_id']
        values={PREFIX+'.version.'+v['passport_id']:json.dumps(v) for v in (self.v1,self.v2)}
        values['imiq.experimental.cognitive-passport.v1.archive.'+root]=json.dumps({'response':{'cognitive_passport':self.data['initial_passport']}})
        values[PREFIX+'.tip.'+root]=self.v2['passport_id']
        self.assertEqual(len(validate_storage(values)),2)
        bad=deepcopy(values);bad.pop(PREFIX+'.version.'+self.v1['passport_id'])
        with self.assertRaises(ValueError):validate_storage(bad)
        bad=deepcopy(values);bad[PREFIX+'.review.foreign']=self.v1['passport_id']
        with self.assertRaises(ValueError):validate_storage(bad)
        original=deepcopy(self.data['initial_passport']);original['profile']['valences']['walk']=0
        bad=deepcopy(values);bad['imiq.experimental.cognitive-passport.v1.archive.'+root]=json.dumps({'response':{'cognitive_passport':original}})
        with self.assertRaises(ValueError):validate_storage(bad)

    def test_endpoint_rejects_wrong_root_config_and_mutated_history(self):
        app=Flask(__name__);register(app);client=app.test_client()
        self.assertEqual(client.post('/api/dyconet/d-day/learned-passport/update',json={}).status_code,422)
        d=deepcopy(self.next);d['initial_passport']['agent_id']='other'
        with self.assertRaises(ValueError):build(d)
        d=deepcopy(self.next);d['records'][0]['journeys'][0]['experience']['completion']='not_taken'
        with self.assertRaises(ValueError):build(d)
        r=client.post('/api/dyconet/d-day/learned-passport/update',json=self.data)
        self.assertEqual(r.status_code,200)
        self.assertEqual(r.get_json()['new_versions'][0],self.v1)


if __name__=='__main__':unittest.main()
