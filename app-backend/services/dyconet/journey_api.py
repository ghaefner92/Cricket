"""Read-only backend contract checking. The device remains the evidence store."""
from datetime import datetime, timezone
from flask import jsonify, request
from journey_contract import SCHEMA, validate_journey, evidence_id


def register_journeys(app):
    @app.get("/api/dyconet/journeys/contract")
    def journey_contract():
        return jsonify(SCHEMA)

    @app.post("/api/dyconet/journeys/validate")
    def validate():
        if request.content_length is not None and request.content_length > 8 * 1024 * 1024:
            return jsonify(error="journey_too_large", message="Journey exceeds 8 MiB"), 413
        payload = request.get_json(silent=True)
        if not isinstance(payload, dict) or set(payload) != {"record", "journey"}:
            return jsonify(error="invalid_journey_request", message="Expected record and journey"), 422
        try:
            journey = validate_journey(payload["record"], payload["journey"])
            digest = evidence_id(journey)
        except (ValueError, KeyError, TypeError, IndexError, OverflowError):
            return jsonify(error="invalid_journey_evidence", message="Journey contract or evidence identity is invalid"), 422
        return jsonify(schema_version="cricket-journey-validation-v1", journey_id=journey["journey_id"],
                       evidence_id=digest, status="valid", stored_on_server=False, learning_applied=False,
                       validated_at=datetime.now(timezone.utc).isoformat())
