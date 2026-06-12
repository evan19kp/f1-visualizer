"""CLI entry point for track mesh generation."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from track_mesh.centerline import choose_reference_driver, extract_centerline
from track_mesh.mesh import build_ribbon_mesh, export_glb
from track_mesh.normalize import normalize_samples
from track_mesh.openf1_client import OpenF1Client
from track_mesh.validate import print_validation_report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Generate a flat track GLB from OpenF1 GPS samples."
    )
    parser.add_argument("--session-key", type=int, required=True)
    parser.add_argument(
        "--circuit-slug",
        help="Circuit slug for default output path (e.g. bahrain).",
    )
    parser.add_argument(
        "--out",
        type=Path,
        help="Output GLB path (default: tools/track-mesh/out/{circuit-slug}.glb).",
    )
    parser.add_argument(
        "--width",
        type=float,
        default=0.02,
        help="Ribbon half-width scale in normalized units (default: 0.02 total width).",
    )
    parser.add_argument(
        "--points",
        type=int,
        default=300,
        help="Resampled centerline point count (default: 300).",
    )
    parser.add_argument(
        "--driver",
        type=int,
        help="Reference driver number for centerline extraction (default: most samples).",
    )
    parser.add_argument(
        "--method",
        choices=("polar_bin", "arc_length", "angle"),
        default="polar_bin",
        help="Centerline algorithm (default: polar_bin).",
    )
    parser.add_argument(
        "--cache",
        type=Path,
        help="Optional raw JSON cache path (default: tools/track-mesh/cache/{session_key}.json).",
    )
    parser.add_argument(
        "--use-cache",
        action="store_true",
        help="Read raw OpenF1 samples from cache instead of fetching.",
    )
    parser.add_argument(
        "--sidecar",
        type=Path,
        help="Optional JSON sidecar with elevation samples for Blender follow-up.",
    )
    parser.add_argument(
        "--skip-validation",
        action="store_true",
        help="Skip printing validation summary.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    tool_root = Path(__file__).resolve().parents[1]
    circuit_slug = args.circuit_slug or str(args.session_key)
    output_path = args.out or tool_root / "out" / f"{circuit_slug}.glb"
    cache_path = args.cache or tool_root / "cache" / f"{args.session_key}.json"
    sidecar_path = args.sidecar or output_path.with_suffix(".elevation.json")

    client = OpenF1Client()
    raw_samples = client.fetch_locations(
        args.session_key,
        cache_path=cache_path,
        use_cache=args.use_cache,
    )
    if not raw_samples:
        parser.error(f"No OpenF1 location samples returned for session {args.session_key}")

    raw_tuples = [
        (sample.driver_number, sample.timestamp, sample.x, sample.y, sample.z)
        for sample in raw_samples
    ]
    normalized_samples, bounds = normalize_samples(raw_tuples)
    reference_driver = args.driver or choose_reference_driver(normalized_samples)

    centerline = extract_centerline(
        normalized_samples,
        driver_number=reference_driver,
        point_count=args.points,
        method=args.method,
    )
    mesh = build_ribbon_mesh(centerline, width=args.width)
    export_glb(mesh, output_path)

    sidecar_payload = {
        "sessionKey": args.session_key,
        "circuitSlug": circuit_slug,
        "referenceDriver": reference_driver,
        "bounds": bounds.as_dict(),
        "normalization": "CoordinateNormalizer.normalizeAxis -> [-1, 1]",
        "centerlineMethod": args.method,
        "points": [
            {
                "x": point.x,
                "z": point.z,
                "elevationY": point.elevation_y,
            }
            for point in centerline
        ],
    }
    sidecar_path.parent.mkdir(parents=True, exist_ok=True)
    sidecar_path.write_text(json.dumps(sidecar_payload, indent=2), encoding="utf-8")

    print(f"Wrote GLB: {output_path}")
    print(f"Wrote elevation sidecar: {sidecar_path}")

    if not args.skip_validation:
        print_validation_report(
            bounds=bounds,
            samples=normalized_samples,
            centerline=centerline,
            mesh=mesh,
            reference_driver=reference_driver,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
