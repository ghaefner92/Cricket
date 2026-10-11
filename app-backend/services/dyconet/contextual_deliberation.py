"""Phase 5 orchestration for route-conditioned HOTCO deliberation.

Candidate order is preserved. This service composes the existing provider,
normalizer, perturbation builder, and HOTCO simulator without duplicating
their scientific rules.
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace, asdict
from copy import deepcopy
from journey_availability import apply_journey_availability
from typing import Any, Mapping, Sequence

from context_perturbation import (
    PERTURBATION_VERSION,
    ContextPerturbationBuilder,
    ContextPerturbationResult,
    SUPPORTED_TOLERANCES,
    active_tolerance_dimensions,
    ToleranceProfile,
)
from context_models import build_context_perturbation, EXPERIMENTAL_UNCALIBRATED
from hotco_ct_v4_3 import HOTCOCTv43, MODEL_VERSION, SimulationResult
from input_mapping_v4_3 import MODES, NEEDS, ParticipantInput, parse_participant_input
from orion_context import (
    OrionConfigurationError,
    OrionContextProvider,
    OrionHttpError,
    OrionNetworkError,
    OrionProviderError,
    OrionTimeoutError,
)
from passport_v2 import build_cognitive_passport as build_base_passport
from profile_update import _reconstruct_questionnaire
from adaptive_profile_update import (
    AdaptiveProfileUpdateError,
    reconstruct_adaptive_participant,
)
from route_context import CandidateRouteInput, ContextObservation, RouteContext
from route_context_adapter import NORMALIZATION_VERSION, RouteContextAdapter
from xai_diagnostics import ambiguity_state, build_xai_diagnostics
from recommendation_robustness import assess_robustness


REQUEST_SCHEMA_VERSION = "contextual-deliberation-request-v1"
RESPONSE_SCHEMA_VERSION = "contextual-deliberation-response-v1"


class ContextualDeliberationError(ValueError):
    pass


class ContextualProviderConfigurationError(ContextualDeliberationError):
    pass


@dataclass(frozen=True)
class ContextQueryConfiguration:
    query_orion: bool = False
    entity_families: tuple[str, ...] = ("weather", "traffic", "parking", "air_quality", "ev_charging")
    radius_meters: float = 500.0
    weather_radius_meters: float = 5000.0
    limit: int = 100

    def __post_init__(self) -> None:
        if self.radius_meters <= 0 or self.weather_radius_meters <= 0 or self.limit <= 0:
            raise ContextualDeliberationError("context radii and limit must be positive")

    @classmethod
    def from_dict(cls, data: Mapping[str, Any] | None) -> "ContextQueryConfiguration":
        data = data or {}
        return cls(
            bool(data.get("query_orion", False)),
            tuple(data.get("entity_families", cls.entity_families)),
            float(data.get("radius_meters", 500.0)),
            float(data.get("weather_radius_meters", 5000.0)),
            int(data.get("limit", 100)),
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "query_orion": self.query_orion,
            "entity_families": list(self.entity_families),
            "radius_meters": self.radius_meters,
            "weather_radius_meters": self.weather_radius_meters,
            "limit": self.limit,
            "spatial_matching": "segment_midpoint_near_radius",
        }


@dataclass(frozen=True)
class ContextualDeliberationRequest:
    search_id: str
    timestamp: str
    participant: ParticipantInput
    tolerance_profile: ToleranceProfile
    candidate_routes: tuple[CandidateRouteInput, ...]
    observations: Mapping[str, Any] = field(default_factory=dict)
    query_configuration: ContextQueryConfiguration = ContextQueryConfiguration()
    warnings: tuple[str, ...] = ()
    record_trajectories: bool = False
    context_policy: str = "observations_only"

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "ContextualDeliberationRequest":
        if data.get("schema_version", REQUEST_SCHEMA_VERSION) != REQUEST_SCHEMA_VERSION:
            raise ContextualDeliberationError("unsupported contextual deliberation request schema")
        if any(key in data for key in ("api_key", "orion_api_key", "IMIQ_ORION_API_KEY")):
            raise ContextualDeliberationError("Orion credentials must be configured in the server environment")
        search_id = str(data.get("search_id", "")).strip()
        if not search_id:
            raise ContextualDeliberationError("search_id is required")
        timestamp = str(data.get("timestamp", "")).strip()
        if not timestamp:
            raise ContextualDeliberationError("timestamp is required")
        candidates = tuple(CandidateRouteInput.from_dict(item) for item in data.get("candidate_routes", ()))
        if not candidates:
            raise ContextualDeliberationError("candidate_routes must contain at least one route")
        passport_value = data.get("cognitive_passport")
        if data.get("participant") is not None:
            participant = parse_participant_input(data["participant"])
        elif passport_value is not None:
            root = (
                passport_value.get(
                    "cognitive_passport",
                    passport_value,
                )
                if isinstance(
                    passport_value,
                    Mapping,
                )
                else passport_value
            )

            if (
                isinstance(
                    root,
                    Mapping,
                )
                and "adaptive_passport"
                in root
            ):
                if not isinstance(
                    root.get(
                        "adaptive_passport"
                    ),
                    Mapping,
                ):
                    raise ContextualDeliberationError(
                        "adaptive_passport must be a JSON object"
                    )

                try:
                    participant = (
                        reconstruct_adaptive_participant(
                            root
                        )
                    )
                except AdaptiveProfileUpdateError as exc:
                    raise ContextualDeliberationError(
                        "invalid Adaptive Cognitive Passport: "
                        + str(exc)
                    ) from exc

            else:
                participant = parse_participant_input(
                    _reconstruct_questionnaire(
                        root
                    )
                )
        else:
            raise ContextualDeliberationError("participant or cognitive_passport is required")

        if "journey_availability" in data:
            try:
                participant = apply_journey_availability(participant, data["journey_availability"])
            except ValueError as exc:
                raise ContextualDeliberationError(str(exc)) from exc

        warnings: list[str] = []
        tolerance_values = data.get("tolerance_profile")
        if tolerance_values is None and isinstance(passport_value, Mapping):
            root = passport_value.get("cognitive_passport", passport_value)
            profile = root.get("profile", {}) if isinstance(root, Mapping) else {}
            tolerance_values = profile.get("environmental_tolerances", {}) if isinstance(profile, Mapping) else {}
        tolerance_values = tolerance_values or {}
        if not isinstance(tolerance_values, Mapping):
            raise ContextualDeliberationError("tolerance_profile must be a JSON object")
        filtered = {
            name: tolerance_values[name]
            for name in SUPPORTED_TOLERANCES
            if name in tolerance_values and tolerance_values[name] is not None
        }
        for name in active_tolerance_dimensions(filtered):
            if name not in filtered:
                warnings.append(f"tolerance '{name}' is missing; that contextual dimension remains UNKNOWN")
        extras = sorted(set(tolerance_values) - set(SUPPORTED_TOLERANCES))
        if extras:
            warnings.append(f"unsupported tolerance dimensions ignored: {', '.join(extras)}")
        policy = data.get("context_policy", "observations_only")
        if policy not in ("observations_only", "legacy_context_drive"):
            raise ContextualDeliberationError("unsupported context_policy")
        return cls(
            search_id,
            timestamp,
            participant,
            ToleranceProfile(filtered),
            candidates,
            data.get("contextual_observations", {}),
            ContextQueryConfiguration.from_dict(data.get("contextual_query")),
            tuple(warnings),
            data.get("record_trajectories") is True,
            policy,
        )


@dataclass(frozen=True)
class HOTCODeliberationSummary:
    final_action_activations: Mapping[str, float]
    probabilities: Mapping[str, float]
    winner: str
    context_metadata: Mapping[str, Any]
    process_diagnostics: Mapping[str, Any] = field(default_factory=dict)
    node_activations: Mapping[str, Any] = field(default_factory=dict)
    ambiguity_state: str = "UNKNOWN"
    simulation_evidence: Mapping[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {"final_action_activations": dict(self.final_action_activations), "probabilities": dict(self.probabilities), "winner": self.winner, "context_metadata": dict(self.context_metadata), "process_diagnostics": dict(self.process_diagnostics), "node_activations": dict(self.node_activations), "ambiguity_state": self.ambiguity_state, "simulation_evidence": dict(self.simulation_evidence)}


@dataclass(frozen=True)
class RouteDeliberationResult:
    route_id: str
    route_context: RouteContext
    context_perturbation: ContextPerturbationResult
    hotco: HOTCODeliberationSummary
    difference_from_baseline: Mapping[str, Mapping[str, float]]
    warnings: tuple[str, ...]
    source_status: Mapping[str, str]

    def to_dict(self) -> dict[str, Any]:
        return {"route_id": self.route_id, "route_context": self.route_context.to_dict(), "context_perturbation": self.context_perturbation.to_dict(), "hotco": self.hotco.to_dict(), "difference_from_baseline": {key: dict(value) for key, value in self.difference_from_baseline.items()}, "warnings": list(self.warnings), "source_status": dict(self.source_status)}


@dataclass(frozen=True)
class ContextualDeliberationResponse:
    search_id: str
    timestamp: str
    baseline: HOTCODeliberationSummary
    candidate_results: tuple[RouteDeliberationResult, ...]
    model_version: str
    normalization_version: str
    perturbation_version: str
    warnings: tuple[str, ...]

    def to_dict(self) -> dict[str, Any]:
        output = {"schema_version": RESPONSE_SCHEMA_VERSION, "search_id": self.search_id, "timestamp": self.timestamp, "baseline": self.baseline.to_dict(), "candidate_results": [item.to_dict() for item in self.candidate_results], "model_version": self.model_version, "normalization_version": self.normalization_version, "perturbation_version": self.perturbation_version, "warnings": list(self.warnings)}
        from hashlib import sha256
        import json
        archive = {}
        for summary in [output["baseline"], *(item["hotco"] for item in output["candidate_results"])]:
            evidence = summary["simulation_evidence"]
            states = evidence.pop("states", [])
            if states:
                identity = "trajectory-"+sha256(json.dumps(states, separators=(",", ":")).encode()).hexdigest()
                archive.setdefault(identity, states)
                evidence["trajectory_id"] = identity
                evidence["sample_count"] = len(states)
        output["trajectory_archive"] = archive
        return output



class ContextualDeliberationService:
    def __init__(self, *, engine: HOTCOCTv43 | None = None, provider: OrionContextProvider | None = None, adapter: RouteContextAdapter | None = None) -> None:
        self.engine = engine or HOTCOCTv43()
        self.provider = provider
        self.adapter = adapter or RouteContextAdapter()

    def deliberate(self, request: ContextualDeliberationRequest) -> ContextualDeliberationResponse:
        # Cache within this request only: identical effective context forces
        # share checks, while route observations and metadata stay separate.
        robustness_cache = {}
        simulation_cache = {}
        baseline_result = self.engine.simulate(request.participant)
        def summarize(result, context=None):
            vector, _ = self.engine._context_vectors(context, 1)
            key = (vector * self.engine.parameters.alpha_context).tobytes()
            if key not in robustness_cache:
                robustness_cache[key] = assess_robustness(request.participant, result, self.engine, context)
            return self._summary(request.participant, result, robustness_cache[key], request.record_trajectories)
        baseline = summarize(baseline_result)
        candidate_results: list[RouteDeliberationResult] = []
        for candidate in request.candidate_routes:
            route_context, source_status, context_warnings = self._enrich_candidate(candidate, request)
            if request.context_policy == "observations_only":
                # D-Day excludes F3: preserve measured context, never manufacture
                # a learned expectation or a direct ODE force from these readings.
                perturbation = ContextPerturbationResult(candidate.route_id, NORMALIZATION_VERSION,
                    PERTURBATION_VERSION, (), {}, {}, {}, (), (), (), (), {})
                context_model, rho = "observations_only", None
            else:
                perturbation, context_model, rho = build_context_perturbation(route_context, baseline_result, self.engine, dict(request.tolerance_profile.values))
            # One participant and fixed engine parameters per request. Equal
            # aligned forcing vectors have identical dynamics, even when their
            # route evidence/warnings differ. Keep that metadata per candidate.
            vector, metadata = self.engine._context_vectors(perturbation, 1)
            simulation_key = vector.tobytes()
            if simulation_key not in simulation_cache:
                simulation_cache[simulation_key] = (baseline_result if request.context_policy == "observations_only" else
                    self.engine.simulate(request.participant, context_perturbation=perturbation))
            cached = simulation_cache[simulation_key]
            contextual_result = replace(cached, trajectory=cached.trajectory.copy(),
                topology=cached.topology.copy(), availability=cached.availability.copy(),
                projection_corrections=deepcopy(cached.projection_corrections), context_metadata=metadata)
            contextual_result.context_metadata.update({"context_model": context_model, "context_model_status": EXPERIMENTAL_UNCALIBRATED, "rho": rho})
            if request.context_policy == "observations_only":
                contextual_result.context_metadata.update({"context_policy": "observations_only",
                    "context_model_status": "NOT_APPLIED", "learning_applied": False, "state_carryover_applied": False})
            contextual = summarize(contextual_result, perturbation)
            candidate_results.append(RouteDeliberationResult(candidate.route_id, route_context, perturbation, contextual, self._difference(baseline, contextual), tuple(dict.fromkeys((*context_warnings, *perturbation.warnings, *contextual.context_metadata.get("warnings", ())))), source_status))
        return ContextualDeliberationResponse(request.search_id, request.timestamp, baseline, tuple(candidate_results), MODEL_VERSION, NORMALIZATION_VERSION, PERTURBATION_VERSION, request.warnings)

    def _enrich_candidate(self, candidate: CandidateRouteInput, request: ContextualDeliberationRequest) -> tuple[RouteContext, dict[str, str], list[str]]:
        supplied = request.observations.get(candidate.route_id, {}) if isinstance(request.observations, Mapping) else {}
        shared = tuple(ContextObservation.from_dict(item) for item in supplied.get("route", ())) if isinstance(supplied, Mapping) else ()
        segment_payload = supplied.get("segments", {}) if isinstance(supplied, Mapping) else {}
        segment_observations = {str(segment_id): [ContextObservation.from_dict(item) for item in values] for segment_id, values in segment_payload.items()}
        preliminary = self.adapter.enrich(candidate, segment_observations=segment_observations)
        for segment in preliminary.segments:
            segment_observations.setdefault(segment.segment_id, []).extend(shared)
        source_status = {"supplied": "OBSERVED" if shared or segment_payload else "EMPTY"}
        warnings: list[str] = []
        query_metadata: dict[str, Any] = {}
        if request.query_configuration.query_orion:
            provider = self.provider
            if provider is None:
                try:
                    provider = OrionContextProvider()
                except OrionConfigurationError as exc:
                    raise ContextualProviderConfigurationError(str(exc)) from exc
            for segment in preliminary.segments:
                latitude = (segment.start.latitude + segment.end.latitude) / 2.0
                longitude = (segment.start.longitude + segment.end.longitude) / 2.0
                coords = f"{latitude},{longitude}"
                query_metadata[segment.segment_id] = {"method": "segment_midpoint_near_radius", "radius_meters": request.query_configuration.radius_meters, "coordinates": {"latitude": latitude, "longitude": longitude}}
                for family in request.query_configuration.entity_families:
                    radius_meters = request.query_configuration.weather_radius_meters if family == "weather" else request.query_configuration.radius_meters
                    try:
                        observations = provider.get_context(
                            family,
                            georel=f"near;maxDistance:{radius_meters:g}",
                            geometry="point",
                            coords=coords,
                            limit=request.query_configuration.limit,
                            nearest_only=family == "weather",
                        )
                        segment_observations.setdefault(segment.segment_id, []).extend(observations)
                        source_status[family] = "OBSERVED" if observations else "EMPTY"
                    except OrionHttpError as exc:
                        source_status[family] = "UNAVAILABLE"
                        warnings.append(f"Orion source {family} unavailable: HTTP {exc.status_code}")
                    except (OrionTimeoutError, OrionNetworkError) as exc:
                        source_status[family] = "UNAVAILABLE"
                        warnings.append(f"Orion source {family} unavailable: {type(exc).__name__}")
                    except OrionProviderError as exc:
                        source_status[family] = "MALFORMED"
                        warnings.append(f"Orion source {family} unusable: {type(exc).__name__}")
        route_context = self.adapter.enrich(candidate, segment_observations=segment_observations, query_metadata=query_metadata)
        return route_context, source_status, warnings

    def _summary(self, participant: ParticipantInput, result: SimulationResult, robustness=None, record_trajectories=False) -> HOTCODeliberationSummary:
        passport = build_base_passport(participant, result, self.engine)["cognitive_passport"]
        deliberation = passport["deliberation"]
        trajectory = result.trajectory[0]
        valence_start = self.engine.n_needs + self.engine.n_modes
        node_activations = {
            "needs": {
                "initial": dict(zip(NEEDS, trajectory[0, : self.engine.n_needs].tolist())),
                "final": dict(zip(NEEDS, trajectory[-1, : self.engine.n_needs].tolist())),
            },
            "valences": {
                "initial": dict(zip(MODES, trajectory[0, valence_start:].tolist())),
                "final": dict(zip(MODES, trajectory[-1, valence_start:].tolist())),
            },
        }
        diagnostics = build_xai_diagnostics(participant, result, self.engine)
        diagnostics["robustness"] = robustness or {"status": "NOT_ASSESSED"}
        state = ambiguity_state(diagnostics.get("competition"))
        if state == "CLEAR" and diagnostics["robustness"]["status"] == "SENSITIVE":
            state = "SENSITIVE"
        return HOTCODeliberationSummary(
            deliberation["terminal_action_activations"],
            deliberation["comparative_readout"],
            deliberation["terminal_tendency"],
            result.context_metadata,
            diagnostics,
            node_activations,
            state,
            {"schema_version": "hotco-simulation-evidence-v1", "state_kind": "model_prediction",
             "model_version": MODEL_VERSION, "parameters": asdict(self.engine.parameters),
             "state_columns": [*NEEDS, *("action:"+m for m in MODES), *("valence:"+m for m in MODES)],
             "time_reference": "model_units_not_elapsed_travel_time", "dt": self.engine.parameters.dt,
             "states": trajectory.tolist() if record_trajectories else [], "topology": result.topology[0].tolist(),
             "availability": result.availability[0].tolist(), "projection_corrections": result.projection_corrections},
        )

    @staticmethod
    def _difference(baseline: HOTCODeliberationSummary, contextual: HOTCODeliberationSummary) -> dict[str, dict[str, float]]:
        return {
            "final_action_activations": {mode: contextual.final_action_activations[mode] - baseline.final_action_activations[mode] for mode in baseline.final_action_activations},
            "probabilities": {mode: contextual.probabilities[mode] - baseline.probabilities[mode] for mode in baseline.probabilities},
        }


def run_contextual_deliberation(payload: Mapping[str, Any], *, service: ContextualDeliberationService | None = None) -> dict[str, Any]:
    request = ContextualDeliberationRequest.from_dict(payload)
    return (service or ContextualDeliberationService()).deliberate(request).to_dict()
