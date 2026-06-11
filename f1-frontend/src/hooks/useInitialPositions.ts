import { useEffect } from 'react'
import { API_URL } from '../config/session'
import { useRaceStore } from '../store/raceStore'
import type { Position } from '../types/position'

export function useInitialPositions(sessionKey: string): void {
  const updatePositions = useRaceStore((s) => s.updatePositions)

  useEffect(() => {
    if (!sessionKey) {
      return
    }

    const controller = new AbortController()

    void (async () => {
      try {
        const response = await fetch(`${API_URL}/api/sessions/${sessionKey}/positions`, {
          signal: controller.signal,
        })
        const batch: Position[] = response.ok ? await response.json() : []
        if (batch.length > 0) {
          updatePositions(batch)
        }
      } catch {
        /* REST bootstrap is best-effort; STOMP will stream live updates */
      }
    })()

    return () => controller.abort()
  }, [sessionKey, updatePositions])
}
