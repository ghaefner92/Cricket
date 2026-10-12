"""Numerical regression and offline HTTP smoke checks after external-drive removal."""
from dataclasses import replace
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import types

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "app-backend/services/dyconet"
sys.path.insert(0, str(BACKEND))
from hotco_ct_v4_3 import HOTCOCTv43, MODEL_VERSION
from input_mapping_v4_3 import NEEDS, MODES, INPUT_SCHEMA_VERSION, parse_participant_input


def main():
    reference_revision = "308abec"
    source = subprocess.run(["git", "show", f"{reference_revision}:app-backend/services/dyconet/hotco_ct_v4_3.py"],
                            cwd=ROOT, check=True, capture_output=True, text=True).stdout
    reference = types.ModuleType("hotco_frozen_reference")
    sys.modules[reference.__name__] = reference
    exec(compile(source, "<frozen-git-reference>", "exec"), reference.__dict__)
    rng = np.random.default_rng(20261012)
    checks = []
    for index in range(12):
        availability = {m: bool(rng.integers(0, 2)) for m in MODES}
        availability[MODES[index % len(MODES)]] = True
        payload = {"schema_version": INPUT_SCHEMA_VERSION, "agent_id": f"synthetic-regression-{index}",
            "responses": {
                "needs": {n: int(rng.integers(1, 8)) for n in NEEDS},
                "beliefs": {m: {n: int(rng.integers(1, 8)) for n in NEEDS} for m in MODES},
                "valences": {m: int(rng.integers(-3, 4)) for m in MODES},
                "availability": availability}}
        participant = parse_participant_input(payload)
        old = reference.HOTCOCTv43().simulate(participant)
        current = HOTCOCTv43().simulate(participant)
        np.testing.assert_array_equal(old.trajectory, current.trajectory)
        np.testing.assert_array_equal(old.topology, current.topology)
        assert old.projection_corrections == current.projection_corrections
        checks.append({"case": index, "trajectory_exact_match": True,
                       "trajectory_sha256": hashlib.sha256(current.trajectory.tobytes()).hexdigest()})
    from main import app
    client = app.test_client()
    health = client.get("/health")
    assert health.status_code == 200
    assert health.json["context_model"]["external_context_drive_supported"] is False
    request = {"search_id": "synthetic-http-regression", "timestamp": "2026-10-12T10:00:00Z",
        "participant": payload, "record_trajectories": True,
        "candidate_routes": [{"route_id": "fixture-route",
            "origin": {"latitude": 52.13, "longitude": 11.62},
            "destination": {"latitude": 52.14, "longitude": 11.64},
            "summary": {"duration_seconds": 600, "distance_meters": 1800},
            "legs": [{"mode": "walk", "duration_seconds": 600, "distance_meters": 1800}]}]}
    response = client.post("/api/dyconet/contextual-deliberation", json=request)
    assert response.status_code == 200, response.json
    candidate = response.json["candidate_results"][0]
    assert "context_perturbation" not in candidate
    assert candidate["hotco"]["context_metadata"]["applied"] is False
    explanation = client.post("/api/dyconet/contextual-explanation",
        json={"route_id": "fixture-route", "deliberation": response.json})
    assert explanation.status_code == 200, explanation.json
    assert explanation.json["contextual_drivers"]["all"] == []
    retired = client.post("/api/dyconet/contextual-deliberation",
        json={**request, "context_policy": "legacy_context_drive"})
    assert retired.status_code == 422
    proof = {"model_version": MODEL_VERSION, "reference_revision": reference_revision,
        "numerical_cases": checks, "health_http": health.status_code,
        "deliberation_http": response.status_code, "explanation_http": explanation.status_code,
        "retired_policy_http": retired.status_code,
        "frontend_absent_during_backend_smoke": not (ROOT / "experimental-frontend").exists(),
        "external_context_drive_supported": False, "synthetic_only": True}
    destination = ROOT / "output/no-context-drive/backend-verification.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({**proof, "numerical_cases": len(checks)}, indent=2))


if __name__ == "__main__":
    main()
