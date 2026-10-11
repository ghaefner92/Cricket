"""Read-only preliminary audit. Private snapshots/reports stay in .local-backups."""
from collections import Counter
import datetime
import json
from pathlib import Path
import sqlite3
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ADB = Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
PACKAGE = 'de.ovgu.imiq.cricket'
DEVICE = 'RZCY70K63HV'
PRIVATE = ROOT / '.local-backups/hotco-preliminary-validation'
PRIVATE.mkdir(parents=True, exist_ok=True)

def read_device(name, optional=False):
    response = subprocess.run([str(ADB), '-s', DEVICE, 'exec-out', 'run-as', PACKAGE,
        'cat', 'databases/' + name], capture_output=True)
    if response.returncode:
        if optional and b'No such file' in response.stderr:
            return b''
        raise RuntimeError(response.stderr.decode(errors='replace'))
    return response.stdout

name = 'cricket-native-v1.db'
for attempt in range(3):
    db_before = read_device(name)
    wal_before = read_device(name + '-wal', True)
    journal = read_device(name + '-journal', True)
    wal_after = read_device(name + '-wal', True)
    db_after = read_device(name)
    if db_before == db_after and wal_before == wal_after:
        break
else:
    raise RuntimeError('Device database changed during all snapshot attempts')

with tempfile.TemporaryDirectory(dir=PRIVATE) as directory:
    path = Path(directory) / name
    path.write_bytes(db_after)
    if wal_after:
        Path(str(path) + '-wal').write_bytes(wal_after)
    # A nonempty rollback journal could indicate an interrupted transaction.
    if journal:
        Path(str(path) + '-journal').write_bytes(journal)
    connection = sqlite3.connect(path)
    integrity = connection.execute('PRAGMA integrity_check').fetchone()[0]
    assert integrity == 'ok', integrity
    records = [json.loads(row[0]) for row in connection.execute('SELECT value FROM searches ORDER BY created')]
    connection.close()

def mode(value):
    return {'foot': 'walk', 'car_driver': 'car', 'pt_bus_tram': 'pt'}.get(value, value)

states, choices, completions, capture_modes, ratings = (Counter() for _ in range(5))
profiles, details, candidate_states = set(), [], Counter()
total_choices = journeys = confirmed = gps_points = 0
for record in records:
    snapshot = record.get('confirmed_snapshot') or {}
    fingerprint = snapshot.get('fingerprint') or record.get('passport_reference', {}).get('fingerprint')
    if fingerprint:
        profiles.add(fingerprint)
    result = record.get('result') or {}
    routes = [r for r in (result.get('routing') or {}).get('routes', []) if r.get('available')]
    candidates = {c.get('route_id'): c for c in (result.get('contextual_deliberation') or {}).get('candidate_results', [])}
    audits = result.get('route_audit', [])
    runs = []
    for route in routes:
        audit = next((a for a in audits if a.get('routing_rank') == route.get('rank') and a.get('mode_key') == route.get('mode_key')), {})
        run = candidates.get(audit.get('route_id'), {}).get('hotco')
        runs.append(run)
        if run:
            candidate_states[run.get('ambiguity_state', 'missing')] += 1
    winners = [r.get('winner') if r else None for r in runs]
    agreed = winners[0] if winners and winners[0] and all(w == winners[0] for w in winners) else None
    available_modes = {mode(r.get('mode_key')) for r in routes}
    matching = agreed and mode(agreed) in available_modes
    if not routes:
        state = 'incomplete'
    elif len(available_modes) == 1:
        state = 'only'
    elif any(r is None for r in runs):
        state = 'incomplete'
    elif any(r.get('ambiguity_state') in ('UNSETTLED', 'UNKNOWN') for r in runs):
        state = 'uncertain'
    elif any(r.get('ambiguity_state') == 'SENSITIVE' for r in runs):
        state = 'sensitive'
    elif any(r.get('ambiguity_state') == 'NEAR_TIE' for r in runs):
        state = 'similar'
    elif not matching:
        state = 'disagreement'
    elif all(r.get('ambiguity_state') == 'CLEAR' and isinstance(r.get('final_action_activations', {}).get(r.get('winner')), (int, float)) for r in runs):
        state = 'clear'
    else:
        state = 'uncertain'
    states[state] += 1
    selection = record.get('choices') or []
    total_choices += len(selection)
    chosen = mode(selection[-1].get('mode_key')) if selection else None
    if chosen:
        choices[chosen] += 1
    durations = [(r.get('summary', {}).get('duration_seconds'), mode(r.get('mode_key'))) for r in routes]
    durations = [(seconds, m) for seconds, m in durations if isinstance(seconds, (int, float)) and seconds > 0]
    fastest = min(durations)[1] if durations else None
    profile = record.get('request', {}).get('cognitive_passport', {}).get('profile', {})
    valences = profile.get('valences') or {}
    affect = [(v, m) for m, v in valences.items() if mode(m) in available_modes and isinstance(v, (int, float))]
    best_affect = max(affect)[1] if affect else None
    trip_count = 0
    for journey in record.get('journeys') or []:
        journeys += 1
        capture_modes[journey.get('capture_mode', 'missing')] += 1
        tracking = journey.get('tracking') or {}
        gps_points += len(tracking.get('points') or [])
        experience = journey.get('experience') or {}
        if experience:
            confirmed += 1
            trip_count += 1
            completions[experience.get('completion', 'missing')] += 1
            for key, rating in (experience.get('ratings') or {}).items():
                ratings[key] += 1
    details.append({'index': len(details) + 1, 'status': record.get('status'),
        'available_modes': sorted(available_modes), 'state': state,
        'agreed_winner': mode(agreed) if matching else None, 'final_choice': chosen,
        'choice_events': len(selection), 'fastest_duration_mode': fastest,
        'highest_valence_mode': mode(best_affect), 'confirmed_experiences': trip_count,
        'possible_test_marker': any(token in str(record.get('search_id', '')).lower() for token in ('test', 'fixture', 'synthetic', 'probe', 'device'))})

eligible = [d for d in details if d['state'] == 'clear' and d['final_choice'] and not d['possible_test_marker']]
def metric(key):
    cases = [d for d in eligible if d[key]]
    matched = sum(d[key] == d['final_choice'] for d in cases)
    return {'n': len(cases), 'matches': matched, 'agreement_fraction': matched / len(cases) if cases else None}
report = {'generated_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'source': 'read-only snapshot of connected device history; user intent not independently verified',
    'integrity_check': integrity, 'searches': len(records),
    'search_statuses': dict(Counter(r.get('status') for r in records)),
    'distinct_profile_fingerprints': len(profiles), 'choice_events': total_choices,
    'searches_with_choices': sum(bool(d['final_choice']) for d in details),
    'selected_modes_last_choice_per_search': dict(choices),
    'recommendation_states': dict(states), 'candidate_hotco_states': dict(candidate_states),
    'journeys': journeys, 'confirmed_experiences': confirmed, 'completions': dict(completions),
    'capture_modes': dict(capture_modes), 'retained_gps_points': gps_points, 'rating_counts': dict(ratings),
    'clear_multi_mode_chosen_search_comparison': {
        'hotco': metric('agreed_winner'), 'shortest_itinerary_duration': metric('fastest_duration_mode'),
        'highest_declared_valence': metric('highest_valence_mode')},
    'records_without_identity_or_coordinates': details,
    'limitations': ['One device does not establish number of independent participants.',
        'Choices follow visible recommendations: agreement is not independent prediction.',
        'Repeated searches are not independent observations.',
        'Simulation time is not human deliberation time.',
        'Shortest-duration baseline omits initial waiting before itinerary departure.',
        'No causal, population or parameter-identification claim is supported by this audit.']}
target = PRIVATE / 'report.json'
target.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=True, indent=2))
print('Private report:', target)
