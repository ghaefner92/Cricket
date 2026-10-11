"""Verify the audited minimal backend package without network or local .env files."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / 'docs/vercel-audit'
summary = json.loads((REPORTS / 'inventory-summary.json').read_text(encoding='utf-8'))
stage = ROOT / '.local-backups/vercel-runtime-smoke' / uuid.uuid4().hex
stage.mkdir(parents=True)
assert stage.resolve().is_relative_to((ROOT / '.local-backups/vercel-runtime-smoke').resolve())
try:
    selected = summary['required_backend_modules'] + summary['required_backend_data'] + ['app-backend/services/dyconet/requirements.txt']
    for rel in selected:
        target = stage / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / rel, target)
    code = '''import json,urllib.request
def blocked(*args,**kwargs):raise RuntimeError('Network disabled for minimal-package smoke test')
urllib.request.urlopen=blocked
import main
client=main.app.test_client()
checks={
 'health':client.get('/health').status_code,
 'journey_contract':client.get('/api/dyconet/journeys/contract').status_code,
 'invalid_json_rejected':client.post('/api/dyconet',data='{',content_type='application/json').status_code,
 'missing_journey_rejected':client.post('/api/dyconet/journeys/validate',json={}).status_code,
 'choice_status':client.get('/api/dyconet/choice-explanation/status').status_code,
}
assert checks=={'health':200,'journey_contract':200,'invalid_json_rejected':400,'missing_journey_rejected':422,'choice_status':200},checks
print(json.dumps({'checks':checks,'registered_routes':sorted(str(r) for r in main.app.url_map.iter_rules()),'network_used':False,'private_env_copied':False}))
'''
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    proc = subprocess.run([sys.executable, '-c', code], cwd=stage / 'app-backend/services/dyconet',
        env=env, capture_output=True, text=True, timeout=45)
    if proc.returncode:
        raise RuntimeError(proc.stderr)
    result = json.loads(proc.stdout)
    result['copied_files'] = len(selected)
    result['limitations'] = 'Uses locally installed Python dependencies; does not verify Vercel build, external providers or production load.'
    (REPORTS / 'backend-runtime-smoke.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))
finally:
    assert stage.resolve().is_relative_to((ROOT / '.local-backups/vercel-runtime-smoke').resolve())
    shutil.rmtree(stage)
