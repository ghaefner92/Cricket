import copy
import json
import sys
import unittest
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from d_day_evidence import validate_backup, PASSPORT, register_d_day
from test_context_simulation_reuse import request_fixture
from contextual_deliberation import ContextualDeliberationService, ContextualDeliberationRequest
from hotco_ct_v4_3 import HOTCOCTv43, HOTCOCTParameters
from flask import Flask


class DDayTest(unittest.TestCase):
    def fixture(self):
        return json.loads((Path(__file__).resolve().parents[4] / "validation/fixtures/d_day_backup.json").read_text(encoding="utf-8"))

    def test_shared_fixture_and_actual_parent_distinct_from_decision(self):
        index = validate_backup(self.fixture())
        self.assertEqual("fixture-passport", index["searches"][0]["decision_passport"]["passport_id"])
        self.assertEqual("daily-update", index["experience_events"][0]["passport_transition"]["before"]["passport_id"])
        self.assertFalse(index["learning_applied"])

    def test_inconsistent_manifest_and_missing_parent_rejected(self):
        b = self.fixture(); b["longitudinal"]["experience_events"][0]["choice_event_id"] = "forged"
        with self.assertRaises(ValueError): validate_backup(b)
        b = self.fixture(); del b["storage"][PASSPORT + ".archive.daily-update"]
        with self.assertRaises(KeyError): validate_backup(b)

    def test_profile_and_feedback_overwrite_rejected(self):
        b = self.fixture(); current = json.loads(b["storage"][PASSPORT]); current["response"]["cognitive_passport"]["profile"]["needs"]["comfort"] = .99
        b["storage"][PASSPORT] = json.dumps(current)
        with self.assertRaises(ValueError): validate_backup(b)
        b = self.fixture(); b["simulations"][0]["journeys"][0]["experience"]["changes"] = "changed"
        with self.assertRaises(ValueError): validate_backup(b)

    def test_read_only_endpoint_and_duplicate_event_rejection(self):
        app = Flask(__name__)
        from journey_api import register_journeys
        register_journeys(app); register_d_day(app); client = app.test_client()
        response = client.post("/api/dyconet/d-day/validate", json=self.fixture())
        self.assertEqual(200, response.status_code); self.assertFalse(response.json["stored_on_server"])
        b = self.fixture(); b["simulations"][0]["journeys"].append(copy.deepcopy(b["simulations"][0]["journeys"][0]))
        self.assertEqual(422, client.post("/api/dyconet/d-day/validate", json=b).status_code)

    def test_observations_only_preserves_trajectories(self):
        req = replace(request_fixture(), context_policy="observations_only", record_trajectories=True)
        service = ContextualDeliberationService(engine=HOTCOCTv43(parameters=HOTCOCTParameters(horizon=2)))
        result = service.deliberate(req).to_dict()
        baseline = result["baseline"]
        for route in result["candidate_results"]:
            self.assertTrue(route["route_context"])
            self.assertEqual(baseline["final_action_activations"], route["hotco"]["final_action_activations"])
            self.assertEqual(baseline["simulation_evidence"]["trajectory_id"], route["hotco"]["simulation_evidence"]["trajectory_id"])
            self.assertEqual("NOT_APPLIED", route["hotco"]["context_metadata"]["context_model_status"])
            self.assertNotIn("context_perturbation", route)

    def test_unknown_policy_rejected(self):
        p = json.loads(self.fixture()["storage"][PASSPORT])["response"]["cognitive_passport"]
        p["agent_id"] = "synthetic-d-day"
        data = {"search_id":"bad", "timestamp":"2026-10-09T10:00:00Z", "context_policy":"invented",
                "cognitive_passport":p, "candidate_routes":[r.to_dict() for r in request_fixture().candidate_routes]}
        with self.assertRaisesRegex(ValueError, "unsupported context_policy"):
            ContextualDeliberationRequest.from_dict(data)
        del data["context_policy"]
        self.assertEqual("observations_only", ContextualDeliberationRequest.from_dict(data).context_policy)


if __name__ == "__main__":
    unittest.main()
