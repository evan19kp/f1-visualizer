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
  autoBootstrap: boolean
  bootstrapStatus: 'idle' | 'running' | 'complete' | 'failed'
  historyReady: boolean
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
  playbackError: string | null
  clearPlaybackError: () => void
  play: (speed?: number) => Promise<void>
  pause: () => Promise<void>
  seek: (instant: string) => Promise<void>
} {
  const authToken = useRaceStore((s) => s.authToken)
  const setReplayMode = useRaceStore((s) => s.setReplayMode)
  const [playback, setPlayback] = useState<PlaybackState | null>(null)
  const [playbackError, setPlaybackError] = useState<string | null>(null)

  const refresh = useCallback(async (): Promise<void> => {
    if (!sessionKey) {
      setPlayback(null)
      return
    }
    try {
      const response = await fetch(`${API_URL}/api/sessions/${sessionKey}/playback`)
      if (response.ok) {
        setPlayback((await response.json()) as PlaybackState)
      } else {
        setPlayback(null)
      }
    } catch {
      setPlayback(null)
    }
  }, [sessionKey])

  useEffect(() => {
    void refresh()
    const interval = window.setInterval(() => void refresh(), 1000)
    return () => {
      window.clearInterval(interval)
      setReplayMode(false)
    }
  }, [refresh, setReplayMode])

  useEffect(() => {
    setReplayMode(false)
  }, [sessionKey, setReplayMode])

  const postPlayback = async (
    path: string,
    body?: Record<string, unknown>,
  ): Promise<PlaybackState | null> => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (import.meta.env.DEV) {
      await ensureDevAuth()
    }
    const token = useRaceStore.getState().authToken ?? authToken
    if (token) {
      headers.Authorization = `Bearer ${token}`
    } else if (!import.meta.env.DEV) {
      throw new Error('Login required for playback controls')
    }
    const response = await fetch(`${API_URL}/api/sessions/${sessionKey}/playback${path}`, {
      method: 'POST',
      headers,
      body: body ? JSON.stringify(body) : undefined,
    })
    if (!response.ok) {
      throw new Error(`Playback request failed: ${response.status}`)
    }
    const state = (await response.json()) as PlaybackState
    setPlayback(state)
    setPlaybackError(null)
    return state
  }

  const enableReplayMode = (): void => {
    setReplayMode(true)
  }

  const disableReplayMode = (): void => {
    setReplayMode(false)
  }

  const wrapControl = async (action: () => Promise<void>): Promise<void> => {
    try {
      await action()
    } catch (error) {
      setPlaybackError(error instanceof Error ? error.message : 'Playback control failed')
    }
  }

  const play = async (speed = 1): Promise<void> => {
    await wrapControl(async () => {
      enableReplayMode()
      await postPlayback('/play', { speed })
    })
  }

  const pause = async (): Promise<void> => {
    await wrapControl(async () => {
      await postPlayback('/pause')
      disableReplayMode()
    })
  }

  const seek = async (instant: string): Promise<void> => {
    await wrapControl(async () => {
      enableReplayMode()
      await postPlayback('/seek', { instant })
    })
  }

  const clearPlaybackError = (): void => setPlaybackError(null)

  return { playback, playbackError, clearPlaybackError, play, pause, seek }
}
