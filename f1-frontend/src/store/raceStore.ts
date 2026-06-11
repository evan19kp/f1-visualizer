import { create } from 'zustand'
import type { Position } from '../types/position'

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected'

interface RaceState {
  sessionKey: string
  positions: Map<number, Position>
  connectionStatus: ConnectionStatus
  selectedDriver: number | null
  setSessionKey: (key: string) => void
  updatePositions: (batch: Position[]) => void
  setSelectedDriver: (driver: number | null) => void
  setConnectionStatus: (status: ConnectionStatus) => void
}

export const useRaceStore = create<RaceState>((set) => ({
  sessionKey: '',
  positions: new Map(),
  connectionStatus: 'disconnected',
  selectedDriver: null,
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
}))
