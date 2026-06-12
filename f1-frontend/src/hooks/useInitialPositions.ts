import { useEffect } from 'react'
import { API_URL } from '../config/session'
import { useRaceStore } from '../store/raceStore'
import type { Position } from '../types/position'

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

export function useInitialPositions(sessionKey: string): void {
  const updatePositions = useRaceStore((s) => s.updatePositions)
  const setPositionsFetchError = useRaceStore((s) => s.setPositionsFetchError)

  useEffect(() => {
    if (!sessionKey) {
      return
    }

    const controller = new AbortController()
    let active = true

    void (async () => {
      try {
        const response = await fetch(`${API_URL}/api/sessions/${sessionKey}/positions`, {
          signal: controller.signal,
        })
        if (!response.ok) {
          if (active) {
            setPositionsFetchError(
              `Could not load positions for session ${sessionKey} (HTTP ${response.status})`,
            )
          }
          return
        }

        const batch = (await response.json()) as Position[]
        if (!active) {
          return
        }

        setPositionsFetchError(null)
        if (batch.length > 0) {
          updatePositions(batch)
        }
      } catch (error) {
        if (!active || isAbortError(error)) {
          return
        }
        setPositionsFetchError(
          error instanceof Error ? error.message : 'Failed to load initial positions',
        )
      }
    })()

    return () => {
      active = false
      controller.abort()
    }
  }, [sessionKey, setPositionsFetchError, updatePositions])
}
