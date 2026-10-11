"""Explicit evidence exclusions; missing GPS is not evidence of non-travel."""
VERSION = "d-day-external-evaluation-exclusions-v1"
STORAGE_KEY = "d-day.evidence-exclusions.v1"
CLASSES = {"real_experience", "test", "synthetic", "stationary_test"}


def backup_exclusions(backup, exclusions=None):
    if exclusions is None:
        import json
        value = backup.get("storage", {}).get(STORAGE_KEY)
        exclusions = json.loads(value) if isinstance(value, str) else value
    validate_exclusions(exclusions)
    return exclusions


def validate_exclusions(exclusions):
    if exclusions is None:
        return []
    if not isinstance(exclusions, dict) or exclusions.get("schema_version") != VERSION:
        raise ValueError("Unknown evidence exclusion schema")
    rows = exclusions.get("exclusions")
    if not isinstance(rows, list):
        raise ValueError("Exclusions must be a list")
    for row in rows:
        if (not isinstance(row, dict) or not any(isinstance(row.get(k), str) and row[k] for k in ("journey_id", "experience_event_id"))
                or not isinstance(row.get("reason"), str) or not row["reason"]
                or not isinstance(row.get("targets"), list) or not row["targets"]
                or any(t not in ("comfort", "affect") for t in row["targets"])):
            raise ValueError("Invalid evidence exclusion")
    return rows


def exclusion_reason(record, journey, *, target, exclusions=None):
    rows = validate_exclusions(exclusions)
    experience = journey.get("experience") or {}
    if not experience:
        return "experience_not_confirmed"
    classification = journey.get("evidence_classification", record.get("evidence_classification"))
    if classification is not None:
        if classification not in CLASSES:
            raise ValueError("Unknown evidence classification")
        if classification != "real_experience":
            return "explicit_" + classification
    if journey.get("status") == "cancelled":
        return "trip_cancelled"
    if experience.get("completion") == "not_taken":
        return "trip_not_taken"
    for row in rows:
        # When both IDs are supplied both must match; no broad OR match.
        if target in row["targets"] and all(row[k] == value for k, value in (
            ("journey_id", journey.get("journey_id")), ("experience_event_id", experience.get("event_id"))) if k in row):
            return row["reason"]
    return None
