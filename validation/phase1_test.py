import json
from copy import deepcopy
from pathlib import Path
import sys
import unittest
from flask import Flask

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"app-backend/services/dyconet"))
from journey_contract import validate_journey, evidence_id
from journey_api import register_journeys

class Phase1Contracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cases=json.loads((ROOT/"validation/fixtures/journey_cases.json").read_text())
        app=Flask(__name__);register_journeys(app);cls.client=app.test_client()

    def test_shared_contract_cases(self):
        for case in self.cases:
            with self.subTest(case["name"]):
                if case["valid"]:validate_journey(case["record"],case["journey"])
                else:
                    with self.assertRaises((ValueError,KeyError,TypeError,IndexError)):validate_journey(case["record"],case["journey"])

    def test_api_cases(self):
        for case in self.cases:
            with self.subTest(case["name"]):
                response=self.client.post("/api/dyconet/journeys/validate",json={"record":case["record"],"journey":case["journey"]})
                self.assertEqual(200 if case["valid"] else 422,response.status_code)
                if case["valid"]:
                    self.assertFalse(response.json["learning_applied"]);self.assertFalse(response.json["stored_on_server"])

    def test_idempotent_receipt_and_no_profile_mutation(self):
        case=self.cases[1];before=deepcopy(case)
        first=self.client.post("/api/dyconet/journeys/validate",json={"record":case["record"],"journey":case["journey"]}).json
        second=self.client.post("/api/dyconet/journeys/validate",json={"record":case["record"],"journey":case["journey"]}).json
        self.assertEqual(first["evidence_id"],second["evidence_id"]);self.assertEqual(before,case)

    def test_bad_payload_and_schema_endpoint(self):
        self.assertEqual(422,self.client.post("/api/dyconet/journeys/validate",json={}).status_code)
        self.assertEqual("cricket-journey-v1",self.client.get("/api/dyconet/journeys/contract").json["properties"]["schema_version"]["const"])
        self.assertEqual(413,self.client.post("/api/dyconet/journeys/validate",data=" "*(8*1024*1024+1)).status_code)

    def test_routing_aliases_preserve_identity_and_confirm_canonical_modes(self):
        for raw in ("foot", "walk", "bike", "pt", "pt_bus_tram", "car", "car_driver"):
            canonical = {"foot": "walk", "pt_bus_tram": "pt", "car_driver": "car"}.get(raw, raw)
            for original in (self.cases[1], self.cases[2], self.cases[5]):
                with self.subTest(raw=raw, status=original["journey"]["status"]):
                    case = deepcopy(original)
                    record, journey = case["record"], case["journey"]
                    record["choices"][0]["mode_key"] = raw
                    record["result"]["routing"]["routes"][0]["mode_key"] = raw
                    journey["selection"]["mode_key"] = raw
                    journey["plan"]["route"]["mode_key"] = raw
                    journey["plan"]["alternatives"][0]["mode_key"] = raw
                    if journey["experience"] is not None:
                        journey["experience"]["actual_modes"] = [canonical]
                    validate_journey(record, journey)
                    self.assertEqual(200, self.client.post("/api/dyconet/journeys/validate", json={"record": record, "journey": journey}).status_code)
                    self.assertEqual(raw, journey["selection"]["mode_key"])
                    if journey["experience"] is not None:
                        journey["experience"]["actual_modes"] = ["walk" if canonical == "car" else "car"]
                        with self.assertRaises(ValueError):
                            validate_journey(record, journey)

if __name__=="__main__":unittest.main(verbosity=2)
