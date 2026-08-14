# Project Status

Last updated: 2026-08-14 — via PR #45: feat: frontend production container/image

## Completed
- Maven multi-module backend (`f1-core`, `f1-persistence`, `f1-ingestion`, `f1-ai`, `f1-api`) with Spring Boot API entrypoint — verified by: module layout + `F1VisualizerApplication`
- Postgres `race_sessions` persistence via Flyway `V1__race_sessions.sql` + JPA repository — verified by: migration + `RaceSessionRepository` / `SessionController`
- Redis live positions, bounds, poll cursor, history ZSET, composite frame lookup, and pub/sub publish — verified by: `RedisPositionStore` implements full `PositionStore`; unit tests exist; ITs present (IT runtime unverified this session)
- OpenF1 location ingestion with normalize → Redis save, cursor advance, empty-window/404 handling, rate-limit backoff, optional token auth — verified by: `IngestionService` + `OpenF1Client` code paths; `OpenF1ClientTest` / `IngestionServiceTest`; backend unit tests passed (`./mvnw -DskipITs test`)
- Decoupled stint polling into Redis + public stint REST — verified by: `StintIngestionService`, `RedisStintStore`, `StintController` + tests
- Session metadata sync from OpenF1 into Postgres — verified by: `SessionMetadataSync` + tests
- Startup replay history auto-bootstrap when ingestion enabled — verified by: `SessionBootstrapService` + `SessionHistoryBackfillService` + tests
- STOMP/WebSocket position fan-out (Redis pub/sub → `/topic/sessions/{key}/positions`) — verified by: `RedisPositionBroadcastBridge` + `WebSocketConfig` + tests
- REST session/position/bounds/track-asset/ingestion-status endpoints (public GETs as coded in `SecurityConfig`) — verified by: controllers + controller tests where present (`IngestionStatusController` has no dedicated test)
- Replay playback backend (play/pause/seek + scheduled tick publishing composite frames) — verified by: `PlaybackService` / `PlaybackController`; `PlaybackServiceTest` + `PlaybackSecurityConfigTest`; unit tests passed
- JWT login API + filter + single in-memory admin user; prod secret/origin/password validators — verified by: `AuthController`, `SecurityConfig`, validator tests; unit tests passed
- Optional AI race-engineer path (race-control poll → detect safety-car/pit signals → OpenAI commentary → insight store → authenticated insights API) — verified by: `RaceControlPoller`, `RaceControlEventDetector`, `RaceEngineerService`, `InsightController` + module tests (live OpenAI call unverified)
- Dev-only track generate / session reset / history backfill APIs gated by `DEV_MODE` + auth — verified by: `DevTrackController`, `DevSessionController`, `DevHistoryController` + related tests
- Frontend 3D race view (R3F/Three): cars, orbit/follow/TV cameras, GLB track mesh with procedural fallback, connection status — verified by: `RaceCanvas`, `Cars`, `TrackMesh`, `CameraRig`, `useStompPositions`, `useInitialPositions` wiring in `App.tsx`
- Frontend HUD: session picker, playback bar, gap tower, tire widget, insight feed, camera selector, connection banner, dev tools — verified by: components mounted from `App.tsx` and API hooks
- Track-mesh Python generator + publish/generate scripts (with Python tests in tree) — verified by: `tools/track-mesh/` package + `tests/test_track_mesh.py` (Python test run unverified this session)
- Local infra compose (Postgres/Redis/LocalStack) + API Dockerfile + optional `api` compose profile — verified by: `docker-compose.yml`, `Dockerfile`
- CI: backend `./mvnw verify` + frontend build/type-check — verified by: `.github/workflows/ci.yml` (workflow execution on GitHub unverified this session)
- Dev bootstrap scripts `scripts/dev-up.sh` / `scripts/dev-check.sh` — verified by: files present (script execution unverified this session)
- [PR #45] Frontend production Docker image: Vite build with `VITE_*` args, nginx SPA + `/api`/`/ws` proxy to `API_BACKEND`, `/health`, compose `--profile frontend` (:8081), CI `frontend-docker` job + `verify-docker-image.sh` — 2026-08-14

## In Progress
- Frontend auth UX — state: `login()` + `ensureDevAuth()` exist (`lib/auth.ts`); used by insights/dev/playback in DEV; no login page/form or production auth flow UI
- Gap Tower standings — state: UI ranks drivers by geometric track angle (`GapTower.tsx` / `trackProgress.ts`), not OpenF1 timing/intervals; usable but not race-order accurate
- AI event coverage — state: `RaceEventType` includes `UNDERCUT` / `OVERTAKE`, but `RaceControlEventDetector` only emits `SAFETY_CAR` / `PIT_WINDOW`
- Multi-session product path — state: UI can select any session from Postgres list; live ingestion is a single `OPENF1_SESSION_KEY` (picker warns when mismatched)
- AI insights in default demo — state: fully wired when `AI_ENABLED=true` + JWT; off by default; frontend feed idle without token
- Tire widget freshness — state: one-shot REST fetch per selected driver; no WebSocket/stint push updates
- End-to-end demo runtime on this machine — state: code + unit tests support it; Docker stack / browser / OpenF1 live path not exercised in this assessment (unverified)

## Planned
- Production frontend login UI using `POST /api/auth/login` — source: README “AI & auth” / production notes (explicitly says do not rely on `VITE_DEV_AUTOLOGIN`)
- Frontend automated tests — source: inferred gap (`f1-frontend/package.json` has no test runner/script; CI only build + type-check)
- Real timing-based gaps / race order (intervals/laps) — source: inferred gap from geometric Gap Tower vs stated “race visualization” intent
- Concurrent multi-session ingestion/control — source: inferred from single configured session key + picker warning
- Driver identity beyond car number (names/teams) — source: inferred gap (UI labels `#driverNumber` only; no drivers API/client)
- Next product sprint after Sprint 11 — source: `.cursor/plans/` ends at Sprint 11; no Sprint 12+ docs in repo

## Superseded
- (none found on `main`; historical sprint/fix remote branches remain as delivery history, not alternate in-tree implementations)

## Known Issues / Deferred Scope
- No frontend unit/e2e tests — verification of UI/WebSocket/replay UX is manual + TypeScript build only
- `IngestionStatusController` has no dedicated test (status service covered indirectly elsewhere)
- Auth is a single Spring `security.user` admin — no user registry, roles beyond authenticated, or account management
- Playback control auth depends on JWT outside DEV/`DEV_MODE`; without auto-login, play/pause/seek fail closed in production frontend path
- Track GLB assets are not stored in git — empty scene mesh until generate/publish to S3/LocalStack
- `RaceEventType.UNDERCUT` / `OVERTAKE` appear only in tests/prompts today — detector never produces them
- GitHub issues/PRs unavailable in this environment (`gh` not installed) — tracker items not cross-checked
- Integration tests (`*IT`) and full `./mvnw verify` not run in this bootstrap (Docker/Testcontainers required)
- Split-origin static-only nginx config for frontend — surfaced in PR #45, deferred because: `VITE_*` build args supported but no separate nginx-only config (proxy blocks unused in split-origin)
- TLS termination in frontend container — surfaced in PR #45, deferred because: left to external reverse proxy/ingress
- `/track-assets` nginx proxy in prod frontend — surfaced in PR #45, deferred because: dev-only LocalStack CORS workaround; prod uses presigned S3 URLs directly
- End-to-end compose smoke test in CI (frontend + api stack) — surfaced in PR #45, deferred because: CI only builds image and checks `/health` + index HTML via `verify-docker-image.sh`

## Open Questions
- Is the north-star a polished single-session replay demo, or a multi-user live multi-session product? README/demo defaults emphasize session `9161`; prod notes imply broader deployability.
- Should Gap Tower move to OpenF1 timing/interval data, or stay geometric by design?
- Are `UNDERCUT` / `OVERTAKE` AI events still intended, and if so from what signal source?
- What should the next sprint prioritize now that Sprint 11 playback/bootstrap is merged? No in-repo roadmap after Sprint 11.
