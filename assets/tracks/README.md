# Track mesh assets (S3)

Circuit meshes are stored in S3, not in git. The API serves presigned URLs via:

```
GET /api/sessions/{sessionKey}/track-asset
```

When no object exists (404), the frontend keeps the procedural center-line + plane track.

## S3 layout

| Object key | Example |
|------------|---------|
| `tracks/{circuit-slug}.glb` | `tracks/bahrain.glb` |

The circuit slug is derived from `race_sessions.circuit_name` (lowercase, non-alphanumeric → `-`).  
Session `9161` uses circuit name `Bahrain` → slug `bahrain`.

Default bucket: `f1-visualizer-assets` (`S3_BUCKET`).

## Coordinate system

GLB meshes should use **normalized OpenF1 x/z coordinates** — the same space as driver positions before the frontend applies `TRACK_SCALE` (50). The loader scales the mesh by `TRACK_SCALE` to align with car positions.

## LocalStack (optional)

Start LocalStack with docker compose, then upload a demo mesh:

```bash
docker compose up -d localstack

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566

aws --endpoint-url="$AWS_ENDPOINT_URL" s3 mb s3://f1-visualizer-assets
aws --endpoint-url="$AWS_ENDPOINT_URL" s3 cp ./my-bahrain.glb s3://f1-visualizer-assets/tracks/bahrain.glb
```

Run the API with the same `AWS_ENDPOINT_URL` (and credentials above). Ensure the session row exists with `circuit_name = Bahrain`.

## Production upload

```bash
aws s3 cp ./bahrain.glb s3://f1-visualizer-assets/tracks/bahrain.glb
```

Use IAM credentials via the default AWS provider chain (env vars, instance profile, etc.).
