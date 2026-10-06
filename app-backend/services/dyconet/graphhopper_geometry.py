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
        if distance_m(points[0], [start['lon'], start['lat']]) > 250 or distance_m(points[-1], [stop['lon'], stop['lat']]) > 250:
            raise GeometryError('GraphHopper endpoints are too far from the requested trip')
        for name in ('distance', 'time'):
            value = path.get(name)
            if type(value) not in (int, float) or not math.isfinite(value) or value <= 0:
                raise GeometryError('Invalid GraphHopper path metrics')
        return {'geometry': copy.deepcopy(geometry), 'duration_seconds': path['time']/1000, 'distance_meters': path['distance'], 'profile': profile}


def supplemental_route(route, path):
    """Use geometry and timing from ONE source, preserving external ranking."""
    result = copy.deepcopy(route)
    mode = route['mode_key']
    summary = {name: path[name] for name in ('duration_seconds', 'distance_meters')}
    summary['transfers'] = 0
    original = route.get('summary') or {}
    comparison = {}
    for name in ('duration_seconds', 'distance_meters'):
        value = original.get(name)
        if type(value) in (int, float) and math.isfinite(value) and value > 0:
            comparison[name + '_relative_difference'] = abs(summary[name]-value)/value
    result['summary'] = summary
    result['geometry'] = path['geometry']
    result['legs'] = [{'mode': mode, **summary, 'geometry': path['geometry']}]
    result['_geometry_evidence'] = {
        'provider': 'graphhopper', 'geometry_provenance': 'GRAPHHOPPER_INDEPENDENT_PATH',
        'route_identity_verified': False, 'candidate_kind': 'supplemental_same_mode_path',
        'timing_source': 'graphhopper', 'reference_routing_summary': copy.deepcopy(original),
        'metric_comparison': comparison, 'profile': path['profile'],
    }
    return result
