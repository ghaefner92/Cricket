import json
import pathlib
import sys
from urllib.parse import urlencode
from urllib.request import urlopen
from dotenv import load_dotenv
root = pathlib.Path(__file__).resolve().parents[1]
load_dotenv(root / 'app-backend/.env')
sys.path.insert(0, str(root / 'app-backend/services/dyconet'))
from graphhopper_geometry import GraphHopperGeometryClient, distance_m
snapshot = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / '.local-backups/auto-goals-audit/latest-search.json'
q = json.loads(snapshot.read_text(encoding='utf-8'))['request']
client = GraphHopperGeometryClient()
def raw(mode, a, b):
    query = urlencode([('point', f"{a['lat']},{a['lon']}"), ('point', f"{b['lat']},{b['lon']}"), ('profile', mode), ('points_encoded', 'false')])
    with urlopen(client.base_url + '/route?' + query, timeout=15) as response:
        return json.load(response)['paths'][0]
car = raw('car', q['start'], q['stop'])
points = car['points']['coordinates']
for a, b, role in ((q['start'], {'lon': points[0][0], 'lat': points[0][1]}, 'access'), ({'lon': points[-1][0], 'lat': points[-1][1]}, q['stop'], 'egress')):
    gap = distance_m([a['lon'], a['lat']], [b['lon'], b['lat']])
    walk = raw('foot', a, b)
    ends = walk['points']['coordinates']
    print(json.dumps({'role': role, 'gap_m': gap, 'walk_m': walk['distance'], 'walk_seconds': walk['time']/1000,
                      'walk_start_gap_m': distance_m(ends[0], [a['lon'], a['lat']]),
                      'walk_end_gap_m': distance_m(ends[-1], [b['lon'], b['lat']])}))
if '--parking' in sys.argv:
    from parking_planner import parking_path
    selection = {'policy': 'nearest-public-osm-magdeburg-v1'}
    for argument in sys.argv:
        if argument.startswith('--origin-parking='):
            selection['origin_id'] = argument.split('=', 1)[1]
    result = parking_path(client, q['start'], q['stop'], selection)
    report = {k: v for k, v in result.items() if k not in ('geometry', 'legs')}
    report['legs'] = [{'mode': leg['mode'], 'seconds': leg['duration_seconds'],
                       'metres': leg['distance_meters'], 'points': len(leg['geometry']['coordinates'])} for leg in result['legs']]
    print(json.dumps(report, ensure_ascii=True))
    (snapshot.parent / 'parking-probe.json').write_text(json.dumps(report), encoding='utf-8')
