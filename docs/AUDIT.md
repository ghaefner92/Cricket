# Runtime snapshot audit

Source: `IMIQ_Cricket_Audit_20261006-140920.zip`, exported from the working Windows app on 6 October 2026.

## Included

- 108 original runtime/build/resource files; their bytes are unchanged from the export.
- Backend: 30 Python modules in the transitive import closure of `main.py`, its requirements and the runtime model artifact.
- Frontend: modules reachable from `src/main.js`, including lazy imports, stylesheet resource URLs, application branding, favicon, vendored Leaflet images and license, and build configuration/lockfile.
- Added repository metadata only: this audit, a startup README, `.gitignore`, and an environment-variable example without credentials.

## Excluded

Tests are validation material rather than runtime dependencies and are retained in the supplied audit archive, outside this initial runtime-only Git snapshot. No tests were removed from the original Windows project.

| Category | Decision |
|---|---|
| Tests and synthetic fixtures | Execute before publication; omit from runtime snapshot |
| `HelloWorld.vue`, Vite/Vue template graphics and `public/icons.svg` | Not reachable from the app entry point |
| `digital_companion_dev.py`, `digital_companion_connectivity.py` | Standalone diagnostic scripts outside the backend import closure |
| Proxy/web/GraphHopper Docker services and compose configuration | Outside the working Flask + Vite setup; do not bundle the colleague routing service |
| `node_modules`, dist, virtualenv, bytecode, editor state, backups | Local/generated files |
| `.env` and local credentials | Excluded from the export and repository |
| Export inventories, stale service/template README and example request | Audit-only or superseded startup documentation |

The backend retains modules for all endpoints imported by `main.py`, including optional narration, even when a particular current screen does not call them. Removing those modules would require code changes, outside this snapshot task.

## Validation

- Frontend: 32 source tests passed.
- Selected frontend: fresh `npm ci` and `npm run build` passed.
- Selected backend: import of `main`, Flask `/health` (HTTP 200), and loading the required adaptive-passport artifact passed.
- Backend source suite: 256 tests passed in 79.630 seconds.
- Secret scan: no private key or recognizable live token was found in selected source; recognized API credentials are read from environment variables.
- The required model artifact contains learned parameters and aggregate training/validation metadata; it is loaded at runtime. It is retained instead of being mistaken for a disposable generated file.

Backend validation used the declared packages in a virtual environment with preinstalled NumPy available. External routing, Orion and optional LLM availability depend on their services and local configuration. The test results do not establish scientific validity of the model or a production deployment.

## Original files omitted from the export selection

- `AUDIT_INVENTORY.csv`
- `AUDIT_INVENTORY.json`
- `AUDIT_SCOPE.txt`
- `app-backend/.gitignore`
- `app-backend/README.md`
- `app-backend/compose.yml`
- `app-backend/services/dyconet/.gitignore`
- `app-backend/services/dyconet/Dockerfile`
- `app-backend/services/dyconet/README.md`
- `app-backend/services/dyconet/digital_companion_connectivity.py`
- `app-backend/services/dyconet/digital_companion_dev.py`
- `app-backend/services/dyconet/example_request.json`
- `app-backend/services/dyconet/tests/fixtures/adaptive_passport_v1_parity_fixture.json`
- `app-backend/services/dyconet/tests/fixtures/f0_f3_runtime_regression.json`
- `app-backend/services/dyconet/tests/test_adaptive_contextual_deliberation.py`
- `app-backend/services/dyconet/tests/test_adaptive_hotco_adapter.py`
- `app-backend/services/dyconet/tests/test_adaptive_hotco_bootstrap_http.py`
- `app-backend/services/dyconet/tests/test_adaptive_passport_http.py`
- `app-backend/services/dyconet/tests/test_adaptive_passport_onboarding.py`
- `app-backend/services/dyconet/tests/test_adaptive_passport_v1.py`
- `app-backend/services/dyconet/tests/test_adaptive_passport_xai.py`
- `app-backend/services/dyconet/tests/test_adaptive_profile_update.py`
- `app-backend/services/dyconet/tests/test_adaptive_profile_update_http.py`
- `app-backend/services/dyconet/tests/test_adaptive_question_trigger.py`
- `app-backend/services/dyconet/tests/test_adaptive_questioning.py`
- `app-backend/services/dyconet/tests/test_companion_interpretation.py`
- `app-backend/services/dyconet/tests/test_context_integration.py`
- `app-backend/services/dyconet/tests/test_context_models.py`
- `app-backend/services/dyconet/tests/test_context_perturbation.py`
- `app-backend/services/dyconet/tests/test_contextual_deliberation.py`
- `app-backend/services/dyconet/tests/test_contextual_xai.py`
- `app-backend/services/dyconet/tests/test_contextual_xai_narrator.py`
- `app-backend/services/dyconet/tests/test_environmental_tolerances.py`
- `app-backend/services/dyconet/tests/test_graphhopper_geometry.py`
- `app-backend/services/dyconet/tests/test_hotco_ct_v4_3.py`
- `app-backend/services/dyconet/tests/test_json_request_validation.py`
- `app-backend/services/dyconet/tests/test_orion_context.py`
- `app-backend/services/dyconet/tests/test_orion_query_list.py`
- `app-backend/services/dyconet/tests/test_phase1_availability.py`
- `app-backend/services/dyconet/tests/test_profile_update.py`
- `app-backend/services/dyconet/tests/test_route_context.py`
- `app-backend/services/dyconet/tests/test_route_context_adapter.py`
- `app-backend/services/dyconet/tests/test_routing_bridge.py`
- `app-backend/services/dyconet/tests/test_split_thermal_tolerances.py`
- `app-backend/services/dyconet/tests/test_winfred_contract.py`
- `app-backend/services/graphhopper/Dockerfile`
- `app-backend/services/graphhopper/cmd.sh`
- `app-backend/services/graphhopper/config.yml`
- `app-backend/services/proxy/Dockerfile`
- `app-backend/services/proxy/traefik/dynamic.yml`
- `app-backend/services/proxy/traefik/traefik.yml`
- `app-backend/services/web/Dockerfile`
- `app-backend/services/web/main.py`
- `app-backend/services/web/templates/index.html`
- `experimental-frontend/.gitignore`
- `experimental-frontend/.vscode/extensions.json`
- `experimental-frontend/README.md`
- `experimental-frontend/public/icons.svg`
- `experimental-frontend/src/assets/hero.png`
- `experimental-frontend/src/assets/vite.svg`
- `experimental-frontend/src/assets/vue.svg`
- `experimental-frontend/src/components/HelloWorld.vue`
- `experimental-frontend/tests/addressesMap.test.mjs`
- `experimental-frontend/tests/autocomplete.test.mjs`
- `experimental-frontend/tests/photon.test.mjs`
- `experimental-frontend/tests/routeExplanation.test.mjs`
- `experimental-frontend/tests/routePlanning.test.mjs`
- `experimental-frontend/tests/routePresentation.test.mjs`
