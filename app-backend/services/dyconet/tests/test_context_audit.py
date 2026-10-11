import unittest
from copy import deepcopy
from comfort_pilot.context_audit import audit_context
from comfort_pilot.evidence import _route_context, validate_request
from test_comfort_pilot import request_fixture

STAMP = "2026-10-09T10:00:00Z"


def reading(key="temperature", value=18, unit="C", **extra):
    return {"variable":key, "numeric_value":value, "unit":unit,
            "timestamp":STAMP, "status":"OBSERVED", "source":"Orion",
            "source_entity_id":"sensor-1", **extra}


def route_context(segments):
    journey = {"selection":{"chosen_at":STAMP,"mode_key":"walk"},
               "experience":{"completion":"as_planned"},
               "plan":{"route":{"summary":{"duration_seconds":600}},
                       "context_at_selection":{"snapshot":{"route_context":{"segments":segments}}}}}
    return _route_context({"completed_at":STAMP}, journey, mode="walk")


class ContextAuditTest(unittest.TestCase):
    def test_units_ranges_staleness_and_raw_preservation(self):
        c = {"queried_at":STAMP, "measurements":[reading(), reading("humidity",101,"%"),
             reading("windSpeed",18,"km/h"), reading("rain",0,"mm",timestamp="2026-10-09T09:00:00Z"),
             reading("uvIndex",2,"1")]}
        before = deepcopy(c); a = audit_context(c, STAMP)
        self.assertEqual(c,before)
        self.assertEqual(a["values"]["temperature"],18)
        self.assertEqual(a["weather_used"],1)
        self.assertEqual(sum(r["included"] for r in a["readings"]),1)
        self.assertIn("humidity: outside measurement domain", a["warnings"])
        self.assertEqual(a["readings"][2]["measurement"]["unit"],"km/h")

    def test_partial_and_conflicting_segments_are_excluded(self):
        for segments, reason in [
            ([{"raw_observations":[reading()]}, {"raw_observations":[]}], "partial_segment_coverage"),
            ([{"raw_observations":[reading()]}, {"raw_observations":[reading(value=25)]}],
             "conflicting_segment_readings_no_exposure_weights")]:
            c = route_context(segments); a = audit_context(c,STAMP)
            self.assertIsNone(a["values"]["temperature"])
            entry = a["route_coverage"]["variables"]["temperature"]
            self.assertEqual(entry["exclusion_reason"],reason)
            self.assertTrue(entry["raw_readings"])

    def test_shared_sensor_is_not_counted_as_independent_exposure(self):
        c = route_context([{"raw_observations":[reading()]}]*2)
        a = audit_context(c,STAMP)
        self.assertEqual(a["values"]["temperature"],18)
        self.assertEqual(a["route_coverage"]["variables"]["temperature"]["segment_fraction"],1)
        self.assertFalse(a["actual_trip_exposure_reconstructed"])
        self.assertEqual(a["scope"],"selected_route_search_query")

    def test_rain_window_is_not_inferred_from_mm_or_zero(self):
        c = {"queried_at":STAMP,"measurements":[reading("rain",0,"mm")]}
        a = audit_context(c,STAMP)
        self.assertEqual(a["values"]["rain"],0)
        self.assertFalse(a["semantic_metadata_complete"])
        self.assertTrue(a["readings"][0]["semantic_issues"])
        c["measurements"][0]["source_metadata"] = {
            "measurement_semantics":"accumulated_precipitation", "measurement_window_seconds":3600}
        self.assertTrue(audit_context(c,STAMP)["semantic_metadata_complete"])

    def test_future_and_duplicates_remain_errors(self):
        c = {"queried_at":STAMP,"measurements":[reading(timestamp="2026-10-09T10:01:00Z")]}
        with self.assertRaises(ValueError): audit_context(c,STAMP)
        c["measurements"] = [reading(),reading()]
        with self.assertRaises(ValueError): audit_context(c,STAMP)

    def test_both_targets_retain_audit_and_planned_duration_provenance(self):
        data = request_fixture(); before = deepcopy(data)
        data["prediction_contexts"][0]["context"] = route_context([])
        _, observations, predictions = validate_request(data)
        self.assertIn("context_audit",observations[0])
        a = predictions[0]["context_audit"]
        self.assertIn("planned_duration_not_measured_trip_duration",a["readings"][0]["semantic_issues"])
        self.assertEqual(data["initial_passport"],before["initial_passport"])
        data["schema_version"] = "d-day-affect-pilot-request-v1"
        o = data["observations"][0]
        o.update(question_version="mode-affect-ordinal-minus3-to3-v1",scale="minus3-to-3",
                 rating_raw_minus3_to_3=0,valence_normalized=0)
        _, observations, predictions = validate_request(data,target="affect")
        self.assertEqual(predictions[0]["context_audit"],a)
        self.assertEqual(observations[0]["context_audit"]["schema_version"],a["schema_version"])


if __name__ == "__main__": unittest.main()
