import { beforeEach, describe, expect, it } from 'vitest'
import { useRaceStore } from './raceStore'
import type { Position } from '../types/position'

function samplePosition(driverNumber: number, x = 0): Position {
  return {
    driverNumber,
    sessionKey: 9161,
    x,
    y: 0,
    z: 0,
    timestamp: '2023-01-01T00:00:00Z',
  }
}

describe('useRaceStore', () => {
  beforeEach(() => {
    useRaceStore.setState({
      sessionKey: '',
      positions: new Map(),
      connectionStatus: 'disconnected',
      positionsFetchError: null,
      selectedDriver: null,
      cameraMode: 'orbit',
      authToken: null,
      authUsername: null,
      trackAssetVersion: 0,
      replayMode: false,
    })
  })

  it('merges position updates by driver number', () => {
    const { updatePositions } = useRaceStore.getState()
    updatePositions([samplePosition(1, 1), samplePosition(2, 2)])
    updatePositions([samplePosition(1, 5)])

    const positions = useRaceStore.getState().positions
    expect(positions.size).toBe(2)
    expect(positions.get(1)?.x).toBe(5)
    expect(positions.get(2)?.x).toBe(2)
  })

  it('replaces all positions on setPositions', () => {
    const { updatePositions, setPositions } = useRaceStore.getState()
    updatePositions([samplePosition(1), samplePosition(2)])
    setPositions([samplePosition(3, 9)])

    const positions = useRaceStore.getState().positions
    expect(positions.size).toBe(1)
    expect(positions.get(3)?.x).toBe(9)
  })

  it('clears positions and replay mode when session changes', () => {
    const { setSessionKey, updatePositions, setReplayMode } = useRaceStore.getState()
    setSessionKey('9161')
    updatePositions([samplePosition(1)])
    setReplayMode(true)

    useRaceStore.getState().setSessionKey('9999')

    const state = useRaceStore.getState()
    expect(state.sessionKey).toBe('9999')
    expect(state.positions.size).toBe(0)
    expect(state.replayMode).toBe(false)
  })

  it('stores auth session fields together', () => {
    useRaceStore.getState().setAuthSession('token-abc', 'admin')
    const state = useRaceStore.getState()
    expect(state.authToken).toBe('token-abc')
    expect(state.authUsername).toBe('admin')
  })
})
