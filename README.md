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

### Endpoints

- `GET /api/health` - service status
- `GET /api/summary` - shadow diff summary payload

## Frontend (React + Vite)

### Run

```bash
cd frontend
npm install
npm run dev
```

Vite proxies `/api` to `http://localhost:8080` so the frontend can call the backend without extra config.

## MVP architecture

- **Models**: API payloads returned by the backend.
- **Presenters**: React hooks that load data and shape UI state (`src/presenters`).
- **Views**: Stateless components that render UI (`src/views`).

## Next steps

- Add real traffic capture and shadow replay pipeline.
- Persist diff results in a datastore.
- Expand AI explanations with LLM-backed insight generation.
