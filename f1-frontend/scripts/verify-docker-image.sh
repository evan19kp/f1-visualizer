#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_TAG="${IMAGE_TAG:-f1-frontend:verify}"
CONTAINER_NAME="${CONTAINER_NAME:-f1-frontend-verify-$$}"

cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Building frontend image (${IMAGE_TAG})..."
docker build -t "$IMAGE_TAG" "$ROOT"

echo "Starting container..."
docker run -d --name "$CONTAINER_NAME" -p 18081:80 "$IMAGE_TAG" >/dev/null

for _ in $(seq 1 20); do
  if curl -fsS "http://127.0.0.1:18081/health" >/dev/null; then
    break
  fi
  sleep 0.25
done

echo "Checking /health..."
curl -fsS "http://127.0.0.1:18081/health" | grep -q '^ok$'

echo "Checking SPA index..."
curl -fsS "http://127.0.0.1:18081/" | grep -q '<div id="root">'

echo "Frontend container verification passed."
