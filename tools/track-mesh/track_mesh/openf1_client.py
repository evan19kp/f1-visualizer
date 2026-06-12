"""Fetch OpenF1 location samples with auth and rate limiting."""

from __future__ import annotations

import json
import os
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

DEFAULT_BASE_URL = "https://api.openf1.org/v1"
DEFAULT_TOKEN_URL = "https://api.openf1.org/token"
DEFAULT_RATE_LIMIT_SECONDS = 3.0
MAX_RETRIES = 5
POLL_WINDOW = timedelta(minutes=5)


@dataclass(frozen=True)
class RawLocationSample:
    driver_number: int
    timestamp: str
    x: float
    y: float
    z: float


class OpenF1Client:
    def __init__(
        self,
        *,
        base_url: str | None = None,
        token_url: str | None = None,
        access_token: str | None = None,
        username: str | None = None,
        password: str | None = None,
        rate_limit_seconds: float = DEFAULT_RATE_LIMIT_SECONDS,
    ) -> None:
        self.base_url = (base_url or os.environ.get("OPENF1_BASE_URL") or DEFAULT_BASE_URL).rstrip("/")
        self.token_url = token_url or os.environ.get("OPENF1_TOKEN_URL") or DEFAULT_TOKEN_URL
        self.access_token = (access_token or os.environ.get("OPENF1_ACCESS_TOKEN") or "").strip()
        self.username = (username or os.environ.get("OPENF1_USERNAME") or "").strip()
        self.password = (password or os.environ.get("OPENF1_PASSWORD") or "").strip()
        self.rate_limit_seconds = rate_limit_seconds
        self._cached_token = ""
        self._last_request_at = 0.0

    def fetch_locations(
        self,
        session_key: int | str,
        *,
        cache_path: Path | None = None,
        use_cache: bool = False,
    ) -> list[RawLocationSample]:
        if use_cache and cache_path and cache_path.is_file():
            return _parse_cached_samples(json.loads(cache_path.read_text(encoding="utf-8")))

        session = self.fetch_session(session_key)
        if session is None:
            raise RuntimeError(f"OpenF1 session {session_key} not found")

        start = _parse_iso(session["date_start"])
        end = _parse_iso(session.get("date_end") or datetime.now(timezone.utc).isoformat())

        raw_rows: list[dict[str, Any]] = []
        window_start = start
        while window_start < end:
            window_end = min(window_start + POLL_WINDOW, end)
            params = {
                "session_key": str(session_key),
                "date>=": _format_openf1_instant(window_start),
                "date<": _format_openf1_instant(window_end),
            }
            chunk = self._get_json("/location", params)
            if chunk:
                raw_rows.extend(chunk)
            window_start = window_end

        if cache_path is not None:
            cache_path.parent.mkdir(parents=True, exist_ok=True)
            cache_path.write_text(json.dumps(raw_rows, indent=2), encoding="utf-8")

        return _parse_cached_samples(raw_rows)

    def fetch_session(self, session_key: int | str) -> dict[str, Any] | None:
        rows = self._get_json("/sessions", {"session_key": str(session_key)})
        if not rows:
            return None
        return rows[0]

    def _get_json(self, path: str, params: dict[str, str]) -> list[dict[str, Any]]:
        query = urllib.parse.urlencode(params)
        url = f"{self.base_url}{path}?{query}"
        request = urllib.request.Request(url, headers={"Accept": "application/json"})
        token = self._resolve_access_token()
        if token:
            request.add_header("Authorization", f"Bearer {token}")

        for attempt in range(MAX_RETRIES):
            self._throttle()
            try:
                with urllib.request.urlopen(request, timeout=120, context=_ssl_context()) as response:
                    payload = json.loads(response.read().decode("utf-8"))
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    return []
                if error.code == 429 and attempt + 1 < MAX_RETRIES:
                    time.sleep(DEFAULT_RATE_LIMIT_SECONDS * (attempt + 2))
                    continue
                raise RuntimeError(f"OpenF1 request failed ({error.code}) for {url}") from error
            except urllib.error.URLError as error:
                raise RuntimeError(f"OpenF1 request failed for {url}: {error}") from error
            break
        else:
            raise RuntimeError(f"OpenF1 request failed after retries for {url}")

        if payload is None:
            return []
        if isinstance(payload, list):
            return payload
        raise RuntimeError(f"Unexpected OpenF1 response for {url}: {type(payload)}")

    def _resolve_access_token(self) -> str:
        if self.access_token:
            return self.access_token
        if not self.username or not self.password:
            return ""
        if self._cached_token:
            return self._cached_token

        self._throttle()
        form = urllib.parse.urlencode(
            {"username": self.username, "password": self.password}
        ).encode("utf-8")
        request = urllib.request.Request(
            self.token_url,
            data=form,
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=60, context=_ssl_context()) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.URLError as error:
            raise RuntimeError(f"OpenF1 token request failed: {error}") from error

        token = str(payload.get("access_token", "")).strip()
        if not token:
            raise RuntimeError("OpenF1 token request succeeded without access_token")
        self._cached_token = token
        return token

    def _throttle(self) -> None:
        elapsed = time.monotonic() - self._last_request_at
        if elapsed < self.rate_limit_seconds:
            time.sleep(self.rate_limit_seconds - elapsed)
        self._last_request_at = time.monotonic()


def _parse_cached_samples(rows: list[dict[str, Any]]) -> list[RawLocationSample]:
    samples: list[RawLocationSample] = []
    for row in rows:
        samples.append(
            RawLocationSample(
                driver_number=int(row["driver_number"]),
                timestamp=str(row["date"]),
                x=float(row["x"]),
                y=float(row["y"]),
                z=float(row["z"]),
            )
        )
    return samples


def _parse_iso(value: str) -> datetime:
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _format_openf1_instant(value: datetime) -> str:
    return value.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def _ssl_context() -> ssl.SSLContext:
    for cafile in (
        os.environ.get("SSL_CERT_FILE"),
        "/etc/ssl/cert.pem",
        "/etc/pki/tls/certs/ca-bundle.crt",
    ):
        if cafile and Path(cafile).is_file():
            return ssl.create_default_context(cafile=cafile)
    return ssl.create_default_context()
