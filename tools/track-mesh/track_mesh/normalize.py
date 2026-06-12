"""Mirror f1-ingestion CoordinateNormalizer and SessionBounds."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Sequence


@dataclass(frozen=True)
class SessionBounds:
    min_x: float
    max_x: float
    min_y: float
    max_y: float
    min_z: float
    max_z: float

    @classmethod
    def empty(cls) -> SessionBounds:
        return cls(
            float("inf"),
            float("-inf"),
            float("inf"),
            float("-inf"),
            float("inf"),
            float("-inf"),
        )

    @property
    def initialized(self) -> bool:
        return self.min_x != float("inf")

    def expand(self, x: float, y: float, z: float) -> SessionBounds:
        if not self.initialized:
            return SessionBounds(x, x, y, y, z, z)
        return SessionBounds(
            min(self.min_x, x),
            max(self.max_x, x),
            min(self.min_y, y),
            max(self.max_y, y),
            min(self.min_z, z),
            max(self.max_z, z),
        )

    def expand_samples(self, samples: Iterable[tuple[float, float, float]]) -> SessionBounds:
        bounds = self
        for x, y, z in samples:
            bounds = bounds.expand(x, y, z)
        return bounds

    def as_dict(self) -> dict[str, float]:
        return {
            "minX": self.min_x,
            "maxX": self.max_x,
            "minY": self.min_y,
            "maxY": self.max_y,
            "minZ": self.min_z,
            "maxZ": self.max_z,
        }


@dataclass(frozen=True)
class NormalizedSample:
    driver_number: int
    timestamp: str
    x: float
    y: float
    z: float


def normalize_axis(value: float, minimum: float, maximum: float) -> float:
    """Same formula as CoordinateNormalizer.normalizeAxis."""
    if maximum == minimum:
        return 0.0
    normalized = 2.0 * (value - minimum) / (maximum - minimum) - 1.0
    return max(-1.0, min(1.0, normalized))


def normalize_samples(
    samples: Sequence[tuple[int, str, float, float, float]],
    bounds: SessionBounds | None = None,
) -> tuple[list[NormalizedSample], SessionBounds]:
    """
    Expand bounds across all raw samples, then normalize each axis to [-1, 1].
    samples: (driver_number, timestamp, raw_x, raw_y, raw_z)
    """
    updated_bounds = bounds if bounds and bounds.initialized else SessionBounds.empty()
    for _, _, x, y, z in samples:
        updated_bounds = updated_bounds.expand(x, y, z)

    normalized: list[NormalizedSample] = []
    for driver_number, timestamp, x, y, z in samples:
        normalized.append(
            NormalizedSample(
                driver_number=driver_number,
                timestamp=timestamp,
                x=normalize_axis(x, updated_bounds.min_x, updated_bounds.max_x),
                y=normalize_axis(y, updated_bounds.min_y, updated_bounds.max_y),
                z=normalize_axis(z, updated_bounds.min_z, updated_bounds.max_z),
            )
        )
    return normalized, updated_bounds
