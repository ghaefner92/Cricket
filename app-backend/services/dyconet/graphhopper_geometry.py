"""Independent single-mode paths; never claim identity with ranked routes."""
import copy
import json
import math
import os
from urllib.parse import urlencode, urlsplit
from urllib.request import urlopen


class GeometryError(ValueError):
    pass


def distance_m(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, (a[1], a[0], b[1], b[0]))
    h = math.sin((lat2-lat1)/2)**2 + math.cos(lat1)*math.cos(lat2)*math.sin((lon2-lon1)/2)**2
    return 6371000 * 2 * math.asin(math.sqrt(min(1, h)))


class GraphHopperGeometryClient:
    def __init__(self, base_url=None, timeout=None):
        self.base_url = (base_url or os.environ.get('IMIQ_GRAPHHOPPER_BASE_URL', '')).rstrip('/')
        parsed = urlsplit(self.base_url)
        if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise GeometryError('Invalid server-configured GraphHopper URL')
        self.timeout = float(timeout if timeout is not None else os.environ.get('IMIQ_GEOMETRY_TIMEOUT_SECONDS', '15'))
        if not math.isfinite(self.timeout) or self.timeout <= 0:
            raise GeometryError('Geometry timeout must be positive and finite')

    def path(self, mode, start, stop):
        profile = {'walk': 'foot', 'foot': 'foot', 'bike': 'bike', 'car': 'car'}.get(mode)
        if not profile:
            raise GeometryError('Independent geometry supports car, bike and foot only')
        query = urlencode([('point', f"{start['lat']},{start['lon']}"), ('point', f"{stop['lat']},{stop['lon']}"), ('profile', profile), ('points_encoded', 'false')])
        with urlopen(self.base_url + '/route?' + query, timeout=self.timeout) as response:
            data = json.load(response)
        paths = data.get('paths') if isinstance(data, dict) else None
        if not isinstance(paths, list) or not paths or not isinstance(paths[0], dict):
            raise GeometryError('GraphHopper returned no usable path')
        path = paths[0]
        geometry = path.get('points')
        points = geometry.get('coordinates') if isinstance(geometry, dict) and geometry.get('type') == 'LineString' else None
        if not isinstance(points, list) or len(points) < 2:
            raise GeometryError('GraphHopper returned no unencoded LineString')
        for point in points:
            if not isinstance(point, list) or len(point) < 2 or any(type(v) not in (int, float) or not math.isfinite(v) for v in point[:2]):
                raise GeometryError('Invalid geometry coordinate')
            if not -180 <= point[0] <= 180 or not -90 <= point[1] <= 90:
                raise GeometryError('Geometry coordinate out of range')
        if profile != 'car' and (distance_m(points[0], [start['lon'], start['lat']]) > 250 or distance_m(points[-1], [stop['lon'], stop['lat']]) > 250):
            raise GeometryError('GraphHopper endpoints are too far from the requested trip')
        # GraphHopper can snap both foot endpoints to the same network point.
        # Accept only a genuinely empty path close to BOTH requested endpoints.
        empty_foot = (profile == 'foot' and path.get('distance') == 0 and path.get('time') == 0
                      and all(distance_m(point, points[0]) <= 1 for point in points)
                      and distance_m(points[0], [start['lon'], start['lat']]) <= 25
                      and distance_m(points[-1], [stop['lon'], stop['lat']]) <= 25)
        for name in ('distance', 'time'):
            value = path.get(name)
            if type(value) not in (int, float) or not math.isfinite(value) or (value <= 0 and not empty_foot):
                raise GeometryError('Invalid GraphHopper path metrics')
        result = {'geometry': copy.deepcopy(geometry), 'duration_seconds': path['time']/1000, 'distance_meters': path['distance'], 'profile': profile}
        if profile == 'car':
            driving = {'mode': 'car', 'geometry': copy.deepcopy(geometry), 'duration_seconds': result['duration_seconds'], 'distance_meters': result['distance_meters']}
            legs = []
            for a, b, role in ((start, {'lon': points[0][0], 'lat': points[0][1]}, 'access'),
                               ({'lon': points[-1][0], 'lat': points[-1][1]}, stop, 'egress')):
                # Only rounding-sized endpoint gaps are ignored. Every larger
                # gap needs a separately routed pedestrian path, never a chord.
                if distance_m([a['lon'], a['lat']], [b['lon'], b['lat']]) <= 5:
                    continue
                walk = self.path('foot', a, b)
                endpoints = walk['geometry']['coordinates']
                start_gap = distance_m(endpoints[0], [a['lon'], a['lat']])
                end_gap = distance_m(endpoints[-1], [b['lon'], b['lat']])
                # The junction between car and foot must match. A place origin
                # or destination (e.g. station centroid) can differ from the
                # network entrance. Keep that place endpoint unresolved,
                # with no invented final geometry, distance travelled or time.
                junction_gap = end_gap if role == 'access' else start_gap
                if junction_gap > 25:
                    raise GeometryError('Pedestrian access endpoints do not match the car journey')
                place_gap = start_gap if role == 'access' else end_gap
                if place_gap > 25:
                    endpoint = 'origin' if role == 'access' else 'destination'
                    result[endpoint + '_access_offset_m'] = place_gap
                    result[endpoint + '_access_time_unknown'] = True
                if walk['duration_seconds'] == 0:
                    continue
                legs.append({'mode': 'walk', 'access_role': role, **{k: walk[k] for k in ('geometry', 'duration_seconds', 'distance_meters')}})
            ordered = [leg for leg in legs if leg['access_role'] == 'access'] + [driving] + [leg for leg in legs if leg['access_role'] == 'egress']
            result.update(legs=ordered,
                          duration_seconds=sum(leg['duration_seconds'] for leg in ordered),
                          distance_meters=sum(leg['distance_meters'] for leg in ordered),
                          walk_duration_seconds=sum(leg['duration_seconds'] for leg in legs),
                          walk_distance_meters=sum(leg['distance_meters'] for leg in legs))
        return result


def supplemental_route(route, path):
    """Use geometry and timing from ONE source, preserving external ranking."""
    result = copy.deepcopy(route)
    mode = route['mode_key']
    summary = {name: path[name] for name in ('duration_seconds', 'distance_meters')}
    summary['transfers'] = 0
    if 'parking_assumption' in path:
        summary['parking_assumption'] = copy.deepcopy(path['parking_assumption'])
    for name in ('walk_duration_seconds', 'walk_distance_meters', 'origin_access_offset_m', 'origin_access_time_unknown', 'destination_access_offset_m', 'destination_access_time_unknown'):
        if name in path: summary[name] = path[name]
    original = route.get('summary') or {}
    comparison = {}
    for name in ('duration_seconds', 'distance_meters'):
        value = original.get(name)
        if type(value) in (int, float) and math.isfinite(value) and value > 0:
            comparison[name + '_relative_difference'] = abs(summary[name]-value)/value
    result['summary'] = summary
    result['geometry'] = None if len(path.get('legs', [])) > 1 else path['geometry']
    result['legs'] = copy.deepcopy(path.get('legs') or [{'mode': mode, **summary, 'geometry': path['geometry']}])
    result['_geometry_evidence'] = {
        **({'parking_assumption': copy.deepcopy(path['parking_assumption'])} if 'parking_assumption' in path else {}),
        'provider': 'graphhopper', 'geometry_provenance': 'GRAPHHOPPER_INDEPENDENT_PATH',
        'route_identity_verified': False, 'candidate_kind': 'supplemental_same_mode_path',
        'pedestrian_access_routed': bool(path.get('walk_duration_seconds')),
        'origin_access_offset_m': path.get('origin_access_offset_m', 0),
        'origin_access_time_unknown': path.get('origin_access_time_unknown', False),
        'destination_access_offset_m': path.get('destination_access_offset_m', 0),
        'destination_access_time_unknown': path.get('destination_access_time_unknown', False),
        'timing_source': 'graphhopper', 'reference_routing_summary': copy.deepcopy(original),
        'metric_comparison': comparison, 'profile': path['profile'],
    }
    return result


def independent_route(mode, path, rank):
    """A complete GraphHopper option with its own metrics and provenance."""
    summary = {name: path[name] for name in ('duration_seconds', 'distance_meters')}
    summary['transfers'] = 0
    if 'parking_assumption' in path:
        summary['parking_assumption'] = copy.deepcopy(path['parking_assumption'])
    for name in ('walk_duration_seconds', 'walk_distance_meters', 'origin_access_offset_m', 'origin_access_time_unknown', 'destination_access_offset_m', 'destination_access_time_unknown'):
        if name in path: summary[name] = path[name]
    return {
        'mode_key': mode, 'rank': rank, 'available': True, 'feasible': True,
        'provider': 'graphhopper', 'score': None, 'summary': summary,
        'geometry': None if len(path.get('legs', [])) > 1 else copy.deepcopy(path['geometry']),
        'legs': copy.deepcopy(path.get('legs') or [{'mode': mode, **summary, 'geometry': copy.deepcopy(path['geometry'])}]),
        '_geometry_evidence': {
            **({'parking_assumption': copy.deepcopy(path['parking_assumption'])} if 'parking_assumption' in path else {}),
            'provider': 'graphhopper', 'geometry_provenance': 'GRAPHHOPPER_INDEPENDENT_PATH',
            'route_identity_verified': True, 'candidate_kind': 'independent_mode_path',
            'timing_source': 'graphhopper', 'profile': path['profile'],
        },
    }
