import { create } from 'zustand'
import type { Position } from '../types/position'

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected'
export type CameraMode = 'orbit' | 'follow' | 'tv'

interface RaceState {
  sessionKey: string
  positions: Map<number, Position>
  connectionStatus: ConnectionStatus
  positionsFetchError: string | null
  selectedDriver: number | null
  cameraMode: CameraMode
  authToken: string | null
  authUsername: string | null
  trackAssetVersion: number
  replayMode: boolean
  setSessionKey: (key: string) => void
  updatePositions: (batch: Position[]) => void
  setPositions: (batch: Position[]) => void
  setReplayMode: (active: boolean) => void
  setSelectedDriver: (driver: number | null) => void
  setConnectionStatus: (status: ConnectionStatus) => void
  setPositionsFetchError: (error: string | null) => void
  setCameraMode: (mode: CameraMode) => void
  setAuthSession: (token: string | null, username: string | null) => void
  bumpTrackAssetVersion: () => void
  clearPositions: () => void
}

export const useRaceStore = create<RaceState>((set) => ({
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
  setSessionKey: (key) =>
    set({ sessionKey: key, positions: new Map(), positionsFetchError: null, replayMode: false }),
  updatePositions: (batch) =>
    set((state) => {
      const positions = new Map(state.positions)
      for (const position of batch) {
        positions.set(position.driverNumber, position)
      }
      return { positions }
    }),
  setPositions: (batch) =>
    set({ positions: new Map(batch.map((position) => [position.driverNumber, position])) }),
  setReplayMode: (active) => set({ replayMode: active }),
  setSelectedDriver: (driver) => set({ selectedDriver: driver }),
  setConnectionStatus: (status) => set({ connectionStatus: status }),
  setPositionsFetchError: (error) => set({ positionsFetchError: error }),
  setCameraMode: (mode) => set({ cameraMode: mode }),
  setAuthSession: (token, username) => set({ authToken: token, authUsername: username }),
  bumpTrackAssetVersion: () =>
    set((state) => ({ trackAssetVersion: state.trackAssetVersion + 1 })),
  clearPositions: () => set({ positions: new Map() }),
}))
