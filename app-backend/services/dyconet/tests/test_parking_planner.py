import unittest
from unittest.mock import patch
from parking_planner import select_parking, parking_path, GeometryError

def poi(identity, lon, access='yes'):
    return {'id': identity, 'kind': 'parking', 'lat': 52., 'lon': lon, 'name': identity,
            'position_kind': 'node', 'tags': {'access': access}}

class ParkingPlannerTest(unittest.TestCase):
    def data(self):
        return {'source': 'OSM fixture', 'osm_base': '2026-10-11', 'points':
                [poi('private',11.0001,'private'), poi('unknown',11.0002,'unknown'),
                 poi('public',11.001),poi('destination',11.02)]}

    @patch('parking_planner.dataset')
    def test_nearest_public_excludes_private_and_unknown(self, data):
        data.return_value=self.data()
        self.assertEqual(select_parking({'lat':52.,'lon':11.})['id'],'public')
        self.assertIsNone(select_parking({'lat':50.,'lon':11.}))

    @patch('parking_planner.dataset')
    def test_user_can_select_unknown_but_not_restricted_or_outside_radius(self, data):
        data.return_value=self.data()
        location={'lat':52.,'lon':11.}
        self.assertEqual(select_parking(location,'unknown')['id'],'unknown')
        for identity in ('private','nonexistent'):
            with self.assertRaises(GeometryError):select_parking(location,identity)
        with self.assertRaises(GeometryError):select_parking({'lat':50.,'lon':11.},'public')

    @patch('parking_planner.dataset')
    def test_walking_and_parking_assumptions_are_preserved(self, data):
        data.return_value=self.data()
        class Client:
            def path(self,mode,a,b):
                geometry={'type':'LineString','coordinates':[[a['lon'],a['lat']],[b['lon'],b['lat']]]}
                result={'geometry':geometry,'duration_seconds':100 if mode=='car' else 40,'distance_meters':1000 if mode=='car' else 100}
                if mode=='car':result['legs']=[{'mode':'car',**result}]
                return result
        start={'lat':52.,'lon':11.};stop={'lat':52.,'lon':11.021}
        result=parking_path(Client(),start,stop,{'policy':'nearest-public-osm-magdeburg-v1'})
        self.assertEqual([l['mode'] for l in result['legs']],['walk','car','walk'])
        self.assertEqual(result['duration_seconds'],180)
        self.assertEqual(result['walk_duration_seconds'],80)
        self.assertEqual(result['parking_assumption']['origin']['id'],'public')
        self.assertFalse(result['parking_assumption']['actual_vehicle_location_known'])
        self.assertEqual(start,{'lat':52.,'lon':11.})

    @patch('parking_planner.dataset')
    def test_missing_public_parking_does_not_fabricate_a_route(self, data):
        data.return_value={**self.data(),'points':[]}
        with self.assertRaises(GeometryError):
            parking_path(None,{'lat':52.,'lon':11.},{'lat':52.,'lon':11.01},{'policy':'nearest-public-osm-magdeburg-v1'})

if __name__=='__main__':unittest.main()
