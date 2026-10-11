"""Shared positive/negative examples exercised by Kotlin and Python validators."""
from copy import deepcopy
from hashlib import sha256
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app-backend/services/dyconet"))
from journey_contract import distance_m

def generate():
    route = dict(rank=0, mode_key="walk", available=True, provider="fixture", summary=dict(duration_seconds=600, distance_meters=1000), legs=[])
    choice = dict(chosen_at="2026-10-08T10:00:00Z", mode_key="walk", routing_rank=0, route_id="route-fixture")
    record = dict(version=1, search_id="phase1-fixture", created_at="2026-10-08T09:59:00Z", status="complete",
                  request=dict(search_id="phase1-fixture", datetime="2026-10-08T09:59:00Z"),
                  passport_reference=dict(passport_id="fixture-passport", revision=1, fingerprint="fixture-fingerprint", confirmed_at="2026-10-08T09:00:00Z"),
                  places=dict(origin=dict(lat=52.1, lon=11.6, label="Fixture origin"), destination=dict(lat=52.11, lon=11.6, label="Fixture destination")),
                  result=dict(search_id="phase1-fixture", schema_version="routed-contextual-deliberation-v1", routing=dict(routes=[route]), route_audit=[]),
                  recommendation=dict(state="uncertain", winner=None, primary_route_id=None), choices=[choice])
    selection = {k: choice.get(k) for k in ("chosen_at", "mode_key", "route_id", "routing_rank", "provider", "provider_itinerary_id")}
    base = dict(schema_version="cricket-journey-v1", journey_id="trip-fixture", search_id=record["search_id"], choice_index=0,
                choice_event_id="choice-"+sha256(b"phase1-fixture|0|2026-10-08T10:00:00Z").hexdigest(),
                created_at="2026-10-08T10:01:00Z", started_at=None, ended_at="2026-10-08T10:01:00Z", status="awaiting_confirmation", capture_mode="manual",
                selection=selection, passport_reference=record["passport_reference"],
                plan=dict(route=route, recommendation=record["recommendation"], alternatives=[dict(routing_rank=0, mode_key="walk", available=True)],
                          context_at_selection=dict(queried_at=record["request"]["datetime"], snapshot=None, temporal_reference="search_query")),
                tracking=dict(points=[], events=[], rejected_fixes=0, distance_m=None, elapsed_seconds=None, gap_count=0, segment_index=0, approximate=True, started_monotonic_ms=None, boot_count=None), experience=None)
    experience = dict(schema_version="cricket-experience-v1", event_id="experience-trip-fixture", confirmed_at="2026-10-08T10:02:00Z", completion="as_planned", actual_modes=["walk"], changes="", ratings=dict(comfort=6), choice_reason=None, learning_applied=False)
    confirmed=deepcopy(base);confirmed.update(status="confirmed", experience=experience)
    tracked=deepcopy(base);tracked.update(capture_mode="tracked",started_at=base["created_at"],ended_at="2026-10-08T10:03:00Z")
    points=[dict(lat=52.1,lon=11.6,accuracy_m=5,recorded_at="2026-10-08T10:01:10Z",received_at="2026-10-08T10:01:10Z",monotonic_ms=11000,provider="gps",mock=False,segment_index=0),
            dict(lat=52.101,lon=11.6,accuracy_m=5,recorded_at="2026-10-08T10:02:10Z",received_at="2026-10-08T10:02:10Z",monotonic_ms=71000,provider="gps",mock=False,segment_index=0)]
    tracked["tracking"].update(points=points, distance_m=distance_m(*points), elapsed_seconds=120, started_monotonic_ms=1000, boot_count=1,
                               events=[dict(at=base["created_at"],kind="start",detail=""),dict(at=tracked["ended_at"],kind="finish",detail="")])
    cases=[]
    def add(name,journey,valid=True):cases.append(dict(name=name,valid=valid,record=deepcopy(record),journey=deepcopy(journey)))
    add("manual_pending",base);add("manual_confirmed",confirmed);add("tracked_finished",tracked)
    changed=deepcopy(confirmed);changed["experience"].update(completion="changed",actual_modes=["pt","walk"],changes="Changed to tram");add("changed_modes",changed)
    not_taken=deepcopy(confirmed);not_taken["experience"].update(completion="not_taken",actual_modes=[],ratings={});add("not_taken",not_taken)
    active=deepcopy(tracked);active.update(status="recording",ended_at=None);active["tracking"].update(elapsed_seconds=None,events=active["tracking"]["events"][:1]);add("tracked_recording",active)
    gap=deepcopy(tracked);gap["tracking"].update(segment_index=1,gap_count=1,distance_m=0);gap["tracking"]["points"][1]["segment_index"]=1;add("gap_is_not_joined",gap)
    def bad(name,source,path,value):
        changed=deepcopy(source);node=changed
        for key in path[:-1]:node=node[key]
        node[path[-1]]=value;add(name,changed,False)
    bad("wrong_schema",base,["schema_version"],"v0")
    bad("wrong_search",base,["search_id"],"other")
    bad("wrong_choice",base,["choice_event_id"],"other")
    bad("choice_out_of_range",base,["choice_index"],99)
    bad("wrong_passport",base,["passport_reference","revision"],2)
    bad("changed_route",base,["plan","route","summary","duration_seconds"],100)
    bad("wrong_recommendation",base,["plan","recommendation","state"],"clear")
    bad("wrong_alternatives",base,["plan","alternatives"],[])
    bad("wrong_context_time",base,["plan","context_at_selection","queried_at"],"2026-10-08T09:00:00Z")
    bad("invented_weather",base,["plan","context_at_selection","snapshot"],{"weather":"sunny"})
    bad("manual_fake_distance",base,["tracking","distance_m"],100)
    bad("manual_fake_duration",base,["tracking","elapsed_seconds"],600)
    bad("implicit_confirmation",confirmed,["status"],"awaiting_confirmation")
    bad("learning_not_enabled",confirmed,["experience","learning_applied"],True)
    bad("rating_out_of_range",confirmed,["experience","ratings","comfort"],8)
    bad("rating_unknown_need",confirmed,["experience","ratings","wealth"],5)
    bad("duplicate_modes",confirmed,["experience","actual_modes"],["walk","walk"])
    bad("as_planned_mode_mismatch",confirmed,["experience","actual_modes"],["car"])
    bad("unperformed_trip_rating",not_taken,["experience","ratings"],{"comfort":4})
    bad("confirmation_before_end",confirmed,["experience","confirmed_at"],"2026-10-08T09:00:00Z")
    bad("timestamp_without_zone",base,["created_at"],"2026-10-08T10:01:00")
    bad("mock_fix",tracked,["tracking","points",0,"mock"],True)
    bad("poor_accuracy",tracked,["tracking","points",0,"accuracy_m"],100)
    bad("invalid_coordinate",tracked,["tracking","points",0,"lat"],91)
    bad("fabricated_distance",tracked,["tracking","distance_m"],5000)
    bad("duplicate_fix_time",tracked,["tracking","points",1,"monotonic_ms"],11000)
    bad("joined_long_gap",tracked,["tracking","points",1,"monotonic_ms"],211000)
    bad("gps_teleport",tracked,["tracking","points",1,"lat"],55)
    bad("segment_without_gap",tracked,["tracking","segment_index"],1)
    bad("future_fix",tracked,["tracking","points",1,"recorded_at"],"2026-10-08T10:04:00Z")
    bad("unknown_field",base,["automatic_learning"],True)
    target=ROOT/"validation/fixtures/journey_cases.json";target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(cases,indent=2)+"\n",encoding="utf-8")
    print(f"Generated {len(cases)} shared contract cases")

if __name__=="__main__":generate()
