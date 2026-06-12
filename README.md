# F1 3D Race Visualizer

Real-time Formula 1 race visualization with 3D rendering, WebSocket position streaming, and AI race engineer commentary.

## Stack

- **Backend:** Java 21, Spring Boot 4.x, JPA, JWT, STOMP/WebSocket, Redis, PostgreSQL 16, Flyway, S3, OpenAI
- **Frontend:** React 18, TypeScript, Three.js (@react-three/fiber), Zustand, Tailwind, Vite

## Prerequisites

- Java 21+ (project targets 21; newer JDKs usually work)
- Docker Compose
- Node 20+ (frontend)
- Maven is optional — use `./mvnw` from the **repository root**

## Quick start

All backend commands run from the **repo root** (`f1-visualizer/`), not `f1-frontend/`.

### 1. Infrastructure

```bash
cd /path/to/f1-visualizer
docker compose up -d
```

Postgres listens on **host port 5433** (see `docker-compose.yml`: `5433:5432`) to avoid conflicts with a local Postgres on 5432. Redis uses 6379.

### 2. Backend API + ingestion

```bash
INGESTION_ENABLED=true OPENF1_SESSION_KEY=9161 ./mvnw spring-boot:run -pl f1-api
```

`9161` is the 2024 Bahrain GP race — a reliable demo session. Wait for `Started F1VisualizerApplication`. No API keys are required for the basic 3D demo.

Optional: copy `.env.example` to `.env` at the repo root and export the vars (`set -a && source .env && set +a`), or pass them inline as above.

Optional AI insights (requires JWT; see [AI & auth](#ai--auth)):

```bash
AI_ENABLED=true OPENAI_API_KEY=sk-... ./mvnw spring-boot:run -pl f1-api
```

### 3. Frontend

In a **second terminal**:

```bash
cd f1-frontend
npm install
npm run dev
```

Open http://localhost:5173 (Vite uses 5174 if 5173 is busy). You should see cars on the track, HUD panels, and **Connected** in the header.

### Verify backend

```bash
curl -s http://localhost:8080/api/sessions/9161/positions | head -c 200
```

Returns a JSON array of driver positions when ingestion has populated Redis.

## Environment variables

### Backend

Copy `.env.example` to `.env` at the repo root for a ready-made local profile (no secrets; dev defaults only).

| Variable | Default | Notes |
|----------|---------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5433/f1` | Host **5433** — matches `docker-compose.yml` (`5433:5432`) |
| `DB_USER` / `DB_PASSWORD` | `f1user` / `f1pass` | Match `docker-compose.yml` Postgres service |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | |
| `INGESTION_ENABLED` | `false` | Set `true` to poll OpenF1 |
| `OPENF1_SESSION_KEY` | `latest` | OpenF1 session key (e.g. `9161`) |
| `OPENF1_POLL_INTERVAL_MS` | `2500` | ~24 req/min; OpenF1 free tier caps at 30/min |
| `SERVER_PORT` | `8080` | REST + STOMP endpoint |
| `ADMIN_USER` / `ADMIN_PASSWORD` | `admin` / `changeme` | Dev login; pair with frontend dev auto-login |
| `AI_ENABLED` | `false` | Enables race-control poller + GPT insights |
| `OPENAI_API_KEY` | — | Required when `AI_ENABLED=true` |
| `JWT_SECRET` | dev placeholder | Required in production |
| `AWS_REGION` / `S3_BUCKET` | `us-east-1` / `f1-visualizer-assets` | Track mesh assets (Sprint 7) |

### Frontend (`f1-frontend/.env`)

Copy `f1-frontend/.env.example` to `.env` and adjust as needed.

| Variable | Default | Notes |
|----------|---------|-------|
| `VITE_SESSION_KEY` | `9161` | Must match backend `OPENF1_SESSION_KEY` |
| `VITE_WS_URL` | `http://localhost:8080/ws` | STOMP endpoint (SockJS) |
| `VITE_API_URL` | *(empty)* | Empty = Vite dev proxy to `:8080` |
| `VITE_DEV_AUTOLOGIN` | — | Set `true` for dev JWT auto-login |
| `VITE_DEV_AUTH_USER` / `VITE_DEV_AUTH_PASS` | — | Credentials for dev auto-login |

## AI & auth

Most `/api/**` routes require JWT. Position reads (`GET /api/sessions/{key}/positions`) are public.

For local insight feed testing, enable dev auto-login in `f1-frontend/.env`:

```
VITE_DEV_AUTOLOGIN=true
VITE_DEV_AUTH_USER=admin
VITE_DEV_AUTH_PASS=changeme
```

Default admin credentials match `application.yml` (`ADMIN_USER` / `ADMIN_PASSWORD`).

## Modules

| Module | Role |
|--------|------|
| `f1-core` | Domain models, events, shared interfaces |
| `f1-persistence` | JPA, Redis, S3, Flyway |
| `f1-ingestion` | OpenF1 client, scheduler, GPS normalizer |
| `f1-ai` | OpenAI race engineer |
| `f1-api` | REST, WebSocket, security |
| `f1-frontend` | React + Three.js UI |

## Troubleshooting

### `./mvnw: No such file or directory`

You are in `f1-frontend/`. `cd` to the repository root first.

### OpenF1 `422` — too much data

Fixed on main via chunked location polls. If it persists after `git pull`:

```bash
redis-cli DEL f1:session:9161:poll_cursor
```

Restart the API so ingestion resets its poll window.

### OpenF1 `404` — no results

Normal when a time window has no location data. The API treats this as an empty batch (no error spam). See PR #20 for the client-side handling.

### STOMP shows **Disconnected**

- Ensure the API is running on port 8080.
- Frontend uses a SockJS `global` polyfill in `vite.config.ts`; restart `npm run dev` after pulling.
- Check browser console for WebSocket/proxy errors.

### Empty track / zero drivers

- Confirm `INGESTION_ENABLED=true` and matching session keys on backend and frontend.
- Wait ~30s for the first OpenF1 poll cycle.
- `curl` the positions endpoint (see above).

### Postgres connection refused on 5432

The app defaults to port **5433**, not 5432. `docker-compose.yml` maps `5433:5432` so a local Postgres on 5432 does not collide. Use the default `DB_URL` or `jdbc:postgresql://localhost:5433/f1`.

## Tests

```bash
./mvnw test      # unit
./mvnw verify    # unit + integration (Docker)
```

## Development plans

Sprint plans and agent prompts live in [`.cursor/plans/`](.cursor/plans/).
