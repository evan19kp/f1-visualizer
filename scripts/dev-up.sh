#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "Starting Docker services..."
docker compose up -d

CACHE_GLB="$ROOT/tools/track-mesh/out/bahrain.glb"
SESSION_KEY="${OPENF1_SESSION_KEY:-9161}"

if [[ -f "$CACHE_GLB" ]]; then
  echo "Publishing cached track mesh for session ${SESSION_KEY}..."
  "$ROOT/tools/track-mesh/publish.sh" --session-key "$SESSION_KEY" --use-cache
else
  echo "No cached GLB at tools/track-mesh/out/bahrain.glb — skip publish or run generate.sh first."
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

Then open http://localhost:5173 — use Dev → Backfill history → Play on the timeline.

EOF
