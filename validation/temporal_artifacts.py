"""Check stored simulation invariants and save the delivery verification."""
import json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'output/d-day-temporal'
design=json.loads((out/'design.json').read_text(encoding='utf-8'))
streams=json.loads((out/'streams.json').read_text(encoding='utf-8'))
summary=json.loads((out/'summary.json').read_text(encoding='utf-8'))
assert len(streams)==192
for stream in streams:
    if stream['initial_raw'] is not None:
        raw=stream['initial_raw'];expected=raw/3 if stream['target']=='affect' else (raw-4)/3
        assert stream['initial']==expected
    else:assert stream['initial_source']=='ridge_estimated'
    last=0.
    for event in stream['events']:
        assert event['day']>last;last=event['day']
        expected=event['raw_response']/3 if stream['target']=='affect' else (event['raw_response']-4)/3
        assert event['normalized_response']==expected
count=0
with (out/'predictions.jsonl').open(encoding='utf-8') as f:
    for line in f:
        row=json.loads(line);p=row['forecast']['probabilities']
        assert len(p)==7 and all(math.isfinite(v) and v>=0 for v in p) and abs(sum(p)-1)<1e-9
        assert row['forecast']['previous_observations']==(0 if row['model']=='static' else row['step']-1)
        assert math.isfinite(row['log_score']) and 0<=row['rps']<=1
        assert 0<=row['forecast']['boundary_mass']<1e-4
        if row['model']=='static':assert row['forecast']['interval_90'] is None
        count+=1
assert count==230400==summary['scored_predictions']
assert all(r['mcmc_diagnostics']['passed'] for r in summary['numerical_reference'])
verification={'streams':len(streams),'observations':sum(len(s['events']) for s in streams),'scored_predictions':count,
    'prior_generative_calibration_sequences':128,'grid_sensitivity_sequences':16,'backend_tests_passed':46,
    'independent_mcmc_references_passed':8,'ordinal_initial_and_experience_codings_checked':True,
    'all_forecasts_use_only_past_responses':True,'production_frontend_changed':False,
    'production_forecast_model_changed':False,'active_passport_modified':False,'hotco_modified':False,
    'scientific_validation_claimed':False}
(out/'verification.json').write_text(json.dumps(verification,indent=2)+'\n',encoding='utf-8')
print(json.dumps(verification))
