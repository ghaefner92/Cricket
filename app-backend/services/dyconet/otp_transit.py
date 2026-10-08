"""Local OTP 2.7 itineraries. Transport facts are not HOTCO forcing inputs."""
from __future__ import annotations
import copy,datetime,hashlib,json,math,os,time
from urllib.parse import urlsplit
from urllib.request import Request,urlopen

QUERY='''query CricketTransit($from: InputCoordinates!, $to: InputCoordinates!, $date: String!, $time: String!, $ignore: Boolean!) {
 plan(from: $from, to: $to, date: $date, time: $time, numItineraries: 5, ignoreRealtimeUpdates: $ignore,
 transportModes: [{mode: WALK}, {mode: TRANSIT}]) {
 routingErrors { code description }
 itineraries { duration startTime endTime walkDistance waitingTime numberOfTransfers
 legs { mode transitLeg duration distance startTime endTime realTime departureDelay arrivalDelay serviceDate
 from { name lat lon stop { gtfsId } } to { name lat lon stop { gtfsId } }
 route { gtfsId shortName longName } trip { gtfsId }
 legGeometry { points length } }
 } } }'''
TRANSIT={'TRAM','SUBWAY','RAIL','BUS','FERRY','CABLE_CAR','GONDOLA','FUNICULAR','TROLLEYBUS','MONORAIL'}
class OtpError(ValueError):pass

def local_url(url):
    p=urlsplit(url)
    if p.scheme!='http' or p.hostname not in ('127.0.0.1','localhost','::1') or p.username or p.password or p.query or p.fragment:
        raise OtpError('OTP endpoints must be server-configured loopback HTTP URLs')
    return url.rstrip('/')

def number(value,*,minimum=None):
    if type(value) not in (int,float) or not math.isfinite(value) or (minimum is not None and value<minimum):raise OtpError('Invalid OTP metric')
    return value

def decode_polyline(encoded):
    if not isinstance(encoded,str) or len(encoded)>2_000_000:raise OtpError('Missing or oversized OTP geometry')
    p=0;lat=0;lon=0;points=[]
    def read():
        nonlocal p
        result=0;shift=0
        while True:
            if p>=len(encoded) or shift>35:raise OtpError('Invalid encoded geometry')
            b=ord(encoded[p])-63;p+=1
            if not 0<=b<=63:raise OtpError('Invalid encoded geometry character')
            result|=(b&31)<<shift;shift+=5
            if b<32:return ~(result>>1) if result&1 else result>>1
    while p<len(encoded):
        lat+=read();lon+=read();a,b=lat/1e5,lon/1e5
        if not -90<=a<=90 or not -180<=b<=180:raise OtpError('Geometry coordinate out of range')
        points.append([b,a])
        if len(points)>100_000:raise OtpError('Geometry too large')
    if len(points)<2:raise OtpError('Missing leg geometry')
    return {'type':'LineString','coordinates':points}

def berlin(when):
    when=datetime.datetime.fromisoformat(when.replace('Z','+00:00'))
    if when.tzinfo is None:raise OtpError('Departure timezone required')
    from zoneinfo import ZoneInfo,ZoneInfoNotFoundError
    try:return when.astimezone(ZoneInfo('Europe/Berlin'))
    except ZoneInfoNotFoundError:
        utc=when.astimezone(datetime.timezone.utc)
        def transition(month):
            last=datetime.date(utc.year,month,31)
            sunday=last-datetime.timedelta(days=(last.weekday()+1)%7)
            return datetime.datetime.combine(sunday,datetime.time(1),datetime.timezone.utc)
        hours=2 if transition(3)<=utc<transition(10) else 1
        return utc.astimezone(datetime.timezone(datetime.timedelta(hours=hours)))

def fresh(status,now=None):
    now=time.time() if now is None else now
    stamp=status.get('feed_timestamp') if isinstance(status,dict) else None
    return type(stamp) in (int,float) and math.isfinite(stamp) and 0<=now-stamp<=180 and status.get('ready') is True

def plan_of(response):
    if not isinstance(response,dict) or response.get('errors'):raise OtpError('OTP GraphQL error')
    data=response.get('data')
    if not isinstance(data,dict):raise OtpError('OTP returned invalid GraphQL data')
    plan=data.get('plan')
    if not isinstance(plan,dict) or not isinstance(plan.get('itineraries'),list):raise OtpError('OTP returned no valid plan')
    return plan

def adapt_itineraries(response,availability,max_walk,rt_fresh):
    plan=plan_of(response);routes=[];rejections=[];seen=set()
    for index,it in enumerate(plan['itineraries']):
        try:
            if not isinstance(it,dict):raise OtpError('Invalid itinerary')
            supplied=it['legs']
            if not isinstance(supplied,list) or not supplied or any(not isinstance(l,dict) for l in supplied):raise OtpError('Missing or malformed legs')
            if not any(l.get('mode') in TRANSIT for l in supplied):continue
            if not availability['pt']:continue
            duration=number(it['duration'],minimum=0)
            if duration<=0:raise OtpError('Missing positive itinerary duration')
            walk=number(it['walkDistance'],minimum=0)
            if walk>max_walk:raise OtpError('walking_limit_exceeded')
            if walk>0 and not availability['walk']:raise OtpError('walking_unavailable')
            start=number(it['startTime'],minimum=0);end=number(it['endTime'],minimum=0)
            if end<start:raise OtpError('Invalid itinerary times')
            transfers=it['numberOfTransfers']
            if type(transfers) is not int or transfers<0:raise OtpError('Invalid transfers')
            legs=[];identity=[]
            for i,leg in enumerate(supplied):
                source_mode=leg['mode']
                if source_mode not in TRANSIT|{'WALK'}:raise OtpError('Unsupported OTP leg mode')
                transit=source_mode in TRANSIT
                if not transit and not availability['walk']:raise OtpError('walking_unavailable')
                dist=number(leg['distance'],minimum=0);seconds=number(leg['duration'],minimum=0)
                if seconds<=0:raise OtpError('Missing positive leg duration')
                a=number(leg['startTime'],minimum=0);b=number(leg['endTime'],minimum=0)
                if b<a:raise OtpError('Invalid leg times')
                geometry=decode_polyline((leg.get('legGeometry') or {}).get('points'))
                realtime=transit and rt_fresh and leg.get('realTime') is True
                def delay(key):
                    value=leg.get(key)
                    return int(value) if realtime and type(value) is int else None
                dd,ad=delay('departureDelay'),delay('arrivalDelay')
                route=leg.get('route') or {};trip=leg.get('trip') or {}
                if transit and not trip.get('gtfsId'):raise OtpError('Missing transit trip identity')
                details={'source_mode':source_mode,'line':route.get('shortName'),'line_name':route.get('longName'),
                    'gtfs_route_id':route.get('gtfsId'),'gtfs_trip_id':trip.get('gtfsId'),'service_date':leg.get('serviceDate'),
                    'from':copy.deepcopy(leg.get('from')),'to':copy.deepcopy(leg.get('to')),
                    'start_time_ms':a,'end_time_ms':b,'realtime':realtime,
                    'departure_delay_seconds':dd,'arrival_delay_seconds':ad,
                    'scheduled_start_time_ms':a-dd*1000 if dd is not None else a if not realtime else None,
                    'scheduled_end_time_ms':b-ad*1000 if ad is not None else b if not realtime else None,
                    'geometry_quality':'APPROXIMATE_NO_SHAPES' if transit else 'OSM_ROUTED'}
                legs.append({'mode':'pt' if transit else 'walk','duration_seconds':seconds,'distance_meters':dist,'geometry':geometry,'transit':details})
                identity.append([source_mode,trip.get('gtfsId'),leg.get('serviceDate'),details['scheduled_start_time_ms'],a,(leg.get('from') or {}).get('stop'),(leg.get('to') or {}).get('stop')])
            key=json.dumps(identity,sort_keys=True)
            if key in seen:continue
            seen.add(key)
            leg_sum=sum(x['duration_seconds'] for x in legs)
            if leg_sum>duration+2:raise OtpError('Leg duration exceeds itinerary duration')
            gaps=[]
            for before,after in zip(legs,legs[1:]):
                gap=(after['transit']['start_time_ms']-before['transit']['end_time_ms'])/1000
                if gap < -2:raise OtpError('Overlapping OTP legs')
                if gap>0:gaps.append({'seconds':gap,'at':before['transit']['to']})
            waiting=number(it['waitingTime'],minimum=0)
            routes.append({'mode_key':'pt','available':True,'feasible':True,'score':None,
                'summary':{'duration_seconds':duration,'distance_meters':sum(x['distance_meters'] for x in legs),'transfers':transfers,'walk_distance_meters':walk,'waiting_seconds':waiting},
                'legs':legs,'provider':'otp','provider_option':index+1,
                'provider_itinerary_id':'otp-'+hashlib.sha256(key.encode()).hexdigest()[:24],
                'transit':{'start_time_ms':start,'end_time_ms':end,'waiting_seconds':waiting,'waiting_intervals':gaps,
                    'unallocated_duration_seconds':max(0,duration-leg_sum),'realtime_status':'REALTIME' if any(l['transit']['realtime'] for l in legs) else 'SCHEDULED',
                    'geometry_quality':'APPROXIMATE_NO_SHAPES','walking_limit_policy':'total_itinerary_walking',
                    'context_waiting_policy':'not_sampled_or_distributed_over_moving_legs','delay_hotco_policy':'display_and_audit_only'},
                '_geometry_evidence':{'provider':'otp','geometry_provenance':'OTP_LEG_GEOMETRY','route_identity_verified':True,'geometry_quality':'APPROXIMATE_NO_SHAPES','context_spatial_fidelity':'LOW','timing_source':'otp'}})
        except (KeyError,TypeError,ValueError) as exc:
            rejection={'provider_option':index+1,'reason':str(exc)}
            if str(exc)=='walking_limit_exceeded':
                rejection.update(walk_distance_meters=walk, max_walk_meters=max_walk)
            rejections.append(rejection)
    return routes,{'routing_errors':plan.get('routingErrors') or [],'rejected_itineraries':rejections}

class OtpTransitClient:
    def __init__(self,base_url=None,status_url=None,timeout=None):
        self.base=local_url(base_url or os.environ.get('CRICKET_OTP_BASE_URL','http://127.0.0.1:8091'))
        self.status_url=local_url(status_url or os.environ.get('CRICKET_OTP_STATUS_URL','http://127.0.0.1:8092/status'))
        self.timeout=float(timeout or os.environ.get('CRICKET_OTP_TIMEOUT_SECONDS','20'))
        if not math.isfinite(self.timeout) or self.timeout<=0:raise OtpError('Invalid OTP timeout')
    def request(self,url,data=None):
        req=Request(url,data=json.dumps(data).encode() if data is not None else None,headers={'Content-Type':'application/json','User-Agent':'Cricket/0.2.0'})
        with urlopen(req,timeout=self.timeout if data is not None else min(3,self.timeout)) as r:raw=r.read(10_000_001)
        if len(raw)>10_000_000:raise OtpError('OTP response too large')
        return json.loads(raw)
    def status(self):
        try:return self.request(self.status_url)
        except (OSError,ValueError):return None
    def routes(self,*,start,stop,departure,availability,max_walk):
        when=berlin(departure);status_before=self.status();use_rt=fresh(status_before)
        variables={'from':start,'to':stop,'date':when.strftime('%Y-%m-%d'),'time':when.strftime('%H:%M:%S'),'ignore':not use_rt}
        request={'query':QUERY,'variables':variables}
        try:
            raw=self.request(self.base+'/otp/routers/default/index/graphql',request);plan_of(raw)
        except (OSError,ValueError) as exc:raise OtpError('OTP unavailable or invalid plan') from exc
        # Retrieve a second, explicit departure window rather than fabricating
        # later times by shifting the first itinerary's schedule.
        later=when+datetime.timedelta(minutes=30)
        later_request={'query':QUERY,'variables':{**variables,'date':later.strftime('%Y-%m-%d'),'time':later.strftime('%H:%M:%S')}}
        later_raw=None;later_failure=None
        try:
            later_raw=self.request(self.base+'/otp/routers/default/index/graphql',later_request);plan_of(later_raw)
        except (OSError,ValueError) as exc:
            later_failure=type(exc).__name__
            later_raw=None
        status_after=self.status() if use_rt else None
        rt_fresh=use_rt and fresh(status_after)
        if use_rt and not rt_fresh:
            variables={**variables,'ignore':True};request={'query':QUERY,'variables':variables}
            try:raw=self.request(self.base+'/otp/routers/default/index/graphql',request);plan_of(raw)
            except (OSError,ValueError) as exc:raise OtpError('OTP scheduled fallback unavailable') from exc
            later_request={'query':QUERY,'variables':{**later_request['variables'],'ignore':True}}
            try:
                later_raw=self.request(self.base+'/otp/routers/default/index/graphql',later_request);plan_of(later_raw)
                later_failure=None
            except (OSError,ValueError) as exc:
                later_raw=None;later_failure=type(exc).__name__
        combined=copy.deepcopy(raw)
        if later_raw:
            combined['data']['plan']['itineraries'].extend(plan_of(later_raw)['itineraries'])
            combined['data']['plan']['routingErrors']=(plan_of(raw).get('routingErrors') or [])+(plan_of(later_raw).get('routingErrors') or [])
        routes,audit=adapt_itineraries(combined,availability,max_walk,rt_fresh)
        return routes,{**audit,'provider':'otp','status':'AVAILABLE' if routes else 'NO_MATCHING_ITINERARIES',
            'max_walk_meters':max_walk,'scope':'MVB Magdeburg','captured_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'realtime_status_before':status_before,'realtime_status_after':status_after,'realtime_feed_fresh':bool(rt_fresh),
            'feed_status_is_separate_observation':True,'query':request,'raw_response':raw,
            'later_departures':{'offset_minutes':30,'status':'AVAILABLE' if later_raw else 'UNAVAILABLE',
                'reason':later_failure,'query':later_request,'raw_response':later_raw},
            'ranking_policy':'independent_provider_order_no_cross_provider_score','attribution':'GTFS.de · MVB · OpenStreetMap contributors','gtfs_license':'CC BY-SA 4.0'}
