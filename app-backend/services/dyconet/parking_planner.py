"""Magdeburg pilot: explicit OSM parking assumptions, never vehicle location."""
import copy
import json
from functools import lru_cache
from pathlib import Path
from graphhopper_geometry import distance_m, GeometryError

@lru_cache(maxsize=1)
def dataset():
    return json.loads((Path(__file__).resolve().parents[3] / 'contracts/magdeburg-mobility.json').read_text(encoding='utf-8'))

def point(p):
    return [p['lon'], p['lat']]

def effective_access(p):
    tags = p['tags']
    return tags.get('motorcar', tags.get('motor_vehicle', tags.get('access', 'unknown')))

def permitted(p, explicit=False):
    access = effective_access(p)
    return p['kind'] == 'parking' and (access in ('yes', 'permissive') or explicit and access == 'unknown')

def select_parking(location, requested=None):
    candidates = [p for p in dataset()['points'] if permitted(p, requested is not None)
                  and distance_m(point(p), point(location)) <= 2000]
    if requested is not None:
        selected = next((p for p in candidates if p['id'] == requested), None)
        if selected is None:
            raise GeometryError('Selected parking is outside 2 km or access is restricted')
        return selected
    return min(candidates, key=lambda p: (distance_m(point(p), point(location)), p['id']), default=None)

def parking_path(client, start, stop, selection):
    if not isinstance(selection, dict) or selection.get('policy') != 'nearest-public-osm-magdeburg-v1':
        raise GeometryError('Invalid parking policy')
    origin = select_parking(start, selection.get('origin_id'))
    destination = select_parking(stop, selection.get('destination_id'))
    if origin is None or destination is None:
        raise GeometryError('No explicitly public OSM parking within 2 km')
    # Obtain real road geometry between the proposed parking locations. Centers
    # are cartographic reference points, not certified garage entrances.
    parked = client.path('car', origin, destination)
    driving = copy.deepcopy(next(leg for leg in parked['legs'] if leg['mode'] == 'car'))
    coordinates = driving['geometry']['coordinates']
    for actual, parking in ((coordinates[0], origin), (coordinates[-1], destination)):
        if distance_m(actual, point(parking)) > 250:
            raise GeometryError('Driving endpoint too far from proposed parking')
    legs, flags = [], {}
    for a, b, role in ((start, {'lon': coordinates[0][0], 'lat': coordinates[0][1]}, 'access'),
                       ({'lon': coordinates[-1][0], 'lat': coordinates[-1][1]}, stop, 'egress')):
        if role == 'egress':
            legs.append(driving)
        if distance_m(point(a), point(b)) <= 5:
            continue
        walk = client.path('foot', a, b)
        ends = walk['geometry']['coordinates']
        junction_gap = distance_m(ends[-1], point(b)) if role == 'access' else distance_m(ends[0], point(a))
        if junction_gap > 25:
            raise GeometryError('Pedestrian path does not reach parking road endpoint')
        place_gap = distance_m(ends[0], point(a)) if role == 'access' else distance_m(ends[-1], point(b))
        if place_gap > 25:
            endpoint = 'origin' if role == 'access' else 'destination'
            flags[endpoint + '_access_offset_m'] = place_gap
            flags[endpoint + '_access_time_unknown'] = True
        if walk['duration_seconds'] > 0:
            legs.append({'mode': 'walk', 'access_role': role,
                         **{key: walk[key] for key in ('geometry', 'duration_seconds', 'distance_meters')}})
    def evidence(p, location, role):
        return {**{k: p[k] for k in ('id', 'name', 'lat', 'lon', 'position_kind')},
                'access': effective_access(p), 'fee': p['tags'].get('fee', 'unknown'),
                'selection': 'user_selected' if selection.get(role + '_id') else 'nearest_public_assumption',
                'geographic_distance_m': distance_m(point(p), point(location)), 'occupancy_known': False}
    assumption = {'policy': selection['policy'], 'source': dataset()['source'], 'osm_base': dataset()['osm_base'],
                  'origin': evidence(origin, start, 'origin'), 'destination': evidence(destination, stop, 'destination'),
                  'actual_vehicle_location_known': False, 'parking_entrance_verified': False}
    return {'geometry': driving['geometry'], 'profile': 'car', 'legs': legs, **flags,
            'duration_seconds': sum(l['duration_seconds'] for l in legs),
            'distance_meters': sum(l['distance_meters'] for l in legs),
            'walk_duration_seconds': sum(l['duration_seconds'] for l in legs if l['mode'] == 'walk'),
            'walk_distance_meters': sum(l['distance_meters'] for l in legs if l['mode'] == 'walk'),
            'parking_assumption': assumption}
