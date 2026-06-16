#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "Starting Docker services..."
docker compose up -d

SESSION_KEY="${OPENF1_SESSION_KEY:-9161}"
CIRCUIT_SLUG="${CIRCUIT_SLUG:-singapore}"
CACHE_JSON="$ROOT/tools/track-mesh/cache/${SESSION_KEY}.json"
PUBLISH_ARGS=(--session-key "$SESSION_KEY" --circuit-slug "$CIRCUIT_SLUG")
if [[ -f "$CACHE_JSON" ]]; then
  echo "Publishing cached track mesh for session ${SESSION_KEY}..."
  PUBLISH_ARGS+=(--use-cache)
  "$ROOT/tools/track-mesh/publish.sh" "${PUBLISH_ARGS[@]}" || echo "Track publish failed — run again after API is up or fetch cache first."
else
  echo "No OpenF1 cache at ${CACHE_JSON} — fetching track mesh for session ${SESSION_KEY} (first run)..."
  "$ROOT/tools/track-mesh/publish.sh" "${PUBLISH_ARGS[@]}" || echo "Track publish failed — cars still load; mesh may be procedural until publish succeeds."
fi

cat <<EOF

Copy into terminal 1 (API):

export DEV_MODE=true
export INGESTION_ENABLED=true
export OPENF1_SESSION_KEY=${SESSION_KEY}
export OPENF1_POLL_INTERVAL_MS=7000
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
./mvnw spring-boot:run -pl f1-api

Copy into terminal 2 (frontend):

cd f1-frontend
export VITE_DEV_AUTOLOGIN=true
export VITE_DEV_AUTH_USER=admin
export VITE_DEV_AUTH_PASS=changeme
npm install
npm run dev

Then open http://localhost:5173 — wait for session history to load (~30–90s), then press **Play** on the timeline. Playback controls work without login when the API runs with DEV_MODE=true.

EOF
