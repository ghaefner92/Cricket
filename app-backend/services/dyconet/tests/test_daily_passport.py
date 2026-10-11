import copy
import sys
import unittest
from pathlib import Path
from dataclasses import replace
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from daily_passport import confirm_daily_passport, validate_daily_goals, attach_initial_daily
from adaptive_hotco_adapter import build_adaptive_hotco_input
from adaptive_passport_xai import build_adaptive_cognitive_passport
from adaptive_profile_update import reconstruct_adaptive_participant
from hotco_ct_v4_3 import HOTCOCTv43, HOTCOCTParameters
from input_mapping_v4_3 import NEEDS, MODES
from test_weekly_need_allocation import complete
from test_context_simulation_reuse import request_fixture
from contextual_deliberation import ContextualDeliberationService


class DailyPassportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ratings = {n: i % 7 + 1 for i, n in enumerate(NEEDS)}
        _, completed = complete({"needs": cls.ratings})
        adapted = build_adaptive_hotco_input(completed, availability={m: True for m in MODES})
        cls.engine = HOTCOCTv43(parameters=HOTCOCTParameters(horizon=2))
        cls.output = build_adaptive_cognitive_passport(adapted, cls.engine.simulate(adapted.participant), cls.engine)

    def goals(self, day="2026-10-09", ratings=None):
        return {"local_date": day, "timezone": "Europe/Berlin", "confirmed_at": day+"T08:00:00Z", "ratings": ratings or self.ratings}

    def test_initial_daily_snapshot_and_ordinal_provenance(self):
        output = attach_initial_daily(copy.deepcopy(self.output), self.goals())
        self.assertEqual(self.ratings, output["cognitive_passport"]["daily_goals"]["ratings"])
        wrong = self.goals(ratings={n: 7 for n in NEEDS})
        with self.assertRaises(ValueError): attach_initial_daily(copy.deepcopy(self.output), wrong)

    def test_daily_revision_preserves_all_beliefs_and_can_be_reconstructed(self):
        previous = copy.deepcopy(self.output["cognitive_passport"])
        original = copy.deepcopy(previous)
        updated = confirm_daily_passport({"schema_version": "daily-passport-confirm-v1", "previous_passport": previous,
            "daily_goals": self.goals(ratings={n: 7 for n in NEEDS})}, self.engine)["cognitive_passport"]
        self.assertEqual(original, previous)
        self.assertEqual(original["profile"]["beliefs"], updated["profile"]["beliefs"])
        self.assertEqual(original["profile"]["valences"], updated["profile"]["valences"])
        self.assertEqual(original["lineage"]["passport_id"], updated["lineage"]["parent_passport_id"])
        self.assertEqual(11, sum(updated["profile"]["needs"].values()))
        reconstructed = reconstruct_adaptive_participant(updated)
        self.assertEqual([1]*11, reconstructed.needs)
        baseline = reconstruct_adaptive_participant(original)
        self.assertEqual(baseline.beliefs, reconstructed.beliefs)
        again = confirm_daily_passport({"schema_version": "daily-passport-confirm-v1", "previous_passport": updated,
            "daily_goals": self.goals(ratings={n: 7 for n in NEEDS})}, self.engine)["cognitive_passport"]
        self.assertEqual(updated, again)

    def test_next_day_keeps_estimation_basis_and_lineage(self):
        previous = self.output["cognitive_passport"]
        for day, rating in [("2026-10-09", 7), ("2026-10-10", 1)]:
            previous = confirm_daily_passport({"schema_version": "daily-passport-confirm-v1", "previous_passport": previous,
                "daily_goals": self.goals(day, {n: rating for n in NEEDS})}, self.engine)["cognitive_passport"]
            self.assertEqual(self.output["cognitive_passport"]["profile"]["beliefs"], previous["profile"]["beliefs"])
            self.assertEqual([float(rating-1)/6]*11, reconstruct_adaptive_participant(previous).needs)

    def test_timezone_and_missing_ratings_are_rejected(self):
        value = self.goals(); value["confirmed_at"] = "2026-10-08T20:00:00Z"
        with self.assertRaises(ValueError): validate_daily_goals(value)
        for invalid in [0, 8, True, 1.5]:
            value = self.goals(ratings=dict(self.ratings)); value["ratings"][NEEDS[0]] = invalid
            with self.assertRaises(ValueError): validate_daily_goals(value)

    def test_full_trajectory_archive_and_default_lightweight_response(self):
        request = request_fixture()
        engine = HOTCOCTv43(parameters=HOTCOCTParameters(horizon=2))
        service = ContextualDeliberationService(engine=engine)
        lightweight = service.deliberate(request).to_dict()
        self.assertEqual({}, lightweight["trajectory_archive"])
        recorded = service.deliberate(replace(request, record_trajectories=True)).to_dict()
        summary = recorded["baseline"]["simulation_evidence"]
        states = recorded["trajectory_archive"][summary["trajectory_id"]]
        self.assertEqual(engine.n_steps+1, len(states))
        self.assertEqual(engine.simulate(request.participant).trajectory[0].tolist(), states)
        self.assertEqual(19, len(summary["state_columns"]))
        self.assertEqual("model_prediction", summary["state_kind"])
        self.assertLess(len(recorded["trajectory_archive"]), 1+len(recorded["candidate_results"]))


if __name__ == "__main__": unittest.main()
