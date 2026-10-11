"""Read back the isolated Android cycle and verify frozen forecasts and scores."""
import json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'app-backend/services/dyconet'))
from comfort_pilot.prospective_evaluation import report
from comfort_pilot.prospective import forecast

adb=Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'
payload=subprocess.check_output([str(adb),'-s','RZCY70K63HV','exec-out','run-as','de.ovgu.imiq.cricket','cat','cache/affect-pilot-synthetic-backup.json'])
backup=json.loads(payload);before=json.dumps(backup,sort_keys=True)
result=report(backup)
assert len(result['evaluations'])==1 and result['evaluations'][0]['status']=='scored'
assert len(result['paired_model_comparisons'])==6
record=next(r for r in backup['simulations'] if r.get('d_day_forecast'))
frozen=record['d_day_forecast'];offline=forecast(frozen['request']);stored=frozen['result']
offline.pop('computed_at');expected=dict(stored);expected.pop('computed_at')
assert offline==expected and json.dumps(backup,sort_keys=True)==before
out=ROOT/'output/d-day-prospective';out.mkdir(parents=True,exist_ok=True)
for filename,value in (('synthetic_backup.json',backup),('report.json',result)):
    (out/filename).write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'status':'passed','android_background_forecast_matches_offline':True,'paired_comparisons':6,'active_profile_modified':False}))
