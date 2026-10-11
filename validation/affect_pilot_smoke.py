"""Read-only offline/live parity for the affect endpoint, using synthetic data."""
import http.client
import json
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BACKEND=ROOT/'app-backend/services/dyconet'
sys.path[:0]=[str(BACKEND),str(BACKEND/'tests')]
from test_affect_pilot import affect_fixture
from comfort_pilot.affect import analyze_affect

data=affect_fixture();before=json.dumps(data,sort_keys=True)
offline=analyze_affect(data)
connection=http.client.HTTPConnection('127.0.0.1',8077,timeout=90)
connection.request('POST','/api/dyconet/d-day/affect-pilot/analyze',json.dumps(data),{'Content-Type':'application/json'})
response=connection.getresponse();live=json.loads(response.read())
assert response.status==200 and live==offline
assert json.dumps(data,sort_keys=True)==before
assert not live['learning_applied_to_active_passport'] and not live['hotco_modified']
out=ROOT/'output/d-day-affect'
for filename,value in (('request_example.json',data),('result_example.json',live)):
    (out/filename).write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'status':'passed','offline_matches_live':True,'active_passport_modified':False}))
