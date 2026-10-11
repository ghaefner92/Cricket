"""OSM reference alignment for an exact tram line/direction; never live vehicle evidence."""
import heapq
import copy
import hashlib
import json
import math
from functools import lru_cache
from pathlib import Path
from graphhopper_geometry import distance_m

@lru_cache(maxsize=1)
def dataset():
    path=Path(__file__).resolve().parents[3]/'contracts/magdeburg-tram-lines.json'
    if not path.exists():return {'elements':[]}
    raw=path.read_text(encoding='utf-8');value=json.loads(raw)
    value['_reference_cache_key']=hashlib.sha256(raw.encode('utf-8')).hexdigest()
    return value

def project(point,a,b):
    scale=math.cos(math.radians(point[1]))
    dx=(b[0]-a[0])*scale;dy=b[1]-a[1]
    f=(((point[0]-a[0])*scale*dx+(point[1]-a[1])*dy)/(dx*dx+dy*dy)) if dx or dy else 0
    f=max(0,min(1,f))
    p=[a[0]+(b[0]-a[0])*f,a[1]+(b[1]-a[1])*f]
    return distance_m(point,p),p,f

def network(relation,ways):
    edges=[];coords={};graph={}
    for member in relation['members']:
        way=ways.get(member['ref']) if member['type']=='way' and member.get('role','') in ('','forward','backward') else None
        if not way:continue
        nodes=way['nodes'];points=[[p['lon'],p['lat']] for p in way['geometry']]
        if len(nodes)!=len(points):continue
        direction=way.get('tags',{}).get('oneway')
        if member.get('role')=='backward' or direction=='-1':nodes=nodes[::-1];points=points[::-1]
        bidirectional=direction not in ('yes','1','true','-1') and member.get('role','')==''
        coords.update(zip(nodes,points))
        for a,b in zip(nodes,nodes[1:]):
            length=distance_m(coords[a],coords[b])
            if length<=0:continue
            edges.append((a,b,length,bidirectional))
            graph.setdefault(a,[]).append((b,length))
            if bidirectional:graph.setdefault(b,[]).append((a,length))
    return coords,edges,graph

def closest(point,coords,edges):
    return min(((*project(point,coords[a],coords[b]),a,b,length,both) for a,b,length,both in edges),key=lambda x:x[0],default=None)

def path_between(start,end,coords,graph):
    # Virtual locations within edges; respect mapped travel direction.
    _,sp,sf,sa,sb,sl,sboth=start;_,ep,ef,ea,eb,el,eboth=end
    if (sa,sb)==(ea,eb) and (ef>=sf or sboth):return [sp,ep]
    starts=[(sb,(1-sf)*sl)]+([(sa,sf*sl)] if sboth else [])
    ends={ea:ef*el}
    if eboth:ends[eb]=(1-ef)*el
    distance={};previous={};queue=[]
    for n,d in starts:
        distance[n]=d;previous[n]=None;heapq.heappush(queue,(d,n))
    best=None;best_total=math.inf
    while queue:
        d,n=heapq.heappop(queue)
        if d!=distance[n]:continue
        if d>best_total:break
        if n in ends and d+ends[n]<best_total:best=n;best_total=d+ends[n]
        for neighbour,cost in graph.get(n,[]):
            value=d+cost
            if value<distance.get(neighbour,math.inf):
                distance[neighbour]=value;previous[neighbour]=n;heapq.heappush(queue,(value,neighbour))
    if best is None:return None
    nodes=[]
    while best is not None:nodes.append(best);best=previous[best]
    return [sp]+[coords[n] for n in nodes[::-1]]+[ep]

def _match_line_tram(line,origin,destination,original,data=None):
    data=data or dataset();elements=data['elements']
    ways={e['id']:e for e in elements if e['type']=='way'}
    stops={e['id']:e for e in elements if e['type']=='node'}
    candidates=[]
    for relation in elements:
        reference=relation.get('tags',{}).get('ref')
        # MVB publishes line 1 as 1N / 1S during the 2026 construction period.
        # Compatibility still requires this leg's ordered stops and rail anchors.
        if relation['type']!='relation' or not (reference==line or line=='1' and reference in ('1N','1S')):continue
        positions=[stops[m['ref']] for m in relation['members'] if m['type']=='node' and m.get('role','').startswith('stop') and m['ref'] in stops]
        if len(positions)<2:continue
        def nearest(p):return min((distance_m(p,[s['lon'],s['lat']]),i) for i,s in enumerate(positions))
        a,b=nearest(origin),nearest(destination)
        if a[0]>25 or b[0]>25 or a[1]>=b[1]:continue
        coords,edges,graph=network(relation,ways)
        if not edges:continue
        start=closest(origin,coords,edges);end=closest(destination,coords,edges)
        if start[0]>25 or end[0]>25:continue
        path=path_between(start,end,coords,graph)
        if not path:continue
        path=[p for i,p in enumerate(path) if i==0 or p!=path[i-1]]
        if len(path)<2:continue
        lengths=[distance_m(p,q) for p,q in zip(path,path[1:])];length=sum(lengths)
        direct=distance_m(origin,destination)
        if length<direct*.9 or length>max(direct*3,300):continue
        # Original no-shapes geometry contains stop anchors. All must occur in
        # sequence along this reference alignment; no nearest-track shortcut.
        previous=-1;valid=True
        for point in original:
            total=0;locations=[]
            for p,q,d in zip(path,path[1:],lengths):
                gap,_,fraction=project(point,p,q);locations.append((gap,total+fraction*d));total+=d
            gap,at=min(locations)
            if gap>25 or at<previous-20:valid=False;break
            previous=at
        if not valid:continue
        candidates.append((path,relation['id'],length,start[0],end[0],reference))
    if not candidates:return None
    first=candidates[0]
    # Divergent matching variants remain unresolved, rather than selecting one.
    if any(abs(c[2]-first[2])>25 or any(min(project(p,a,b)[0] for a,b in zip(first[0],first[0][1:]))>10 for p in c[0]) for c in candidates[1:]):return None
    return {'type':'LineString','coordinates':first[0]}, {'source':'OSM_REFERENCE_LINE','relation_id':first[1],
      'osm_base':data.get('osm_base'),'length_meters':first[2],'boarding_offset_m':first[3],'alighting_offset_m':first[4],
      'osm_line_reference':first[5],'timetable_line_reference':line,
      'vehicle_path_observed':False,'service_shape_verified':False}

def _reference_network(line,origin,destination,original,data):
    """Physical rail reference through timetable anchors, not a service shape.

    Temporary/split route relations can omit a direction. Use the connected
    mapped rail network only when the timetable anchors constrain the path.
    Nearby tracks are candidates, not artificial connections between tracks.
    """
    references={e.get('tags',{}).get('ref') for e in data['elements'] if e['type']=='relation'}
    if not (line in references or line in data.get('timetable_tram_lines',[]) or line=='1' and references.intersection({'1N','1S'})):return None
    anchors=[]
    for p in [origin]+list(original)+[destination]:
        if not anchors or distance_m(p,anchors[-1])>2:anchors.append(p)
    if len(anchors)<2:return None
    ways={e['id']:e for e in data['elements'] if e['type']=='way'}
    relation={'members':[{'type':'way','ref':i,'role':''} for i in ways]}
    coords,edges,graph=network(relation,ways)
    if not edges:return None
    @lru_cache(maxsize=256)
    def point_options(point):
        values=[(*project(point,coords[a],coords[b]),a,b,length,both) for a,b,length,both in edges]
        return sorted((v for v in values if v[0]<=25),key=lambda v:v[0])[:32]
    def options(point):return point_options(tuple(point))
    # Every timetable stop must itself be near a mapped rail, not just endpoints.
    if any(not options(p) for p in anchors):return None
    candidates=[];direct=distance_m(origin,destination);anchor_checks={}
    for start in options(origin):
        for end in options(destination):
            path=path_between(start,end,coords,graph)
            if not path:continue
            path=[p for i,p in enumerate(path) if i==0 or p!=path[i-1]]
            if len(path)<2:continue
            lengths=[distance_m(a,b) for a,b in zip(path,path[1:])];length=sum(lengths)
            if length<direct*.9 or length>max(direct*3,300):continue
            corridor=tuple(tuple(p) for p in path)
            cached=anchor_checks.get(corridor)
            if cached is not None:
                valid,gaps=cached
                if valid:candidates.append((start[0]+end[0],length,path,start[0],end[0],max(gaps)))
                continue
            previous=-1;valid=True;gaps=[]
            for anchor in anchors:
                total=0;locations=[]
                for a,b,d in zip(path,path[1:],lengths):
                    gap,_,fraction=project(anchor,a,b);locations.append((gap,total+fraction*d));total+=d
                gap,at=min(locations)
                if gap>25 or at<previous-20:valid=False;break
                previous=at;gaps.append(gap)
            anchor_checks[corridor]=(valid,gaps)
            if valid:candidates.append((start[0]+end[0],length,path,start[0],end[0],max(gaps)))
    if not candidates:return None
    # Prefer direct connected references over circling a terminal to reach its
    # opposite rail. This is a cartographic path policy, not service inference.
    shortest=min(c[1] for c in candidates)
    candidates=[c for c in candidates if c[1]<=shortest+50]
    candidates.sort(key=lambda c:(c[0],c[1]));best=candidates[0]
    # Endpoint projections vary slightly. Divergent interior corridors stay
    # unresolved instead of presenting the shortest corridor as actual service.
    for candidate in candidates[1:]:
        if abs(candidate[1]-best[1])>max(100,best[1]*.1):return None
        for p in candidate[2]:
            if min(distance_m(p,origin),distance_m(p,destination))<=50:continue
            if min(project(p,a,b)[0] for a,b in zip(best[2],best[2][1:]))>25:return None
        for p in best[2]:
            if min(distance_m(p,origin),distance_m(p,destination))<=50:continue
            if min(project(p,a,b)[0] for a,b in zip(candidate[2],candidate[2][1:]))>25:return None
    return {'type':'LineString','coordinates':best[2]}, {
        'source':'OSM_REFERENCE_NETWORK','osm_base':data.get('osm_base'),
        'timetable_line_reference':line,'length_meters':best[1],
        'boarding_offset_m':best[3],'alighting_offset_m':best[4],
        'anchor_count':len(anchors),'maximum_anchor_offset_m':best[5],
        'path_policy':'shortest-connected-reference-with-ordered-stop-anchors',
        'line_relation_matched':False,'vehicle_path_observed':False,
        'service_shape_verified':False}

def _ordered_anchor_network(line,origin,destination,original,data):
    """Only forced detours/terminal returns; never bypass corridor ambiguity."""
    anchors=[]
    for p in [origin]+list(original)+[destination]:
        if not anchors or distance_m(p,anchors[-1])>2:anchors.append(p)
    if len(anchors)<3:return None
    anchor_length=sum(distance_m(a,b) for a,b in zip(anchors,anchors[1:]))
    if anchor_length<=max(distance_m(origin,destination)*3,300):return None
    pieces=[];evidence=[]
    for a,b in zip(anchors,anchors[1:]):
        match=_match_line_tram(line,a,b,[a,b],data) or _reference_network(line,a,b,[a,b],data)
        if not match:return None
        coords=match[0]['coordinates']
        # Exact shared projected rail position; never draw a bridge between tracks.
        if pieces and pieces[-1]!=coords[0]:return None
        pieces.extend(coords if not pieces else coords[1:]);evidence.append(match[1])
    return {'type':'LineString','coordinates':pieces},{
        'source':'OSM_REFERENCE_NETWORK','osm_base':data.get('osm_base'),
        'timetable_line_reference':line,'length_meters':sum(distance_m(a,b) for a,b in zip(pieces,pieces[1:])),
        'anchor_count':len(anchors),'piece_count':len(evidence),
        'boarding_offset_m':evidence[0]['boarding_offset_m'],'alighting_offset_m':evidence[-1]['alighting_offset_m'],
        'maximum_anchor_offset_m':max(max(e.get('maximum_anchor_offset_m',0),e['boarding_offset_m'],e['alighting_offset_m']) for e in evidence),
        'path_policy':'ordered-anchors-with-exact-connected-rail-junctions',
        'line_relation_matched':False,'vehicle_path_observed':False,'service_shape_verified':False}

@lru_cache(maxsize=128)
def _cached_match(line,origin,destination,original,data_identity):
    data=dataset()
    return _match_line_tram(line,origin,destination,original,data) or _reference_network(line,origin,destination,original,data) or _ordered_anchor_network(line,origin,destination,original,data)

def match_tram(line,origin,destination,original,data=None):
    if data is not None:
        return _match_line_tram(line,origin,destination,original,data) or _reference_network(line,origin,destination,original,data) or _ordered_anchor_network(line,origin,destination,original,data)
    # Snapshot identity invalidates the bounded cache if dataset() is reloaded.
    # Coordinates/line determine reference geometry; departures/RT/weather never
    # enter this cache. Copy the result so callers cannot corrupt another trip.
    snapshot=dataset()
    value=_cached_match(line,tuple(origin),tuple(destination),tuple(tuple(p) for p in original),snapshot.get('_reference_cache_key',id(snapshot)))
    return copy.deepcopy(value)
