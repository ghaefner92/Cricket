"""Download a bounded OSM snapshot; no device or personal data is sent."""
import json
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
QUERY = '''[out:json][timeout:60];
area["name"="Magdeburg"]["boundary"="administrative"]["admin_level"="6"]->.city;
(nwr["amenity"="parking"](area.city);
 nwr["amenity"="parking_entrance"](area.city);
 nwr["public_transport"="platform"](area.city);
 node["highway"="bus_stop"](area.city);
 node["railway"="tram_stop"](area.city);
 rel["type"="route"]["route"~"^(tram|bus|trolleybus)$"](area.city););
out center body;'''
request = Request('https://overpass-api.de/api/interpreter', data=urlencode({'data': QUERY}).encode(),
                  headers={'User-Agent': 'Cricket-Magdeburg-pilot/0.4.71', 'Content-Type': 'application/x-www-form-urlencoded'})
with urlopen(request, timeout=90) as response:
    data = json.load(response)
if data.get('remark') or not data.get('elements'):
    raise RuntimeError('Incomplete or empty OSM response')
points = []
pairs = set()
for element in data['elements']:
    tags = element.get('tags', {})
    if tags.get('type') == 'route':
        previous = None
        for member in element.get('members', []):
            role = member.get('role', '')
            if role.startswith('stop'):
                previous = member
            elif role.startswith('platform') and previous:
                pairs.add((f"{previous['type']}/{previous['ref']}", f"{member['type']}/{member['ref']}"))
                previous = None
            else:
                previous = None
        continue
    point = element if 'lat' in element else element.get('center', {})
    if not {'lat', 'lon'} <= point.keys():
        continue
    kind = 'parking' if tags.get('amenity') in ('parking', 'parking_entrance') else 'stop'
    points.append({'id': f"{element['type']}/{element['id']}", 'kind': kind,
                   'lat': point['lat'], 'lon': point['lon'], 'name': tags.get('name', ''), 'tags': tags,
                   'position_kind': 'node' if element['type'] == 'node' else 'area_center'})
result = {'schema_version': 'magdeburg-mobility-pois-v1', 'city': 'Magdeburg',
          'retrieved_at': datetime.now(timezone.utc).isoformat(),
          'osm_base': data.get('osm3s', {}).get('timestamp_osm_base'),
          'source': 'OpenStreetMap via Overpass API', 'license': 'ODbL-1.0',
          'copyright_url': 'https://www.openstreetmap.org/copyright', 'query': QUERY, 'points': points,
          'stop_platform_pairs': [{'stop': s, 'platform': p} for s, p in sorted(pairs)]}
target = ROOT / 'contracts/magdeburg-mobility.json'
target.write_bytes((json.dumps(result, ensure_ascii=False, separators=(',', ':')) + '\n').encode())
print(json.dumps({'parking': sum(p['kind'] == 'parking' for p in points),
                  'stops': sum(p['kind'] == 'stop' for p in points), 'target': str(target)}))
