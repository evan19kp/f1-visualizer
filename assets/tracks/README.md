# Track mesh assets (S3)

Circuit meshes live in S3, not in git. The API serves presigned URLs via:

```
GET /api/sessions/{sessionKey}/track-asset
```

When no object exists (404), the frontend keeps the procedural center-line + plane track.

**Generate meshes** with the OpenF1 GPS tool: [`tools/track-mesh/README.md`](../../tools/track-mesh/README.md).

## End-to-end workflow

1. **Generate** a flat GLB for the session you ingest (same `session_key` as `OPENF1_SESSION_KEY`):

   ```bash
   ./tools/track-mesh/generate.sh --session-key 9161 --circuit-slug bahrain
   ```

2. **Validate in the app** — upload to LocalStack (below), run API + frontend with session `9161`, confirm cars sit on the ribbon in **x/z**.

3. **Manual pass (optional)** — import the GLB in Blender, add elevation and banking, re-export without changing x/z scale (see [Manual pass in Blender](#manual-pass-in-blender)).

4. **Upload** the final GLB to `tracks/{circuit-slug}.glb` in S3 (LocalStack or production).

5. **Verify** the API returns a presigned URL:

   ```bash
   curl -s http://localhost:8080/api/sessions/9161/track-asset
   ```

## S3 layout

| Object key | Example |
|------------|---------|
| `tracks/{circuit-slug}.glb` | `tracks/bahrain.glb` |

The circuit slug is derived from `race_sessions.circuit_name` (lowercase, non-alphanumeric → `-`).  
Session `9161` uses circuit name `Bahrain` → slug `bahrain`.

Use the same slug for `--circuit-slug` when generating and for the S3 object key. Confirm via the track-asset response (`circuitSlug` field) or:

```bash
curl -s http://localhost:8080/api/sessions/9161 | jq .circuitName
```

Default bucket: `f1-visualizer-assets` (`S3_BUCKET`).

## Coordinate contract

Meshes must align with **ingestion-normalized driver positions**, not scene units.

| Rule | Detail |
|------|--------|
| **x / z** | Normalized OpenF1 coordinates in `[-1, 1]` (same bounds expansion as ingestion across all drivers in the session) |
| **y (V1 generator)** | Flat constant (`0.0` in the flat GLB); OpenF1 elevation is in `{slug}.elevation.json` for Blender only |
| **No baked `TRACK_SCALE`** | Do **not** multiply vertices by 50 in the GLB. The frontend applies `TRACK_SCALE` at load time (`TrackMesh.tsx`: `scale={[50,50,50]}`) |
| **GLB axis mapping** | Generator writes `x` = norm x, `y` = `-norm z`, `z` = flat; loader rotation maps cars to scene x/z |

After Blender edits, keep x/z in normalized space. Elevation and banking change **y** (and mesh shape); do not rescale or re-center x/z.

See [`tools/track-mesh/README.md`](../../tools/track-mesh/README.md) for normalization formula and centerline options.

## LocalStack upload

Start LocalStack with docker compose, then upload:

```bash
docker compose up -d localstack

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566

aws --endpoint-url="$AWS_ENDPOINT_URL" s3 mb s3://f1-visualizer-assets 2>/dev/null || true
aws --endpoint-url="$AWS_ENDPOINT_URL" s3 cp \
  tools/track-mesh/out/bahrain.glb \
  s3://f1-visualizer-assets/tracks/bahrain.glb
```

Run the API with the same endpoint and credentials (`AWS_ENDPOINT_URL`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` in `.env` or inline). Ensure a `race_sessions` row exists with matching `circuit_name` (ingestion syncs this from OpenF1 when `INGESTION_ENABLED=true`).

## Production upload

```bash
aws s3 cp ./bahrain.glb s3://f1-visualizer-assets/tracks/bahrain.glb
```

Use IAM credentials via the default AWS provider chain (env vars, instance profile, etc.). Set `AWS_REGION` and `S3_BUCKET` on the API; do not set `AWS_ENDPOINT_URL`.

## Manual pass in Blender

The generator outputs a **flat** ribbon. Elevation and banking are intentional manual steps:

1. **Import** `{circuit-slug}.glb` (File → Import → glTF 2.0).
2. **Elevation** — use normalized OpenF1 y from `tools/track-mesh/out/{slug}.elevation.json` (one value per centerline point) to sculpt height, or paint from a reference DEM.
3. **Banking** — add cross-slope by editing mesh geometry, shrinkwrap, or a curve modifier along the centerline.
4. **Re-export** as GLB:
   - Do **not** apply object scale that changes x/z normalization.
   - Do **not** bake `TRACK_SCALE` (50) into vertex positions.
   - Keep the mesh origin and x/z extent consistent with the flat export (normalized `[-1, 1]` on horizontal axes).
5. **Re-upload** to `tracks/{circuit-slug}.glb` and reload the app.

Cars should still align in **x/z** after upload; only vertical alignment and visual banking should change.

Generator CLI, validation, and checklist: [`tools/track-mesh/README.md`](../../tools/track-mesh/README.md).
