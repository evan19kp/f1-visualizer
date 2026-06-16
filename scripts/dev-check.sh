#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
SESSION_KEY="${OPENF1_SESSION_KEY:-9161}"

failures=0

check() {
  local name="$1"
  local url="$2"
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' "$url" || echo "000")"
  if [[ "$code" =~ ^2 ]]; then
    echo "OK   $name ($code)"
  else
    echo "FAIL $name ($code) $url"
    failures=$((failures + 1))
  fi
}

check "health" "$API_URL/actuator/health"
check "positions" "$API_URL/api/sessions/${SESSION_KEY}/positions"
code="$(curl -s -o /dev/null -w '%{http_code}' "$API_URL/api/sessions/${SESSION_KEY}/track-asset" || echo "000")"
if [[ "$code" =~ ^2 ]] || [[ "$code" == "404" ]]; then
  echo "OK   track-asset ($code)"
else
  echo "FAIL track-asset ($code) $API_URL/api/sessions/${SESSION_KEY}/track-asset"
  failures=$((failures + 1))
fi
check "ingestion-status" "$API_URL/api/ingestion/status"
check "playback" "$API_URL/api/sessions/${SESSION_KEY}/playback"

if command -v jq >/dev/null 2>&1; then
  status_json="$(curl -sf "$API_URL/api/ingestion/status" 2>/dev/null || echo '{}')"
  bootstrap_status="$(echo "$status_json" | jq -r '.bootstrapStatus // "unknown"')"
  history_ready="$(echo "$status_json" | jq -r '.historyReady // false')"
  echo "INFO bootstrapStatus=$bootstrap_status historyReady=$history_ready"
  playback_json="$(curl -sf "$API_URL/api/sessions/${SESSION_KEY}/playback" 2>/dev/null || echo '{}')"
  history_loaded="$(echo "$playback_json" | jq -r '.historyLoaded // false')"
  echo "INFO playback.historyLoaded=$history_loaded"
else
  echo "SKIP jq not installed — skipping ingestion/playback field checks"
fi

play_code="$(curl -s -o /dev/null -w '%{http_code}' -X POST \
  "$API_URL/api/sessions/${SESSION_KEY}/playback/play" \
  -H 'Content-Type: application/json' \
  -d '{"speed":1}' || echo "000")"
if [[ "$play_code" =~ ^2 ]]; then
  echo "OK   playback-play-no-auth ($play_code)"
else
  echo "FAIL playback-play-no-auth ($play_code) — requires DEV_MODE=true on API"
  failures=$((failures + 1))
fi

if [[ "$failures" -gt 0 ]]; then
  echo "${failures} check(s) failed"
  exit 1
fi

echo "All checks passed"
