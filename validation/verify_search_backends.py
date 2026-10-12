"""Probe running search services without confirming a choice or recording a trip."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
import json
from urllib.request import Request, urlopen

root = Path(__file__).resolve().parents[1]
out = root / '.local-backups/search-service-recovery'
out.mkdir(parents=True, exist_ok=True)

def fetch(path, payload=None, base='http://127.0.0.1:8077'):
    request = Request(base + path, data=json.dumps(payload).encode() if payload else None,
                      headers={'Content-Type': 'application/json'})
    with urlopen(request, timeout=150) as response:
        return json.load(response)

def routes():
    saved = json.loads((root / '.local-backups/auto-goals-audit/latest-search.json').read_text(encoding='utf-8'))
    payload = saved['request']
    payload['datetime'] = datetime.now(timezone.utc).isoformat()
    payload['record_trajectories'] = False
    payload['context_policy'] = 'observations_only'
    data = fetch('/api/dyconet/routed-contextual-deliberation', payload)
    routing = data.get('routing', {})
    options = routing.get('routes', [])
    return {'route_count': len(options), 'modes': sorted({r['mode_key'] for r in options}),
            'otp_status': routing.get('provider_audit', {}).get('otp', {}).get('status')}

def probe(name, function):
    try:
        return name, {'ok': True, 'result': function()}
    except Exception as exc:
        return name, {'ok': False, 'error': str(exc)}

checks = [
    ('backend', lambda: fetch('/health')['status']),
    ('addresses', lambda: len(fetch('/api/dyconet/geocoding?q=Universitat%20Magdeburg&lang=en')['features'])),
    ('realtime', lambda: fetch('/status', base='http://127.0.0.1:8092')['ready']),
    ('routes', routes),
]
with ThreadPoolExecutor(max_workers=4) as pool:
    result = dict(pool.map(lambda check: probe(*check), checks))
(out / 'verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result))
if not all(check['ok'] for check in result.values()):
    raise SystemExit(1)
