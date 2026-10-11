import io
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from graphhopper_geometry import GraphHopperGeometryClient, GeometryError, supplemental_route


class CarPedestrianAccessTest(unittest.TestCase):
    def test_basic_simulation_without_routes_never_queries_route_context(self):
        from dataclasses import replace
        from contextual_deliberation import ContextualDeliberationService
        from test_context_simulation_reuse import request_fixture
        from unittest.mock import Mock
        provider = Mock()
        request = replace(request_fixture(), candidate_routes=(), context_policy='observations_only')
        result = ContextualDeliberationService(provider=provider).deliberate(request).to_dict()
        self.assertEqual(result['candidate_results'], [])
        self.assertIn('topology', result['baseline']['simulation_evidence'])
        self.assertIn('final', result['baseline']['node_activations']['needs'])
        self.assertEqual(provider.mock_calls, [])

    def response(self, url, **kwargs):
        query = parse_qs(urlsplit(url).query)
        points = [[float(p.split(',')[1]), float(p.split(',')[0])] for p in query['point']]
        car = query['profile'] == ['car']
        if car:
            points[-1][0] -= .005
        return io.BytesIO(json.dumps({'paths': [{'points': {'type': 'LineString', 'coordinates': points},
                                               'time': 600000 if car else 180000,
                                               'distance': 5000 if car else 400}]}).encode())

    @patch('graphhopper_geometry.urlopen')
    def test_verified_egress_is_routed_and_included_in_displayed_totals(self, http):
        http.side_effect = self.response
        path = GraphHopperGeometryClient('http://localhost:9999').path('car', {'lat': 52., 'lon': 11.}, {'lat': 52.01, 'lon': 11.02})
        self.assertEqual([leg['mode'] for leg in path['legs']], ['car', 'walk'])
        self.assertEqual(path['walk_duration_seconds'], 180)
        self.assertEqual(path['duration_seconds'], 780)
        self.assertEqual(http.call_count, 2)
        original = {'mode_key': 'car', 'summary': {'duration_seconds': 600, 'distance_meters': 5000}}
        route = supplemental_route(original, path)
        self.assertEqual(route['summary']['duration_seconds'], 780)
        self.assertEqual(route['legs'][-1]['access_role'], 'egress')
        self.assertIsNone(route['geometry'])
        self.assertEqual(original['summary']['duration_seconds'], 600)

    @patch('graphhopper_geometry.urlopen')
    def test_walking_endpoint_gap_is_not_silently_bridged(self, http):
        def displaced(url, **kwargs):
            body = json.load(self.response(url))
            if parse_qs(urlsplit(url).query)['profile'] == ['foot']:
                body['paths'][0]['points']['coordinates'][0][0] -= .001
            return io.BytesIO(json.dumps(body).encode())
        http.side_effect = displaced
        with self.assertRaisesRegex(GeometryError, 'Pedestrian access endpoints'):
            GraphHopperGeometryClient('http://localhost:9999').path('car', {'lat': 52., 'lon': 11.}, {'lat': 52.01, 'lon': 11.02})

    @patch('graphhopper_geometry.urlopen')
    def test_destination_entrance_keeps_geometry_and_marks_unrouted_final_access(self, http):
        def entrance(url, **kwargs):
            body = json.load(self.response(url))
            if parse_qs(urlsplit(url).query)['profile'] == ['foot']:
                body['paths'][0]['points']['coordinates'][-1][0] -= .001
            return io.BytesIO(json.dumps(body).encode())
        http.side_effect = entrance
        path = GraphHopperGeometryClient('http://localhost:9999').path('car', {'lat': 52., 'lon': 11.}, {'lat': 52.01, 'lon': 11.02})
        self.assertTrue(path['destination_access_time_unknown'])
        self.assertGreater(path['destination_access_offset_m'], 25)
        self.assertEqual(path['duration_seconds'], 780)
        self.assertNotEqual(path['legs'][-1]['geometry']['coordinates'][-1], [11.02, 52.01])
        route = supplemental_route({'mode_key': 'car'}, path)
        self.assertTrue(route['summary']['destination_access_time_unknown'])

    @patch('graphhopper_geometry.urlopen')
    def test_origin_entrance_gap_and_zero_length_egress_keep_real_route(self, http):
        origin = {'lat': 52., 'lon': 11.}
        stop = {'lat': 52.01, 'lon': 11.02}
        driving = [[11.002, 52.], [11.02, 52.00985]]
        walking = [[11.001, 52.], driving[0]]
        def body(points, time, distance):
            return io.BytesIO(json.dumps({'paths': [{'points': {'type': 'LineString', 'coordinates': points},
                                                    'time': time, 'distance': distance}]}).encode())
        http.side_effect = [body(driving, 600000, 5000), body(walking, 180000, 400),
                            body([driving[-1], driving[-1]], 0, 0)]
        path = GraphHopperGeometryClient('http://localhost:9999').path('car', origin, stop)
        self.assertEqual([leg['mode'] for leg in path['legs']], ['walk', 'car'])
        self.assertTrue(path['origin_access_time_unknown'])
        self.assertGreater(path['origin_access_offset_m'], 25)
        self.assertEqual(path['duration_seconds'], 780)
        self.assertEqual(path['legs'][0]['geometry']['coordinates'], walking)
        route = supplemental_route({'mode_key': 'car'}, path)
        self.assertTrue(route['summary']['origin_access_time_unknown'])
        self.assertEqual(route['_geometry_evidence']['origin_access_offset_m'], path['origin_access_offset_m'])

    @patch('graphhopper_geometry.urlopen')
    def test_zero_length_foot_cannot_hide_a_large_endpoint_gap(self, http):
        points = [[11., 52.], [11., 52.]]
        http.return_value = io.BytesIO(json.dumps({'paths': [{'points': {'type': 'LineString', 'coordinates': points},
                                                            'time': 0, 'distance': 0}]}).encode())
        with self.assertRaises(GeometryError):
            GraphHopperGeometryClient('http://localhost:9999').path('foot', {'lat': 52., 'lon': 11.}, {'lat': 52., 'lon': 11.002})

    @patch('graphhopper_geometry.urlopen')
    def test_unavailable_walking_path_does_not_fabricate_an_access(self, http):
        http.side_effect = [self.response('http://localhost/route?profile=car&point=52,11&point=52.01,11.02'), OSError('Unavailable')]
        with self.assertRaises(OSError):
            GraphHopperGeometryClient('http://localhost:9999').path('car', {'lat': 52., 'lon': 11.}, {'lat': 52.01, 'lon': 11.02})
