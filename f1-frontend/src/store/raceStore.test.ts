import { beforeEach, describe, expect, it } from 'vitest'
import { useRaceStore } from './raceStore'
import type { Position } from '../types/position'

function position(driverNumber: number, x: number): Position {
  return {
    driverNumber,
    sessionKey: 9161,
    timestamp: '2023-09-16T13:00:00Z',
    x,
    y: 0,
    z: 0,
  }
}

beforeEach(() => {
  useRaceStore.setState({
    sessionKey: '',
    positions: new Map(),
    positionsFetchError: null,
    replayMode: false,
    selectedDriver: null,
  })
})

describe('race store', () => {
  it('merges live position batches by driver without dropping other drivers', () => {
    const state = useRaceStore.getState()
    state.setPositions([position(1, 10), position(4, 40)])
    state.updatePositions([position(1, 11)])

    const positions = useRaceStore.getState().positions
    expect([...positions.keys()]).toEqual([1, 4])
    expect(positions.get(1)?.x).toBe(11)
    expect(positions.get(4)?.x).toBe(40)
  })

  it('clears session-specific state when the session changes', () => {
    useRaceStore.setState({
      positions: new Map([[1, position(1, 10)]]),
      positionsFetchError: 'request failed',
      replayMode: true,
    })

    useRaceStore.getState().setSessionKey('9999')

    const state = useRaceStore.getState()
    expect(state.sessionKey).toBe('9999')
    expect(state.positions.size).toBe(0)
    expect(state.positionsFetchError).toBeNull()
    expect(state.replayMode).toBe(false)
  })
})
