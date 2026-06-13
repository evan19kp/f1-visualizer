# Track mesh generator (OpenF1 GPS → flat GLB)

Generates a **flat** circuit ribbon GLB aligned with the app's normalized driver positions. Elevation and banking are intentionally omitted — add those manually in Blender after import.

## Prerequisites

- Python 3.11+
- OpenF1 credentials when the API requires auth (same env vars as the backend):
  - `OPENF1_ACCESS_TOKEN`, or
  - `OPENF1_USERNAME` + `OPENF1_PASSWORD`

Optional overrides: `OPENF1_BASE_URL`, `OPENF1_TOKEN_URL`.

On macOS Python.org installs, if HTTPS fails with certificate errors, run `Install Certificates.command` from your Python folder or set `SSL_CERT_FILE=/etc/ssl/cert.pem`.

## Quick start

```bash
./tools/track-mesh/generate.sh --session-key 9161 --circuit-slug bahrain
```

Output (gitignored):

- `tools/track-mesh/out/bahrain.glb`
- `tools/track-mesh/out/bahrain.elevation.json` (normalized OpenF1 y per centerline point for Blender)

First run creates a local venv (stdlib only; no pip packages required). Raw OpenF1 samples are cached at `tools/track-mesh/cache/9161.json` for offline re-runs.

## CLI

```bash
./tools/track-mesh/generate.sh \
  --session-key 9161 \
  --circuit-slug bahrain \
  --out ./tools/track-mesh/out/bahrain.glb \
  --width 0.02 \
  --points 300 \
  --method polar_bin
```

| Flag | Default | Description |
|------|---------|-------------|
| `--session-key` | required | OpenF1 session key |
| `--circuit-slug` | session key | Used for default output filename |
| `--out` | `out/{slug}.glb` | Output GLB path |
| `--width` | `0.02` | Ribbon width in normalized units (~2% of bbox) |
| `--points` | `300` | Resampled centerline vertices |
| `--method` | `polar_bin` | `polar_bin`, `arc_length`, or `angle` |
| `--driver` | most samples | Reference driver for lap centerline |
| `--use-cache` | off | Skip network; read `cache/{session_key}.json` |
| `--cache` | `cache/{session_key}.json` | Raw sample cache path |

## Coordinate system

This tool **mirrors ingestion**, not a separate 0–1 space:

```java
// CoordinateNormalizer.normalizeAxis
normalized = 2 * (value - min) / (max - min) - 1   // clamped to [-1, 1]
```

Bounds are expanded across **all fetched samples** (all drivers), matching ingestion batch behavior.

The frontend scales positions as `x/z × 50`, `y × 2` (`scenePosition.ts`). `TrackMesh.tsx` loads GLB with `scale={[50,50,50]}` and `rotation={[-π/2, 0, 0]}`.

GLB vertices are authored as:

| GLB axis | Value |
|----------|-------|
| `x` | normalized OpenF1 `x` |
| `y` | `-normalized OpenF1 z` |
| `z` | flat constant (`0.0` in V1) |

After the loader rotation, cars align in scene **x/z** with the ribbon. OpenF1 elevation (`y`) is stored in the sidecar JSON only.

## Centerline extraction

**V1 default (`polar_bin`):**

1. Expand bounds and normalize all drivers' samples (mirrors ingestion).
2. Bin samples by angle around the x/z centroid.
3. Average samples in each bin into a centerline vertex.
4. Resample uniformly by arc length (~300 points) and close the loop.

Works well for qualifying/practice where drivers never return to a single lap closure point. For full race sessions you can use `arc_length` with `--driver`.

**`arc_length` (Option B):** sort one reference driver's samples by timestamp, detect one lap, resample by arc length. Best when the driver completes clear lap loops.

**`angle` (Option A):** sort by `atan2(z, x)` around the centroid — same idea as `buildCenterLineVertices` in `TrackMesh.tsx`. Useful for snapshots but poor on concave circuits.

## Validation

The CLI prints bounds, centerline length, mesh counts, and length vs bounding-box diagonal ratio.

Re-run from cache without network:

```bash
./tools/track-mesh/generate.sh --session-key 9161 --circuit-slug bahrain --use-cache
```

Unit tests:

```bash
cd tools/track-mesh
python3 -m venv .venv
PYTHONPATH=. .venv/bin/python -m unittest discover -s tests -v
```

Dev check in the app:

1. Upload GLB to LocalStack (see below).
2. Run API + frontend with session `9161`.
3. Cars should sit on the ribbon in **x/z** (elevation may differ until Blender pass).

## Pre-upload checklist

Before uploading to S3 (LocalStack or production):

- [ ] **Session key matches ingestion** — same value as `OPENF1_SESSION_KEY` and `VITE_SESSION_KEY`
- [ ] **`circuit_name` → slug matches S3 key** — e.g. `Bahrain` → `bahrain` → `tracks/bahrain.glb` (confirm via `GET /api/sessions/{key}/track-asset` → `circuitSlug`)
- [ ] **Cars on strip in x/z** — flat GLB aligns horizontally in the app before any Blender pass
- [ ] **After Blender edit, x/z unchanged** — re-export without baking scale or shifting normalized horizontal coordinates

## Reference run — Bahrain session 9161

```bash
# Generate (requires OpenF1 auth for live/historical access)
./tools/track-mesh/generate.sh --session-key 9161 --circuit-slug bahrain

# Upload to LocalStack (do not commit the GLB)
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

aws --endpoint-url=http://localhost:4566 s3 cp \
  tools/track-mesh/out/bahrain.glb \
  s3://f1-visualizer-assets/tracks/bahrain.glb

# Verify API
curl -s http://localhost:8080/api/sessions/9161/track-asset
```

Run the app with `INGESTION_ENABLED=true OPENF1_SESSION_KEY=9161` and frontend session `9161`.

## Blender hand-off (manual elevation / banking)

After generating the flat GLB:

1. Import `bahrain.glb` in Blender.
2. Use OpenF1 normalized y from `{circuit}.elevation.json` (or a height map) to sculpt elevation.
3. Add cross-slope / banking by hand on the mesh or with a curve modifier.
4. Re-export GLB **without changing x/z scale** — stay in normalized [-1, 1] space on each axis.
5. Re-upload to `tracks/{circuit-slug}.glb` in S3.

See also `assets/tracks/README.md` for S3 layout and coordinate notes.
