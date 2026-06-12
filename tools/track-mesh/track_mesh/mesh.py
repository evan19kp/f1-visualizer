"""Build a flat ribbon mesh and export binary GLB (stdlib only)."""

from __future__ import annotations

import json
import struct
from dataclasses import dataclass
from pathlib import Path

from track_mesh.centerline import CenterlinePoint


@dataclass(frozen=True)
class RibbonMesh:
    positions: list[float]
    normals: list[float]
    indices: list[int]


def build_ribbon_mesh(
    centerline: list[CenterlinePoint],
    *,
    width: float,
    flat_z: float = 0.0,
) -> RibbonMesh:
    """
    Extrude a flat strip in GLB space.

    TrackMesh applies rotation [-pi/2, 0, 0] and scale 50. Mapping:
      glb.x = normalized x
      glb.y = -normalized z
      glb.z = flat height (constant for V1)
    """
    if len(centerline) < 2:
        raise ValueError("Centerline must contain at least two points")

    half_width = width / 2.0
    positions: list[float] = []
    normals: list[float] = []
    indices: list[int] = []

    count = len(centerline)
    for index, point in enumerate(centerline):
        previous_point = centerline[(index - 1) % count]
        next_point = centerline[(index + 1) % count]
        tangent_x = next_point.x - previous_point.x
        tangent_y = -(next_point.z - previous_point.z)
        length = (tangent_x * tangent_x + tangent_y * tangent_y) ** 0.5
        if length <= 1e-9:
            tangent_x, tangent_y = 1.0, 0.0
        else:
            tangent_x /= length
            tangent_y /= length

        normal_x = -tangent_y
        normal_y = tangent_x

        left_x = point.x + normal_x * half_width
        left_y = -point.z + normal_y * half_width
        right_x = point.x - normal_x * half_width
        right_y = -point.z - normal_y * half_width

        positions.extend([left_x, left_y, flat_z, right_x, right_y, flat_z])
        normals.extend([0.0, 0.0, 1.0, 0.0, 0.0, 1.0])

    for index in range(count):
        next_index = (index + 1) % count
        left = index * 2
        right = index * 2 + 1
        next_left = next_index * 2
        next_right = next_index * 2 + 1
        indices.extend([left, next_left, right, right, next_left, next_right])

    return RibbonMesh(positions=positions, normals=normals, indices=indices)


def export_glb(mesh: RibbonMesh, output_path: Path) -> None:
    positions_bytes = struct.pack(f"<{len(mesh.positions)}f", *mesh.positions)
    normals_bytes = struct.pack(f"<{len(mesh.normals)}f", *mesh.normals)
    indices_bytes = struct.pack(f"<{len(mesh.indices)}H", *mesh.indices)

    buffer_bytes = positions_bytes + normals_bytes + indices_bytes
    positions_offset = 0
    normals_offset = len(positions_bytes)
    indices_offset = normals_offset + len(normals_bytes)
    vertex_count = len(mesh.positions) // 3
    index_count = len(mesh.indices)

    gltf = {
        "asset": {"version": "2.0", "generator": "f1-visualizer track-mesh"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0}],
        "meshes": [
            {
                "primitives": [
                    {
                        "attributes": {"POSITION": 0, "NORMAL": 1},
                        "indices": 2,
                        "mode": 4,
                    }
                ]
            }
        ],
        "accessors": [
            {
                "bufferView": 0,
                "componentType": 5126,
                "count": vertex_count,
                "type": "VEC3",
                "max": _vec3_max(mesh.positions),
                "min": _vec3_min(mesh.positions),
            },
            {
                "bufferView": 1,
                "componentType": 5126,
                "count": vertex_count,
                "type": "VEC3",
            },
            {
                "bufferView": 2,
                "componentType": 5123,
                "count": index_count,
                "type": "SCALAR",
            },
        ],
        "bufferViews": [
            {
                "buffer": 0,
                "byteOffset": positions_offset,
                "byteLength": len(positions_bytes),
                "target": 34962,
            },
            {
                "buffer": 0,
                "byteOffset": normals_offset,
                "byteLength": len(normals_bytes),
                "target": 34962,
            },
            {
                "buffer": 0,
                "byteOffset": indices_offset,
                "byteLength": len(indices_bytes),
                "target": 34963,
            },
        ],
        "buffers": [{"byteLength": len(buffer_bytes)}],
    }

    json_bytes = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    json_padding = (4 - (len(json_bytes) % 4)) % 4
    json_bytes += b" " * json_padding

    bin_padding = (4 - (len(buffer_bytes) % 4)) % 4
    buffer_bytes += b"\x00" * bin_padding

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("wb") as handle:
        handle.write(b"glTF")
        handle.write(struct.pack("<I", 2))
        handle.write(struct.pack("<I", 12 + 8 + len(json_bytes) + 8 + len(buffer_bytes)))
        handle.write(struct.pack("<I", len(json_bytes)))
        handle.write(b"JSON")
        handle.write(json_bytes)
        handle.write(struct.pack("<I", len(buffer_bytes)))
        handle.write(b"BIN\x00")
        handle.write(buffer_bytes)


def _vec3_min(positions: list[float]) -> list[float]:
    xs = positions[0::3]
    ys = positions[1::3]
    zs = positions[2::3]
    return [min(xs), min(ys), min(zs)]


def _vec3_max(positions: list[float]) -> list[float]:
    xs = positions[0::3]
    ys = positions[1::3]
    zs = positions[2::3]
    return [max(xs), max(ys), max(zs)]
