import copy
import re
import unittest
from flask import Flask
from unittest.mock import patch
from companion_reflection import reflection_catalog, reflect, register, REQUEST
from choice_groq_narrator import validate_selection
from comfort_pilot.learned_passport import identity
from comfort_pilot.eligibility import VERSION
from input_mapping_v4_3 import NEEDS
from hotco_ct_v4_3 import HOTCOCTParameters


def fixture():
    p={'schema_version':'2.0','agent_id':'person-one','topology':{'cognitive_scaling':'R / 11'},
       'lineage':{'passport_id':'original','revision':1},
       'profile':{'needs':{k:float(k=='physical') for k in NEEDS},
                  'beliefs':{m:{k:float(k=='physical') for k in NEEDS} for m in ('walk','bike','pt','car')}}}
    params=HOTCOCTParameters(); scale=params.cognitive_gain*params.need_to_action_gain/len(NEEDS)
    e={'event_id':'experience-one','confirmed_at':'2026-10-12T08:00:00Z','completion':'as_planned','actual_modes':['bike'],
       'affect_scale_version':'mode-affect-ordinal-minus3-to3-v1','mode_valences_raw_minus3_to_3':{'bike':-2},
       'comfort_observation':{'question_version':'comfort-physical-bipolar-v1','scale':'helps-hinders-1-to-7','attribution':'actual_mode','mode':'bike','rating':6},
       'need_satisfaction_raw_1_to_7':{'physical':6}}
    hotco={'node_activations':{'needs':{'final':{k:float(k=='physical') for k in NEEDS}}},
           'process_diagnostics':{'support_dynamics':{'by_mode':{'bike':{'available':True,'cognitive_signed_input':{'terminal':scale},'affective_signed_input':{'terminal':-.2}}}}}}
    row={'search_id':'search-one','passport':p,'journey':{'journey_id':'journey-one','status':'confirmed','selection':{'mode_key':'bike'},'experience':e},'hotco':hotco}
    return {'schema_version':REQUEST,'language':'en','passport':p,'records':[row],'history_reviewed':True,
            'evidence_exclusions':{'schema_version':VERSION,'exclusions':[]},'learned':None}


class CompanionReflectionTests(unittest.TestCase):
    def test_reports_and_expectations_are_distinct(self):
        prompt,evidence=reflection_catalog(fixture())
        rules={s['rule'] for s in prompt['statements']}
        self.assertTrue({'REPORTED_AFFECT','REPORTED_COMFORT','REPORTED_GOAL','EXPECTED_GOAL','EXPECTED_TENSION','BOUNDARY'}<=rules)
        self.assertEqual(evidence['affect']['bike']['negative'],1)
        self.assertEqual(evidence['comfort']['bike']['positive'],1)
        self.assertTrue(all(not re.search(r'\b(model|modell|hotco|passport|backend)\b',s['options'][0],re.I) for s in prompt['statements']))

    def test_missing_review_does_not_turn_stationary_history_into_patterns(self):
        data=fixture();data['evidence_exclusions']=None;data['history_reviewed']=False
        prompt,evidence=reflection_catalog(data)
        self.assertEqual(evidence['ledger'],[])
        self.assertIn('UNREVIEWED',[s['rule'] for s in prompt['statements']])
        self.assertNotIn('EXPECTED_GOAL',[s['rule'] for s in prompt['statements']])

    def test_explicit_test_exclusion(self):
        data=fixture();data['records'][0]['journey']['evidence_classification']='stationary_test'
        self.assertEqual(reflection_catalog(data)[1]['ledger'],[])

    def test_target_exclusion_keeps_other_target_separate(self):
        data=fixture();data['evidence_exclusions']['exclusions']=[{'experience_event_id':'experience-one','reason':'comfort_not_comparable','targets':['comfort']}]
        _,e=reflection_catalog(data)
        self.assertEqual(e['affect']['bike']['negative'],1)
        self.assertEqual(sum(e['comfort']['bike'].values()),0)

    def test_not_taken_and_cancelled_are_excluded(self):
        for change in ('not_taken','cancelled'):
            data=fixture()
            if change=='not_taken':data['records'][0]['journey']['experience']['completion']=change
            else:data['records'][0]['journey']['status']=change
            self.assertEqual(reflection_catalog(data)[1]['ledger'],[])

    def test_neutral_is_a_response_absence_is_not(self):
        data=fixture();e=data['records'][0]['journey']['experience'];e['mode_valences_raw_minus3_to_3']['bike']=0
        self.assertEqual(reflection_catalog(data)[1]['affect']['bike']['neutral'],1)
        e['mode_valences_raw_minus3_to_3']={}
        self.assertEqual(sum(reflection_catalog(data)[1]['affect']['bike'].values()),0)

    def test_duplicate_and_conflicting_evidence(self):
        data=fixture();data['records'].append(copy.deepcopy(data['records'][0]))
        self.assertEqual(len(reflection_catalog(data)[1]['ledger']),1)
        data['records'][1]['journey']['experience']['mode_valences_raw_minus3_to_3']['bike']=3
        with self.assertRaises(ValueError):reflection_catalog(data)

    def test_foreign_subject_rejected(self):
        data=fixture();data['records'][0]['passport']=copy.deepcopy(data['passport']);data['records'][0]['passport']['agent_id']='another-person'
        with self.assertRaises(ValueError):reflection_catalog(data)

    def test_multimodal_changed_trip_does_not_inherit_bike_comparison(self):
        data=fixture();e=data['records'][0]['journey']['experience'];e['completion']='changed';e['actual_modes']=['walk','pt'];e['mode_valences_raw_minus3_to_3']={'walk':1,'pt':0};e['comfort_observation']['mode']='pt'
        _,evidence=reflection_catalog(data)
        self.assertEqual(evidence['comparison_counts']['bike'],0)
        self.assertEqual(evidence['affect']['walk']['positive'],1)

    def test_invalid_saved_algebra_keeps_reports_but_excludes_expectations(self):
        data=fixture();data['records'][0]['hotco']['process_diagnostics']['support_dynamics']['by_mode']['bike']['cognitive_signed_input']['terminal']=999
        _,e=reflection_catalog(data)
        self.assertEqual(len(e['ledger']),1);self.assertEqual(e['comparison_counts']['bike'],0)

    def test_valid_llm_selection_and_failure_are_truthful(self):
        def provider(prompt):
            self.assertNotIn('person-one',str(prompt));self.assertNotIn('search-one',str(prompt))
            return {'opening':1,'tone':'reflective','question':'none','emphasis':'tensions','variants':{s['id']:0 for s in prompt['statements']}}
        result=reflect(fixture(),provider=provider)
        self.assertTrue(result['narration']['generated_by_llm'])
        self.assertEqual(result['reflection_id'],identity({k:v for k,v in result.items() if k!='reflection_id'}))
        broken=reflect(fixture(),provider=lambda p:{'invented':'You love driving'})
        self.assertFalse(broken['narration']['generated_by_llm']);self.assertEqual(broken['narration']['reason'],'PROVIDER_OR_VALIDATION_FAILURE')

    def test_nullable_sql_projection_and_german(self):
        data=fixture();data['language']='de';data['records'][0]['journey']['experience']['need_satisfaction_raw_1_to_7']=None
        self.assertEqual(reflect(data,provider=lambda p: (_ for _ in ()).throw(ValueError()))['language'],'de')

    def test_endpoint_rejects_unbounded_or_unknown_evidence(self):
        app=Flask(__name__);register(app);client=app.test_client()
        data=fixture();data['records']*=61
        self.assertEqual(client.post('/api/dyconet/companion-reflection',json=data).status_code,422)
        self.assertEqual(client.post('/api/dyconet/companion-reflection',json={'schema_version':'old'}).status_code,422)


if __name__=='__main__':unittest.main()
