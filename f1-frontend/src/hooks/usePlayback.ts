import { useCallback, useEffect, useState } from 'react'
import { API_URL } from '../config/session'
import { ensureDevAuth } from '../lib/auth'
import { useRaceStore } from '../store/raceStore'

export interface IngestionStatus {
  enabled: boolean
  configuredSessionKey: string
  resolvedSessionKey: number
  lastPollAt: string | null
  lastError: string
}

export function useIngestionStatus(): IngestionStatus | null {
  const [status, setStatus] = useState<IngestionStatus | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    const poll = async (): Promise<void> => {
      try {
        const response = await fetch(`${API_URL}/api/ingestion/status`, {
          signal: controller.signal,
        })
        if (!response.ok || !active) {
          return
        }
        const data = (await response.json()) as IngestionStatus
        setStatus(data)
      } catch (error) {
        if (active && !(error instanceof DOMException && error.name === 'AbortError')) {
          setStatus(null)
        }
      }
    }

    void poll()
    const interval = window.setInterval(() => void poll(), 5000)

    return () => {
      active = false
      controller.abort()
      window.clearInterval(interval)
    }
  }, [])

  return status
}

export interface PlaybackState {
  start: string
  end: string
  current: string
  state: 'STOPPED' | 'PLAYING' | 'PAUSED'
  speed: number
  historyLoaded: boolean
}

export function usePlayback(sessionKey: string): {
  playback: PlaybackState | null
  play: (speed?: number) => Promise<void>
  pause: () => Promise<void>
  seek: (instant: string) => Promise<void>
} {
  const authToken = useRaceStore((s) => s.authToken)
  const [playback, setPlayback] = useState<PlaybackState | null>(null)

  const refresh = useCallback(async (): Promise<void> => {
    if (!sessionKey) {
      return
    }
    try {
      const response = await fetch(`${API_URL}/api/sessions/${sessionKey}/playback`)
      if (response.ok) {
        setPlayback((await response.json()) as PlaybackState)
      }
    } catch {
      setPlayback(null)
    }
  }, [sessionKey])

  useEffect(() => {
    void refresh()
    const interval = window.setInterval(() => void refresh(), 1000)
    return () => window.clearInterval(interval)
  }, [refresh])

  const authorizedPost = async (
    path: string,
    body?: Record<string, unknown>,
  ): Promise<PlaybackState | null> => {
    await ensureDevAuth()
    const token = useRaceStore.getState().authToken ?? authToken
    if (!token) {
      throw new Error('Dev auth required for playback controls')
    }
    const response = await fetch(`${API_URL}/api/sessions/${sessionKey}/playback${path}`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: body ? JSON.stringify(body) : undefined,
    })
    if (!response.ok) {
      throw new Error(`Playback request failed: ${response.status}`)
    }
    const state = (await response.json()) as PlaybackState
    setPlayback(state)
    return state
  }

  const play = async (speed = 1): Promise<void> => {
    await authorizedPost('/play', { speed })
  }

  const pause = async (): Promise<void> => {
    await authorizedPost('/pause')
  }

  const seek = async (instant: string): Promise<void> => {
    await authorizedPost('/seek', { instant })
  }

  return { playback, play, pause, seek }
}
