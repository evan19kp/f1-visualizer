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
| `OPENF1_ACCESS_TOKEN` | — | Optional bearer token for authenticated OpenF1 REST access |
| `OPENF1_USERNAME` / `OPENF1_PASSWORD` | — | Optional OpenF1 credentials; backend exchanges them for a bearer token |
| `OPENF1_TOKEN_FAILURE_COOLDOWN_MS` | `30000` | Delay before retrying failed OpenF1 token requests |
| `OPENF1_POLL_INTERVAL_MS` | `2500` | ~24 req/min; OpenF1 free tier caps at 30/min |
| `SERVER_PORT` | `8080` | REST + STOMP endpoint |
| `ADMIN_USER` / `ADMIN_PASSWORD` | `admin` / `changeme` | Dev login; pair with frontend dev auto-login |
| `AI_ENABLED` | `false` | Enables race-control poller + GPT insights |
| `OPENAI_API_KEY` | — | Required when `AI_ENABLED=true` |
| `JWT_SECRET` | dev placeholder | Required in production |
| `AWS_REGION` / `S3_BUCKET` | `us-east-1` / `f1-visualizer-assets` | Track mesh assets (optional) |
| `AWS_ENDPOINT_URL` | — | LocalStack S3 (`http://localhost:4566`) |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | — | LocalStack: `test` / `test` |

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

Most `/api/**` routes require JWT. Public reads: `GET /api/sessions/{key}/positions` and `GET /api/sessions/{key}/track-asset` (404 → procedural track; see [assets/tracks/README.md](assets/tracks/README.md)).

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
./mvnw test      # unit tests only (*Test.java)
./mvnw verify    # unit + integration (*IT.java via Testcontainers)
```

`./mvnw verify` requires **Docker** — integration tests spin up ephemeral `postgres:16` and `redis:7-alpine` containers. Unit tests (`./mvnw test`) do not need Docker.

## Production deployment

Use the `prod` Spring profile for a VPS or single-host deploy. The API fails fast at startup if secrets or WebSocket origins are missing or still set to dev defaults.

### Backend

Set `SPRING_PROFILES_ACTIVE=prod` and provide managed Postgres + Redis (not the dev `docker-compose` defaults unless you change credentials). Example:

```bash
export SPRING_PROFILES_ACTIVE=prod

export DB_URL=jdbc:postgresql://db.internal:5432/f1
export DB_USER=f1user
export DB_PASSWORD='<strong-db-password>'

export REDIS_HOST=redis.internal
export REDIS_PORT=6379

export JWT_SECRET='<random-string-at-least-32-chars>'
export ADMIN_USER=admin
export ADMIN_PASSWORD='<strong-admin-password>'

# Comma-separated browser origins that open the frontend (no localhost default in prod)
export WEBSOCKET_ALLOWED_ORIGINS=https://f1.example.com

# Only when the SPA is on a different origin than the API
export APP_CORS_ORIGINS=https://f1.example.com

# Ingestion is off by default in prod — opt in explicitly
export INGESTION_ENABLED=true
export OPENF1_SESSION_KEY=9161
# Optional during live sessions when OpenF1 requires authenticated access
export OPENF1_USERNAME='<openf1-account-email>'
export OPENF1_PASSWORD='<openf1-account-password>'

java -jar f1-api/target/f1-api-0.0.1-SNAPSHOT.jar
```

Build the JAR from the repo root: `./mvnw package -pl f1-api -am -DskipTests`.

| Variable | Required in prod | Notes |
|----------|------------------|-------|
| `SPRING_PROFILES_ACTIVE` | yes | Must include `prod` |
| `JWT_SECRET` | yes | ≥ 32 characters; must not contain `CHANGE_ME` |
| `ADMIN_PASSWORD` | yes | Must not be `changeme` |
| `WEBSOCKET_ALLOWED_ORIGINS` | yes | Comma-separated frontend URLs |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | yes | Managed Postgres |
| `REDIS_HOST`, `REDIS_PORT` | yes | Managed Redis |
| `APP_CORS_ORIGINS` | if split origin | REST CORS when SPA and API differ |
| `INGESTION_ENABLED` | no | Defaults to `false` in prod |
| `OPENF1_ACCESS_TOKEN` or `OPENF1_USERNAME` / `OPENF1_PASSWORD` | if OpenF1 requires auth | Needed for live-session REST access |
| `OPENAI_API_KEY` | if `AI_ENABLED=true` | Validated only when AI is on |

**Security notes:** CSRF remains enabled in prod. JWTs travel in the `Authorization` header (stateless SPA), so cookie-based CSRF is not a concern. CSRF is disabled only in non-prod profiles for easier local testing.

Logging in prod: `com.evanp.f1` at INFO, Hibernate SQL at WARN (`application-prod.yml`).

### Frontend

Build with production API URLs (baked in at compile time):

```bash
cd f1-frontend
export VITE_API_URL=https://api.example.com
export VITE_WS_URL=https://api.example.com/ws
export VITE_SESSION_KEY=9161
npm ci
npm run build
```

Serve `f1-frontend/dist/` with any static host (nginx, S3 + CloudFront, etc.). Do not enable `VITE_DEV_AUTOLOGIN` in production.

### Reverse proxy (optional)

A typical layout terminates TLS at nginx and proxies to the API on `:8080`:

- `/api/**` and `/ws/**` → backend
- `/` → static `dist/` files

Ensure `WEBSOCKET_ALLOWED_ORIGINS` and `APP_CORS_ORIGINS` match the public site origin (scheme + host + port).

## Development plans

Sprint plans and agent prompts live in [`.cursor/plans/`](.cursor/plans/).
