import { describe, expect, it } from 'vitest'
import { angleGapToLeader, trackAngle } from './trackProgress'
import type { Position } from '../types/position'

function position(x: number, z: number): Position {
  return {
    driverNumber: 1,
    sessionKey: 9161,
    timestamp: '2023-09-16T13:00:00Z',
    x,
    y: 0,
    z,
  }
}

describe('track progress', () => {
  it('derives the angle from the position in the XZ plane', () => {
    expect(trackAngle(position(0, 1))).toBeCloseTo(Math.PI / 2)
    expect(trackAngle(position(-1, 0))).toBeCloseTo(Math.PI)
  })

  it('measures a forward gap across the angle wrap boundary', () => {
    const leaderAngle = -Math.PI + 0.1
    const trailingAngle = Math.PI - 0.1

    expect(angleGapToLeader(trailingAngle, leaderAngle)).toBeCloseTo(0.2)
  })

  it('returns zero for the leader', () => {
    expect(angleGapToLeader(1.25, 1.25)).toBe(0)
  })
})
