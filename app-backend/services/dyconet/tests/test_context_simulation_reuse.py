"""Observation evidence stays separate while identical HOTCO inputs share dynamics."""
import sys
import unittest
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import contextual_deliberation as cd
from hotco_ct_v4_3 import HOTCOCTv43, HOTCOCTParameters
from input_mapping_v4_3 import NEEDS, MODES


class CountingEngine(HOTCOCTv43):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.calls = 0

    def simulate(self, *args, **kwargs):
        self.calls += 1
        return super().simulate(*args, **kwargs)


def request_fixture():
    return cd.ContextualDeliberationRequest.from_dict({
        "search_id": "reuse-test", "timestamp": "2026-10-09T10:00:00Z",
        "cognitive_passport": {"schema_version": "2.0", "agent_id": "synthetic", "profile": {
            "needs": {key: .5 for key in NEEDS},
            "beliefs": {mode: {key: 0 for key in NEEDS} for mode in MODES},
            "valences": {mode: 0 for mode in MODES},
            "availability": {mode: True for mode in MODES}}},
        "candidate_routes": [{"route_id": f"route-{i}",
            "origin": {"latitude": 52.13, "longitude": 11.62},
            "destination": {"latitude": 52.14, "longitude": 11.64},
            "summary": {"duration_seconds": 600, "distance_meters": 1800},
            "legs": [{"mode": "walk", "duration_seconds": 600, "distance_meters": 1800}]}
            for i in range(3)]})


class SimulationReuseTest(unittest.TestCase):
    def test_equal_inputs_share_dynamics_robustness_and_trajectory(self):
        engine = CountingEngine(parameters=HOTCOCTParameters(horizon=2))
        request = replace(request_fixture(), record_trajectories=True)
        with patch.object(cd, "assess_robustness", wraps=cd.assess_robustness) as check:
            result = cd.ContextualDeliberationService(engine=engine).deliberate(request).to_dict()
        self.assertEqual(1, check.call_count)
        # Baseline plus two belief stress cases; dt refinement uses its own engine.
        self.assertEqual(3, engine.calls)
        self.assertEqual(1, len(result["trajectory_archive"]))
        for route in result["candidate_results"]:
            self.assertEqual(result["baseline"], route["hotco"])
            self.assertTrue(all(v == 0 for v in route["difference_from_baseline"]["probabilities"].values()))

    def test_distinct_route_evidence_does_not_change_dynamics_or_get_merged(self):
        observation = {"source": "fixture", "variable": "temperature", "numeric_value": 8,
            "unit": "C", "timestamp": "2026-10-09T10:00:00Z", "status": "OBSERVED"}
        request = replace(request_fixture(), observations={"route-1": {"route": [observation]}})
        result = cd.ContextualDeliberationService(
            engine=HOTCOCTv43(parameters=HOTCOCTParameters(horizon=2))).deliberate(request).to_dict()
        routes = result["candidate_results"]
        self.assertEqual("EMPTY", routes[0]["source_status"]["supplied"])
        self.assertEqual("OBSERVED", routes[1]["source_status"]["supplied"])
        self.assertEqual("EMPTY", routes[2]["source_status"]["supplied"])
        self.assertEqual(routes[0]["hotco"], routes[1]["hotco"])
        self.assertNotEqual(routes[0]["route_context"], routes[1]["route_context"])

    def test_retired_policy_cannot_be_reenabled_in_direct_calls(self):
        service = cd.ContextualDeliberationService()
        with self.assertRaisesRegex(ValueError, "unsupported context_policy"):
            service.deliberate(replace(request_fixture(), context_policy="legacy_context_drive"))

    def test_solver_has_no_external_drive_argument_or_parameter(self):
        with self.assertRaises(TypeError):
            HOTCOCTParameters(alpha_context=1)
        with self.assertRaises(TypeError):
            HOTCOCTv43().simulate(request_fixture().participant, context_perturbation={})


if __name__ == "__main__":
    unittest.main()
