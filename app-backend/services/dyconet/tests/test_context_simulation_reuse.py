"""Compare complete responses against the former uncached orchestration."""
import sys
import unittest
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import contextual_deliberation as cd
from hotco_ct_v4_3 import HOTCOCTv43, HOTCOCTParameters
from input_mapping_v4_3 import NEEDS, MODES


class ReferenceService(cd.ContextualDeliberationService):
    def deliberate(self, request):
        robustness_cache = {}
        baseline_result = self.engine.simulate(request.participant)
        def summarize(result, context=None):
            vector, _ = self.engine._context_vectors(context, 1)
            key = (vector * self.engine.parameters.alpha_context).tobytes()
            if key not in robustness_cache:
                robustness_cache[key] = cd.assess_robustness(request.participant, result, self.engine, context)
            return self._summary(request.participant, result, robustness_cache[key])
        baseline = summarize(baseline_result)
        results = []
        for candidate in request.candidate_routes:
            route_context, source_status, warnings = self._enrich_candidate(candidate, request)
            perturbation, model, rho = cd.build_context_perturbation(route_context, baseline_result, self.engine, dict(request.tolerance_profile.values))
            result = self.engine.simulate(request.participant, context_perturbation=perturbation)
            result.context_metadata.update({"context_model": model, "context_model_status": cd.EXPERIMENTAL_UNCALIBRATED, "rho": rho})
            summary = summarize(result, perturbation)
            results.append(cd.RouteDeliberationResult(candidate.route_id, route_context, perturbation, summary,
                self._difference(baseline, summary), tuple(dict.fromkeys((*warnings, *perturbation.warnings, *summary.context_metadata.get("warnings", ())))), source_status))
        return cd.ContextualDeliberationResponse(request.search_id, request.timestamp, baseline, tuple(results), cd.MODEL_VERSION, cd.NORMALIZATION_VERSION, cd.PERTURBATION_VERSION, request.warnings)


class CountingEngine(HOTCOCTv43):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.calls = 0
    def simulate(self, *args, **kwargs):
        self.calls += 1
        return super().simulate(*args, **kwargs)


def request_fixture():
    return cd.ContextualDeliberationRequest.from_dict({"context_policy": "legacy_context_drive", "search_id": "reuse-test", "timestamp": "2026-10-09T10:00:00Z",
        "cognitive_passport": {"schema_version": "2.0", "agent_id": "synthetic", "profile": {
            "needs": {key: .5 for key in NEEDS}, "beliefs": {mode: {key: 0 for key in NEEDS} for mode in MODES},
            "valences": {mode: 0 for mode in MODES}, "availability": {mode: True for mode in MODES}}},
        "candidate_routes": [{"route_id": f"route-{i}", "origin": {"latitude": 52.13, "longitude": 11.62},
            "destination": {"latitude": 52.14, "longitude": 11.64}, "summary": {"duration_seconds": 600, "distance_meters": 1800},
            "legs": [{"mode": "walk", "duration_seconds": 600, "distance_meters": 1800}]} for i in range(3)]})


class SimulationReuseTest(unittest.TestCase):
    def test_equal_context_full_response_and_call_reduction(self):
        req = request_fixture()
        reference_engine = CountingEngine()
        optimized_engine = CountingEngine()
        expected = ReferenceService(engine=reference_engine).deliberate(req).to_dict()
        actual = cd.ContextualDeliberationService(engine=optimized_engine).deliberate(req).to_dict()
        self.assertEqual(expected, actual)
        self.assertEqual(reference_engine.calls - 2, optimized_engine.calls)

    def test_distinct_forcing_and_route_metadata_are_not_merged(self):
        req = request_fixture()
        builder = cd.build_context_perturbation
        def forcing(route, *args):
            perturbation, model, rho = builder(route, *args)
            values = dict(perturbation.need_perturbations)
            values[NEEDS[0]] = -.07 if route.route_id == "route-2" else .05
            return replace(perturbation, need_perturbations=values, warnings=(*perturbation.warnings, route.route_id)), model, rho
        parameters = HOTCOCTParameters(horizon=2)
        with patch.object(cd, "build_context_perturbation", side_effect=forcing):
            expected = ReferenceService(engine=CountingEngine(parameters=parameters)).deliberate(req).to_dict()
            engine = CountingEngine(parameters=parameters)
            actual = cd.ContextualDeliberationService(engine=engine).deliberate(req).to_dict()
        self.assertEqual(expected, actual)
        self.assertIn("route-1", actual["candidate_results"][1]["hotco"]["context_metadata"]["warnings"])
        self.assertNotIn("route-0", actual["candidate_results"][1]["hotco"]["context_metadata"]["warnings"])


if __name__ == "__main__":
    unittest.main()
