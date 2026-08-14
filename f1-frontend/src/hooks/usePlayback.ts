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

const PLAYBACK_REQUEST_TIMEOUT_MS = 8_000

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
  const [playbackErrorSessionKey, setPlaybackErrorSessionKey] = useState(sessionKey)
  // Latest session key for in-flight control responses. Synced in an effect so we
  // do not write refs during render (react-hooks/refs).
  const sessionKeyRef = useRef(sessionKey)
  const controlGenerationRef = useRef(0)
  const pendingControlRef = useRef<number | null>(null)
  const activePollControllerRef = useRef<AbortController | null>(null)
  const activeControlControllerRef = useRef<AbortController | null>(null)

  useEffect(() => {
    sessionKeyRef.current = sessionKey
    controlGenerationRef.current += 1
    pendingControlRef.current = null
    return () => {
      activePollControllerRef.current?.abort()
      activeControlControllerRef.current?.abort()
    }
  }, [sessionKey])

  useEffect(() => {
    if (!sessionKey) {
      return
    }

    let active = true
    let pollInFlight = false

    const poll = async (): Promise<void> => {
      if (pollInFlight || pendingControlRef.current !== null) {
        return
      }

      pollInFlight = true
      const controlGeneration = controlGenerationRef.current
      const controller = new AbortController()
      activePollControllerRef.current = controller
      const requestTimeout = window.setTimeout(
        () => controller.abort(),
        PLAYBACK_REQUEST_TIMEOUT_MS,
      )
      try {
        const response = await fetch(`${API_URL}/api/sessions/${sessionKey}/playback`, {
          signal: controller.signal,
        })
        if (!active || controlGeneration !== controlGenerationRef.current) {
          return
        }
        if (!response.ok) {
          setPlayback(null)
          setPlaybackSessionKey(sessionKey)
          return
        }
        const data = (await response.json()) as PlaybackState
        if (!active || controlGeneration !== controlGenerationRef.current) {
          return
        }
        setPlayback(data)
        setPlaybackSessionKey(sessionKey)
      } catch (error) {
        if (
          active &&
          controlGeneration === controlGenerationRef.current &&
          !(error instanceof DOMException && error.name === 'AbortError')
        ) {
          setPlayback(null)
          setPlaybackSessionKey(sessionKey)
        }
      } finally {
        window.clearTimeout(requestTimeout)
        if (activePollControllerRef.current === controller) {
          activePollControllerRef.current = null
        }
        pollInFlight = false
      }
    }

    void poll()
    const interval = window.setInterval(() => void poll(), 1000)
    return () => {
      active = false
      activePollControllerRef.current?.abort()
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
    const controlId = ++controlGenerationRef.current
    pendingControlRef.current = controlId
    activePollControllerRef.current?.abort()
    activeControlControllerRef.current?.abort()
    const controller = new AbortController()
    activeControlControllerRef.current = controller
    const requestTimeout = window.setTimeout(
      () => controller.abort(),
      PLAYBACK_REQUEST_TIMEOUT_MS,
    )
    try {
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
        signal: controller.signal,
      })
      if (!response.ok) {
        clearAuthIfUnauthorized(response.status)
        throw new Error(`Playback request failed: ${response.status}`)
      }
      const state = (await response.json()) as PlaybackState
      // Session or a newer control may have changed while this request was in flight.
      if (
        sessionKeyRef.current !== requestedKey ||
        controlGenerationRef.current !== controlId
      ) {
        return state
      }
      setPlayback(state)
      setPlaybackSessionKey(requestedKey)
      setPlaybackError(null)
      setPlaybackErrorSessionKey(requestedKey)
      return state
    } finally {
      window.clearTimeout(requestTimeout)
      if (activeControlControllerRef.current === controller) {
        activeControlControllerRef.current = null
      }
      if (pendingControlRef.current === controlId) {
        pendingControlRef.current = null
      }
    }
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
      setPlaybackErrorSessionKey(requestedKey)
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

  // Only expose playback/error that belong to the active session (avoids setState-in-effect clears
  // and prevents a prior session's poll/control result from flashing under a new key).
  const activePlayback = sessionKey && playbackSessionKey === sessionKey ? playback : null
  const activePlaybackError =
    sessionKey && playbackErrorSessionKey === sessionKey ? playbackError : null

  return {
    playback: activePlayback,
    playbackError: activePlaybackError,
    clearPlaybackError,
    play,
    pause,
    seek,
  }
}
