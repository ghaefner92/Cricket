"""Offline GTFS geometry probes; no journey/choice/Experience writes."""
import csv,io,json,sys,time,zipfile,hashlib
from collections import defaultdict,Counter
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'app-backend/services/dyconet'))
from tram_alignment import match_tram,dataset,network,closest
from graphhopper_geometry import distance_m
source=ROOT.parent/'Cricket-OTP-Probe/graph/mvb.gtfs.zip'
with zipfile.ZipFile(source) as z:
 def rows(n):
  with z.open(n) as f:return list(csv.DictReader(io.TextIOWrapper(f,encoding='utf-8-sig')))
 routes={r['route_id']:r for r in rows('routes.txt') if r['route_type'] in ('0','3')}
 trips={t['trip_id']:t for t in rows('trips.txt') if t['route_id'] in routes}
 stops={s['stop_id']:s for s in rows('stops.txt')}
 times=defaultdict(list)
 for t in rows('stop_times.txt'):
  if t['trip_id'] in trips:times[t['trip_id']].append(t)
patterns=defaultdict(dict)
for trip,values in times.items():
 values.sort(key=lambda x:int(x['stop_sequence']))
 ids=tuple(t['stop_id'] for t in values)
 if len(ids)>1:patterns[trips[trip]['route_id']].setdefault(ids,trip)
output=ROOT/'output/magdeburg-all-lines-audit.json'
results=[];started=time.monotonic()
for rid,r in sorted(routes.items(),key=lambda x:(x[1]['route_type'],x[1]['route_short_name'])):
 # Every distinct GTFS stop pattern, several spans; no artificial reverse journeys.
 for pattern,trip in sorted(patterns[rid].items()):
  pts=[[float(stops[s]['stop_lon']),float(stops[s]['stop_lat'])] for s in pattern]
  n=len(pts);pairs=list(dict.fromkeys((a,min(n-1,a+2)) for a in (0,n//3,2*n//3,max(0,n-3))))
  for a,b in pairs:
   if b<=a:continue
   anchors=pts[a:b+1];case={'line':r['route_short_name'],'route_id':rid,'vehicle':'TRAM' if r['route_type']=='0' else 'BUS','trip_id':trip,'from':pattern[a],'to':pattern[b],'anchor_count':len(anchors)}
   if r['route_type']=='0':
    t=time.monotonic();m=match_tram(r['route_short_name'],anchors[0],anchors[-1],anchors);case.update(status='ALIGNED' if m else 'UNRESOLVED',seconds=round(time.monotonic()-t,3))
    if m:case.update(points=len(m[0]['coordinates']),reference=m[1])
   else:case.update(status='STOP_ANCHORS_ONLY_NO_GTFS_SHAPE')
   results.append(case)
  print(json.dumps({'line':r['route_short_name'],'vehicle':r['route_type'],'cases':len(results),'elapsed_s':round(time.monotonic()-started)}),flush=True)
  output.write_text(json.dumps({'scope':'all local MVB GTFS tram and bus route patterns','gtfs_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'patterns':sum(len(v) for v in patterns.values()),'line_count':len(routes),'cases':results,'status_counts':dict(Counter(v['status'] for v in results))},indent=2),encoding='utf-8')
print(json.dumps({'done':True,'lines':len(routes),'cases':len(results),'counts':dict(Counter(c['status'] for c in results))}),flush=True)
