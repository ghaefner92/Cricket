# Cricket

Audited snapshot of the IMIQ experimental web app, captured on 6 October 2026: a Vue/SNES interface with a confirmed avatar, weekly goals, Cognitive Passport, HOTCO-CT deliberation, Orion environmental context and route cards linked to a Leaflet map.

Address suggestions and explicit searches use Photon. Routing is provided by the external colleague-owned IMIQ routing service. This repository does not modify or include that service.

## Structure

- `experimental-frontend/`: frontend source, required assets, vendored Leaflet and npm lockfile.
- `app-backend/services/dyconet/`: Flask service, its transitive local imports and the required adaptive-passport model artifact.
- `docs/AUDIT.md`: inclusion decisions and validation of this snapshot.

The runtime source is preserved from the working Windows export. Tests, diagnostic scripts, template examples, unrelated Docker services, local credentials and generated files are omitted from this initial runtime snapshot. Validation was performed before publication using the tests retained in the audit export.

## Run on Windows / PowerShell

Requires Python 3.11+ and a current Node.js version supported by Vite 8. Validation used Python 3.12 and Node 24.19. Dependencies are recorded in `requirements.txt` and `package-lock.json`.

Backend terminal, from the repository root:

```powershell
Set-Location .\app-backend\services\dyconet
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
$env:PORT = '8077'
$env:IMIQ_ROUTING_BASE_URL = 'https://imiq-app.et.uni-magdeburg.de/api/routing'
$env:IMIQ_GRAPHHOPPER_BASE_URL = 'https://imiq-app.et.uni-magdeburg.de/api/graphhopper'
python main.py
```

Frontend terminal, from the repository root:

```powershell
Set-Location .\experimental-frontend
npm ci
npm run dev
```

Open `http://127.0.0.1:5173`. The Vite development proxy sends `/api/dyconet` requests to Flask on port 8077. Check the backend at `http://127.0.0.1:8077/health`.

The default routing client points to localhost:8000 unless `IMIQ_ROUTING_BASE_URL` is set. The public URL above is an external dependency. Orion defaults to the public IMIQ Orion endpoint. Optional LLM narration requires separately configured provider credentials and model; no credentials are committed. See `app-backend/.env.example`. The complete example file is not loaded automatically; set the variables in the process environment. Existing Windows environment settings are not copied by Git.

## Address search and map

Photon defaults to `https://photon.komoot.io`. A different CORS-enabled Photon service can be configured with `VITE_PHOTON_BASE_URL` in an ignored frontend `.env.local`. The demo service is appropriate for moderate use and has no availability guarantee. Queries are cached temporarily and paced per browser tab. Only address queries, language and a location bias near Magdeburg are sent to Photon; the Cognitive Passport is not sent to it.

The app keeps unknown observations distinct from measured zero. It does not fabricate missing house numbers or postal codes. Some displayed route geometry is supplemental GraphHopper geometry whose identity with the ranked itinerary is unverified; the UI retains that disclosure.

## Build

```powershell
Set-Location .\experimental-frontend
npm ci
npm run build
```

`dist/` is generated and ignored. A production deployment must supply the backend API reverse proxy; the Vite development proxy is not part of the static build. This commit does not deploy the application.

Leaflet's license is retained at `experimental-frontend/src/vendor/leaflet/LICENSE`. Photon/OpenStreetMap attribution remains visible in the interface.
