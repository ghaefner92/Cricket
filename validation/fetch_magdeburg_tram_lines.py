"""OSM reference tram alignments, not observations of a vehicle's actual path."""
import json
from datetime import datetime,timezone
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request,urlopen
QUERY='''[out:json][timeout:60];
area["name"="Magdeburg"]["boundary"="administrative"]["admin_level"="6"]->.city;
rel["type"="route"]["route"="tram"](area.city)->.routes;
.routes out body;
(way(r.routes)["railway"~"^(tram|light_rail|subway)$"];
 way(area.city)["railway"~"^(tram|light_rail|subway)$"];);out geom;
node(r.routes)["public_transport"="stop_position"];out body;'''
request=Request('https://overpass-api.de/api/interpreter',data=urlencode({'data':QUERY}).encode(),
 headers={'User-Agent':'Cricket/0.4.73','Content-Type':'application/x-www-form-urlencoded'})
with urlopen(request,timeout=90) as response:data=json.load(response)
if data.get('remark') or not data.get('elements'):raise RuntimeError('Incomplete OSM response')
result={'schema_version':'magdeburg-tram-alignments-v1','source':'OpenStreetMap via Overpass API','license':'ODbL-1.0',
 'osm_base':data.get('osm3s',{}).get('timestamp_osm_base'),'retrieved_at':datetime.now(timezone.utc).isoformat(),'query':QUERY,'elements':data['elements']}
# Authoritative timetable names permit physical-network references even when OSM
# lacks a current route relation (e.g. special/night services). No bus names.
import zipfile,csv,io,hashlib
feed=Path(__file__).resolve().parents[2]/'Cricket-OTP-Probe/graph/mvb.gtfs.zip'
if feed.exists():
    with zipfile.ZipFile(feed) as archive,archive.open('routes.txt') as source:
        routes=csv.DictReader(io.TextIOWrapper(source,encoding='utf-8-sig'))
        result['timetable_tram_lines']=sorted({r['route_short_name'] for r in routes if r['route_type']=='0'})
    result['timetable_source_sha256']=hashlib.sha256(feed.read_bytes()).hexdigest()
target=Path(__file__).resolve().parents[1]/'contracts/magdeburg-tram-lines.json'
target.write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
print(json.dumps({'relations':sum(e['type']=='relation' for e in data['elements']),'ways':sum(e['type']=='way' for e in data['elements'])}))
