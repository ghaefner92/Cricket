"""Offline evidence for a saved Cricket choice. No network, model run or writes.

Scope: descriptive HOTCO relations, not measured motives, goal attainment or
subjective conflict. Browser history is internally cross-checked, not signed.
"""
from __future__ import annotations
from journey_availability import validate_journey_availability

import argparse
import copy
import hashlib
import json
import math
from pathlib import Path
from typing import Any, Mapping

from companion_interpretation import interpretation_metrics
from hotco_ct_v4_3 import HOTCOCTParameters, MODEL_VERSION
from input_mapping_v4_3 import MODES, NEEDS

SCHEMA = 'cricket-choice-evidence-v1'
MODE_MAP = {**{m: m for m in MODES}, 'foot': 'walk',
            'car_driver': 'car', 'pt_bus_tram': 'pt'}
GOAL_MAP = dict(zip(
    ['env', 'health_activity', 'crowding', 'flex', 'cost', 'time',
     'safety_accident', 'safety_crime', 'comfort_physical', 'reliable', 'health_infection'],
    ['pro_env', 'physical', 'privacy', 'autonomy', 'cost', 'speed',
     'safety_accident', 'safety_crime', 'comfort', 'reliable', 'health_infection']))


class ChoiceEvidenceError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise ChoiceEvidenceError(message)


def obj(value, name):
    require(isinstance(value, Mapping), f'{name} must be an object')
    return value


def number(value, name):
    require(isinstance(value, (int, float)) and not isinstance(value, bool)
            and math.isfinite(value), f'{name} must be finite')
    return float(value)


def digest(value):
    return 'sha256:' + hashlib.sha256(json.dumps(
        value, sort_keys=True, separators=(',', ':'), ensure_ascii=False,
        allow_nan=False).encode('utf-8')).hexdigest()


def one(items, predicate, name):
    require(isinstance(items, list), f'{name} collection must be a list')
    matches = [v for v in items if isinstance(v, Mapping) and predicate(v)]
    require(len(matches) == 1, f'{name} must identify exactly one record')
    return matches[0]


def goals(snapshot, passport):
    if snapshot is None:
        return {'status': 'UNKNOWN', 'reason': 'confirmed_snapshot_not_supplied'}
    points = obj(snapshot.get('goalPoints'), 'goalPoints')
    require(set(points) == set(GOAL_MAP), 'goalPoints keys do not match Cricket')
    require(all(type(v) is int and 0 <= v <= 10 for v in points.values())
            and sum(points.values()) == 10, 'goalPoints must allocate exactly 10 points')
    require(snapshot.get('conversion') == 'points_div10_round_to_1_7_v1'
            and snapshot.get('needsSource') == 'weekly_goal_allocation',
            'unsupported goal conversion')
    raw = obj(passport.get('observed_measurements'), 'observed_measurements').get('needs_raw_1_to_7', {})
    for key, need in GOAL_MAP.items():
        # Math.round for non-negative JS values, not Python banker's rounding.
        expected = 1 + math.floor(6 * points[key] / 10 + .5)
        require(raw.get(need) == expected, f'goal conversion mismatch: {key}')
    return {'status': 'AVAILABLE', 'week_start': snapshot.get('weekStart'),
            'points': dict(points), 'budget': 10, 'goal_to_need': GOAL_MAP.copy(),
            'conversion': snapshot['conversion'],
            'interpretation': 'priorities for this Passport; no completed-trip progress'}


def selected_relations(summary, passport, mode, scale):
    process = obj(summary.get('process_diagnostics'), 'process_diagnostics')
    row = obj(process.get('support_dynamics', {}).get('by_mode', {}).get(mode),
              'selected-mode support')
    require(row.get('available') is True, 'selected HOTCO mode is unavailable')
    needs = obj(summary.get('node_activations', {}).get('needs', {}).get('final'), 'final needs')
    beliefs = obj(passport.get('profile', {}).get('beliefs', {}).get(mode), 'selected beliefs')
    provenance = passport.get('measurement_provenance', {}).get('beliefs', {}).get(mode, {})
    terms = []
    for need in NEEDS:
        contribution = scale * number(needs.get(need), need) * number(beliefs.get(need), need)
        source = provenance.get(need, {})
        terms.append({'need': need, 'signed_input_terminal': contribution,
                      'relation': 'SUPPORT' if contribution > 0 else 'OPPOSITION' if contribution < 0 else 'ZERO',
                      'belief_source': source.get('source', 'unknown'),
                      'direct_user_response': source.get('direct_user_response')})
    total = number(row.get('cognitive_signed_input', {}).get('terminal'), 'cognitive terminal input')
    require(math.isclose(sum(t['signed_input_terminal'] for t in terms), total,
                         rel_tol=1e-7, abs_tol=1e-8), 'support reconstruction mismatch')
    return {'by_need': terms, 'support_dynamics': copy.deepcopy(row),
            'mixed_cognitive_support': copy.deepcopy(process.get('mixed_cognitive_support', {}).get('by_mode', {}).get(mode)),
            'interpretation': 'signed inputs before rectification and shunting; not causal shares'}


def build_choice_evidence(record: Mapping[str, Any], *, choice_index: int = -1,
                          confirmed_snapshot: Mapping[str, Any] | None = None) -> dict[str, Any]:
    """Accept exactly one complete IndexedDB search record and an existing choice.

    Optional confirmed_snapshot is the local PASSPORT_KEY object for that search,
    including archived versions; never substitute the currently active Passport.
    """
    obj(record, 'record')
    require(record.get('version') == 1 and record.get('status') == 'complete', 'complete history v1 required')
    search_id = record.get('search_id')
    require(isinstance(search_id, str) and bool(search_id.strip()), 'search_id required')
    request = obj(record.get('request'), 'request')
    result = obj(record.get('result'), 'result')
    require(request.get('search_id') == result.get('search_id') == search_id, 'search identity mismatch')
    require(result.get('schema_version') == 'routed-contextual-deliberation-v1', 'unsupported result schema')
    choices = record.get('choices')
    require(isinstance(choices, list) and choices, 'saved choice required')
    require(type(choice_index) is int and -len(choices) <= choice_index < len(choices), 'invalid choice_index')
    index = choice_index % len(choices)
    choice = obj(choices[index], 'choice')
    require(isinstance(choice.get('chosen_at'), str) and bool(choice['chosen_at']), 'chosen_at required')
    rank, mode_key = choice.get('routing_rank'), choice.get('mode_key')
    require(type(rank) is int and isinstance(mode_key, str), 'choice rank and mode required')
    route = one(result.get('routing', {}).get('routes'),
                lambda v: v.get('rank') == rank and v.get('mode_key') == mode_key, 'routing choice')
    require(route.get('available') is True, 'chosen routing alternative unavailable')
    audit = one(result.get('route_audit'),
                lambda v: v.get('routing_rank') == rank and v.get('mode_key') == mode_key, 'route audit')
    require(choice.get('route_id') == audit.get('route_id'), 'route identity mismatch')
    if audit.get('context_ready') is True:
        require(isinstance(choice.get('route_id'), str) and bool(choice['route_id']), 'contextual route_id required')
    mode = MODE_MAP.get(mode_key)
    require(mode is not None, 'mode unsupported in phase 1; intermodal choices must not be collapsed')
    passport = obj(request.get('cognitive_passport'), 'search Passport')
    lineage = obj(passport.get('lineage'), 'lineage')
    ref = obj(record.get('passport_reference'), 'passport_reference')
    require(isinstance(lineage.get('passport_id'), str) and bool(lineage['passport_id'])
            and type(lineage.get('revision')) is int and lineage['revision'] >= 1,
            'Passport identity required')
    require(ref.get('passport_id') == lineage['passport_id']
            and ref.get('revision') == lineage['revision'], 'Passport reference mismatch')
    if 'journey_availability' in request:
        effective_availability = validate_journey_availability(request['journey_availability'])
        require(result.get('journey_availability') == effective_availability,
                'search availability differs from routing/simulation response')
        availability_source = 'current_journey_declaration'
    else:
        effective_availability = passport.get('profile', {}).get('availability', {})
        availability_source = 'confirmed_passport'
    require(effective_availability.get(mode) is True, 'search mode unavailable')
    if confirmed_snapshot is not None:
        obj(confirmed_snapshot, 'confirmed_snapshot')
        require(confirmed_snapshot.get('response', {}).get('cognitive_passport') == passport,
                'confirmed snapshot is not the Passport used by this search')
        require(isinstance(ref.get('fingerprint'), str) and bool(ref['fingerprint'])
                and confirmed_snapshot.get('fingerprint') == ref['fingerprint'], 'Passport fingerprint mismatch')
    payload = {'schema_version': SCHEMA, 'scope': 'post_choice_simulation_interpretation',
               'identity': {'search_id': search_id, 'choice_index': index,
                            'choice_event_id': digest({'search_id': search_id, 'index': index, 'choice': choice}),
                            'route_id': choice.get('route_id'), 'routing_rank': rank, 'mode_key': mode_key,
                            'model_mode': mode, 'chosen_at': choice['chosen_at'],
                            'passport_id': lineage['passport_id'], 'passport_revision': lineage['revision'],
                            'passport_document_hash': digest(passport)},
               'weekly_goals': goals(confirmed_snapshot, passport),
               'routing_summary': copy.deepcopy(route.get('summary')),
               'geometry_evidence': copy.deepcopy(audit.get('geometry_evidence')),
               'limitations': ['simulation relations are not observed motives or subjective conflict',
                               'selection is not evidence of completing the trip',
                               'browser evidence is cross-checked, not authenticated',
                               'separate contextual runs must not be scored as one route ranking']}
    if 'journey_availability' in request:
        payload['journey_availability'] = {'by_mode': dict(effective_availability), 'source': availability_source}
    deliberation = result.get('contextual_deliberation')
    if not isinstance(deliberation, Mapping) or audit.get('context_ready') is not True:
        payload.update(status='CONTEXT_UNAVAILABLE', selected_relations=None, context=None)
    else:
        require(deliberation.get('search_id') == search_id, 'contextual search identity mismatch')
        require(deliberation.get('model_version') == MODEL_VERSION, 'unsupported HOTCO model version')
        candidate = one(deliberation.get('candidate_results'),
                        lambda v: v.get('route_id') == choice['route_id'], 'contextual candidate')
        current = obj(candidate.get('hotco'), 'contextual HOTCO')
        baseline = obj(deliberation.get('baseline'), 'baseline')
        parameters = HOTCOCTParameters()
        # The evidence reconstruction supports the audited reference scaling only.
        require(passport.get('topology', {}).get('cognitive_scaling') == 'R / 11',
                'unsupported or missing cognitive scaling')
        scale = parameters.cognitive_gain * parameters.need_to_action_gain / len(NEEDS)
        current_relations = selected_relations(current, passport, mode, scale)
        baseline_relations = selected_relations(baseline, passport, mode, scale)
        current_action = number(current.get('final_action_activations', {}).get(mode), 'selected activation')
        baseline_action = number(baseline.get('final_action_activations', {}).get(mode), 'baseline activation')
        route_context = obj(candidate.get('route_context'), 'route_context')
        require(route_context.get('route_id') == choice['route_id'], 'context route identity mismatch')
        payload.update(status='EVIDENCE_READY', selected_relations=current_relations,
                       baseline_selected_relations=baseline_relations,
                       deliberation={'baseline': interpretation_metrics(baseline),
                                     'contextual': interpretation_metrics(current),
                                     'selected_activation_delta': current_action - baseline_action,
                                     'selected_mode_is_terminal_leader': current.get('winner') == mode,
                                     'ambiguity_state': current.get('ambiguity_state', 'UNKNOWN')},
                       context={'raw_observations': copy.deepcopy(route_context.get('raw_observations', [])),
                                'normalized_stressors': copy.deepcopy(route_context.get('normalized_stressors', [])),
                                'source_status': copy.deepcopy(candidate.get('source_status', {})),
                                'perturbation': copy.deepcopy(candidate.get('context_perturbation')),
                                'warnings': copy.deepcopy(candidate.get('warnings', [])),
                                'source_metadata': copy.deepcopy(route_context.get('source_metadata', {}))})
        payload['limitations'].append('full trajectories are not present in the saved response')
    payload['evidence_id'] = digest(payload)
    return payload


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--record', required=True, type=Path)
    parser.add_argument('--passport', type=Path)
    parser.add_argument('--choice-index', type=int, default=-1)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    record = json.loads(args.record.read_text(encoding='utf-8-sig'))
    snapshot = json.loads(args.passport.read_text(encoding='utf-8-sig')) if args.passport else None
    evidence = build_choice_evidence(record, choice_index=args.choice_index, confirmed_snapshot=snapshot)
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(evidence, stream, ensure_ascii=False, allow_nan=False, indent=2)
    print(f'Evidence created: {args.output}')


if __name__ == '__main__':
    main()
