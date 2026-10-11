import unittest
from copy import deepcopy
from dataclasses import replace
from flask import Flask
from unittest.mock import patch
from orion_context import OrionEntity
from orion_query_list import OrionQueryListProvider
from route_context import ContextObservation, ContextStatus
from comfort_pilot.context_capture import prepare_reading, sample_context, register, validate_capture
from comfort_pilot.context_audit import audit_context
from comfort_pilot.trip_context_review import capture_summary


def sensor(stamp="2026-10-10 12:00:00", entity="Sensor:Weather:Walter", unit=None):
    return {"variable":"temperature","numeric_value":18,"raw_value":18,"unit":unit,
            "timestamp":stamp,"source":"Orion","status":"OBSERVED","source_entity_id":entity,
            "source_metadata":{"source_contract_confidence":"CONFIRMED"}}


class ContextCaptureTest(unittest.TestCase):
    def test_shared_android_backend_contract_cases(self):
        import json
        from pathlib import Path
        from journey_contract import validate_journey
        cases=json.loads((Path(__file__).resolve().parents[4]/"validation/fixtures/context_capture_cases.json").read_text(encoding="utf-8"))
        for case in cases:
            with self.subTest(name=case["name"]):
                if case["valid"]:validate_journey(case["record"],case["journey"])
                else:
                    with self.assertRaises(ValueError):validate_journey(case["record"],case["journey"])
    def test_berlin_summer_winter_and_original_preservation(self):
        for raw, expected in [("2026-07-10 12:00:00","+02:00"),("2026-01-10 12:00:00","+01:00")]:
            s=sensor(raw);before=deepcopy(s);r=prepare_reading(s)
            self.assertEqual(s,before);self.assertTrue(r["timestamp"].endswith(expected))
            self.assertEqual(r["source_metadata"]["timestamp_raw"],raw)
            self.assertEqual(r["unit"],"C")

    def test_dst_ambiguous_or_nonexistent_time_is_not_guessed(self):
        for stamp in ("2026-10-25 02:30:00","2026-03-29 02:30:00"):
            r=prepare_reading(sensor(stamp))
            self.assertEqual(r["timestamp"],stamp)
            self.assertEqual(r["source_metadata"]["timestamp_confidence"],"AMBIGUOUS_OR_NONEXISTENT_LOCAL_TIME")

    def test_shared_walter_time_is_bound_to_same_query_list(self):
        p=OrionQueryListProvider()
        walter=OrionEntity.from_mapping({"id":"Sensor:Weather:Walter","type":"Weather","datetime":{"value":"2026-10-10 12:00:00"}})
        winfred=OrionEntity.from_mapping({"id":"Sensor:Weather:Winfred","type":"Weather","temperature":{"value":18,"type":"Number"}})
        self.assertIsNone(p.observations(winfred,variables=("temperature",))[0].timestamp)
        p._weather_list=(walter,winfred)
        raw=p.observations(winfred,variables=("temperature",))[0].to_dict()
        r=prepare_reading(raw)
        self.assertEqual(r["timestamp"],"2026-10-10T12:00:00+02:00")
        self.assertEqual(r["source_metadata"]["timestamp_reference_entity"],walter.entity_id)
        self.assertEqual(winfred.attributes["temperature"],{"value":18,"type":"Number"})
        no_metadata=sensor(None,entity="Sensor:Weather:Winfred",unit="C")
        no_metadata["source_metadata"]=None
        self.assertIsNone(prepare_reading(no_metadata)["timestamp"])

    def test_rain_rate_is_not_relabelled_as_accumulation(self):
        r=sensor("2026-10-10T10:00:00Z",unit="mm/h");r.update(variable="rain",numeric_value=2)
        r=prepare_reading(r);a=audit_context({"queried_at":"2026-10-10T10:00:00Z","measurements":[r]},"2026-10-10T10:00:00Z")
        self.assertEqual(r["unit"],"mm/h");self.assertIsNone(a["values"]["rain"])
        self.assertFalse(a["readings"][0]["unit_compatible"])

    def test_read_only_sampling_requests_nearest_station_and_audits(self):
        class Provider:
            def get_weather(self,**query):
                self.query=query
                return (ContextObservation.from_dict(sensor()),)
        p=Provider();clock=iter(["2026-10-10T10:00:01Z","2026-10-10T10:00:02Z"])
        r=sample_context({"lat":52.13,"lon":11.62},provider=p,clock=lambda:next(clock))
        self.assertTrue(p.query["nearest_only"])
        self.assertEqual(r["audit"]["values"]["temperature"],18)
        self.assertFalse(r["personal_exposure_measured"]);self.assertFalse(r["stored_on_server"])
        self.assertEqual(r["query_location"],{"lat":52.13,"lon":11.62})

    def test_endpoint_rejects_invalid_locations_without_orion_query(self):
        app=Flask(__name__);register(app)
        with patch("comfort_pilot.context_capture.OrionQueryListProvider") as provider:
            for data in ({"lat":True,"lon":10},{"lat":91,"lon":10},{"lat":52,"lon":10,"subject_id":"x"}):
                self.assertEqual(app.test_client().post("/api/dyconet/d-day/context/sample",json=data).status_code,422)
            provider.assert_not_called()

    def test_capture_identity_pending_close_and_manual_guards(self):
        p={"monotonic_ms":100,"segment_index":0,"received_at":"2026-10-10T10:00:00Z","lat":52,"lon":11}
        sample={"sample_id":"s","point_monotonic_ms":100,"segment_index":0,"requested_at":p["received_at"],"received_at":None,"status":"pending","result":None,"failure_reason":None}
        j={"capture_mode":"tracked","ended_at":None,"tracking":{"points":[p],"events":[]},"context_capture":{"schema_version":"d-day-trip-context-v1","sample_limit":240,"sampling_interval_seconds":60,"samples":[sample]}}
        validate_capture(j)
        j["ended_at"]="2026-10-10T10:02:00Z"
        with self.assertRaises(ValueError):validate_capture(j)
        sample["status"]="interrupted";sample["failure_reason"]="capture_stopped_before_response"
        validate_capture(j)
        review=capture_summary(j);self.assertEqual(review["sample_status_counts"],{"interrupted":1})
        self.assertFalse(review["continuous_exposure_reconstructed"])
        sample["point_monotonic_ms"]=999
        with self.assertRaises(ValueError):validate_capture(j)


if __name__ == "__main__":unittest.main()
