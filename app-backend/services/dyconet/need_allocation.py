"""Explicit weekly allocations, distinct from legacy 1..7 questionnaire ratings."""
from typing import Any, Mapping

from input_mapping_v4_3 import NEEDS

SCHEMA_VERSION = "weekly_need_allocation_1.0"


def validate_need_allocation(value: Any) -> tuple[dict, dict[str, float]]:
    if not isinstance(value, Mapping) or set(value) != {"schema_version", "budget", "points"}:
        raise ValueError("needs_allocation must contain schema_version, budget and points")
    if value["schema_version"] != SCHEMA_VERSION:
        raise ValueError("Unsupported needs_allocation schema_version")
    if type(value["budget"]) is not int or value["budget"] != 10:
        raise ValueError("needs_allocation budget must be 10")
    points = value["points"]
    if not isinstance(points, Mapping) or set(points) != set(NEEDS):
        raise ValueError("needs_allocation points must contain all 11 canonical needs exactly once")
    if any(type(p) is not int or not 0 <= p <= 10 for p in points.values()):
        raise ValueError("needs_allocation points must be integers from 0 to 10")
    if sum(points.values()) != 10:
        raise ValueError("needs_allocation points must sum to 10")
    allocation = {"schema_version": SCHEMA_VERSION, "budget": 10, "points": {n: points[n] for n in NEEDS}}
    return allocation, {n: points[n] / 10.0 for n in NEEDS}
