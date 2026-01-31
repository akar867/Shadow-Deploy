# ShadowDeploy MVP

ShadowDeploy runs a new code version in parallel with production traffic, compares behavior, and highlights what will break before users see it. This repository contains a Spring Boot backend and a React frontend (MVP pattern with presenters and views).

## Project layout

```
/backend  -> Spring Boot API (shadow summary data)
/frontend -> React UI (MVP: presenters + views)
```

## Backend (Spring Boot)

### Run

```bash
cd backend
mvn spring-boot:run
```

### Tests (JUnit 5)

```bash
cd backend
mvn test
```

### Endpoints

- `GET /api/health` - service status
- `POST /api/auth/login` - login to receive an auth token
- `GET /api/auth/me` - current user profile (requires token)
- `POST /api/auth/logout` - revoke current token
- `GET /api/summary` - latest shadow diff summary payload (requires token)
- `GET /api/traffic-dumps` - list uploaded traffic dumps (requires token)
- `POST /api/traffic-dumps` - upload a traffic dump (requires token)
- `POST /api/demo/run` - run a demo analysis (requires token)

### Default credentials

```
username: admin
password: shadowdeploy
```

### Storage

The backend persists data in a local H2 file database at `./data/shadowdeploy`.

### LLM insights

Set environment variables to enable LLM-backed insights (OpenAI-compatible endpoint):

```
export SHADOW_LLM_ENABLED=true
export SHADOW_LLM_API_KEY=your_api_key
export SHADOW_LLM_BASE_URL=https://api.openai.com/v1/chat/completions
export SHADOW_LLM_MODEL=gpt-4o-mini
```

If the API key is missing or the request fails, the backend falls back to heuristic insights.

## Frontend (React + Vite)

### Run

```bash
cd frontend
npm install
npm run dev
```

Vite proxies `/api` to `http://localhost:8080` so the frontend can call the backend without extra config.

### Uploading traffic dumps

Use the "Upload traffic dump" panel to send a text log or JSON lines file. The backend will store the dump, simulate shadow analysis, and update the summary metrics.

### Sample traffic dumps

Try the included samples:

```
samples/shadowdeploy-demo.jsonl
samples/shadowdeploy-plain.log
```

The JSONL sample includes structured prod/shadow payloads to exercise realistic diffing.

Example JSONL schema (one request per line):

```
{"statusProd":200,"statusShadow":500,"latencyProdMs":210,"latencyShadowMs":480,"responseProd":{"ok":true},"responseShadow":{"error":"Timeout"}}
```

### Demo mode

Use the "Run demo mode" button in the UI to instantly analyze a built-in sample without uploading a file.

## MVP architecture

- **Models**: API payloads returned by the backend.
- **Presenters**: React hooks that load data and shape UI state (`src/presenters`).
- **Views**: Stateless components that render UI (`src/views`).

## Next steps

- Add real traffic capture and shadow replay pipeline.
- Expand diff storage and retention policies.
- Expand AI explanations with LLM-backed insight generation.
