"""Exact GTFS boarding and scheduled itineraries; no choice or feedback writes."""
import copy
import datetime
import threading
import time
from flask import jsonify, request
from otp_transit import OtpTransitClient, OtpError
from graphhopper_geometry import GraphHopperGeometryClient, distance_m, GeometryError

_lock=threading.Lock()
_cache=None
_cache_time=0

def associate_platforms(stops, data):
    """Only explicit stop/platform route pairs; proximity alone never picks a sidewalk."""
    points={p['id']:p for p in data['points']}
    waiting={k:p for k,p in points.items() if p['kind']=='stop' and p['tags'].get('public_transport')=='platform'}
    pairs={}
    for pair in data.get('stop_platform_pairs',[]):
        if pair['platform'] in waiting:pairs.setdefault(pair['stop'],set()).add(pair['platform'])
    vehicles=[points[k] for k in pairs if k in points and points[k]['tags'].get('public_transport')=='stop_position']
    result=copy.deepcopy(stops)
    for s in result:
        exact=[p for p in waiting.values() if s.get('code') and s['code']==p['tags'].get('ref:IFOPT')]
        direct=sorted((distance_m([s['lon'],s['lat']],[p['lon'],p['lat']]),p['id']) for p in waiting.values())
        candidates=exact
        method='IFOPT'
        if not candidates and direct and direct[0][0]<=3 and (len(direct)==1 or direct[1][0]-direct[0][0]>=5):
            candidates=[waiting[direct[0][1]]];method='coincident_platform'
        if not candidates:
            near=sorted((distance_m([s['lon'],s['lat']],[p['lon'],p['lat']]),p['id']) for p in vehicles)
            if near and near[0][0]<=15 and (len(near)==1 or near[1][0]-near[0][0]>=5):
                candidates=[waiting[k] for k in pairs[near[0][1]]];method='OSM_route_stop_platform_pair'
        if len(candidates)==1:
            s['waiting_platform']=copy.deepcopy(candidates[0]);s['platform_association']=method
    return result

def stop_catalog(client=None):
    global _cache,_cache_time
    with _lock:
        if client is None and _cache is not None and time.monotonic()-_cache_time<600:return copy.deepcopy(_cache)
        c=client or OtpTransitClient()
        raw=c.request(c.base+'/otp/routers/default/index/graphql',{'query':'{ stops { gtfsId name lat lon code } }'})
        if raw.get('errors') or not isinstance(raw.get('data',{}).get('stops'),list):raise OtpError('Stop registry unavailable')
        stops=raw['data']['stops']
        if client is None:
            from parking_planner import dataset
            stops=associate_platforms(stops,dataset())
        if client is None:_cache,_cache_time=copy.deepcopy(stops),time.monotonic()
        return stops

def first_boarding(route):
    return next((l['transit'] for l in route['legs'] if l['mode']=='pt'),None)

def plan_from_stop(payload,client=None,geometry=None):
    c=client or OtpTransitClient()
    catalog=stop_catalog(client)
    selected=next((s for s in catalog if s['gtfsId']==payload['boarding_stop_id']),None)
    if selected is None:raise OtpError('Unknown GTFS stop')
    start,stop=payload['start'],payload['stop']
    from journey_availability import validate_journey_availability
    available=validate_journey_availability(payload['availability'])
    max_walk=payload.get('max_walk_m',1500)
    if type(max_walk) is not int or not 0<=max_walk<=20000:raise OtpError('Invalid walking limit')
    from routing_bridge import _coordinate, _datetime
    _coordinate(start);_coordinate(stop)
    departure=_datetime(payload.get('boarding_plan_datetime') or payload['datetime'])
    when=datetime.datetime.fromisoformat(departure.replace('Z','+00:00'))
    access=None
    if distance_m([start['lon'],start['lat']],[selected['lon'],selected['lat']])>5:
        if not available['walk']:raise OtpError('Walking access is disabled')
        access=(geometry or GraphHopperGeometryClient()).path('foot',start,selected)
        end=access['geometry']['coordinates'][-1]
        if distance_m(end,[selected['lon'],selected['lat']])>25:raise GeometryError('Walking path cannot reach the selected boarding stop')
    seconds=access['duration_seconds'] if access else 0
    metres=access['distance_meters'] if access else 0
    if metres>max_walk:raise OtpError('Walking access exceeds your walking limit')
    reached=when+datetime.timedelta(seconds=seconds)
    routes,audit=c.routes(start={'lat':selected['lat'],'lon':selected['lon']},stop=stop,departure=reached.isoformat(),
                          availability=available,max_walk=max_walk,boarding_stop_id=selected['gtfsId'])
    combined=[]
    for source in routes:
        route=copy.deepcopy(source)
        board=first_boarding(route)
        if not board or ((board.get('from') or {}).get('stop') or {}).get('gtfsId')!=selected['gtfsId']:continue
        if payload.get('boarding_trip_id') and board.get('gtfs_trip_id')!=payload['boarding_trip_id']:continue
        if payload.get('boarding_departure_ms') and board.get('scheduled_start_time_ms')!=payload['boarding_departure_ms']:continue
        if access and distance_m(access['geometry']['coordinates'][-1],route['legs'][0]['geometry']['coordinates'][0])>25:continue
        walking=route['summary']['walk_distance_meters']+metres
        if walking>max_walk:continue
        start_ms=route['transit']['start_time_ms']-seconds*1000
        if start_ms<when.timestamp()*1000-2000:continue
        if access and seconds>0:
            route['legs'].insert(0,{'mode':'walk','duration_seconds':seconds,'distance_meters':metres,
             'geometry':access['geometry'],'transit':{'source_mode':'WALK','from':{'name':'Origin',**start},
             'to':{'name':selected['name'],'lat':selected['lat'],'lon':selected['lon'],'stop':{'gtfsId':selected['gtfsId']}},
             'start_time_ms':start_ms,'end_time_ms':start_ms+seconds*1000,'geometry_quality':'OSM_ROUTED'}})
            origin_gap=distance_m(access['geometry']['coordinates'][0],[start['lon'],start['lat']])
            if origin_gap>25:route['summary'].update(origin_access_offset_m=origin_gap,origin_access_time_unknown=True)
        route['summary'].update(duration_seconds=(route['transit']['end_time_ms']-start_ms)/1000,
                                distance_meters=route['summary']['distance_meters']+metres,walk_distance_meters=walking)
        route['transit']['start_time_ms']=start_ms
        route['transit']['unallocated_duration_seconds']=max(0,route['summary']['duration_seconds']-sum(l['duration_seconds'] for l in route['legs']))
        route['transit']['selected_boarding_stop']=copy.deepcopy(selected)
        route['_geometry_evidence']['selected_boarding_stop']=copy.deepcopy(selected)
        combined.append(route)
    return combined,{**audit,'selected_boarding_stop':selected,'status':'AVAILABLE' if combined else 'NO_MATCHING_ITINERARIES'},selected

def bounded_trip_stops(stops,from_id,to_id):
    pairs=[(a,b) for a,s in enumerate(stops) if s.get('gtfsId')==from_id
           for b,t in enumerate(stops) if b>a and t.get('gtfsId')==to_id]
    if len(pairs)!=1:return []
    a,b=pairs[0]
    return copy.deepcopy(stops[a:b+1])

from functools import lru_cache
@lru_cache(maxsize=128)
def _trip_stops(base,trip,bucket):
    c=OtpTransitClient()
    raw=c.request(base+'/otp/routers/default/index/graphql',{
        'query':'query($id:String!){ trip(id:$id) { gtfsId stops { gtfsId name lat lon } } }',
        'variables':{'id':trip}})
    data=(raw.get('data') or {}).get('trip') or {}
    if raw.get('errors') or data.get('gtfsId')!=trip:raise OtpError('Trip stops unavailable')
    return data.get('stops',[])

def register_transit_stops(app):
    @app.post('/api/dyconet/transit/route-stops')
    def route_stops():
        try:
            legs=request.get_json(force=True)['legs']
            if not isinstance(legs,list) or len(legs)>30:raise OtpError('Invalid leg list')
            result=[];c=OtpTransitClient()
            for item in legs:
                trip=item['trip_id'];a=item['from_id'];b=item['to_id']
                if not all(isinstance(v,str) and 0<len(v)<=200 for v in (trip,a,b)):raise OtpError('Invalid trip identity')
                try:stops=bounded_trip_stops(_trip_stops(c.base,trip,int(time.monotonic()//600)),a,b)
                except (OtpError,OSError):stops=[]
                result.append({'leg_index':item['leg_index'],'stops':stops})
            return jsonify(legs=result,source='GTFS trip stop sequence',display_only=True)
        except (ValueError,TypeError,KeyError):return jsonify(message='Trip stops unavailable'),422

    @app.post('/api/dyconet/transit/tram-alignment')
    def tram_geometry():
        try:
            payload=request.get_json(force=True)
            legs=payload['legs']
            if not isinstance(legs,list) or len(legs)>100:raise OtpError('Invalid geometry request')
            from tram_alignment import match_tram
            from routing_bridge import _coordinate
            aligned=[]
            for item in legs:
                a,b=item['from'],item['to'];_coordinate(a);_coordinate(b)
                points=item['geometry']['coordinates']
                if not isinstance(points,list) or not 2<=len(points)<=5000:raise OtpError('Invalid geometry anchors')
                for p in points:
                    if not isinstance(p,list) or len(p)!=2:raise OtpError('Invalid anchor')
                    _coordinate({'lon':p[0],'lat':p[1]})
                match=match_tram(item['line'],[a['lon'],a['lat']],[b['lon'],b['lat']],points)
                if match:aligned.append({'route_id':item['route_id'],'leg_index':item['leg_index'],'geometry':match[0],'reference_alignment':match[1]})
            return jsonify(alignments=aligned)
        except (ValueError,TypeError,KeyError):return jsonify(message='Reference tram alignment unavailable.'),422

    @app.get('/api/dyconet/transit/stops')
    def stops():
        try:return jsonify(stops=stop_catalog(),source='GTFS · MVB',captured_at=datetime.datetime.now(datetime.timezone.utc).isoformat())
        except (OSError,ValueError,KeyError) as e:return jsonify(message='Stop information is temporarily unavailable.'),503

    @app.post('/api/dyconet/transit/stop-itineraries')
    def itineraries():
        try:
            payload=request.get_json(force=True)
            if not isinstance(payload,dict):raise OtpError('Invalid planning request')
            if payload.get('boarding_platform_id'):
                selected=[s for s in stop_catalog() if s.get('waiting_platform',{}).get('id')==payload['boarding_platform_id']]
                if not selected:raise OtpError('No unambiguous platform association')
                routes=[];failed=0;captured_at=datetime.datetime.now(datetime.timezone.utc).isoformat()
                for s in selected:
                    try:
                        planned,audit,_=plan_from_stop({**payload,'boarding_stop_id':s['gtfsId']})
                        routes.extend(planned);captured_at=audit['captured_at']
                    except (OSError,ValueError,KeyError,TypeError):failed+=1
                if failed==len(selected):raise OtpError('No platform services could be checked')
                routes=list({r['provider_itinerary_id']:r for r in routes}.values())
                routes.sort(key=lambda r:r['transit']['end_time_ms'])
                return jsonify(routes=routes,status='AVAILABLE' if routes else 'NO_MATCHING_ITINERARIES',captured_at=captured_at,incomplete=failed>0)
            routes,audit,stop=plan_from_stop(payload)
            return jsonify(routes=routes,stop=stop,status=audit['status'],captured_at=audit['captured_at'])
        except (OSError,ValueError,KeyError,TypeError):return jsonify(message='No verified itinerary could be retrieved. Check the destination, walking limit and connection.'),422
