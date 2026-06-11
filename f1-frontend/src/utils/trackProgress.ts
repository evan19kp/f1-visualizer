import type { Position } from '../types/position'

/** Client-side track progress proxy: angle around origin in the XZ plane. */
export function trackAngle(position: Position): number {
  return Math.atan2(position.z, position.x)
}

export function angleGapToLeader(angle: number, leaderAngle: number): number {
  let gap = leaderAngle - angle
  if (gap < 0) {
    gap += Math.PI * 2
  }
  return gap
}
