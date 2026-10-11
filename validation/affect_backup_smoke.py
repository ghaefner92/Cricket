"""Validate the real Android synthetic export through the offline affect adapter."""
import json
import subprocess
import sys
from datetime import datetime,timedelta
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'app-backend/services/dyconet'))
from comfort_pilot.evidence import request_from_backup
from comfort_pilot.affect import analyze_affect

adb=Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'
payload=subprocess.check_output([str(adb),'-s','RZCY70K63HV','exec-out','run-as','de.ovgu.imiq.cricket','cat','cache/affect-pilot-synthetic-backup.json'])
backup=json.loads(payload)
before=json.dumps(backup,sort_keys=True)
journey=backup['simulations'][0]['journeys'][0];e=journey['experience']
stamp=e['confirmed_at'];future=(datetime.fromisoformat(stamp.replace('Z','+00:00'))+timedelta(seconds=1)).isoformat()
contexts=[{'prediction_id':'synthetic-export-affect','mode':'walk','as_of':future,'context':{'queried_at':stamp,'measurements':[]}}]
data,excluded=request_from_backup(backup,prediction_contexts=contexts,target='affect')
assert len(data['observations'])==1 and not excluded
o=data['observations'][0]
assert o['rating_raw_minus3_to_3']==2 and abs(o['valence_normalized']-2/3)<1e-12
assert o['context']['scope']=='selected_route_search_query'
result=analyze_affect(data)
assert result['predictions'][0]['models']['basic']['observation_count']==1
assert not result['learning_applied_to_active_passport'] and not result['hotco_modified']
assert json.dumps(backup,sort_keys=True)==before
out=ROOT/'output/d-day-affect'
for filename,value in (('synthetic_backup.json',backup),('backup_request.json',data),('backup_result.json',result)):
    (out/filename).write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'status':'passed','android_export_adapter':True,'observations':len(data['observations']),'active_passport_modified':False}))
