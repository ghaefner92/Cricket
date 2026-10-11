import copy
import unittest
from tram_alignment import match_tram

def network():
    return {'osm_base':'2026-10-11T13:22:06Z','elements':[
        {'type':'node','id':10,'lon':11.,'lat':52.},
        {'type':'node','id':11,'lon':11.001,'lat':52.001},
        {'type':'way','id':9,'nodes':[1,2,3],'tags':{'railway':'tram','oneway':'yes'},
         'geometry':[{'lon':11.,'lat':52.},{'lon':11.001,'lat':52.},{'lon':11.001,'lat':52.001}]},
        {'type':'relation','id':20,'tags':{'ref':'6'},'members':[
            {'type':'node','ref':10,'role':'stop'},{'type':'node','ref':11,'role':'stop'},
            {'type':'way','ref':9,'role':''}]}]}
A=[11.,52.];B=[11.001,52.001]

class TramAlignmentTest(unittest.TestCase):
    def test_trip_slice_preserves_order_and_rejects_ambiguous_or_reversed_ids(self):
        from transit_stop_api import bounded_trip_stops
        stops=[{'gtfsId':str(i)} for i in range(5)]
        self.assertEqual(bounded_trip_stops(stops,'1','3'),stops[1:4])
        self.assertEqual(bounded_trip_stops(stops,'3','1'),[])
        self.assertEqual(bounded_trip_stops(stops+[{'gtfsId':'3'}],'1','3'),[])
        self.assertEqual(bounded_trip_stops(stops,'missing','3'),[])

    def test_gtfs_confirmed_special_tram_can_use_physical_network_without_osm_line_relation(self):
        data=network();data['timetable_tram_lines']=['13']
        geometry,evidence=match_tram('13',A,B,[A,B],data)
        self.assertEqual(evidence['source'],'OSM_REFERENCE_NETWORK')
        self.assertFalse(evidence['line_relation_matched'])
        self.assertFalse(evidence['service_shape_verified'])
        self.assertIsNone(match_tram('48',A,B,[A,B],data))

    def test_terminal_return_follows_ordered_anchors_without_endpoint_shortcut(self):
        data=network();way=data['elements'][2];turn=[11.003,52.003]
        data['elements'][1].update(lon=turn[0],lat=turn[1])
        way.update(nodes=[1,2,3,4,1],geometry=[
            {'lon':11.,'lat':52.},{'lon':11.003,'lat':52.},
            {'lon':11.003,'lat':52.003},{'lon':11.,'lat':52.003},{'lon':11.,'lat':52.}])
        geometry,evidence=match_tram('6',A,A,[A,turn,A],data)
        self.assertIn(turn,geometry['coordinates'])
        self.assertIn([11.,52.003],geometry['coordinates'])
        self.assertEqual(geometry['coordinates'][0],geometry['coordinates'][-1])
        self.assertEqual(evidence['piece_count'],2)
        self.assertFalse(evidence['service_shape_verified'])

    def test_geometry_cache_reuses_work_but_never_shares_mutable_results(self):
        from unittest.mock import patch
        from tram_alignment import _cached_match,_match_line_tram
        _cached_match.cache_clear()
        with patch('tram_alignment.dataset',return_value=network()),patch('tram_alignment._match_line_tram',wraps=_match_line_tram) as compute:
            first=match_tram('6',A,B,[A,B]);first[0]['coordinates'][0][0]=99
            second=match_tram('6',A,B,[A,B])
            self.assertEqual(second[0]['coordinates'][0][0],11.)
            self.assertEqual(compute.call_count,1)
            self.assertIsNone(match_tram('4',A,B,[A,B]))
            self.assertEqual(compute.call_count,2)
        _cached_match.cache_clear()
    def test_follows_bent_rail_instead_of_straight_stop_connection(self):
        original=[A,B];before=copy.deepcopy(original)
        geometry,evidence=match_tram('6',A,B,original,network())
        self.assertIn([11.001,52.],geometry['coordinates'])
        self.assertEqual(original,before)
        self.assertFalse(evidence['vehicle_path_observed']);self.assertFalse(evidence['service_shape_verified'])
    def test_other_line_or_opposite_direction_is_not_substituted(self):
        self.assertIsNone(match_tram('4',A,B,[A,B],network()))
        self.assertIsNone(match_tram('6',B,A,[B,A],network()))
    def test_off_line_intermediate_stop_rejects_alignment(self):
        self.assertIsNone(match_tram('6',A,B,[A,[11.003,52.],B],network()))
    def test_incomplete_track_does_not_invent_connection(self):
        data=network();data['elements'][2]['nodes']=[1,2];data['elements'][2]['geometry'].pop()
        self.assertIsNone(match_tram('6',A,B,[A,B],data))
    def test_split_line_uses_connected_reference_not_nearest_dead_end(self):
        data=network();data['elements'][3]['tags']['ref']='1S'
        data['elements'][3]['members'][:2]=data['elements'][3]['members'][:2][::-1]
        data['elements'].append({'type':'way','id':90,'nodes':[90,91],
            'tags':{'railway':'tram','oneway':'yes'},'geometry':[
                {'lon':11.,'lat':52.00005},{'lon':11.0002,'lat':52.00005}]})
        start=[11.,52.00005]
        geometry,evidence=match_tram('1',start,B,[start,A,B],data)
        self.assertIn([11.001,52.],geometry['coordinates'])
        self.assertEqual(evidence['source'],'OSM_REFERENCE_NETWORK')
        self.assertFalse(evidence['line_relation_matched'])
        self.assertFalse(evidence['service_shape_verified'])
        self.assertLess(evidence['boarding_offset_m'],6)
    def test_network_rejects_two_different_compatible_corridors(self):
        data=network();data['elements'][3]['tags']['ref']='1S'
        data['elements'][3]['members'][:2]=data['elements'][3]['members'][:2][::-1]
        data['elements'][1].update(lon=11.002,lat=52.002)
        data['elements'][2].update(nodes=[1,2,3],geometry=[
            {'lon':11.,'lat':52.},{'lon':11.002,'lat':52.},{'lon':11.002,'lat':52.002}])
        data['elements'].append({'type':'way','id':91,'nodes':[1,4,3],
            'tags':{'railway':'tram','oneway':'yes'},'geometry':[
                {'lon':11.,'lat':52.},{'lon':11.,'lat':52.002},{'lon':11.002,'lat':52.002}]})
        end=[11.002,52.002]
        self.assertIsNone(match_tram('1',A,end,[A,end],data))
    def test_display_endpoint_returns_reference_without_rewriting_request(self):
        from flask import Flask
        from transit_stop_api import register_transit_stops
        from unittest.mock import patch
        app=Flask(__name__);register_transit_stops(app)
        request={'legs':[{'route_id':'1-pt','leg_index':1,'line':'6','from':{'lat':52.,'lon':11.},'to':{'lat':52.001,'lon':11.001},'geometry':{'type':'LineString','coordinates':[A,B]}}]}
        before=copy.deepcopy(request)
        with patch('tram_alignment.dataset',return_value=network()):
            response=app.test_client().post('/api/dyconet/transit/tram-alignment',json=request)
        self.assertEqual(response.status_code,200);self.assertEqual(len(response.json['alignments']),1)
        self.assertEqual(request,before)

if __name__=='__main__':unittest.main()
