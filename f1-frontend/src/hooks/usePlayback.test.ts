import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useRaceStore } from '../store/raceStore'
import { usePlayback } from './usePlayback'

function playbackState(current: string) {
  return {
    start: '2023-01-01T00:00:00Z',
    end: '2023-01-01T01:00:00Z',
    current,
    state: 'PAUSED' as const,
    speed: 1,
    historyLoaded: true,
  }
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  useRaceStore.setState({ replayMode: false, authToken: null })
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('usePlayback', () => {
  it('does not expose the previous session playback after the session key changes', async () => {
    let resolveSessionA!: (value: Response) => void
    const sessionAResponse = new Promise<Response>((resolve) => {
      resolveSessionA = resolve
    })

    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/api/sessions/111/playback')) {
        return sessionAResponse
      }
      if (url.includes('/api/sessions/222/playback')) {
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('session-b')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      return Promise.reject(new Error(`unexpected url: ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { result, rerender } = renderHook(
      ({ sessionKey }: { sessionKey: string }) => usePlayback(sessionKey),
      { initialProps: { sessionKey: '111' } },
    )

    // Switch sessions before session A's in-flight response settles.
    rerender({ sessionKey: '222' })

    await act(async () => {
      resolveSessionA(
        new Response(JSON.stringify(playbackState('session-a')), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
    })

    await waitFor(() => {
      expect(result.current.playback?.current).toBe('session-b')
    })

    // Late session-A JSON must not overwrite or flash under session B.
    expect(result.current.playback?.current).toBe('session-b')
    expect(result.current.playback?.current).not.toBe('session-a')
  })

  it('returns null playback while a new session has not loaded yet', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/api/sessions/111/playback')) {
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('session-a')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      if (url.includes('/api/sessions/222/playback')) {
        return new Promise<Response>(() => {
          /* hang until unmount/switch */
        })
      }
      return Promise.reject(new Error(`unexpected url: ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { result, rerender } = renderHook(
      ({ sessionKey }: { sessionKey: string }) => usePlayback(sessionKey),
      { initialProps: { sessionKey: '111' } },
    )

    await waitFor(() => {
      expect(result.current.playback?.current).toBe('session-a')
    })

    rerender({ sessionKey: '222' })

    expect(result.current.playback).toBeNull()
  })
})
