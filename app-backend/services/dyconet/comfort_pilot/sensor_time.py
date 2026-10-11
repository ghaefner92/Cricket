"""Versioned provider actuality, distinct from an unmaintained published clock."""
from copy import deepcopy
from datetime import timedelta
from journey_contract import timestamp

CONTRACT = "orion-current-values-reception-v1"
ENTITIES = {"Sensor:Weather:Walter", "Sensor:Weather:Winfred"}


def attach_reception(reading, received_at):
    r = deepcopy(reading)
    if r.get("source_entity_id") not in ENTITIES or str(r.get("source", "")).lower() != "orion":
        return r
    timestamp(received_at)
    metadata = dict(r.get("source_metadata") or {})
    metadata.update(sensor_time_contract=CONTRACT, provider_response_received_at=received_at,
                    published_timestamp_role="unmaintained_provider_record",
                    actuality_provenance="PROVIDER_CURRENT_VALUES_CONFIRMED_BY_AUTHOR_2026_10_11",
                    actual_measurement_time_known=False)
    r["source_metadata"] = metadata
    return r


def reception_check(reading, queried):
    """None means legacy observation-time policy must remain unchanged."""
    m = reading.get("source_metadata") or {}
    if m.get("sensor_time_contract") != CONTRACT:
        return None
    if (reading.get("source_entity_id") not in ENTITIES
            or str(reading.get("source", "")).lower() != "orion"
            or m.get("actuality_provenance") != "PROVIDER_CURRENT_VALUES_CONFIRMED_BY_AUTHOR_2026_10_11"
            or m.get("published_timestamp_role") != "unmaintained_provider_record"
            or m.get("actual_measurement_time_known") is not False):
        raise ValueError("Invalid Orion reception-time contract")
    received = timestamp(m["provider_response_received_at"])
    if received > queried:
        raise ValueError("Sensor reception is later than its context query")
    age = (queried - received).total_seconds()
    return {"freshness_basis":"provider_guarantee_and_response_reception",
            "reception_age_seconds_at_query":age,
            "measurement_age_seconds_at_query":None,
            "reason":reading.get("variable", "unknown") + ": stale provider response" if queried-received > timedelta(minutes=30) else None}
