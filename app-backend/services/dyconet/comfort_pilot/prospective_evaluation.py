"""Recompute scores from frozen probabilities, never from an epistemic interval."""
import math
from journey_contract import timestamp
from .model import identity
from .eligibility import exclusion_reason, validate_exclusions, backup_exclusions

def evaluate(record,journey, *, exclusions=None):
    e=journey['experience'];f=record.get('d_day_forecast')
    out={'schema_version':'d-day-prospective-evaluation-v1','event_id':e['event_id'],
        'journey_id':journey['journey_id'],'choice_event_id':journey['choice_event_id'],
        'learning_applied':False,'scores':[]}
    def skip(reason):return {**out,'status':'unscored','reason':reason}
    reasons = {target:exclusion_reason(record,journey,target=target,exclusions=exclusions) for target in ('comfort','affect')}
    if all(reasons.values()):return skip(reasons['comfort'])
    if any(reasons.values()):out['target_exclusions']={target:reason for target,reason in reasons.items() if reason}
    if e['completion']=='not_taken':return skip('trip_not_taken')
    if e['completion']=='changed':return skip('changed_route_context_not_attributable')
    if not f or f['status']!='complete':return skip('forecast_not_available')
    selection=journey['selection']
    if timestamp(f['recorded_at'])>=timestamp(selection['chosen_at']):return skip('forecast_recorded_after_choice')
    result=f['result'];pid=f"{selection['routing_rank']}-{selection['mode_key']}"
    out.update(forecast_request_digest=result['request_digest'],prediction_id=pid,as_of=result['as_of'])
    for target in ('comfort','affect'):
        if reasons[target]:continue
        o=e.get('comfort_observation') or {}
        mode=o.get('mode') if target=='comfort' else {'foot':'walk','pt_bus_tram':'pt','car_driver':'car'}.get(selection['mode_key'],selection['mode_key'])
        raw=o.get('rating') if target=='comfort' else e.get('mode_valences_raw_minus3_to_3',{}).get(mode)
        if raw is None:continue
        prediction=next((p for p in result['targets'][target]['predictions'] if p['prediction_id']==pid and p['mode']==mode),None)
        if prediction is None:continue
        y=raw-1 if target=='comfort' else raw+3
        normalized=(raw-4)/3 if target=='comfort' else raw/3
        for name in ('static','basic','contextual'):
            model=prediction['models'][name]
            if not model['prediction_usable_for_research']:continue
            p=model['category_probabilities']
            if len(p)!=7 or any(type(v) not in (int,float) or not math.isfinite(v) or v<0 for v in p) or abs(sum(p)-1)>1e-6:raise ValueError('Invalid frozen probabilities')
            cumulative=0.;rps=0.
            for k in range(6):
                cumulative+=p[k];rps+=(cumulative-(1. if y<=k else 0.))**2
            mean=sum(v*(i-3)/3 for i,v in enumerate(p))
            out['scores'].append({'target':target,'mode':mode,'model':name,'raw_response':raw,'normalized_response':normalized,
                'log_score':math.log(max(p[y],1e-15)),'ranked_probability_score':rps/6,
                'normalized_absolute_error':abs(mean-normalized),'predicted_normalized_mean':mean})
            contract = result['targets'][target].get('weather_contract')
            if contract:
                out['scores'][-1]['weather_model_version'] = contract['weather_model_version']
    out['status']='scored' if out['scores'] else 'unscored'
    if not out['scores']:out['reason']='no_eligible_prediction'
    return out

def validate_record(record):
    f=record.get('d_day_forecast')
    if f is None:
        if record.get('d_day_evaluations'):raise ValueError('Orphan evaluation')
        return
    if f['status'] not in ('queued','pending','complete','failed'):raise ValueError('Unknown forecast status')
    if f['status']=='complete':
        r=f['result'];req=f['request']
        if r['schema_version']!='d-day-prospective-forecast-v1' or r['request_digest']!=identity(req) or r['search_id']!=record['search_id'] or req['search_id']!=record['search_id'] or r['as_of']!=req['as_of']:raise ValueError('Forecast does not match frozen request')
        if r['learning_applied_to_active_passport'] or r['hotco_modified']:raise ValueError('Unexpected active update')
        if timestamp(f['recorded_at'])<timestamp(r['as_of']):raise ValueError('Forecast clock ordering invalid')
    seen=set()
    for stored in record.get('d_day_evaluations',[]):
        event=stored['event_id']
        if event in seen:raise ValueError('Duplicate evaluation')
        seen.add(event)
        journey=next((j for j in record['journeys'] if (j.get('experience') or {}).get('event_id')==event),None)
        if journey is None:raise ValueError('Orphan evaluation')
        expected=evaluate(record,journey);actual={k:v for k,v in stored.items() if k!='evaluated_at'}
        def equal(a,b):
            if isinstance(a,dict):return isinstance(b,dict) and a.keys()==b.keys() and all(equal(v,b[k]) for k,v in a.items())
            if isinstance(a,list):return isinstance(b,list) and len(a)==len(b) and all(equal(x,y) for x,y in zip(a,b))
            if type(a) in (int,float) and type(b) in (int,float):return math.isfinite(b) and abs(a-b)<1e-9
            return a==b
        if not equal(expected,actual):raise ValueError('Evaluation inconsistent with forecast/experience')

def report(backup, *, exclusions=None):
    from d_day_evidence import validate_backup
    validate_backup(backup)
    exclusions = backup_exclusions(backup, exclusions)
    validate_exclusions(exclusions)
    evaluations=[evaluate(r,j,exclusions=exclusions) for r in backup['simulations'] if r.get('d_day_forecast') for j in r.get('journeys',[]) if j.get('experience')]
    groups=[]
    for target in ('comfort','affect'):
        for mode in ('walk','bike','pt','car'):
            # Compare on exactly the same eligible experiences for all models.
            complete=[e for e in evaluations if all(any(s['target']==target and s['mode']==mode and s['model']==m for s in e['scores']) for m in ('static','basic','contextual'))]
            for model in ('static','basic','contextual'):
                rows=[s for e in complete for s in e['scores'] if s['target']==target and s['mode']==mode and s['model']==model]
                for version in sorted({s.get('weather_model_version', '') for s in rows}):
                    subset = [s for s in rows if s.get('weather_model_version', '') == version]
                    groups.append({'target':target,'mode':mode,'model':model,'n':len(subset),
                        **({'weather_model_version': version} if version else {}),
                        **{k:sum(s[k] for s in subset)/len(subset) for k in ('log_score','ranked_probability_score','normalized_absolute_error')}})
    return {'schema_version':'d-day-prospective-report-v1','evaluations':evaluations,'paired_model_comparisons':groups,
        'learning_applied':False,'scientific_validation_claimed':False,
        'interval_coverage_for_latent_expectation_not_observable_from_single_rating':True}

if __name__=='__main__':
    import argparse,json
    from pathlib import Path
    p=argparse.ArgumentParser();p.add_argument('backup',type=Path);p.add_argument('--output',type=Path,required=True);p.add_argument('--exclusions',type=Path);args=p.parse_args()
    exclusions=json.loads(args.exclusions.read_text(encoding='utf-8')) if args.exclusions else None
    args.output.write_text(json.dumps(report(json.loads(args.backup.read_text(encoding='utf-8')),exclusions=exclusions),indent=2)+'\n',encoding='utf-8')
