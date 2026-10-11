"""Reproducible offline/live read-only pilot check using synthetic evidence."""
import json,sys,http.client
from pathlib import Path
from hashlib import sha256
ROOT=Path(__file__).resolve().parents[1]
BACKEND=ROOT/'app-backend/services/dyconet'
sys.path.insert(0,str(BACKEND));sys.path.insert(0,str(BACKEND/'tests'))
from test_comfort_pilot import request_fixture
from comfort_pilot.api import analyze

def main():
    output=ROOT/'output/d-day-phase4';output.mkdir(parents=True,exist_ok=True)
    data=request_fixture();before=json.dumps(data,sort_keys=True)
    (output/'request_example.json').write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    offline=analyze(data);assert json.dumps(data,sort_keys=True)==before
    connection=http.client.HTTPConnection('127.0.0.1',8077,timeout=60)
    connection.request('POST','/api/dyconet/d-day/comfort-pilot/analyze',json.dumps(data),{'Content-Type':'application/json'})
    response=connection.getresponse();live=json.loads(response.read());assert response.status==200
    assert live==offline and not live['learning_applied_to_active_passport'] and not live['hotco_modified']
    (output/'result_example.json').write_text(json.dumps(live,indent=2)+'\n',encoding='utf-8')
    files=list((BACKEND/'comfort_pilot').glob('*.py'))+[ROOT/'contracts/journey-v1.schema.json',ROOT/'android-native/app/src/main/java/de/ovgu/imiq/cricket/ComfortPilotUi.kt']
    manifest={'schema_version':'d-day-phase4-implementation-manifest-v1','files':{str(p.relative_to(ROOT)).replace('\\','/'):sha256(p.read_bytes()).hexdigest() for p in files},
              'offline_matches_live':True,'active_passport_modified':False,'live_port':8077,'app_version':'0.4.59'}
    (output/'implementation_manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':'passed','offline_matches_live':True,'active_passport_modified':False}))

if __name__=='__main__':main()
