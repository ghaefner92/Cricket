"""Offline audit of immutable device snapshot; no phone/GPS/feedback writes."""
import json,sys,time
from pathlib import Path
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'app-backend/services/dyconet'))
from tram_alignment import match_tram
from graphhopper_geometry import distance_m
p=json.loads((root/'.local-backups/car-time-0473/latest-search.json').read_text())
legs=[l for r in p['result']['routing']['routes'] for l in r['legs'] if l.get('transit',{}).get('source_mode')=='TRAM']
durations=[]
for _ in range(3):
 start=time.perf_counter()
 for l in legs:
  t=l['transit'];match_tram(t['line'],[t['from']['lon'],t['from']['lat']],[t['to']['lon'],t['to']['lat']],l['geometry']['coordinates'])
 durations.append(round((time.perf_counter()-start)*1000,2))
issues=[]
for r in p['result']['routing']['routes']:
 if r.get('mode_key')!='pt':continue
 t=r['transit'];span=(t['end_time_ms']-t['start_time_ms'])/1000
 if abs(span-r['summary']['duration_seconds'])>2:issues.append('itinerary_time_mismatch')
 for l in r['legs']:
  lt=l['transit']
  if abs((lt['end_time_ms']-lt['start_time_ms'])/1000-l['duration_seconds'])>2:issues.append('leg_time_mismatch')
  if lt['start_time_ms']<t['start_time_ms']-2000 or lt['end_time_ms']>t['end_time_ms']+2000:issues.append('leg_outside_itinerary')
 for a,b in zip(r['legs'],r['legs'][1:]):
  if b['transit']['start_time_ms']<a['transit']['end_time_ms']-2000:issues.append('overlapping_legs')
  if distance_m(a['geometry']['coordinates'][-1],b['geometry']['coordinates'][0])>25:issues.append('disconnected_legs')
print(json.dumps({'snapshot_created':p['created_at'],'tram_legs':len(legs),'alignment_batch_ms':durations,'timing_and_junction_issues':issues}))
