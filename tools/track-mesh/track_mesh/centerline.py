"""Extract a closed centerline from normalized GPS samples."""

from __future__ import annotations

import math
from collections import defaultdict
from dataclasses import dataclass

from track_mesh.normalize import NormalizedSample


@dataclass(frozen=True)
class CenterlinePoint:
    x: float
    y: float
    z: float
    elevation_y: float


def choose_reference_driver(samples: list[NormalizedSample]) -> int:
    """Pick the driver with the most samples for lap-wise centerline extraction."""
    counts: dict[int, int] = defaultdict(int)
    for sample in samples:
        counts[sample.driver_number] += 1
    return max(counts, key=lambda driver: counts[driver])


def extract_centerline(
    samples: list[NormalizedSample],
    *,
    driver_number: int | None = None,
    point_count: int = 300,
    method: str = "polar_bin",
) -> list[CenterlinePoint]:
    """
    Build a closed loop in normalized space.

    method:
      - polar_bin (default): bin all samples by angle around the x/z centroid and
        average points per bin into a closed loop. Works for qualifying/practice
        where drivers do not close a single tidy lap loop.
      - arc_length (Option B): sort one driver's samples by timestamp, detect one
        lap, resample by arc length. Best for race sessions with full laps.
      - angle (Option A): sort by atan2(z, x) around centroid — matches
        TrackMesh.tsx buildCenterLineVertices for a driver snapshot.
    """
    if point_count <= 0:
        raise ValueError("point_count must be > 0")

    if method == "polar_bin":
        return _centerline_by_polar_bins(samples, point_count=point_count)
    if method == "angle":
        return _centerline_by_angle(samples, point_count=point_count)
    if method == "arc_length":
        driver = driver_number if driver_number is not None else choose_reference_driver(samples)
        driver_samples = [sample for sample in samples if sample.driver_number == driver]
        driver_samples.sort(key=lambda sample: sample.timestamp)
        if len(driver_samples) < 4:
            return _centerline_by_polar_bins(samples, point_count=point_count)
        lap = _extract_one_lap(driver_samples)
        if len(lap) >= len(driver_samples):
            return _centerline_by_polar_bins(samples, point_count=point_count)
        return _resample_closed_loop(lap, point_count=point_count)
    raise ValueError(f"Unknown centerline method: {method}")


def _centerline_by_polar_bins(
    samples: list[NormalizedSample], *, point_count: int
) -> list[CenterlinePoint]:
    if len(samples) < 2:
        raise ValueError("Need at least two normalized samples for centerline extraction")

    centroid_x = sum(sample.x for sample in samples) / len(samples)
    centroid_z = sum(sample.z for sample in samples) / len(samples)
    bin_count = max(point_count, 120)
    bins: dict[int, list[NormalizedSample]] = defaultdict(list)

    for sample in samples:
        angle = math.atan2(sample.z - centroid_z, sample.x - centroid_x)
        bin_index = int((angle + math.pi) / (2.0 * math.pi) * bin_count) % bin_count
        bins[bin_index].append(sample)

    ordered: list[CenterlinePoint] = []
    for index in sorted(bins):
        bucket = bins[index]
        mean_x = sum(item.x for item in bucket) / len(bucket)
        mean_z = sum(item.z for item in bucket) / len(bucket)
        mean_y = sum(item.y for item in bucket) / len(bucket)
        ordered.append(
            CenterlinePoint(
                x=mean_x,
                y=0.0,
                z=mean_z,
                elevation_y=mean_y,
            )
        )
    if len(ordered) < 3:
        return _centerline_by_angle(samples, point_count=point_count)
    return _resample_closed_loop(ordered, point_count=point_count)


def _centerline_by_angle(
    samples: list[NormalizedSample], *, point_count: int
) -> list[CenterlinePoint]:
    if len(samples) < 2:
        raise ValueError("Need at least two normalized samples for centerline extraction")

    centroid_x = sum(sample.x for sample in samples) / len(samples)
    centroid_z = sum(sample.z for sample in samples) / len(samples)

    sorted_samples = sorted(
        samples,
        key=lambda sample: math.atan2(sample.z - centroid_z, sample.x - centroid_x),
    )
    return _resample_closed_loop(
        [
            CenterlinePoint(
                x=sample.x,
                y=0.0,
                z=sample.z,
                elevation_y=sample.y,
            )
            for sample in sorted_samples
        ],
        point_count=point_count,
    )


def _extract_one_lap(samples: list[NormalizedSample]) -> list[CenterlinePoint]:
    points = [
        CenterlinePoint(x=sample.x, y=0.0, z=sample.z, elevation_y=sample.y)
        for sample in samples
    ]
    start = points[0]
    cumulative = 0.0
    min_lap_distance = 2.0
    closure_distance = 0.12

    lap_end_index = len(points) - 1
    for index in range(1, len(points)):
        cumulative += _distance_xz(points[index - 1], points[index])
        if cumulative < min_lap_distance:
            continue
        if _distance_xz(start, points[index]) <= closure_distance:
            lap_end_index = index
            break

    lap = points[: lap_end_index + 1]
    if len(lap) < 4:
        return points
    return lap


def _resample_closed_loop(points: list[CenterlinePoint], *, point_count: int) -> list[CenterlinePoint]:
    if len(points) < 2:
        raise ValueError("Need at least two centerline points")

    closed = points if _distance_xz(points[0], points[-1]) < 1e-6 else [*points, points[0]]
    segment_lengths: list[float] = []
    total_length = 0.0
    for index in range(len(closed) - 1):
        length = _distance_xz(closed[index], closed[index + 1])
        segment_lengths.append(length)
        total_length += length

    if total_length <= 1e-9:
        return [points[0]] * point_count

    targets = [(total_length * index) / point_count for index in range(point_count)]
    resampled: list[CenterlinePoint] = []
    segment_index = 0
    traversed = 0.0

    for target in targets:
        while segment_index < len(segment_lengths) and traversed + segment_lengths[segment_index] < target:
            traversed += segment_lengths[segment_index]
            segment_index += 1

        if segment_index >= len(segment_lengths):
            resampled.append(closed[-1])
            continue

        segment_length = segment_lengths[segment_index]
        if segment_length <= 1e-9:
            resampled.append(closed[segment_index])
            continue

        t = (target - traversed) / segment_length
        left = closed[segment_index]
        right = closed[segment_index + 1]
        resampled.append(
            CenterlinePoint(
                x=_lerp(left.x, right.x, t),
                y=0.0,
                z=_lerp(left.z, right.z, t),
                elevation_y=_lerp(left.elevation_y, right.elevation_y, t),
            )
        )

    if _distance_xz(resampled[0], resampled[-1]) > 1e-4:
        resampled[-1] = CenterlinePoint(
            x=resampled[0].x,
            y=resampled[0].y,
            z=resampled[0].z,
            elevation_y=resampled[0].elevation_y,
        )
    return resampled


def centerline_length(points: list[CenterlinePoint]) -> float:
    if len(points) < 2:
        return 0.0
    total = 0.0
    for index in range(len(points)):
        total += _distance_xz(points[index], points[(index + 1) % len(points)])
    return total


def normalized_bounds_diagonal(samples: list[NormalizedSample]) -> float:
    min_x = min(sample.x for sample in samples)
    max_x = max(sample.x for sample in samples)
    min_z = min(sample.z for sample in samples)
    max_z = max(sample.z for sample in samples)
    return math.hypot(max_x - min_x, max_z - min_z)


def _distance_xz(left: CenterlinePoint, right: CenterlinePoint) -> float:
    dx = right.x - left.x
    dz = right.z - left.z
    return math.hypot(dx, dz)


def _lerp(left: float, right: float, t: float) -> float:
    return left + (right - left) * t
