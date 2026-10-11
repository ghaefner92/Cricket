import copy
import datetime
import unittest
from unittest.mock import patch
from transit_stop_api import plan_from_stop,associate_platforms,register_transit_stops
from otp_transit import OtpTransitClient,OtpError,adapt_itineraries
from graphhopper_geometry import GeometryError

WHEN='2026-10-11T12:00:00+00:00'
BASE=datetime.datetime.fromisoformat(WHEN).timestamp()*1000
STOP={'gtfsId':'feed:S','name':'Exact platform','lat':52.001,'lon':11.0}
AVAIL={'walk':True,'bike':True,'car':True,'pt':True}
def route(stop_id='feed:S'):
    t={'from':{'name':'Exact platform','lat':52.001,'lon':11.,'stop':{'gtfsId':stop_id}},'to':{'name':'Destination','lat':52.01,'lon':11.},
       'gtfs_trip_id':'trip:1','scheduled_start_time_ms':BASE+600000,'start_time_ms':BASE+600000,'end_time_ms':BASE+1200000}
    return {'legs':[{'mode':'pt','transit':t,'duration_seconds':600,'distance_meters':1000,'geometry':{'type':'LineString','coordinates':[[11.,52.001],[11.,52.01]]}}],
            'summary':{'duration_seconds':600,'distance_meters':1000,'walk_distance_meters':0},
            'transit':{'start_time_ms':BASE+600000,'end_time_ms':BASE+1200000},'_geometry_evidence':{}}
class Client:
    base='http://localhost:8091'
    def request(self,*args):return {'data':{'stops':[STOP]}}
    def routes(self,**kwargs):self.options=kwargs;return [route('wrong:S'),route()],{'captured_at':WHEN,'status':'AVAILABLE'}
class Geometry:
    def path(self,mode,a,b):return {'duration_seconds':60,'distance_meters':100,'geometry':{'type':'LineString','coordinates':[[a['lon'],a['lat']],[b['lon'],b['lat']]]}}
def payload():return {'boarding_stop_id':'feed:S','start':{'lat':52.,'lon':11.},'stop':{'lat':52.01,'lon':11.},'datetime':WHEN,'availability':AVAIL,'max_walk_m':1500}

class TransitStopsTest(unittest.TestCase):
    def test_access_must_join_actual_first_geometry_not_only_stop_metadata(self):
        class Disconnected(Client):
            def routes(self,**kwargs):
                routes,audit=super().routes(**kwargs)
                routes[-1]['legs'][0]['geometry']['coordinates'][0]=[11.001,52.001]
                return routes,audit
        planned,_,_=plan_from_stop(payload(),Disconnected(),Geometry())
        self.assertEqual(planned,[])
    def test_inconsistent_provider_times_and_disconnected_legs_are_rejected(self):
        points='_p~iF~ps|U_ulLnnqC_mqNvxq'+chr(96)+'@'
        leg={'mode':'BUS','distance':1000,'duration':600,'startTime':BASE,'endTime':BASE+600000,
             'from':{'lat':38.5,'lon':-120.2},'to':{'lat':43.252,'lon':-126.453},
             'trip':{'gtfsId':'trip:1'},'legGeometry':{'points':points}}
        it={'duration':600,'walkDistance':0,'startTime':BASE,'endTime':BASE+600000,'numberOfTransfers':0,'waitingTime':0,'legs':[leg]}
        def reason(value):
            routes,audit=adapt_itineraries({'data':{'plan':{'itineraries':[value]}}},AVAIL,1500,False)
            self.assertEqual(routes,[])
            return audit['rejected_itineraries'][0]['reason']
        changed=copy.deepcopy(it);changed['duration']=500
        self.assertIn('Itinerary duration',reason(changed))
        changed=copy.deepcopy(it);changed['legs'][0]['duration']=500
        self.assertIn('Leg duration',reason(changed))
        changed=copy.deepcopy(it);changed['legs'][0]['startTime']+=3000;changed['legs'][0]['endTime']+=3000
        self.assertIn('outside itinerary',reason(changed))
        changed=copy.deepcopy(it);changed['duration']=1200;changed['endTime']+=600000;changed['walkDistance']=1000
        walk=copy.deepcopy(leg);walk.update(mode='WALK',startTime=BASE+600000,endTime=BASE+1200000)
        changed['legs'].append(walk)
        self.assertIn('Disconnected',reason(changed))
    def test_transit_geometry_rounding_is_anchored_without_bridging_missing_paths(self):
        points='_p~iF~ps|U_ulLnnqC_mqNvxq'+chr(96)+'@'
        leg={'mode':'TRAM','distance':1000,'duration':600,'startTime':BASE,'endTime':BASE+600000,
             'from':{'lat':38.500004,'lon':-120.2,'stop':{'gtfsId':'a'}},'to':{'lat':43.252,'lon':-126.453,'stop':{'gtfsId':'b'}},
             'trip':{'gtfsId':'trip:1'},'legGeometry':{'points':points}}
        it={'duration':600,'walkDistance':0,'startTime':BASE,'endTime':BASE+600000,'numberOfTransfers':0,'waitingTime':0,'legs':[leg]}
        raw={'data':{'plan':{'itineraries':[it]}}}
        routes,_=adapt_itineraries(raw,AVAIL,1500,False)
        self.assertEqual(routes[0]['legs'][0]['geometry']['coordinates'][0],[-120.2,38.500004])
        self.assertEqual(routes[0]['provider_option'],1)
        leg['from']['lat']=38.502
        routes,audit=adapt_itineraries(raw,AVAIL,1500,False)
        self.assertEqual(routes,[]);self.assertIn('does not reach',audit['rejected_itineraries'][0]['reason'])
    def test_platform_popup_combines_only_its_linked_gtfs_stops(self):
        from flask import Flask
        app=Flask(__name__);register_transit_stops(app)
        catalog=[{**STOP,'waiting_platform':{'id':'way/p'}},{**STOP,'gtfsId':'feed:S2','waiting_platform':{'id':'way/p'}},{**STOP,'gtfsId':'wrong','waiting_platform':{'id':'way/other'}}]
        seen=[]
        def planned(p):
            seen.append(p['boarding_stop_id']);r=route(p['boarding_stop_id']);r['provider_itinerary_id']=p['boarding_stop_id']
            return [r],{'captured_at':WHEN},STOP
        with patch('transit_stop_api.stop_catalog',return_value=catalog),patch('transit_stop_api.plan_from_stop',side_effect=planned):
            response=app.test_client().post('/api/dyconet/transit/stop-itineraries',json={**payload(),'boarding_platform_id':'way/p'})
        self.assertEqual(response.status_code,200);self.assertEqual(seen,['feed:S','feed:S2'])
        self.assertEqual(len(response.json['routes']),2)
    def test_waiting_platform_requires_unique_explicit_relation(self):
        vehicle={'id':'node/v','kind':'stop','lat':52.001,'lon':11.,'tags':{'public_transport':'stop_position'}}
        platform={'id':'way/p','kind':'stop','lat':52.001,'lon':11.0002,'tags':{'public_transport':'platform'}}
        data={'points':[vehicle,platform],'stop_platform_pairs':[{'stop':'node/v','platform':'way/p'}]}
        linked=associate_platforms([STOP],data)[0]
        self.assertEqual(linked['waiting_platform']['id'],'way/p')
        self.assertEqual(linked['lat'],STOP['lat'])
        self.assertNotIn('waiting_platform',associate_platforms([STOP],{'points':data['points']})[0])
        other={**platform,'id':'way/other'}
        data['points'].append(other);data['stop_platform_pairs'].append({'stop':'node/v','platform':'way/other'})
        self.assertNotIn('waiting_platform',associate_platforms([STOP],data)[0])
    def test_exact_stop_and_access_schedule_without_double_counting_wait(self):
        c=Client();routes,audit,_=plan_from_stop(payload(),client=c,geometry=Geometry())
        self.assertEqual(len(routes),1)
        r=routes[0]
        self.assertEqual([l['mode'] for l in r['legs']],['walk','pt'])
        self.assertEqual(r['summary']['duration_seconds'],660)
        self.assertEqual(r['transit']['start_time_ms'],BASE+540000)
        self.assertEqual(r['legs'][0]['transit']['end_time_ms'],r['legs'][1]['transit']['start_time_ms'])
        self.assertEqual(c.options['boarding_stop_id'],'feed:S')
        self.assertEqual(datetime.datetime.fromisoformat(c.options['departure']).timestamp(),BASE/1000+60)
        self.assertEqual(r['summary']['walk_distance_meters'],100)
    def test_unknown_stop_rejected(self):
        p=payload();p['boarding_stop_id']='nonexistent'
        with self.assertRaises(OtpError):plan_from_stop(p,client=Client(),geometry=Geometry())
    def test_selected_trip_and_walking_limit_are_enforced(self):
        p=payload();p['boarding_trip_id']='other-trip'
        self.assertEqual(plan_from_stop(p,client=Client(),geometry=Geometry())[0],[])
        p=payload();p['max_walk_m']=99
        with self.assertRaises(OtpError):plan_from_stop(p,client=Client(),geometry=Geometry())
    def test_missed_departure_cannot_be_selected(self):
        p=payload();p['datetime']='2026-10-11T12:10:00+00:00'
        self.assertEqual(plan_from_stop(p,client=Client(),geometry=Geometry())[0],[])
    def test_walking_must_reach_actual_platform(self):
        class Wrong(Geometry):
            def path(self,*args):
                r=super().path(*args);r['geometry']['coordinates'][-1][1]+=.001;return r
        with self.assertRaises(GeometryError):plan_from_stop(payload(),client=Client(),geometry=Wrong())
    def test_graphql_uses_exact_stop_id_without_coordinate_fallback(self):
        class Empty(OtpTransitClient):
            def status(self):return None
            def request(self,url,data=None):self.last=data;return {'data':{'plan':{'itineraries':[]}}}
        c=Empty();c.routes(start={'lat':52.,'lon':11.},stop={'lat':52.01,'lon':11.},departure=WHEN,availability=AVAIL,max_walk=1500,boarding_stop_id='feed:S')
        self.assertIn('fromPlace: $fromPlace',c.last['query'])
        self.assertNotIn('from',c.last['variables'])
        self.assertEqual(c.last['variables']['fromPlace'],'feed:S')

if __name__=='__main__':unittest.main()
