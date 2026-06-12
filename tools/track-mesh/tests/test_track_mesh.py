"""Tests for track mesh normalization and mesh generation."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from track_mesh.centerline import extract_centerline
from track_mesh.mesh import build_ribbon_mesh, export_glb
from track_mesh.normalize import NormalizedSample, normalize_axis, normalize_samples


class NormalizeTests(unittest.TestCase):
    def test_single_point_maps_to_origin(self) -> None:
        samples = [(44, "2024-03-02T15:00:00Z", 10.0, 20.0, 30.0)]
        normalized, bounds = normalize_samples(samples)
        self.assertEqual(0.0, normalized[0].x)
        self.assertEqual(0.0, normalized[0].y)
        self.assertEqual(0.0, normalized[0].z)
        self.assertTrue(bounds.initialized)

    def test_midpoint_maps_to_zero(self) -> None:
        samples = [
            (1, "t1", 0.0, 0.0, 0.0),
            (2, "t2", 10.0, 10.0, 10.0),
            (3, "t3", 5.0, 5.0, 5.0),
        ]
        normalized, _ = normalize_samples(samples)
        mid = normalized[2]
        self.assertEqual(0.0, mid.x)
        self.assertEqual(0.0, mid.y)
        self.assertEqual(0.0, mid.z)

    def test_flat_axis_returns_zero(self) -> None:
        self.assertEqual(0.0, normalize_axis(5.0, 5.0, 5.0))

    def test_clamps_to_unit_range(self) -> None:
        self.assertEqual(1.0, normalize_axis(20.0, 0.0, 10.0))
        self.assertEqual(-1.0, normalize_axis(-5.0, 0.0, 10.0))


class MeshTests(unittest.TestCase):
    def test_extract_centerline_rejects_non_positive_point_count(self) -> None:
        samples = [
            NormalizedSample(1, "t1", -1.0, 0.0, -1.0),
            NormalizedSample(1, "t2", 1.0, 0.0, 1.0),
        ]
        with self.assertRaisesRegex(ValueError, "point_count must be > 0"):
            extract_centerline(samples, point_count=0)

    def test_export_glb_writes_binary(self) -> None:
        samples = [
            NormalizedSample(1, "t1", -1.0, 0.0, -1.0),
            NormalizedSample(1, "t2", 1.0, 0.0, -1.0),
            NormalizedSample(1, "t3", 1.0, 0.0, 1.0),
            NormalizedSample(1, "t4", -1.0, 0.0, 1.0),
        ]
        centerline = extract_centerline(samples, driver_number=1, point_count=8, method="angle")
        mesh = build_ribbon_mesh(centerline, width=0.1)
        with tempfile.TemporaryDirectory() as temp_dir:
            output_path = Path(temp_dir) / "test.glb"
            export_glb(mesh, output_path)
            payload = output_path.read_bytes()
            self.assertTrue(payload.startswith(b"glTF"))
            self.assertGreater(len(payload), 100)


if __name__ == "__main__":
    unittest.main()
