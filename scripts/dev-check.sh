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

if [[ "$failures" -gt 0 ]]; then
  echo "${failures} check(s) failed"
  exit 1
fi

echo "All checks passed"
