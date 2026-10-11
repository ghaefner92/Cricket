"""Read-only probe: find upcoming delayed MVB journeys and verify them in OTP."""
import csv
import datetime
import io
import json
from pathlib import Path
import sys
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT.parent / 'Cricket-OTP-Probe'
sys.path.insert(0, str(PROBE))
sys.path.insert(0, str(ROOT / 'app-backend/services/dyconet'))
from rt_proxy import fields, field, string
from otp_transit import OtpTransitClient

def local(stamp):
    return datetime.datetime.fromtimestamp(stamp, datetime.timezone(datetime.timedelta(hours=2))).isoformat()

with zipfile.ZipFile(PROBE / 'graph/mvb.gtfs.zip') as archive:
    def rows(name):
        return list(csv.DictReader(io.TextIOWrapper(archive.open(name), encoding='utf-8-sig')))
    trips = {r['trip_id']: r for r in rows('trips.txt')}
    routes = {r['route_id']: r for r in rows('routes.txt')}
    stops = {r['stop_id']: r for r in rows('stops.txt')}

with urllib.request.urlopen('http://127.0.0.1:8092/feed.pb', timeout=10) as response:
    feed = response.read()
now = time.time()
print(json.dumps({'checked_at': local(now), 'feed_time': local(field(field(feed, 1), 3))}), flush=True)
candidates = []
for n, entity, _ in fields(feed):
    if n != 2:
        continue
    update = field(entity, 3)
    if update is None:
        continue
    tid = string(field(update, 1), 1)
    sequence = [s for k, s, _ in fields(update) if k == 2]
    for index, stop_update in enumerate(sequence[:-5]):
        departure = field(stop_update, 3)
        if departure is None:
            continue
        stamp, delay = field(departure, 2), field(departure, 1)
        if stamp is None or delay is None or not 60 <= delay < 3600 or not now + 180 <= stamp <= now + 1200:
            continue
        origin = stops.get(string(stop_update, 4))
        destination = stops.get(string(sequence[min(index + 8, len(sequence) - 1)], 4))
        if origin and destination and origin['stop_id'] != destination['stop_id']:
            candidates.append({'trip_id': tid, 'line': routes[trips[tid]['route_id']]['route_short_name'],
                'origin': origin, 'destination': destination, 'departure': local(stamp),
                'delay_seconds': delay, 'stamp': stamp})
candidates.sort(key=lambda c: abs(c['stamp'] - now - 600))
client = OtpTransitClient()
seen = set()
for candidate in candidates:
    if candidate['trip_id'] in seen:
        continue
    seen.add(candidate['trip_id'])
    def point(stop):
        return {'lat': float(stop['stop_lat']), 'lon': float(stop['stop_lon'])}
    try:
        results, audit = client.routes(start=point(candidate['origin']), stop=point(candidate['destination']),
            departure=datetime.datetime.now(datetime.timezone.utc).isoformat(),
            availability={'pt': True, 'walk': True}, max_walk=1500)
        delayed = [(route, leg['transit']) for route in results for leg in route['legs']
            if leg['mode'] == 'pt' and leg['transit']['realtime']
            and (leg['transit']['departure_delay_seconds'] or 0) > 0]
        print(json.dumps({'candidate': candidate, 'otp_live': audit['realtime_feed_fresh'],
            'otp_delayed_legs': [leg for _, leg in delayed],
            'returned_services': [{'line': leg['transit']['line'], 'trip': leg['transit']['gtfs_trip_id'],
                'realtime': leg['transit']['realtime']} for route in results for leg in route['legs'] if leg['mode'] == 'pt']},
            ensure_ascii=True), flush=True)
        if delayed:
            break
    except Exception as exc:
        print(json.dumps({'error': str(exc), 'trip_id': candidate['trip_id']}), flush=True)
    if len(seen) >= 6:
        break
