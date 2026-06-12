"""Validation helpers for generated track assets."""

from __future__ import annotations

import math

from track_mesh.centerline import CenterlinePoint, centerline_length, normalized_bounds_diagonal
from track_mesh.mesh import RibbonMesh
from track_mesh.normalize import NormalizedSample, SessionBounds


def print_validation_report(
    *,
    bounds: SessionBounds,
    samples: list[NormalizedSample],
    centerline: list[CenterlinePoint],
    mesh: RibbonMesh,
    reference_driver: int,
) -> None:
    raw_diagonal = _raw_bounds_diagonal(bounds)
    normalized_diagonal = normalized_bounds_diagonal(samples)
    length = centerline_length(centerline)
    normalized_ratio = length / normalized_diagonal if normalized_diagonal > 0 else 0.0

    print("Track mesh validation")
    print(f"  reference driver: {reference_driver}")
    print(f"  session bounds (raw OpenF1): {bounds.as_dict()}")
    print(f"  centerline points: {len(centerline)}")
    print(f"  centerline length (normalized xz): {length:.4f}")
    print(f"  normalized xz bbox diagonal: {normalized_diagonal:.4f}")
    print(f"  centerline / normalized diagonal: {normalized_ratio:.4f}")
    print(f"  raw xz bounds diagonal: {raw_diagonal:.4f}")
    print(f"  mesh vertices: {len(mesh.positions) // 3}")
    print(f"  mesh triangles: {len(mesh.indices) // 3}")
    print()
    print("Dev check: upload GLB to S3, open session in the app, and confirm cars sit on the strip in x/z.")


def _raw_bounds_diagonal(bounds: SessionBounds) -> float:
    dx = bounds.max_x - bounds.min_x
    dz = bounds.max_z - bounds.min_z
    return math.hypot(dx, dz)
