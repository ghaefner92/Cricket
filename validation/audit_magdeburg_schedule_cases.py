"""Check each sampled span against unchanged source GTFS scheduled times."""
import csv,io,json,zipfile,collections
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=ROOT/'output/magdeburg-all-lines-audit.json';report=json.loads(p.read_text())
with zipfile.ZipFile(ROOT.parent/'Cricket-OTP-Probe/graph/mvb.gtfs.zip') as z:
 def rows(n):
  with z.open(n) as f:return list(csv.DictReader(io.TextIOWrapper(f,encoding='utf-8-sig')))
 times=collections.defaultdict(list)
 for t in rows('stop_times.txt'):times[t['trip_id']].append(t)
 for v in times.values():v.sort(key=lambda x:int(x['stop_sequence']))
 def seconds(v):
  h,m,s=map(int,v.split(':'));return h*3600+m*60+s
 invalid=[]
 for c in report['cases']:
  v=times[c['trip_id']];durations=[]
  for a,t in enumerate(v):
   b=a+c['anchor_count']-1
   if t['stop_id']==c['from'] and b<len(v) and v[b]['stop_id']==c['to']:
    durations.append(seconds(v[b]['arrival_time'])-seconds(t['departure_time']))
  c['scheduled_duration_range_seconds']=[min(durations),max(durations)] if durations else None
  c['scheduled_time_valid']=bool(durations) and all(d>=0 for d in durations)
  if not c['scheduled_time_valid']:invalid.append(c)
 report['schedule_checks']={'cases':len(report['cases']),'invalid':len(invalid),'scope':'GTFS schedule variants, not a guarantee of service operating now'}
 report['vehicle_line_counts']=dict(collections.Counter(c for c,_ in {(v['vehicle'],v['line']) for v in report['cases']}))
 p.write_text(json.dumps(report,indent=2),encoding='utf-8')
 print(json.dumps({'cases':len(report['cases']),'invalid_schedules':len(invalid),'lines':report['vehicle_line_counts'],'statuses':report['status_counts']}))
