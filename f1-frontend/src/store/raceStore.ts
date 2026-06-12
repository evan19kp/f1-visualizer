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
  setSessionKey: (key: string) => void
  updatePositions: (batch: Position[]) => void
  setSelectedDriver: (driver: number | null) => void
  setConnectionStatus: (status: ConnectionStatus) => void
  setPositionsFetchError: (error: string | null) => void
  setCameraMode: (mode: CameraMode) => void
  setAuthToken: (token: string | null) => void
}

export const useRaceStore = create<RaceState>((set) => ({
  sessionKey: '',
  positions: new Map(),
  connectionStatus: 'disconnected',
  positionsFetchError: null,
  selectedDriver: null,
  cameraMode: 'orbit',
  authToken: null,
  setSessionKey: (key) =>
    set({ sessionKey: key, positions: new Map(), positionsFetchError: null }),
  updatePositions: (batch) =>
    set((state) => {
      const positions = new Map(state.positions)
      for (const position of batch) {
        positions.set(position.driverNumber, position)
      }
      return { positions }
    }),
  setSelectedDriver: (driver) => set({ selectedDriver: driver }),
  setConnectionStatus: (status) => set({ connectionStatus: status }),
  setPositionsFetchError: (error) => set({ positionsFetchError: error }),
  setCameraMode: (mode) => set({ cameraMode: mode }),
  setAuthToken: (token) => set({ authToken: token }),
}))
