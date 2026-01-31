# ShadowDeploy MVP

## What it is (in plain language)
ShadowDeploy helps teams see what might break before a new release goes live.
It runs the new version next to real production traffic (safely), compares the
results, and highlights differences that could become incidents.

Think of it as a pre-deploy safety check using real traffic, not just tests.

## Why it matters
- Avoid production surprises
- Catch edge cases before customers do
- See downstream risks early
- Make confident go or no-go decisions

## What you get in the dashboard
- A clear risk score
- Diff metrics (status drift, payload drift, latency delta)
- Findings with recommended fixes
- AI insights that explain likely failures

## Quick demo (non-technical)
1) Start the app (see "Developer setup" below).
2) Open the UI at http://localhost:5173.
3) Log in with:
   - username: admin
   - password: shadowdeploy
4) Click "Run demo mode".
5) Read the risk score and AI insights.

This is a one-click investor or stakeholder demo.

## Using your own traffic logs
Use the "Upload traffic dump" panel to send a text log or JSON lines file.
ShadowDeploy will analyze the traffic and update the dashboard.

### Sample files included
```
samples/shadowdeploy-demo.jsonl
samples/shadowdeploy-plain.log
```

The JSONL sample includes structured prod vs. shadow payloads to enable
realistic diffing.

Example JSONL schema (one request per line):
```
{"statusProd":200,"statusShadow":500,"latencyProdMs":210,"latencyShadowMs":480,"responseProd":{"ok":true},"responseShadow":{"error":"Timeout"}}
```

## AI insights (optional)
ShadowDeploy can call an LLM to generate better explanations. If no API key is
set, it falls back to built-in heuristics.

```
export SHADOW_LLM_ENABLED=true
export SHADOW_LLM_API_KEY=your_api_key
export SHADOW_LLM_BASE_URL=https://api.openai.com/v1/chat/completions
export SHADOW_LLM_MODEL=gpt-4o-mini
```

---

# Developer setup

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
- GET /api/health - service status
- POST /api/auth/login - login to receive an auth token
- GET /api/auth/me - current user profile (requires token)
- POST /api/auth/logout - revoke current token
- GET /api/summary - latest shadow diff summary payload (requires token)
- GET /api/traffic-dumps - list uploaded traffic dumps (requires token)
- POST /api/traffic-dumps - upload a traffic dump (requires token)
- POST /api/demo/run - run a demo analysis (requires token)

### Storage
The backend persists data in a local H2 file database at ./data/shadowdeploy.

## Frontend (React + Vite)
### Run
```bash
cd frontend
npm install
npm run dev
```

Vite proxies /api to http://localhost:8080 so the frontend can call the backend
without extra config.

## MVP architecture
- Models: API payloads returned by the backend.
- Presenters: React hooks that load data and shape UI state (src/presenters).
- Views: Stateless components that render UI (src/views).

## Next steps
- Add real traffic capture and shadow replay pipeline.
- Expand diff storage and retention policies.
- Expand AI explanations with LLM-backed insight generation.
