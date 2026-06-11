import { create } from 'zustand'
import type { Position } from '../types/position'

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected'
export type CameraMode = 'orbit' | 'follow' | 'tv'

interface RaceState {
  sessionKey: string
  positions: Map<number, Position>
  connectionStatus: ConnectionStatus
  selectedDriver: number | null
  cameraMode: CameraMode
  authToken: string | null
  setSessionKey: (key: string) => void
  updatePositions: (batch: Position[]) => void
  setSelectedDriver: (driver: number | null) => void
  setConnectionStatus: (status: ConnectionStatus) => void
  setCameraMode: (mode: CameraMode) => void
  setAuthToken: (token: string | null) => void
}

export const useRaceStore = create<RaceState>((set) => ({
  sessionKey: '',
  positions: new Map(),
  connectionStatus: 'disconnected',
  selectedDriver: null,
  cameraMode: 'orbit',
  authToken: null,
  setSessionKey: (key) => set({ sessionKey: key, positions: new Map() }),
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
  setCameraMode: (mode) => set({ cameraMode: mode }),
  setAuthToken: (token) => set({ authToken: token }),
}))
