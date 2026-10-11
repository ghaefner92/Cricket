import json
from datetime import datetime,timezone
from pathlib import Path
from urllib.request import Request,urlopen
import uuid
import sys
from dotenv import load_dotenv
ROOT=Path(__file__).resolve().parents[1]
load_dotenv(ROOT/'app-backend/.env')
sys.path.insert(0,str(ROOT/'app-backend/services/dyconet'))
from transit_stop_api import plan_from_stop,first_boarding
r=json.loads((ROOT/'.local-backups/car-map-0470/latest-search.json').read_text())
selected=next(l['transit']['from']['stop']['gtfsId'] for route in r['result']['routing']['routes'] for l in route.get('legs',[]) if l['mode']=='pt')
p={'start':r['request']['start'],'stop':r['request']['stop'],'datetime':datetime.now(timezone.utc).isoformat(),
   'availability':r['request']['journey_availability'],'max_walk_m':1500,'boarding_stop_id':selected}
routes,audit,stop=plan_from_stop(p)
result={'stop':stop,'status':audit['status'],'count':len(routes),'itineraries':[{'trip':first_boarding(x)['gtfs_trip_id'],
         'boarding_stop':first_boarding(x)['from']['stop']['gtfsId'],'summary':x['summary'],'start':x['transit']['start_time_ms'],
         'board':first_boarding(x)['start_time_ms'],'end':x['transit']['end_time_ms']} for x in routes]}
print(json.dumps(result,ensure_ascii=True))
OUT=ROOT/'.local-backups/mobility-map-0472';OUT.mkdir(exist_ok=True)
(OUT/'stop-preview.json').write_text(json.dumps({'payload':p,'response':result,'routes':routes}),encoding='utf-8')
if '--routed' in sys.argv and routes:
    chosen=routes[0];board=first_boarding(chosen)
    request={**r['request'],'search_id':'stop-audit-'+str(uuid.uuid4()),'datetime':datetime.now(timezone.utc).isoformat(),
             'boarding_stop_id':selected,'boarding_trip_id':board['gtfs_trip_id'],
             'boarding_departure_ms':board['scheduled_start_time_ms'],
             'boarding_plan_datetime':datetime.fromtimestamp((chosen['transit']['start_time_ms']-30000)/1000,timezone.utc).isoformat()}
    with urlopen(Request('http://127.0.0.1:8077/api/dyconet/routed-contextual-deliberation',
                        data=json.dumps(request).encode(),headers={'Content-Type':'application/json'}),timeout=180) as response:
        full=json.load(response)
    transit=[x for x in full['routing']['routes'] if any(l['mode']=='pt' for l in x['legs'])]
    assert transit,'Selected departure unavailable during integration check'
    assert all(first_boarding(x)['from']['stop']['gtfsId']==selected and first_boarding(x)['gtfs_trip_id']==board['gtfs_trip_id'] for x in transit)
    (OUT/'stop-routed.json').write_text(json.dumps(full),encoding='utf-8')
    print(json.dumps({'routed_status':'PASS','pt_count':len(transit),'selected_stop':selected,'selected_trip':board['gtfs_trip_id']}))
if '--platform' in sys.argv:
    platform_request={**p,'boarding_platform_id':'way/35917677'}
    with urlopen(Request('http://127.0.0.1:8077/api/dyconet/transit/stop-itineraries',
                        data=json.dumps(platform_request).encode(),headers={'Content-Type':'application/json'}),timeout=180) as response:
        preview=json.load(response)
    print(json.dumps({'platform_status':preview['status'],'itinerary_count':len(preview['routes'])}))
    (OUT/'platform-preview.json').write_text(json.dumps(preview),encoding='utf-8')
