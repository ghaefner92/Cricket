"""Synthetic lifecycle proof and shared Android/backend contract fixture."""
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'app-backend/services/dyconet'))
from comfort_pilot.experiment import passport_fixture
from comfort_pilot.learned_passport import build,predict,Archive,REQUEST,PREFIX,validate_storage,identity
from comfort_pilot.temporal_intensity_experiment import frozen_context


def fixture():
    original=passport_fixture(0);records=[]
    for i,(comfort,affect) in enumerate(((6,2),(2,-2))):
        instant=datetime(2026,10,9+i,10,tzinfo=timezone.utc)
        context=frozen_context({'temperature':18,'humidity':60,'rainIntensity':i*6,
                               'windSpeed':3,'lightIntensity':3000,'duration_seconds':600},instant)
        # Preserve NGSI-shaped raw fixture in the same selection format as the app.
        readings=context['measurements']
        snapshot={'route_context':{'segments':[{'raw_observations':readings}]}}
        stamp=instant.isoformat();confirmed=(instant+timedelta(minutes=10)).isoformat()
        j={'journey_id':f'synthetic-j{i}','choice_event_id':f'synthetic-c{i}',
           'selection':{'mode_key':'walk','chosen_at':stamp},
           'experience':{'event_id':f'synthetic-e{i}','completion':'as_planned','confirmed_at':confirmed,
             'comfort_observation':{'rating':comfort,'mode':'walk','question_version':'comfort-physical-bipolar-v1','scale':'helps-hinders-1-to-7'},
             'affect_scale_version':'mode-affect-ordinal-minus3-to3-v1','mode_valences_raw_minus3_to_3':{'walk':affect},
             'mode_valences_normalized_minus1_to_1':{'walk':affect/3}},
           'plan':{'route':{'summary':{'duration_seconds':600}},'context_at_selection':{'snapshot':snapshot}}}
        records.append({'completed_at':stamp,'journeys':[j],'original_passport_id':original['lineage']['passport_id'],'subject_id':original['agent_id']})
    return {'schema_version':REQUEST,'initial_passport':original,'records':records,
            'as_of':records[-1]['journeys'][0]['experience']['confirmed_at']}


def run():
    out=ROOT/'output/d-day-learned-passport';out.mkdir(parents=True,exist_ok=True)
    d=fixture();first=deepcopy(d);first['records']=first['records'][:1]
    first['as_of']=first['records'][0]['journeys'][0]['experience']['confirmed_at']
    r1=build(first);v1=r1['new_versions'][0]
    next_request=deepcopy(d);next_request['previous_version']=v1
    r2=build(next_request);v2=r2['new_versions'][0]
    complete=build(d);assert complete['new_versions']==[v1,v2]
    retry=deepcopy(d);retry['previous_version']=v2;assert build(retry)['new_versions']==[]
    excluded=deepcopy(d);excluded['records'][1]['journeys'][0]['evidence_classification']='stationary_test'
    assert build(excluded)['new_versions']==[v1]
    root=r1['original_passport_id'];database=out/('archive-'+identity(d)[:12]+'.sqlite');archive=Archive(database)
    archive.commit(first,r1);archive.commit(first,r1);archive.commit(next_request,r2)
    archive.select(root,v2['passport_id']);archive.select(root,v1['passport_id'])
    archive.reproduce(d,v2);archive.close()
    archive=Archive(database);assert archive.state(root)==(v2['passport_id'],v1['passport_id'])
    archive.reproduce_archived(v2['passport_id']);archive.close()
    storage={PREFIX+'.version.'+v['passport_id']:json.dumps(v) for v in (v1,v2)}
    storage['imiq.experimental.cognitive-passport.v1.archive.'+root]=json.dumps({'response':{'cognitive_passport':d['initial_passport']}})
    storage[PREFIX+'.tip.'+root]=v2['passport_id'];storage[PREFIX+'.review.'+root]=v1['passport_id']
    validate_storage(storage)
    fixture_data={'dataset_kind':'synthetic_contract_fixture_not_empirical','request':first,'result':r1,
                  'next_request':next_request,'next_result':r2,'storage':storage,
                  'digest_cases':[{'value':value,'digest':identity(value)} for value in
                     ({'α':'lluvia ☔ 😀','numbers':[1,1.0,-0.0,1e-10,1e5,4294967295],'truth':True,'missing':None},
                      {'keys':{'😀':1,'á':2,'a':3},'text':'a\n\t"\\b'})]}
    (ROOT/'validation/fixtures/learned_passport_cases.json').write_text(json.dumps(fixture_data,allow_nan=False)+'\n',encoding='utf-8')
    when='2026-10-11T10:00:00Z'
    context=frozen_context({'temperature':18,'rainIntensity':0},datetime.fromisoformat(when.replace('Z','+00:00')))
    forecasts={t:predict(v2,t,'walk',context,when) for t in ('comfort','affect')}
    (out/'request.json').write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
    (out/'versions.json').write_text(json.dumps(complete,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    (out/'predictions.json').write_text(json.dumps(forecasts,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    proof={'schema_version':'d-day-learned-lifecycle-proof-v1','dataset_kind':'synthetic_not_observed',
           'versions':2,'eligible_experiences':2,'target_observations':len(v2['evidence_ledger']),
           'sequential_equals_full_reconstruction':True,'retry_does_not_duplicate':True,
           'stationary_test_excluded':True,'durable_reopen_passed':True,'reversible_review_selection':True,
           'original_passport_unchanged':d['initial_passport']==first['initial_passport'],
           'live_activation':False,'hotco_modified':False,'empirical_validation':False}
    (out/'lifecycle_proof.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(proof))


if __name__=='__main__':run()
