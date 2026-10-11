import gzip
import json
import sys
import unittest
from pathlib import Path

from flask import Flask, jsonify

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from response_transport import register_json_compression


class JsonTransportTest(unittest.TestCase):
    def setUp(self):
        self.app = Flask(__name__)
        register_json_compression(self.app)
        self.evidence = {"route_audit": [{"value": "weather · Straßenbahn"}] * 1200}

        @self.app.get("/routes")
        def routes():
            return jsonify(self.evidence)

        @self.app.get("/small")
        def small():
            return jsonify({"ok": True})

        @self.app.get("/error")
        def error():
            return jsonify({"message": "invalid", "audit": self.evidence}), 422

        @self.app.get("/encoded")
        def encoded():
            return b"already encoded", 200, {"Content-Type": "application/json", "Content-Encoding": "br"}

        self.client = self.app.test_client()

    def test_exact_bytes_and_all_evidence_preserved(self):
        plain = self.client.get("/routes")
        compressed = self.client.get("/routes", headers={"Accept-Encoding": "gzip"})
        self.assertEqual("gzip", compressed.headers.get("Content-Encoding"))
        self.assertEqual(plain.data, gzip.decompress(compressed.data))
        self.assertEqual(self.evidence, json.loads(gzip.decompress(compressed.data)))
        self.assertLess(len(compressed.data), len(plain.data) * .1)
        self.assertEqual(len(compressed.data), int(compressed.headers["Content-Length"]))
        self.assertIn("Accept-Encoding", plain.headers["Vary"])

    def test_negotiation_quality_and_head(self):
        for encoding in ("identity", "gzip;q=0", "*;q=0", "*;q=1,gzip;q=0"):
            self.assertIsNone(self.client.get("/routes", headers={"Accept-Encoding": encoding}).headers.get("Content-Encoding"))
        self.assertEqual("gzip", self.client.get("/routes", headers={"Accept-Encoding": "*;q=1"}).headers.get("Content-Encoding"))
        response = self.client.head("/routes", headers={"Accept-Encoding": "gzip"})
        self.assertEqual(b"", response.data)
        self.assertIsNone(response.headers.get("Content-Encoding"))

    def test_small_and_preencoded_responses(self):
        self.assertIsNone(self.client.get("/small", headers={"Accept-Encoding": "gzip"}).headers.get("Content-Encoding"))
        encoded = self.client.get("/encoded", headers={"Accept-Encoding": "gzip"})
        self.assertEqual("br", encoded.headers["Content-Encoding"])
        self.assertEqual(b"already encoded", encoded.data)

    def test_error_status_and_message_preserved(self):
        response = self.client.get("/error", headers={"Accept-Encoding": "gzip"})
        self.assertEqual(422, response.status_code)
        self.assertEqual("invalid", json.loads(gzip.decompress(response.data))["message"])


if __name__ == "__main__":
    unittest.main()
