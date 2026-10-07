"""Per-search explicit access. The confirmed Passport is never rewritten."""
from dataclasses import replace
from input_mapping_v4_3 import MODES
from availability_resolver import resolve_user_declared_availability


def validate_journey_availability(value):
    if not isinstance(value, dict) or set(value) != set(MODES):
        raise ValueError('journey_availability requires exactly walk, bike, pt and car')
    if any(type(value[mode]) is not bool for mode in MODES) or not any(value.values()):
        raise ValueError('journey_availability requires booleans and at least one available mode')
    return {mode: value[mode] for mode in MODES}


def apply_journey_availability(participant, value):
    available = validate_journey_availability(value)
    resolved = resolve_user_declared_availability(available, MODES)
    resolved.provenance['scope'] = 'current_route_search'
    for entry in resolved.provenance['by_mode'].values():
        entry['reason'] = 'explicit current journey access declaration'
    return replace(participant, raw_availability=resolved.by_mode,
                   availability=resolved.vector, availability_provenance=resolved.provenance)
