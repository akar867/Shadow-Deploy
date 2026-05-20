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

## Step-by-step: test your new code with real traffic

### Step 1: Run your new code as a safe shadow instance
You need a second copy of your app running side-by-side with production.
It must be safe: no writes to prod DB, no emails, no payments.

1) Create a shadow profile config (example `application-shadow.yml`):
```yaml
server:
  port: 9090

spring:
  datasource:
    url: jdbc:mysql://shadow-db:3306/app
    username: readonly_user
    password: readonly_pass

feature-flags:
  payments-enabled: false
  emails-enabled: false
  webhooks-enabled: false
```

2) Build and run the new version with the shadow profile:
```bash
./mvnw clean package
SPRING_PROFILES_ACTIVE=shadow SERVER_PORT=9090 java -jar target/your-app.jar
```

3) Verify it is reachable:
```bash
curl http://localhost:9090/health
```

### Step 2: Capture real traffic and send it to ShadowDeploy
Pick one integration path:

#### Option A: Spring Boot (inside the service)
1) Copy the client code from:
```
integrations/spring-shadowdeploy-client
```
2) Add config to your app:
```yaml
shadowdeploy:
  client:
    enabled: true
    base-url: http://shadowdeploy-backend:8080
    auth-token: YOUR_SHADOWDEPLOY_TOKEN
    shadow-base-url: http://localhost:9090
    service-name: your-service
    deployment-id: deploy-rc1
    sample-rate: 0.1
```

#### Option B: Spring Cloud Gateway (recommended)
1) Copy the gateway integration code from:
```
integrations/spring-cloud-gateway-shadowdeploy
```
2) Add config to your gateway app:
```yaml
shadowdeploy:
  gateway:
    enabled: true
    base-url: http://shadowdeploy-backend:8080
    auth-token: YOUR_SHADOWDEPLOY_TOKEN
    shadow-base-url: http://localhost:9090
    service-name: your-service
    deployment-id: deploy-rc1
    sample-rate: 0.1
```

3) Restart your app or gateway.

Now ShadowDeploy will capture real traffic, replay it against your new code,
and update the dashboard automatically.

## AI insights (optional)
ShadowDeploy can call an LLM to generate better explanations. If no API key is
set, it falls back to built-in heuristics.

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
- POST /api/shadow/replay - replay captured traffic against shadow (requires token)

### Storage
The backend uses MySQL by default. Configure credentials via environment
variables:

```
export SHADOW_DB_URL=jdbc:mysql://localhost:3306/shadowdeploy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
export SHADOW_DB_USERNAME=shadow
export SHADOW_DB_PASSWORD=shadow
```

Tests still use an in-memory H2 database.

### Spring integration (drop-in client)
For a Spring Boot app, you can capture real traffic and send it to ShadowDeploy.
See the sample code in:

```
integrations/spring-shadowdeploy-client
```

If you use Spring Cloud Gateway, see:

```
integrations/spring-cloud-gateway-shadowdeploy
```

High-level steps:
1) Copy the client classes into your Spring app.
2) Configure `shadowdeploy.client.*` in application.yml.
3) Provide a ShadowDeploy auth token from `/api/auth/login`.
4) Set `shadow-base-url` to your shadow service endpoint.

### Shadow replay (basic pipeline)
This adds a simple version of the "gateway → replay → diff" flow:

1) Capture requests and production responses as JSON (one request per entry).
2) Send them to /api/shadow/replay with a shadow base URL.
3) ShadowDeploy replays the requests against the new code, compares responses,
   and updates the dashboard.

Minimal replay payload example:
```json
{
  "serviceName": "checkout-service",
  "deploymentId": "deploy-rc1",
  "shadowBaseUrl": "http://localhost:9090",
  "requests": [
    {
      "requestId": "req-1",
      "method": "POST",
      "path": "/checkout",
      "headers": {"Content-Type": "application/json"},
      "body": {"cartId": "c-1"},
      "prodStatus": 200,
      "prodBody": {"orderId": "o-1", "total": 44.99},
      "prodLatencyMs": 180
    }
  ]
}
```

You can set a default shadow base URL with:
```
export SHADOW_REPLAY_SHADOW_BASE_URL=http://localhost:9090
```

### LLM insights (optional)
Set environment variables to enable LLM-backed insights (OpenAI-compatible endpoint):

```
export SHADOW_LLM_ENABLED=true
export SHADOW_LLM_API_KEY=your_api_key
export SHADOW_LLM_BASE_URL=https://api.openai.com/v1/chat/completions
export SHADOW_LLM_MODEL=gpt-4o-mini
```

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

## Further extensions
- Add real traffic capture and shadow replay pipeline.
- Expand diff storage and retention policies.
- Expand AI explanations with LLM-backed insight generation.
