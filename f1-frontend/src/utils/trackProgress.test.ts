import { describe, expect, it } from 'vitest'
import type { Position } from '../types/position'
import { angleGapToLeader, trackAngle } from './trackProgress'

function position(x: number, z: number): Position {
  return {
    driverNumber: 1,
    sessionKey: 9161,
    x,
    y: 0,
    z,
    timestamp: '2023-01-01T00:00:00Z',
  }
}

describe('trackAngle', () => {
  it('returns atan2 of z and x', () => {
    expect(trackAngle(position(1, 0))).toBeCloseTo(0)
    expect(trackAngle(position(0, 1))).toBeCloseTo(Math.PI / 2)
    expect(trackAngle(position(-1, 0))).toBeCloseTo(Math.PI)
  })
})

describe('angleGapToLeader', () => {
  it('returns zero when driver matches leader angle', () => {
    expect(angleGapToLeader(1.5, 1.5)).toBeCloseTo(0)
  })

  it('returns positive gap for driver behind leader on the circle', () => {
    const leader = 0
    const behind = -Math.PI / 2
    expect(angleGapToLeader(behind, leader)).toBeCloseTo(Math.PI / 2)
  })

  it('wraps across the 2π boundary', () => {
    const leader = Math.PI / 4
    const behind = -Math.PI / 2
    const gap = angleGapToLeader(behind, leader)
    expect(gap).toBeGreaterThan(0)
    expect(gap).toBeLessThanOrEqual(Math.PI * 2)
  })
})
