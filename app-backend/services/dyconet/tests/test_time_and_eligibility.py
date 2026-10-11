import unittest
from copy import deepcopy
from comfort_pilot.sensor_time import attach_reception
from comfort_pilot.context_capture import prepare_reading
from comfort_pilot.context_audit import audit_context
from comfort_pilot.evidence import observations_from_records
from comfort_pilot.eligibility import exclusion_reason, validate_exclusions, backup_exclusions, STORAGE_KEY
from comfort_pilot.prospective import forecast
from comfort_pilot.prospective_evaluation import evaluate
from test_prospective import fixture

NOW = "2026-10-11T10:00:00Z"


def sensor(variable="temperature", unit="C"):
    return {"variable":variable,"numeric_value":18,"unit":unit,"source":"orion",
            "source_entity_id":"Sensor:Weather:Walter","timestamp":"2026-10-09T10:00:00Z",
            "status":"OBSERVED"}


def exclusions(targets=None):
    return {"schema_version":"d-day-external-evaluation-exclusions-v1","exclusions":[
        {"journey_id":"old","experience_event_id":"e-old","reason":"stationary_test",
         "targets":targets or ["comfort","affect"]}]}


class TimeAndEligibilityTest(unittest.TestCase):
    def audit(self, item):
        return audit_context({"queried_at":NOW,"measurements":[item]},NOW)

    def test_new_reception_contract_preserves_published_time_and_unknown_measurement_age(self):
        raw = sensor(); before = deepcopy(raw)
        current = attach_reception(raw,NOW)
        self.assertEqual(raw,before)
        self.assertEqual(current["timestamp"],raw["timestamp"])
        result = self.audit(prepare_reading(current))
        self.assertEqual(result["values"]["temperature"],18)
        self.assertIsNone(result["readings"][0]["age_seconds_at_query"])
        self.assertFalse(current["source_metadata"]["actual_measurement_time_known"])
        self.assertEqual(result["readings"][0]["sensor_time_audit"]["reception_age_seconds_at_query"],0)
        self.assertIsNone(self.audit(raw)["values"]["temperature"])

    def test_old_cached_response_remains_excluded_and_future_reception_rejected(self):
        old = attach_reception(sensor(),"2026-10-11T09:00:00Z")
        self.assertIsNone(self.audit(old)["values"]["temperature"])
        future = attach_reception(sensor(),"2026-10-11T10:00:01Z")
        with self.assertRaises(ValueError):self.audit(future)

    def test_receipt_never_establishes_rain_unit_or_missing_value(self):
        r = attach_reception(sensor("rain","mm/h"),NOW)
        self.assertIsNone(self.audit(r)["values"]["rain"])
        r = attach_reception(sensor(),NOW);r["numeric_value"] = None
        self.assertIsNone(self.audit(r)["values"]["temperature"])

    def test_other_sensor_cannot_claim_this_guarantee(self):
        r = sensor();r["source_entity_id"]="other"
        self.assertEqual(attach_reception(r,NOW),r)
        forged = attach_reception(sensor(),NOW);forged["source_entity_id"]="other"
        with self.assertRaises(ValueError):self.audit(forged)

    def test_eligibility_excludes_test_for_both_learning_targets_without_mutation(self):
        data=fixture();before=deepcopy(data)
        for target in ("comfort","affect"):
            observations, skipped=observations_from_records(data["records"],subject=data["initial_passport"]["agent_id"],target=target,exclusions=exclusions())
            self.assertEqual(observations,[])
            self.assertEqual(skipped[0]["reason"],"stationary_test")
        self.assertEqual(data,before)

    def test_no_gps_does_not_imply_test_and_changed_experience_can_still_train(self):
        data=fixture();j=data["records"][0]["journeys"][0]
        self.assertIsNone(exclusion_reason(data["records"][0],j,target="comfort"))
        j["experience"]["completion"]="changed"
        observations,_=observations_from_records(data["records"],subject=data["initial_passport"]["agent_id"],target="comfort")
        self.assertEqual(len(observations),1)
        self.assertEqual(observations[0]["context"]["scope"],"actual_mode_context_unknown")

    def test_both_ids_must_match_and_target_specific_exclusion_is_respected(self):
        data=fixture();r=data["records"][0];j=r["journeys"][0];x=exclusions(["comfort"])
        self.assertIsNone(exclusion_reason(r,j,target="affect",exclusions=x))
        x["exclusions"][0]["experience_event_id"]="different"
        self.assertIsNone(exclusion_reason(r,j,target="comfort",exclusions=x))
        with self.assertRaises(ValueError):validate_exclusions({"schema_version":"bad","exclusions":[]})

    def test_tests_and_non_travel_are_unscored_before_forecast_checks(self):
        data=fixture();r=data["records"][0];j=r["journeys"][0]
        self.assertEqual(evaluate(r,j,exclusions=exclusions())["reason"],"stationary_test")
        j["evidence_classification"]="synthetic"
        self.assertEqual(evaluate(r,j)["reason"],"explicit_synthetic")
        j.pop("evidence_classification");j["experience"]["completion"]="not_taken"
        obs, skipped=observations_from_records(data["records"],subject=data["initial_passport"]["agent_id"],target="affect")
        self.assertFalse(obs);self.assertEqual(skipped[0]["reason"],"trip_not_taken")

    def test_backup_storage_and_forecast_apply_same_explicit_exclusions(self):
        import json
        x=exclusions()
        self.assertEqual(backup_exclusions({"storage":{STORAGE_KEY:json.dumps(x)}}),x)
        data=fixture();data["evidence_exclusions"]=x
        result=forecast(data)
        for target in ("comfort","affect"):
            self.assertEqual(result["targets"][target]["predictions"][0]["models"]["basic"]["observation_count"],0)
            self.assertEqual(result["excluded_legacy_events"][target][0]["reason"],"stationary_test")

    def test_target_specific_exclusion_suppresses_only_its_scores(self):
        data=fixture();result=forecast(data)
        j=deepcopy(data["records"][0]["journeys"][0]);j["selection"].update(chosen_at="2026-10-09T10:03:00Z",routing_rank=1)
        r={"d_day_forecast":{"status":"complete","recorded_at":"2026-10-09T10:02:00Z","result":result}}
        scored=evaluate(r,j,exclusions=exclusions(["comfort"]))
        self.assertEqual(scored["status"],"scored")
        self.assertEqual(len(scored["scores"]),3)
        self.assertEqual({s["target"] for s in scored["scores"]},{"affect"})


if __name__ == "__main__":unittest.main()
