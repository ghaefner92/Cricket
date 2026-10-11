"""Immutable, cumulative learned Passports. Client owns persistence; no activation."""
import argparse
import base64
from copy import deepcopy
import json
from pathlib import Path
import platform
import sqlite3
import struct
from hashlib import sha256
import zlib
import numpy as np
import scipy
from .model import fit, Posterior, MODES
from .weather_contract import INTENSITY, specification
from .evidence import observations_from_records, validate_request
from journey_contract import timestamp

SCHEMA = 'd-day-learned-passport-v1'
REQUEST = 'd-day-learned-passport-request-v1'
RESULT = 'd-day-learned-passport-result-v1'
CONFIG = {'model_version':'d-day-cumulative-ordinal-intensity-v2', 'weather_model_version':INTENSITY,
          'prior_profile':'moderate','draws':512,'warmup':256,'seed':20261011,
          'cutpoint_scale':1.0,'diffusion':0.0,'literature_shape':False,
          'update_strategy':'original_prior_plus_unique_full_history',
          'empirically_validated':False}
PREFIX = 'imiq.experimental.learned-passport.v1'


def identity(value):
    # Cross-language semantic digest: UTF-8 strings and binary64 numeric values.
    # Avoid Python/JSONObject decimal/exponent/Unicode escaping differences.
    def encoded(v):
        if isinstance(v,dict):return '{'+','.join(encoded(k)+':'+encoded(v[k]) for k in sorted(v,key=lambda k:k.encode('utf-8').hex()))+'}'
        if isinstance(v,(list,tuple)):return '['+','.join(encoded(x) for x in v)+']'
        if isinstance(v,str):return 's'+v.encode('utf-8').hex()
        if v is None:return 'null'
        if type(v)is bool:return 'true' if v else 'false'
        if type(v) in (int,float):
            if type(v)is int and abs(v)>2**53-1:raise ValueError('Integer exceeds exact cross-language digest domain')
            number=float(v)
            if not np.isfinite(number):raise ValueError('Nonfinite immutable value')
            return 'n'+struct.pack('>d',0.0 if number==0 else number).hex()
        raise ValueError('Unsupported immutable value')
    return sha256(('d-day-typed-utf8-binary64-v1|'+encoded(value)).encode('ascii')).hexdigest()


def pack(posterior, seed, attempts):
    raw=np.asarray(posterior.chains,dtype='<f8').tobytes()
    return {'contextual':posterior.contextual,'cutpoint_scale':posterior.cutpoint_scale,
            'weather_model_version':posterior.weather_model_version,'prior':posterior.prior,
            'event_ids':list(posterior.event_ids),'diagnostics':posterior.diagnostics,
            'seed':seed,'sampling_attempts':attempts,
            'samples':{'codec':'zlib-base64-float64-le-v1','shape':list(posterior.chains.shape),
                       'digest':identity({'bytes_base64':base64.b64encode(raw).decode('ascii')}),
                       'data':base64.b64encode(zlib.compress(raw)).decode('ascii')}}


def unpack(model):
    blob=model['samples'];shape=blob['shape']
    if (blob['codec']!='zlib-base64-float64-le-v1' or len(shape)!=3 or shape[0]!=4
        or type(shape[1]) is not int or not 128<=shape[1]<=4096
        or type(shape[2]) is not int or shape[2]!=(18 if model['contextual'] else 1)):
        raise ValueError('Invalid archived posterior shape')
    size=int(np.prod(shape))*8
    decoder=zlib.decompressobj()
    raw=decoder.decompress(base64.b64decode(blob['data'],validate=True),size+1)
    if len(raw)!=size or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
        raise ValueError('Invalid or oversized archived posterior')
    if identity({'bytes_base64':base64.b64encode(raw).decode('ascii')})!=blob['digest']:
        raise ValueError('Archived posterior digest mismatch')
    chains=np.frombuffer(raw,dtype='<f8').reshape(shape)
    if not np.isfinite(chains).all():raise ValueError('Invalid archived posterior values')
    return Posterior(chains,model['contextual'],model['cutpoint_scale'],model['diagnostics'],
                     model['prior'],tuple(model['event_ids']),model['weather_model_version'])


def validate_version(version):
    if version.get('schema_version')!=SCHEMA:raise ValueError('Unknown learned Passport')
    body={k:v for k,v in version.items() if k not in ('passport_id','content_digest')}
    if version.get('digest_encoding')!='d-day-typed-utf8-binary64-v1':raise ValueError('Unknown digest encoding')
    digest=identity(body)
    if version['content_digest']!=digest or version['passport_id']!='learned-'+digest:
        raise ValueError('Immutable learned Passport was modified')
    if version['activation']!={'applied':False,'hotco_modified':False,'state_carryover_applied':False}:
        raise ValueError('Learned Passport cannot activate HOTCO')
    ledger=version['evidence_ledger']
    if version['configuration']!=CONFIG or set(version['targets'])!={'comfort','affect'}:
        raise ValueError('Invalid learned model configuration or targets')
    if type(version['revision']) is not int or version['revision']<1:raise ValueError('Invalid learned revision')
    timestamp(version['evidence_cutoff'])
    keys=[(o['target'],o['event_id'],o['mode']) for o in ledger]
    if len(set(keys))!=len(keys):raise ValueError('Duplicate learned evidence')
    for target,modes in version['targets'].items():
        if target not in ('comfort','affect'):raise ValueError('Unknown target')
        for mode,models in modes.items():
            if mode not in MODES:raise ValueError('Unknown mode')
            if set(models)!={'basic','contextual'}:raise ValueError('Missing model')
            ids=[o['event_id'] for o in ledger if o['target']==target and o['mode']==mode]
            for name,model in models.items():
                if (model['contextual']!=(name=='contextual') or model['weather_model_version']!=INTENSITY
                    or model['cutpoint_scale']!=CONFIG['cutpoint_scale'] or model['event_ids']!=ids):raise ValueError('Model/evidence mismatch')
                unpack(model)
    return version


def validate_storage(storage):
    """Validate learned archives without altering the historical longitudinal index."""
    versions={}
    for key,value in storage.items():
        if key.startswith(PREFIX+'.version.'):
            v=validate_version(json.loads(value));identifier=v['passport_id']
            if key!=PREFIX+'.version.'+identifier:raise ValueError('Wrong learned archive key')
            versions[identifier]=v
    for v in versions.values():
        parent=v['parent_passport_id'];root=v['original_passport_id']
        wrapper=json.loads(storage.get(PASSPORT_KEY+'.archive.'+root,storage.get(PASSPORT_KEY,'{}')))
        original=wrapper.get('response',{}).get('cognitive_passport')
        if (not original or original.get('lineage',{}).get('passport_id')!=root
            or identity(original)!=v['original_passport_digest'] or original['agent_id']!=v['subject_id']):
            raise ValueError('Missing or inconsistent original learned Passport')
        if parent==root:
            if v['revision']!=1:raise ValueError('Invalid first learned revision')
        else:
            p=versions.get(parent)
            if not p or p['original_passport_id']!=root or v['revision']!=p['revision']+1:
                raise ValueError('Missing learned ancestor')
            if v['evidence_ledger'][:len(p['evidence_ledger'])]!=p['evidence_ledger']:
                raise ValueError('Learned evidence overwritten')
    for key,value in storage.items():
        for kind in ('tip','review'):
            if key.startswith(PREFIX+'.'+kind+'.'):
                root=key[len(PREFIX+'.'+kind+'.'):]
                if kind=='review' and value==root:
                    if PASSPORT_KEY+'.archive.'+root not in storage:
                        wrapper=json.loads(storage.get(PASSPORT_KEY,'{}'))
                        if wrapper.get('response',{}).get('cognitive_passport',{}).get('lineage',{}).get('passport_id')!=root:
                            raise ValueError('Missing declared Passport for review')
                elif value not in versions or versions[value]['original_passport_id']!=root:
                    raise ValueError('Missing selected learned Passport')
        if key.startswith(PREFIX+'.job.'):
            job=json.loads(value)
            if (job['schema_version']!='d-day-learned-job-v1' or key!=PREFIX+'.job.'+job['event_id']
                or job['status'] not in ('queued','pending','failed','complete')):raise ValueError('Invalid durable learning job')
            timestamp(job['as_of'])
            if job['status']=='complete':
                result=job['result']
                if result['request_digest']!=identity(job['request']):raise ValueError('Learning job digest mismatch')
                for v in result['new_versions']:
                    if versions.get(v['passport_id'])!=v:raise ValueError('Learning job archive mismatch')
    return versions


PASSPORT_KEY='imiq.experimental.cognitive-passport.v1'


def _observations(data):
    passport=data['initial_passport'];subject=passport['agent_id'];all_obs=[];excluded={}
    for target in ('comfort','affect'):
        obs,excluded[target]=observations_from_records(data['records'],subject=subject,target=target,
            exclusions=data.get('evidence_exclusions'),weather_model_version=INTENSITY)
        future=[o for o in obs if timestamp(o['confirmed_at'])>timestamp(data['as_of'])]
        excluded[target]+=[{'event_id':o['event_id'],'reason':'after_request_cutoff'} for o in future]
        obs=[o for o in obs if timestamp(o['confirmed_at'])<=timestamp(data['as_of'])]
        _,checked,_=validate_request({'schema_version':f'd-day-{target}-pilot-request-v1',
            'subject_id':subject,'initial_passport':passport,'observations':obs,
            'prediction_contexts':[],'weather_model_version':INTENSITY},target=target)
        all_obs += [dict(o,target=target) for o in checked]
    all_obs.sort(key=lambda o:(timestamp(o['confirmed_at']),o['event_id'],o['target'],o['mode']))
    return all_obs,excluded


def build(data):
    allowed={'schema_version','initial_passport','records','as_of','previous_version','evidence_exclusions'}
    if not isinstance(data,dict) or data.get('schema_version')!=REQUEST or set(data)-allowed:
        raise ValueError('Invalid learned Passport request')
    timestamp(data['as_of'])
    if not isinstance(data['records'],list) or len(data['records'])>512:raise ValueError('Use bounded histories')
    original=deepcopy(data['initial_passport']);root=original['lineage']['passport_id']
    if any(r.get('original_passport_id')!=root or r.get('subject_id')!=original['agent_id'] for r in data['records']):
        raise ValueError('Evidence belongs to another or unidentified Passport lineage')
    ledger,excluded=_observations(data)
    previous=deepcopy(data.get('previous_version'))
    if previous:
        validate_version(previous)
        if previous['original_passport_digest']!=identity(original) or previous['configuration']!=CONFIG:
            raise ValueError('Different original Passport or model configuration; start separate lineage')
        old=previous['evidence_ledger']
        # Corrections/exclusion changes/late insertions require an explicit separate reconstruction.
        if ledger[:len(old)]!=old:raise ValueError('Evidence prefix changed; reconstruct a separate lineage explicitly')
    else:old=[]
    additions=ledger[len(old):]
    groups=[]
    for o in additions:
        if not groups or groups[-1][0]['event_id']!=o['event_id']:groups.append([])
        groups[-1].append(o)
    # An event may supply both targets/multiple modes, but produces one version.
    if len({g[0]['event_id'] for g in groups})!=len(groups):raise ValueError('Inconsistent event confirmation times')
    versions=[];used=deepcopy(old)
    for group in groups:
        if previous and group[0]['event_id'] in {o['event_id'] for o in previous['evidence_ledger']}:
            raise ValueError('Existing Experience cannot acquire different answers')
        used+=group
        targets=deepcopy(previous['targets']) if previous else {'comfort':{},'affect':{}}
        for target,mode in sorted({(o['target'],o['mode']) for o in group}):
            evidence=[o for o in used if o['target']==target and o['mode']==mode]
            initial=original['profile']['valences'][mode] if target=='affect' else original['profile']['beliefs'][mode]['comfort']
            declared=mode+'__comfort' in original.get('observed_measurements',{}).get('beliefs_raw_1_to_7',{})
            provenance='declared' if target=='affect' or declared else 'ridge_estimated'
            models={}
            for contextual in (False,True):
                seed=(CONFIG['seed']+int(identity({'target':target,'mode':mode,'evidence':evidence,
                      'contextual':contextual})[:8],16))%2**32
                options={k:CONFIG[k] for k in ('prior_profile','draws','warmup','cutpoint_scale','literature_shape','weather_model_version')}
                posterior=fit(evidence,initial,contextual=contextual,provenance=provenance,seed=seed,**options)
                attempts=[{'draws':options['draws'],'diagnostics':posterior.diagnostics}]
                if not posterior.diagnostics['passed']:
                    posterior=fit(evidence,initial,contextual=contextual,provenance=provenance,seed=seed,**{**options,'draws':4096})
                    attempts.append({'draws':4096,'diagnostics':posterior.diagnostics})
                models['contextual' if contextual else 'basic']=pack(posterior,seed,attempts)
            targets[target][mode]=models
        body={'schema_version':SCHEMA,'digest_encoding':'d-day-typed-utf8-binary64-v1',
              'original_passport_id':root,'original_passport_digest':identity(original),
              'subject_id':original['agent_id'],'parent_passport_id':previous['passport_id'] if previous else root,
              'revision':previous['revision']+1 if previous else 1,
              'evidence_cutoff':group[0]['confirmed_at'],'trigger_event_id':group[0]['event_id'],
              'incorporated_evidence_keys':[[o['target'],o['event_id'],o['mode']] for o in group],
              'configuration':deepcopy(CONFIG),'weather_contract':specification(),
              'runtime':{'python':platform.python_version(),'numpy':np.__version__,'scipy':scipy.__version__},
              'evidence_ledger':deepcopy(used),'targets':targets,
              'activation':{'applied':False,'hotco_modified':False,'state_carryover_applied':False},
              'daily_goals_policy':'independent_explicit_measurements_not_learned',
              'scientific_validation_claimed':False}
        digest=identity(body);previous=dict(body,content_digest=digest,passport_id='learned-'+digest)
        versions.append(previous)
    return {'schema_version':RESULT,'request_digest':identity(data),'original_passport_id':root,
            'previous_passport_id':data.get('previous_version',{}).get('passport_id') if data.get('previous_version') else None,
            'new_versions':versions,'tip_passport_id':previous['passport_id'] if previous else None,
            'status':'updated' if versions else 'unchanged','excluded_evidence':excluded,
            'learning_applied_to_active_passport':False,'hotco_modified':False,'stored_on_server':False}


def predict(version,target,mode,context,as_of):
    from .context_audit import audit_context
    validate_version(version)
    if timestamp(as_of)<=timestamp(version['evidence_cutoff']):raise ValueError('Prediction must follow incorporated evidence')
    audit=audit_context(context,as_of,weather_model_version=INTENSITY)
    name='contextual' if audit['contextual_prediction_allowed_for_research'] else 'basic'
    models=version['targets'][target].get(mode)
    if not models:return {'status':'original_prior_required','passport_id':version['passport_id'],'target':target,'mode':mode}
    model=models[name];p=unpack(model).predict(audit['values'])
    history=[o for o in version['evidence_ledger'] if o['target']==target and o['mode']==mode]
    unseen=[k for k,v in audit['values'].items() if v is not None and all(o['context'].get(k) is None for o in history)]
    outside={k:{'observed_min':min(vals),'observed_max':max(vals),'prediction_value':v}
             for k,v in audit['values'].items() if v is not None
             and (vals:=[o['context'][k] for o in history if o['context'].get(k) is not None])
             and not min(vals)<=v<=max(vals)}
    if target=='affect':
        p['normalized_valence_mean']=p.pop('signed_belief_mean')
        p['normalized_valence_median']=p.pop('signed_belief_median')
        p['normalized_valence_interval_90']=p.pop('signed_belief_interval_90')
        p['expected_raw_valence']=3*p['normalized_valence_mean'];p.pop('expected_ordinal_response')
        p['category_labels_raw_minus3_to_3']=list(range(-3,4))
        p['interval_kind']='epistemic_interval_for_expected_normalized_affect_not_single_emotion'
    return {'passport_id':version['passport_id'],'target':target,'mode':mode,'model':name,
            'prediction':p,'context_audit':audit,'unseen_features':unseen if name=='contextual' else [],
            'outside_observed_ranges':outside if name=='contextual' else {},
            'prediction_usable_for_research':model['diagnostics']['passed'],
            'activation_applied':False,'hotco_modified':False}


class Archive:
    """Offline durable archive, atomic append and separate reversible review selection."""
    def __init__(self,path):
        self.db=sqlite3.connect(path)
        self.db.executescript('CREATE TABLE IF NOT EXISTS versions(id TEXT PRIMARY KEY,root TEXT NOT NULL,value TEXT NOT NULL);'
                             'CREATE TABLE IF NOT EXISTS state(root TEXT PRIMARY KEY,tip TEXT,selected TEXT);'
                             'CREATE TABLE IF NOT EXISTS jobs(digest TEXT PRIMARY KEY,value TEXT NOT NULL);'
                             'CREATE TABLE IF NOT EXISTS requests(digest TEXT PRIMARY KEY,value TEXT NOT NULL);'
                             'CREATE TABLE IF NOT EXISTS selections(root TEXT NOT NULL,identifier TEXT);')
    def close(self):self.db.close()
    def version(self,identifier):
        row=self.db.execute('SELECT value FROM versions WHERE id=?',(identifier,)).fetchone()
        return validate_version(json.loads(row[0])) if row else None
    def state(self,root):
        row=self.db.execute('SELECT tip,selected FROM state WHERE root=?',(root,)).fetchone()
        return row or (None,None)
    def commit(self,request,result):
        if result['request_digest']!=identity(request):raise ValueError('Request/result mismatch')
        root=result['original_passport_id'];expected=result['previous_passport_id']
        with self.db:
            saved=self.db.execute('SELECT value FROM requests WHERE digest=?',(result['request_digest'],)).fetchone()
            if saved and json.loads(saved[0])!=request:raise ValueError('Conflicting immutable request')
            if not saved:self.db.execute('INSERT INTO requests VALUES(?,?)',(result['request_digest'],json.dumps(request,allow_nan=False)))
            done=self.db.execute('SELECT value FROM jobs WHERE digest=?',(result['request_digest'],)).fetchone()
            if done:
                if json.loads(done[0])!=result:raise ValueError('Conflicting immutable job')
                return
            if self.state(root)[0]!=expected:raise ValueError('Concurrent learned Passport update')
            parent=expected;prior=self.version(parent) if parent else None
            for v in result['new_versions']:
                validate_version(v)
                if v['original_passport_id']!=root or v['parent_passport_id']!=(parent or root):raise ValueError('Broken learned lineage')
                if v['revision']!=(prior['revision']+1 if prior else 1):raise ValueError('Broken revision')
                self.db.execute('INSERT INTO versions VALUES(?,?,?)',(v['passport_id'],root,json.dumps(v,allow_nan=False)))
                parent=v['passport_id'];prior=v
            if result['tip_passport_id']!=parent:raise ValueError('Wrong learned tip')
            self.db.execute('INSERT INTO state VALUES(?,?,?) ON CONFLICT(root) DO UPDATE SET tip=excluded.tip',
                            (root,parent,None))
            self.db.execute('INSERT INTO jobs VALUES(?,?)',(result['request_digest'],json.dumps(result,allow_nan=False)))
    def select(self,root,identifier):
        v=self.version(identifier) if identifier else None
        if identifier and (v is None or v['original_passport_id']!=root):raise ValueError('Foreign review version')
        with self.db:
            if self.db.execute('UPDATE state SET selected=? WHERE root=?',(identifier,root)).rowcount!=1:
                raise ValueError('Unknown learned lineage')
            self.db.execute('INSERT INTO selections VALUES(?,?)',(root,identifier))
    def reproduce_archived(self,identifier):
        version=self.version(identifier)
        if version is None:raise ValueError('Unknown learned Passport')
        for digest,value in self.db.execute('SELECT digest,value FROM jobs'):
            if any(v['passport_id']==identifier for v in json.loads(value)['new_versions']):
                saved=self.db.execute('SELECT value FROM requests WHERE digest=?',(digest,)).fetchone()
                if saved:return self.reproduce(json.loads(saved[0]),version)
        raise ValueError('Reconstruction request unavailable')
    def reproduce(self,data,version):
        validate_version(version)
        replay=deepcopy(data);replay.pop('previous_version',None);replay['as_of']=version['evidence_cutoff']
        result=build(replay)
        if not result['new_versions'] or result['new_versions'][-1]!=version:raise ValueError('Reproduction mismatch; check evidence/configuration/runtime')
        return result


def register(app):
    from flask import request,jsonify
    def bounded():
        if request.content_length is not None and request.content_length>32*1024*1024:return jsonify(error='learned_request_too_large'),413
    @app.post('/api/dyconet/d-day/learned-passport/update')
    def update():
        limit=bounded()
        if limit:return limit
        try:return jsonify(build(request.get_json(silent=True)))
        except (ValueError,KeyError,TypeError,IndexError,AttributeError,zlib.error):
            return jsonify(error='invalid_learned_passport',message='Invalid evidence, lineage or archived posterior'),422
    @app.post('/api/dyconet/d-day/learned-passport/predict')
    def prediction():
        limit=bounded()
        if limit:return limit
        try:
            data=request.get_json(silent=True)
            return jsonify(predict(data['version'],data['target'],data['mode'],data['context'],data['as_of']))
        except (ValueError,KeyError,TypeError,IndexError,AttributeError,zlib.error):
            return jsonify(error='invalid_learned_prediction',message='Invalid learned version or prediction context'),422


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('input',type=Path)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(build(json.loads(args.input.read_text(encoding='utf-8'))),indent=2,allow_nan=False)+'\n',encoding='utf-8')
