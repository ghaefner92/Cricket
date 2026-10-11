"""Exact points throughout onboarding, HOTCO serialization and explicit updates."""
import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from need_allocation import SCHEMA_VERSION, validate_need_allocation
from input_mapping_v4_3 import NEEDS, MODES
from adaptive_passport_v1 import AdaptivePassportError
from adaptive_passport_onboarding import start_adaptive_passport, complete_adaptive_passport
from adaptive_hotco_adapter import build_adaptive_hotco_input, AdaptiveHotcoAdapterError
from adaptive_passport_xai import build_adaptive_cognitive_passport
from adaptive_profile_update import reconstruct_adaptive_participant, prepare_adaptive_profile_update
from hotco_ct_v4_3 import HOTCOCTv43


def allocation():
    values = {n: 0 for n in NEEDS}
    values.update(pro_env=1, physical=2, cost=3, speed=4)
    return {"schema_version": SCHEMA_VERSION, "budget": 10, "points": values}


def complete(responses):
    start = start_adaptive_passport({"schema_version": "adaptive_cognitive_passport_onboarding_1.0",
        "agent_id": "synthetic-allocation-test", "responses": {**responses, "valences": {m: 0 for m in MODES}}})
    answers = {q["cell_id"]: [1, 3, 5, 7][i] for i, q in enumerate(start["questions"])}
    result = complete_adaptive_passport({"schema_version": start["schema_version"], "start": start, "answers": answers})
    return start, result


class WeeklyAllocationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.start, cls.completed = complete({"needs_allocation": allocation()})
        cls.adapted = build_adaptive_hotco_input(cls.completed, availability={m: True for m in MODES})
        cls.engine = HOTCOCTv43()
        result = cls.engine.simulate(cls.adapted.participant)
        cls.passport = build_adaptive_cognitive_passport(cls.adapted, result, cls.engine)["cognitive_passport"]

    def test_every_point_is_distinct_and_sums_to_one(self):
        for n in range(11):
            value = allocation()
            value["points"] = {key: 0 for key in NEEDS}
            value["points"].update(pro_env=n, physical=10-n)
            _, weights = validate_need_allocation(value)
            self.assertEqual(weights["pro_env"], n/10)
            self.assertAlmostEqual(sum(weights.values()), 1)

    def test_invalid_or_ambiguous_inputs_are_rejected(self):
        variants = []
        for wrong in (True, "10", 9, 10.0):
            value = allocation(); value["budget"] = wrong; variants.append(value)
        for wrong in (-1, 11, .5, True, "1", float("nan")):
            value = allocation(); value["points"]["pro_env"] = wrong; variants.append(value)
        value = allocation(); value["points"].pop("privacy"); variants.append(value)
        value = allocation(); value["points"]["unknown"] = 0; variants.append(value)
        value = allocation(); value["points"]["pro_env"] = 2; variants.append(value)
        for value in variants:
            with self.subTest(value=value), self.assertRaises(ValueError): validate_need_allocation(value)
        with self.assertRaises(AdaptivePassportError):
            complete({"needs_allocation": allocation(), "needs": {n: 1 for n in NEEDS}})

    def test_exact_values_survive_all_boundaries_without_fake_ratings(self):
        expected = {n: allocation()["points"][n]/10 for n in NEEDS}
        self.assertEqual(self.start["normalized_inputs"]["needs"], expected)
        self.assertEqual(self.completed["profile"]["needs"], expected)
        self.assertEqual(dict(zip(NEEDS, self.adapted.participant.needs)), expected)
        self.assertEqual(self.passport["profile"]["needs"], expected)
        self.assertEqual(self.passport["routing_parameters"]["need_weights"], expected)
        for item in (self.completed, self.passport):
            self.assertNotIn("needs_raw_1_to_7", item["observed_measurements"])
            self.assertEqual(item["observed_measurements"]["needs_allocation"], allocation())
        self.assertEqual(self.passport["input_provenance"]["transformations"]["needs"], "points / 10; no rounding")
        restored = reconstruct_adaptive_participant(self.passport)
        self.assertEqual(restored.needs, self.adapted.participant.needs)
        self.assertEqual(restored.needs_allocation, allocation())
        candidate = self.passport["adaptive_questioning"].get("candidate")
        if candidate: self.assertNotEqual(candidate["target"]["kind"], "need")

    def test_tampered_weights_and_original_points_are_rejected(self):
        forged = copy.deepcopy(self.completed); forged["profile"]["needs"]["pro_env"] = 1/6
        with self.assertRaises(AdaptiveHotcoAdapterError):
            build_adaptive_hotco_input(forged, availability={m: True for m in MODES})
        forged = copy.deepcopy(self.start); forged["normalized_inputs"]["needs"]["pro_env"] = 1/6
        with self.assertRaises(AdaptivePassportError):
            complete_adaptive_passport({"schema_version": forged["schema_version"], "start": forged,
                "answers": {q["cell_id"]: 4 for q in forged["questions"]}})

    def test_legacy_integer_scale_still_works(self):
        ratings = {n: 1+i%7 for i, n in enumerate(NEEDS)}
        _, completed = complete({"needs": ratings})
        adapted = build_adaptive_hotco_input(completed, availability={m: True for m in MODES})
        self.assertEqual(adapted.participant.needs, [(ratings[n]-1)/6 for n in NEEDS])
        self.assertIsNone(adapted.participant.needs_allocation)
        self.assertEqual(completed["observed_measurements"]["needs_raw_1_to_7"], ratings)

    def test_explicit_belief_update_preserves_allocation(self):
        # Exercise the existing update protocol with a controlled explicit belief question.
        previous = copy.deepcopy(self.passport)
        target = {"kind": "belief", "mode": "walk", "need": "cost",
            "measurement_status": "observed" if "walk__cost" in previous["observed_measurements"]["beliefs_raw_1_to_7"] else "estimated"}
        previous["adaptive_questioning"] = {"question_needed": True, "candidate": {
            "scope": "profile", "question_id": "synthetic-belief-update", "target": target}}
        adapted, lineage = prepare_adaptive_profile_update({"schema_version": "adaptive_profile_update_1.0",
            "previous_passport": previous, "update": {"question_id": "synthetic-belief-update",
                "target": target, "raw_rating": 6, "explicit_user_confirmation": True}})
        self.assertEqual(adapted.participant.needs, self.adapted.participant.needs)
        self.assertEqual(adapted.participant.needs_allocation, allocation())
        result = self.engine.simulate(adapted.participant)
        revised = build_adaptive_cognitive_passport(adapted, result, self.engine, lineage=lineage)["cognitive_passport"]
        self.assertEqual(revised["observed_measurements"]["needs_allocation"], allocation())
        self.assertNotIn("needs_raw_1_to_7", revised["observed_measurements"])


if __name__ == "__main__": unittest.main()
