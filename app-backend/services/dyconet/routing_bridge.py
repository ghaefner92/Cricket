"""Routing gateway: explicit availability, geometry audit, then HOTCO context.

The colleague's repository remains untouched. Missing route geometry is never
silently replaced with a straight line. Routing utility remains independent
of the HOTCO modal readout.
"""
from __future__ import annotations

from journey_availability import validate_journey_availability

import copy
import hashlib
import json
import math
import os
from datetime import datetime
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

from contextual_deliberation import ContextualDeliberationRequest, ContextualDeliberationService, run_contextual_deliberation
from route_context import CandidateRouteInput, Coordinate
from graphhopper_geometry import GraphHopperGeometryClient, supplemental_route
from orion_query_list import OrionQueryListProvider

MODES = {"foot": "walk", "walk": "walk", "bike": "bike", "car": "car", "pt": "pt"}


class RoutingBridgeError(ValueError):
    pass


class RoutingUnavailableError(RuntimeError):
    pass


def _coordinate(value):
    if not isinstance(value, dict):
        raise RoutingBridgeError("coordinates must be objects with lat and lon")
    lat, lon = float(value["lat"]), float(value["lon"])
    if not math.isfinite(lat) or not math.isfinite(lon):
        raise RoutingBridgeError("coordinates must be finite")
    return Coordinate(lat, lon)


def _datetime(value):
    if not isinstance(value, str):
        raise RoutingBridgeError("datetime is required, including a timezone")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise RoutingBridgeError("datetime must include a timezone")
    return value


def routing_passport(passport):
    """Translate technical access explicitly; never derive it from preference."""
    if not isinstance(passport, dict):
        raise RoutingBridgeError("cognitive_passport must be an object")
    root = copy.deepcopy(passport.get("cognitive_passport", passport))
    if not isinstance(root, dict):
        raise RoutingBridgeError("cognitive_passport envelope must contain an object")
    profile = root.get("profile", {})
    if not isinstance(profile, dict):
        raise RoutingBridgeError("passport profile must be an object")
    availability = profile.get("availability")
    if availability is None:
        availability = root.get("routing_parameters", {}).get("availability")
    if not isinstance(availability, dict) or any(type(availability.get(mode)) is not bool for mode in ("walk", "bike", "pt", "car")):
        raise RoutingBridgeError("passport must declare boolean availability for walk, bike, pt and car")
    root["beliefs"] = {
        "owns_bike": availability["bike"],
        "owns_car": availability["car"],
        "has_pt_access": availability["pt"],
    }
    return root, availability


class RoutingClient:
    def __init__(self, base_url=None, timeout=None):
        self.base_url = (base_url or os.environ.get("IMIQ_ROUTING_BASE_URL", "http://127.0.0.1:8000")).rstrip("/")
        parsed = urlsplit(self.base_url)
        if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise RoutingBridgeError("invalid server-configured routing URL")
        self.timeout = float(timeout or os.environ.get("IMIQ_ROUTING_TIMEOUT_SECONDS", "120"))
        if not math.isfinite(self.timeout) or self.timeout <= 0:
            raise RoutingBridgeError("routing timeout must be positive and finite")

    def ranked_routes(self, payload):
        request = Request(self.base_url + "/ranked-routes", data=json.dumps(payload, allow_nan=False).encode(), headers={"Content-Type": "application/json"}, method="POST")
        try:
            with urlopen(request, timeout=self.timeout) as response:
                data = json.load(response)
        except HTTPError as exc:
            raise RoutingUnavailableError(f"routing returned HTTP {exc.code}") from exc
        except (URLError, TimeoutError, OSError, ValueError) as exc:
            raise RoutingUnavailableError("routing unavailable or returned invalid JSON") from exc
        if not isinstance(data, dict) or not isinstance(data.get("routes"), list):
            raise RoutingUnavailableError("routing response must contain a routes array")
        return data


def _geometry(value):
    if isinstance(value, dict) and value.get("type") == "LineString":
        points = value.get("coordinates", [])
        try:
            result = [Coordinate(float(p[1]), float(p[0])).to_dict() for p in points]
        except (TypeError, ValueError, IndexError):
            return []
        return result if len(result) >= 2 and all(math.isfinite(v) for p in result for v in p.values()) else []
    return []


def _metric(value, name):
    number = float(value)
    if not math.isfinite(number) or number < 0:
        raise RoutingBridgeError(f"{name} must be finite and nonnegative")
    return number


def adapt_route(route, *, search_id, origin, destination, departure, availability):
    """Return (candidate, rejection_reason). Full leg geometry is required."""
    mode_key = route.get("mode_key")
    required = {"foot": {"walk"}, "walk": {"walk"}, "bike": {"bike"}, "car": {"car"}, "pt": {"pt"}, "bike_pt": {"bike", "pt"}, "car_pt": {"car", "pt"}}.get(mode_key)
    if required is None:
        return None, "unsupported_mode"
    if route.get("available") is False or route.get("feasible") is False or not all(availability[m] for m in required):
        return None, "unavailable_or_infeasible"
    supplied_legs = route.get("legs") or []
    if any(MODES.get(leg.get("mode")) is None for leg in supplied_legs):
        return None, "unsupported_leg_mode"
    if any(not availability[MODES[leg["mode"]]] for leg in supplied_legs):
        return None, "unavailable_leg_mode"
    overall = _geometry(route.get("geometry"))
    summary = route.get("summary") or {}
    if not supplied_legs and len(required) == 1 and overall:
        supplied_legs = [{"mode": next(iter(required)), "geometry": route["geometry"], **summary}]
    if not supplied_legs:
        return None, "missing_legs_or_geometry"
    legs = []
    for i, leg in enumerate(supplied_legs):
        geometry = _geometry(leg.get("geometry"))
        if not geometry and len(supplied_legs) == 1:
            geometry = overall
        if not geometry:
            return None, "missing_leg_geometry"
        mode = MODES[leg["mode"]]
        duration = _metric(leg.get("duration_seconds"), "leg duration")
        distance = _metric(leg.get("distance_meters"), "leg distance")
        if duration <= 0:
            return None, "missing_positive_leg_duration"
        legs.append({"segment_id": f"leg-{i+1}", "mode": mode, "start": geometry[0], "end": geometry[-1], "geometry": geometry, "duration_seconds": duration, "distance_meters": distance})
    if not required.issubset({leg["mode"] for leg in legs}):
        return None, "mode_sequence_does_not_match_route"
    _metric(summary.get("duration_seconds"), "route duration")
    _metric(summary.get("distance_meters"), "route distance")
    # Hash excludes rank, which changes when utility is normalised/reordered.
    identity = json.dumps({"search": search_id, "departure": departure, "mode": mode_key, "legs": legs}, sort_keys=True)
    route_id = "routing-" + hashlib.sha256(identity.encode()).hexdigest()[:20]
    evidence = route.get('_geometry_evidence', {})
    return CandidateRouteInput(route_id, origin, destination, summary=summary, legs=tuple(legs), geometry=overall or None, requested_at=departure, transport_modes=tuple(leg["mode"] for leg in legs), source_metadata={"provider": "imiq-routing", "geometry_provenance": "ROUTING_LEG_GEOMETRY", "segment_timing_method": "leg_duration_allocated_by_sampled_distance", "unallocated_duration_seconds": max(0.0, float(summary["duration_seconds"]) - sum(leg["duration_seconds"] for leg in legs)), "routing_rank": route.get("rank"), "routing_score": route.get("score"), "mode_key": mode_key, **evidence}), None


def run_routed_deliberation(payload, *, client=None, deliberate=None, geometry_client=None):
    if not isinstance(payload, dict):
        raise RoutingBridgeError("request must be an object")
    search_id = payload.get("search_id")
    if not isinstance(search_id, str) or not search_id.strip():
        raise RoutingBridgeError("search_id is required")
    departure = _datetime(payload.get("datetime"))
    origin, destination = _coordinate(payload["start"]), _coordinate(payload["stop"])
    passport, availability = routing_passport(payload.get("cognitive_passport"))
    if "journey_availability" in payload:
        availability = validate_journey_availability(payload["journey_availability"])
        # This is a disposable transport envelope; the saved Passport stays original.
        passport["profile"]["availability"] = dict(availability)
        passport.setdefault("routing_parameters", {})["availability"] = dict(availability)
        passport["beliefs"] = {"owns_bike": availability["bike"], "owns_car": availability["car"], "has_pt_access": availability["pt"]}
    max_walk = payload.get("max_walk_m", 500)
    if type(max_walk) is not int or max_walk < 0:
        raise RoutingBridgeError("max_walk_m must be a nonnegative integer")
    # Validate the original passport through the existing strict HOTCO contract
    # BEFORE disclosing it to the server-configured routing service.
    base = {"search_id": search_id, "timestamp": departure, "cognitive_passport": payload["cognitive_passport"], "contextual_query": payload.get("contextual_query", {"query_orion": True}), "candidate_routes": [CandidateRouteInput("validation", origin, destination).to_dict()]}
    if "journey_availability" in payload:
        base["journey_availability"] = dict(availability)
    ContextualDeliberationRequest.from_dict(base)
    routing = (client or RoutingClient()).ranked_routes({"cognitive_passport": passport, "start": payload["start"], "stop": payload["stop"], "datetime": departure, "max_walk_m": max_walk, "include_unavailable": False})
    routing = copy.deepcopy(routing)
    geometry_enabled = geometry_client is not None or bool(os.environ.get('IMIQ_GRAPHHOPPER_BASE_URL'))
    geometry_cache = {}
    candidates, audit = [], []
    for route in routing["routes"]:
        if not isinstance(route, dict):
            raise RoutingUnavailableError("routing returned a malformed route")
        try:
            candidate, reason = adapt_route(route, search_id=search_id, origin=origin, destination=destination, departure=departure, availability=availability)
        except (TypeError, ValueError, KeyError):
            candidate, reason = None, "invalid_route_metrics_or_geometry"
        if reason in ("unavailable_or_infeasible", "unavailable_leg_mode"):
            route["available"] = False
        geometry_failure = None
        if candidate is None and reason in ('missing_leg_geometry', 'missing_legs_or_geometry') and geometry_enabled and route.get('mode_key') in ('car', 'bike', 'foot', 'walk'):
            mode = route['mode_key']
            # Never replace an intermodal leg sequence with one single-mode path.
            if all(MODES.get(leg.get('mode')) == MODES[mode] for leg in route.get('legs', [])):
                try:
                    if mode not in geometry_cache:
                        geometry_cache[mode] = (geometry_client or GraphHopperGeometryClient()).path(mode, payload['start'], payload['stop'])
                    independent = supplemental_route(route, geometry_cache[mode])
                    candidate, reason = adapt_route(independent, search_id=search_id, origin=origin, destination=destination, departure=departure, availability=availability)
                except (OSError, ValueError, TypeError, KeyError) as exc:
                    # Source outages do not erase valid routing results.
                    geometry_failure = type(exc).__name__
                    reason = 'supplemental_geometry_unavailable'
        audit.append({"routing_rank": route.get("rank"), "mode_key": route.get("mode_key"), "route_id": candidate.route_id if candidate else None, "context_ready": candidate is not None, "reason": reason, "geometry_failure": geometry_failure, "geometry_evidence": dict(candidate.source_metadata) if candidate else None})
        if candidate:
            candidates.append(candidate.to_dict())
    contextual = None
    if candidates:
        base["candidate_routes"] = candidates
        if deliberate is not None:
            contextual = deliberate(base)
        else:
            service = ContextualDeliberationService(provider=OrionQueryListProvider())
            contextual = service.deliberate(ContextualDeliberationRequest.from_dict(base)).to_dict()
    supplemental = any(c['source_metadata'].get('route_identity_verified') is False for c in candidates)
    return {"schema_version": "routed-contextual-deliberation-v1", "search_id": search_id, "journey_availability": dict(availability), "availability_source": "current_journey_declaration" if "journey_availability" in payload else "confirmed_passport", "status": "contextual_complete" if candidates and len(candidates) == len(audit) else "contextual_partial" if candidates else "routing_only", "routing": routing, "route_audit": audit, "candidate_routes": candidates, "contextual_deliberation": contextual, "warnings": ["Routing utility and HOTCO modal readouts have different meanings; no combined route ranking is computed."] + (["Independent GraphHopper paths are supplemental same-mode candidates; identity with externally ranked routes is NOT verified. Their geometry and timing both come from GraphHopper."] if supplemental else []) + (["Some routes were excluded from contextual simulation; consult route_audit."] if len(candidates) != len(audit) else [])}
