#!/usr/bin/env bash
# Generate a track GLB, upload to LocalStack S3, and clear Redis session cache.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${ROOT}/../.." && pwd)"

SESSION_KEY=""
USE_CACHE=false
CIRCUIT_SLUG=""
API_URL="${API_URL:-http://localhost:8080}"
S3_BUCKET="${S3_BUCKET:-f1-visualizer-assets}"
LOCALSTACK_CONTAINER="${LOCALSTACK_CONTAINER:-}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"

usage() {
  cat <<'EOF'
Usage: publish.sh --session-key KEY [options]

Generate a track GLB, upload to LocalStack, and clear Redis keys for the session.

Options:
  --session-key KEY   OpenF1 session key (required)
  --circuit-slug SLUG Circuit slug for S3 key (default: resolve from API circuitName)
  --use-cache         Read cached OpenF1 samples instead of fetching
  --api-url URL       API base URL (default: http://localhost:8080)
  --help              Show this help

Environment:
  LOCALSTACK_CONTAINER  Docker container name (auto-detected if unset)
  S3_BUCKET             Bucket name (default: f1-visualizer-assets)
  REDIS_HOST / REDIS_PORT
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --session-key)
      SESSION_KEY="$2"
      shift 2
      ;;
    --circuit-slug)
      CIRCUIT_SLUG="$2"
      shift 2
      ;;
    --use-cache)
      USE_CACHE=true
      shift
      ;;
    --api-url)
      API_URL="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "${SESSION_KEY}" ]]; then
  echo "Error: --session-key is required" >&2
  usage >&2
  exit 1
fi

slugify() {
  echo "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+|-+$//g'
}

resolve_circuit_slug() {
  if [[ -n "${CIRCUIT_SLUG}" ]]; then
    echo "${CIRCUIT_SLUG}"
    return
  fi

  if ! command -v curl >/dev/null 2>&1; then
    echo "Error: --circuit-slug required (curl not available to resolve from API)" >&2
    exit 1
  fi

  local response circuit_name
  response="$(curl -sf "${API_URL}/api/sessions/${SESSION_KEY}" 2>/dev/null)" || {
    echo "Error: could not fetch session ${SESSION_KEY} from ${API_URL}" >&2
    echo "Pass --circuit-slug explicitly (e.g. singapore for session 9161)." >&2
    exit 1
  }

  if command -v jq >/dev/null 2>&1; then
    circuit_name="$(echo "${response}" | jq -r '.circuitName // empty')"
  else
    circuit_name="$(echo "${response}" | sed -n 's/.*"circuitName"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)"
  fi

  if [[ -z "${circuit_name}" ]]; then
    echo "Error: session ${SESSION_KEY} has no circuitName; pass --circuit-slug" >&2
    exit 1
  fi

  slugify "${circuit_name}"
}

detect_localstack_container() {
  if [[ -n "${LOCALSTACK_CONTAINER}" ]]; then
    echo "${LOCALSTACK_CONTAINER}"
    return
  fi
  local name
  name="$(docker ps --format '{{.Names}}' | grep -E 'localstack' | head -1 || true)"
  if [[ -z "${name}" ]]; then
    echo "Error: LocalStack container not found. Start with: docker compose up -d localstack" >&2
    exit 1
  fi
  echo "${name}"
}

CIRCUIT_SLUG="$(resolve_circuit_slug)"
GLB_PATH="${ROOT}/out/${CIRCUIT_SLUG}.glb"

echo "==> Generating track mesh for session ${SESSION_KEY} (${CIRCUIT_SLUG})"
GEN_ARGS=(--session-key "${SESSION_KEY}" --circuit-slug "${CIRCUIT_SLUG}")
if [[ "${USE_CACHE}" == true ]]; then
  GEN_ARGS+=(--use-cache)
fi

if ! "${ROOT}/generate.sh" "${GEN_ARGS[@]}"; then
  echo "Error: track generation failed; skipping upload and Redis clear." >&2
  exit 1
fi

if [[ ! -f "${GLB_PATH}" ]]; then
  echo "Error: expected output not found: ${GLB_PATH}" >&2
  exit 1
fi

CONTAINER="$(detect_localstack_container)"
REMOTE_PATH="/tmp/${CIRCUIT_SLUG}.glb"
S3_KEY="tracks/${CIRCUIT_SLUG}.glb"

echo "==> Uploading to LocalStack (${CONTAINER})"
docker cp "${GLB_PATH}" "${CONTAINER}:${REMOTE_PATH}"
docker exec "${CONTAINER}" awslocal s3 mb "s3://${S3_BUCKET}" 2>/dev/null || true
docker exec "${CONTAINER}" awslocal s3 cp "${REMOTE_PATH}" "s3://${S3_BUCKET}/${S3_KEY}"

echo "==> Clearing Redis session keys for ${SESSION_KEY}"
if command -v redis-cli >/dev/null 2>&1; then
  KEYS="$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" --scan --pattern "f1:session:${SESSION_KEY}:*" 2>/dev/null || true)"
  if [[ -n "${KEYS}" ]]; then
    echo "${KEYS}" | xargs redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" DEL >/dev/null
    echo "    Cleared Redis keys for session ${SESSION_KEY}"
  else
    echo "    No Redis keys found for session ${SESSION_KEY}"
  fi
else
  echo "    Warning: redis-cli not found; clear keys manually:" >&2
  echo "    redis-cli KEYS 'f1:session:${SESSION_KEY}:*' | xargs redis-cli DEL" >&2
fi

echo ""
echo "Done. Next steps:"
echo "  1. API: DEV_MODE=true INGESTION_ENABLED=true OPENF1_SESSION_KEY=${SESSION_KEY} \\"
echo "     AWS_ENDPOINT_URL=http://localhost:4566 AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \\"
echo "     ./mvnw spring-boot:run -pl f1-api"
echo "  2. Frontend: npm run dev (session ${SESSION_KEY})"
echo "  3. Hard refresh browser after track loads"
echo "  4. Verify: curl -s ${API_URL}/api/sessions/${SESSION_KEY}/track-asset"
