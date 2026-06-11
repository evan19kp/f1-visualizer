import { Vector3 } from 'three'
import { ELEVATION_SCALE, TRACK_SCALE } from '../config/scene'
import type { Position } from '../types/position'

export function positionToVector3(position: Position): Vector3 {
  return new Vector3(
    position.x * TRACK_SCALE,
    position.y * ELEVATION_SCALE,
    position.z * TRACK_SCALE,
  )
}
