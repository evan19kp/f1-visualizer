import { useEffect, useRef, useState } from 'react'
import { API_URL } from '../config/session'
import { clearAuthIfUnauthorized, ensureDevAuth } from '../lib/auth'
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
  const [playbackSessionKey, setPlaybackSessionKey] = useState(sessionKey)
  const [playbackError, setPlaybackError] = useState<string | null>(null)
  const sessionKeyRef = useRef(sessionKey)
  sessionKeyRef.current = sessionKey

  useEffect(() => {
    if (!sessionKey) {
      return
    }

    const controller = new AbortController()
    let active = true

    const applyPlayback = (next: PlaybackState | null): void => {
      if (!active) {
        return
      }
      setPlayback(next)
      setPlaybackSessionKey(sessionKey)
    }

    const poll = async (): Promise<void> => {
      try {
        const response = await fetch(`${API_URL}/api/sessions/${sessionKey}/playback`, {
          signal: controller.signal,
        })
        if (!response.ok) {
          applyPlayback(null)
          return
        }
        const data = (await response.json()) as PlaybackState
        applyPlayback(data)
      } catch (error) {
        if (active && !(error instanceof DOMException && error.name === 'AbortError')) {
          applyPlayback(null)
        }
      }
    }

    void poll()
    const interval = window.setInterval(() => void poll(), 1000)
    return () => {
      active = false
      controller.abort()
      window.clearInterval(interval)
      setReplayMode(false)
    }
  }, [sessionKey, setReplayMode])

  useEffect(() => {
    setReplayMode(false)
  }, [sessionKey, setReplayMode])

  const postPlayback = async (
    path: string,
    body?: Record<string, unknown>,
  ): Promise<PlaybackState | null> => {
    const requestedKey = sessionKey
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
    const response = await fetch(`${API_URL}/api/sessions/${requestedKey}/playback${path}`, {
      method: 'POST',
      headers,
      body: body ? JSON.stringify(body) : undefined,
    })
    if (!response.ok) {
      clearAuthIfUnauthorized(response.status)
      throw new Error(`Playback request failed: ${response.status}`)
    }
    const state = (await response.json()) as PlaybackState
    // Session may have changed while the control request was in flight — do not clobber.
    if (sessionKeyRef.current !== requestedKey) {
      return state
    }
    setPlayback(state)
    setPlaybackSessionKey(requestedKey)
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
    const requestedKey = sessionKey
    try {
      await action()
    } catch (error) {
      if (sessionKeyRef.current !== requestedKey) {
        return
      }
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
    const requestedKey = sessionKey
    await wrapControl(async () => {
      await postPlayback('/pause')
      if (sessionKeyRef.current === requestedKey) {
        disableReplayMode()
      }
    })
  }

  const seek = async (instant: string): Promise<void> => {
    await wrapControl(async () => {
      enableReplayMode()
      await postPlayback('/seek', { instant })
    })
  }

  const clearPlaybackError = (): void => setPlaybackError(null)

  // Only expose playback that belongs to the active session (avoids setState-in-effect clears
  // and prevents a prior session's poll/control result from flashing under a new key).
  const activePlayback = sessionKey && playbackSessionKey === sessionKey ? playback : null

  return { playback: activePlayback, playbackError, clearPlaybackError, play, pause, seek }
}
