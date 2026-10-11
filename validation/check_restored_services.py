"""Read-only service probes. No Experience registration or Passport activation."""
import concurrent.futures
import datetime
import json
import pathlib
import sys
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from dotenv import load_dotenv

ROOT = pathlib.Path(__file__).resolve().parents[1]
load_dotenv(ROOT / 'app-backend/.env')
sys.path.insert(0, str(ROOT / 'app-backend/services/dyconet'))
from graphhopper_geometry import GraphHopperGeometryClient, distance_m
from otp_transit import OtpTransitClient

saved = json.loads((ROOT / '.local-backups/auto-goals-audit/latest-search.json').read_text(encoding='utf-8'))
request = saved['request']

if '--pt-walk-probe' in sys.argv:
    routes, audit = OtpTransitClient().routes(
        start=request['start'], stop=request['stop'],
        departure=datetime.datetime.now(datetime.timezone.utc).isoformat(),
        availability=request['journey_availability'], max_walk=1900)
    proof = {'diagnostic_only': True, 'user_settings_changed': False,
             'max_walk_meters': 1900, 'status': audit['status'],
             'pt_options': len(routes), 'realtime_feed_fresh': audit['realtime_feed_fresh'],
             'walking_distances_meters': [round(route['summary']['walk_distance_meters'], 1) for route in routes]}
    (ROOT / 'output/service-recovery-pt-walk-probe-2026-10-11.json').write_text(
        json.dumps(proof, indent=2), encoding='utf-8')
    print(json.dumps(proof, indent=2))
    sys.exit(0)


def get_json(url, data=None):
    req = Request(url, data=json.dumps(data).encode() if data is not None else None,
                  headers={'Content-Type': 'application/json'})
    with urlopen(req, timeout=150) as response:
        return response.status, json.load(response)


def probe(name, function):
    try:
        return name, function()
    except Exception as exc:
        return name, {'ok': False, 'error': type(exc).__name__, 'detail': str(exc)}


def health():
    status, body = get_json('http://127.0.0.1:8077/health')
    return {'http_status': status, 'response': body}


def realtime():
    status, body = get_json('http://127.0.0.1:8092/status')
    return {'http_status': status, 'ready': body.get('ready'),
            'feed_age_seconds': datetime.datetime.now(datetime.timezone.utc).timestamp() - body.get('feed_timestamp', 0)}


def weather():
    status, body = get_json('http://127.0.0.1:8077/api/dyconet/weather?' + urlencode(request['start']))
    return {'http_status': status, 'status': body.get('status')}


def car():
    client = GraphHopperGeometryClient()
    query = urlencode([('point', f"{request['start']['lat']},{request['start']['lon']}"),
                       ('point', f"{request['stop']['lat']},{request['stop']['lon']}"),
                       ('profile', 'car'), ('points_encoded', 'false')])
    status, body = get_json(client.base_url + '/route?' + query)
    path = body['paths'][0]
    points = path['points']['coordinates']
    distances = [distance_m(points[0], [request['start']['lon'], request['start']['lat']]),
                 distance_m(points[-1], [request['stop']['lon'], request['stop']['lat']])]
    return {'http_status': status, 'geometry_points': len(points),
            'origin_offset_m': round(distances[0], 1), 'destination_offset_m': round(distances[1], 1),
            'passes_existing_250m_check': max(distances) <= 250}


def routing():
    live = dict(request)
    live['datetime'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    live['record_trajectories'] = False
    status, body = get_json('http://127.0.0.1:8077/api/dyconet/routed-contextual-deliberation', live)
    routing = body.get('routing', {})
    otp = routing.get('provider_audit', {}).get('otp', {})
    routes = routing.get('routes', [])
    return {'http_status': status, 'modes': [route.get('mode_key') for route in routes],
            'car_journeys': [{'summary': route.get('summary'),
                              'legs': [{key: leg.get(key) for key in ('mode', 'access_role', 'duration_seconds', 'distance_meters')} for leg in route.get('legs', [])]}
                             for route in routes if route.get('mode_key') == 'car'],
            'otp_status': otp.get('status'), 'realtime_feed_fresh': otp.get('realtime_feed_fresh'),
            'pt_options': sum(route.get('mode_key') == 'pt' for route in routes),
            'routing_errors': otp.get('routing_errors'),
            'rejected_itineraries': otp.get('rejected_itineraries'),
            'route_audit': [{key: item.get(key) for key in ('mode_key', 'context_ready', 'reason', 'geometry_failure')}
                            for item in body.get('route_audit', [])]}


with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
    results = dict(pool.map(lambda pair: probe(*pair), [('backend', health), ('realtime', realtime),
                                                       ('weather', weather), ('car_geometry', car),
                                                       ('routed_search', routing)]))
output = ROOT / 'output/service-recovery-2026-10-11.json'
output.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding='utf-8')
print(json.dumps(results, indent=2, ensure_ascii=False))
