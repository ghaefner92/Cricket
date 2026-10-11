"""Audit reference line matching against an immutable private route snapshot."""
import json
from pathlib import Path
import sys
from urllib.request import Request,urlopen
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'app-backend/services/dyconet'))
from tram_alignment import dataset,match_tram,network,closest,path_between
from graphhopper_geometry import distance_m
data=dataset();elements=data['elements'];ways={e['id']:e for e in elements if e['type']=='way'};nodes={e['id']:e for e in elements if e['type']=='node'}
saved=json.loads((ROOT/'.local-backups/car-time-0473/latest-search.json').read_text())
seen=set()
requests=[]
for route in saved['result']['routing']['routes']:
 for index,leg in enumerate(route['legs']):
  t=leg.get('transit',{})
  if t.get('source_mode')!='TRAM':continue
  key=(t['line'],t['from']['stop']['gtfsId'],t['to']['stop']['gtfsId'])
  if key in seen:continue
  seen.add(key);a=[t['from']['lon'],t['from']['lat']];b=[t['to']['lon'],t['to']['lat']]
  requests.append({'route_id':str(route['rank'])+'-'+route['mode_key'],'leg_index':index,'line':t['line'],
       'from':t['from'],'to':t['to'],'geometry':leg['geometry']})
  match=match_tram(t['line'],a,b,leg['geometry']['coordinates'])
  print(json.dumps({'line':t['line'],'matched':match[1] if match else False}))
  if match:continue
  for rel in elements:
   if rel['type']!='relation' or rel.get('tags',{}).get('ref') not in (t['line'],t['line']+'S',t['line']+'N'):continue
   stops=[nodes[m['ref']] for m in rel['members'] if m['type']=='node' and m.get('role','').startswith('stop') and m['ref'] in nodes]
   coord,edges,graph=network(rel,ways);start=closest(a,coord,edges);end=closest(b,coord,edges)
   nearest=lambda p:min((distance_m(p,[n['lon'],n['lat']]),i,n.get('tags',{}).get('name')) for i,n in enumerate(stops)) if stops else None
   print(json.dumps({'relation':rel['id'],'reference':rel['tags']['ref'],'boarding':nearest(a),'alighting':nearest(b),
       'rail_offset':[start[0],end[0]],'path_exists':path_between(start,end,coord,graph) is not None},ensure_ascii=True))
if '--http' in sys.argv:
 with urlopen(Request('http://127.0.0.1:8077/api/dyconet/transit/tram-alignment',data=json.dumps({'legs':requests}).encode(),headers={'Content-Type':'application/json'}),timeout=30) as response:
  aligned=json.load(response)
 expected=sum(match_tram(r['line'],[r['from']['lon'],r['from']['lat']],[r['to']['lon'],r['to']['lat']],r['geometry']['coordinates']) is not None for r in requests)
 assert len(aligned['alignments'])==expected and expected>0
 assert all(len(a['geometry']['coordinates'])>6 for a in aligned['alignments'])
 (ROOT/'.local-backups/car-time-0473/tram-display.json').write_text(json.dumps(aligned),encoding='utf-8')
 print(json.dumps({'display_http':'PASS','matched':len(aligned['alignments']),'source_unchanged':True}))
