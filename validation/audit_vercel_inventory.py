"""Classify deployment inputs without deleting files or reading private backups."""
import ast
from collections import Counter, defaultdict
import csv
import datetime
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / 'docs/vercel-audit'
REPORTS.mkdir(parents=True, exist_ok=True)
BACKEND = ROOT / 'app-backend/services/dyconet'
graph = json.loads((REPORTS / 'frontend-build-graph.json').read_text(encoding='utf-8'))
frontend = set(graph['resolved_frontend_inputs'])
tracked = set(subprocess.check_output(['git', 'ls-files'], cwd=ROOT, text=True).splitlines())
modules = {path.stem: path for path in BACKEND.glob('*.py')}
closure = set()
external_imports = set()
pending = ['main']
while pending:
    name = pending.pop()
    if name in closure:
        continue
    closure.add(name)
    tree = ast.parse(modules[name].read_text(encoding='utf-8-sig'))
    for node in ast.walk(tree):
        imports = [a.name for a in node.names] if isinstance(node, ast.Import) else [node.module] if isinstance(node, ast.ImportFrom) and node.module else []
        for imported in imports:
            head = imported.split('.')[0]
            if head in modules:
                pending.append(head)
            else:
                external_imports.add(head)
backend_required = {modules[name].relative_to(ROOT).as_posix() for name in closure}
data_required = {
    'app-backend/services/dyconet/artifacts/adaptive_passport_v1.json',
    'app-backend/services/dyconet/choice_wording_v1.json',
    'contracts/journey-v1.schema.json',
}
rows = []
opaque = {'.git': 'git_metadata', '.local-backups': 'private_backups', 'output': 'local_artifacts', '.idea': 'editor_state'}
generated = {'node_modules', 'dist', 'build', '.gradle', '.venv', 'venv', '__pycache__', '.pytest_cache'}

def classify(rel, name):
    parts = Path(rel).parts
    if name.startswith('.env') and name != '.env.example':
        return 'private_configuration', 'Exclude; configure deployment secrets separately. Contents not read.'
    if name.endswith('.log'):
        return 'logs', 'Exclude from deployment; diagnostic output.'
    if rel in data_required:
        return 'backend_required_data', 'Required runtime data; retain path layout.'
    if rel in backend_required:
        return 'backend_required_module', 'Reachable from main.py through Python imports, including optional branches.'
    if rel.startswith('experimental-frontend/android/') or rel.startswith('android-native/'):
        return 'android_only', 'Exclude from Vercel web deployment; retain for Android app.'
    if rel.startswith('validation/') or 'tests' in parts or 'fixtures' in parts or 'androidTest' in parts or 'test' in parts:
        return 'validation_only', 'Retain for checks; exclude from runtime bundle.'
    if rel.startswith('experimental-frontend/public/'):
        return 'frontend_public', 'Vite copies public files; required or independently reviewed.'
    if rel in frontend:
        return 'frontend_build_input', 'Resolved by the audited Vite build; retain for reproducible build.'
    if rel.startswith('experimental-frontend/src/vendor/') and name == 'LICENSE':
        return 'license', 'Retain Leaflet attribution/license even without import.'
    if rel.startswith('experimental-frontend/src/vendor/leaflet/images/'):
        return 'frontend_library_resource', 'Retain: CSS url() and Leaflet runtime icon URLs may bypass the JS module graph.'
    if rel.startswith('experimental-frontend/src/'):
        return 'frontend_not_in_build', 'Not resolved in current build; candidate only, inspect runtime URLs and future flows before removing.'
    if rel in {'experimental-frontend/package.json', 'experimental-frontend/package-lock.json', 'experimental-frontend/vite.config.js', 'experimental-frontend/index.html'}:
        return 'frontend_build_config', 'Required build configuration.'
    if rel.startswith('experimental-frontend/scripts/') or rel == 'experimental-frontend/capacitor.config.json':
        return 'android_build_tool', 'Not required by npm run build; retain Android tooling.'
    if rel == 'app-backend/services/dyconet/requirements.txt':
        return 'backend_dependencies', 'Retain for separate backend deployment.'
    if rel.startswith('app-backend/') and name.endswith('.py'):
        return 'backend_not_in_import_closure', 'Not reached from main.py; retain tests/diagnostics unless explicitly retired.'
    if rel.startswith('docs/') or rel in {'README.md', 'PLAN_IMPLEMENTACION_HOTCO.txt'}:
        return 'documentation', 'Retain in repository; exclude from deployment runtime.'
    if name == '.env.example':
        return 'configuration_example', 'Keep as documentation; no production secret values.'
    if rel == '.gitignore':
        return 'repository_config', 'Retain repository exclusion rules; not an application asset.'
    return 'review', 'Manual review required; no deletion inferred.'

def directory_size(path):
    size = count = 0
    for base, dirs, files in os.walk(path):
        dirs[:] = [d for d in dirs if not (Path(base) / d).is_symlink()]
        for name in files:
            try:
                size += (Path(base) / name).stat().st_size
                count += 1
            except OSError:
                pass
    return size, count

for base, dirs, files in os.walk(ROOT):
    for directory in list(dirs):
        path = Path(base) / directory
        rel = path.relative_to(ROOT).as_posix()
        if path.is_symlink() or (Path(base) == ROOT and directory in opaque):
            dirs.remove(directory)
            rows.append({'path': rel + '/', 'bytes': '', 'contained_files': '', 'tracked': '',
                'category': opaque.get(directory, 'symlink'), 'reason': 'Exclude from deployment; contents not inspected.'})
        elif directory in generated:
            dirs.remove(directory)
            size, count = directory_size(path)
            rows.append({'path': rel + '/', 'bytes': size, 'contained_files': count, 'tracked': '',
                'category': 'generated_local', 'reason': 'Exclude from source upload; regenerate during build/install.'})
    for name in files:
        path = Path(base) / name
        rel = path.relative_to(ROOT).as_posix()
        category, reason = classify(rel, name)
        rows.append({'path': rel, 'bytes': path.stat().st_size, 'contained_files': 1,
            'tracked': rel in tracked, 'category': category, 'reason': reason})
rows.sort(key=lambda row: row['path'])
with (REPORTS / 'inventory.csv').open('w', newline='', encoding='utf-8') as stream:
    writer = csv.DictWriter(stream, fieldnames=['path', 'bytes', 'contained_files', 'tracked', 'category', 'reason'])
    writer.writeheader()
    writer.writerows(rows)
sizes = defaultdict(int)
for row in rows:
    if isinstance(row['bytes'], int):
        sizes[row['category']] += row['bytes']
summary = {'generated_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'method': 'Vite resolved build graph + Python AST transitive import closure + filesystem metadata',
    'inventory_entries': len(rows), 'category_entries': dict(Counter(row['category'] for row in rows)),
    'category_bytes': dict(sizes), 'required_backend_modules': sorted(backend_required),
    'required_backend_data': sorted(data_required), 'external_python_imports': sorted(external_imports),
    'frontend_not_in_build': [row['path'] for row in rows if row['category'] == 'frontend_not_in_build'],
    'backend_not_in_import_closure': [row['path'] for row in rows if row['category'] == 'backend_not_in_import_closure'],
    'untracked_required_runtime_inputs': [row['path'] for row in rows if row['tracked'] is False and row['category'] in {'backend_required_module', 'backend_required_data', 'frontend_build_input', 'frontend_public', 'frontend_build_config'}],
    'largest_generated_directories': sorted([row for row in rows if row['category'] == 'generated_local'], key=lambda row: row['bytes'], reverse=True)[:8],
    'private_contents_read': False, 'files_deleted': 0, 'published': False}
(REPORTS / 'inventory-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(summary, ensure_ascii=True, indent=2))
