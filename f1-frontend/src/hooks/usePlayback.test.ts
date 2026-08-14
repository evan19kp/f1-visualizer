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
  it('does not let a newer failing tick suppress an in-flight successful poll', async () => {
    let resolveFirstPoll!: (value: Response) => void
    const firstPoll = new Promise<Response>((resolve) => {
      resolveFirstPoll = resolve
    })
    const fetchMock = vi
      .fn()
      .mockReturnValueOnce(firstPoll)
      .mockResolvedValueOnce(
        new Response(null, { status: 500 }),
      )
      .mockImplementation(() => new Promise<Response>(() => {}))
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => usePlayback('111'))
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await act(async () => {
      vi.advanceTimersByTime(1_000)
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await act(async () => {
      resolveFirstPoll(
        new Response(JSON.stringify(playbackState('first-poll')), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      await firstPoll
    })
    expect(result.current.playback?.current).toBe('first-poll')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(result.current.playback?.current).toBe('first-poll')
  })

  it('does not let a poll started before a control overwrite the control response', async () => {
    let resolvePoll!: (value: Response) => void
    const pollResponse = new Promise<Response>((resolve) => {
      resolvePoll = resolve
    })
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (method === 'GET' && url.includes('/api/sessions/111/playback')) {
        return pollResponse
      }
      if (method === 'POST' && url.includes('/api/sessions/111/playback/seek')) {
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('seek-result')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      return Promise.reject(new Error(`unexpected url: ${method} ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => usePlayback('111'))
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await act(async () => {
      await result.current.seek('2023-01-01T00:30:00Z')
    })
    expect(result.current.playback?.current).toBe('seek-result')

    await act(async () => {
      resolvePoll(
        new Response(JSON.stringify(playbackState('stale-poll')), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      await pollResponse
    })

    expect(result.current.playback?.current).toBe('seek-result')
  })

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

  it('ignores a late playback control response after the session key changes', async () => {
    let resolveSeek!: (value: Response) => void
    const seekResponse = new Promise<Response>((resolve) => {
      resolveSeek = resolve
    })

    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (method === 'POST' && url.includes('/api/sessions/111/playback/seek')) {
        return seekResponse
      }
      if (method === 'GET' && url.includes('/api/sessions/111/playback')) {
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('session-a')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      if (method === 'GET' && url.includes('/api/sessions/222/playback')) {
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('session-b')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      return Promise.reject(new Error(`unexpected url: ${method} ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { result, rerender } = renderHook(
      ({ sessionKey }: { sessionKey: string }) => usePlayback(sessionKey),
      { initialProps: { sessionKey: '111' } },
    )

    await waitFor(() => {
      expect(result.current.playback?.current).toBe('session-a')
    })

    let seekDone!: Promise<void>
    await act(async () => {
      seekDone = result.current.seek('2023-01-01T00:30:00Z')
    })

    rerender({ sessionKey: '222' })

    await waitFor(() => {
      expect(result.current.playback?.current).toBe('session-b')
    })

    await act(async () => {
      resolveSeek(
        new Response(JSON.stringify(playbackState('session-a-seek')), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      await seekDone
    })

    expect(result.current.playback?.current).toBe('session-b')
    expect(result.current.playback?.current).not.toBe('session-a-seek')
    expect(result.current.playbackError).toBeNull()
  })

  it('clears a prior session playback error when the session key changes', async () => {
    let rejectPause!: (reason?: unknown) => void
    const pauseResponse = new Promise<Response>((_resolve, reject) => {
      rejectPause = reject
    })

    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (method === 'POST' && url.includes('/api/sessions/111/playback/pause')) {
        return pauseResponse
      }
      if (method === 'GET' && url.includes('/api/sessions/111/playback')) {
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('session-a')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      if (method === 'GET' && url.includes('/api/sessions/222/playback')) {
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('session-b')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      return Promise.reject(new Error(`unexpected url: ${method} ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { result, rerender } = renderHook(
      ({ sessionKey }: { sessionKey: string }) => usePlayback(sessionKey),
      { initialProps: { sessionKey: '111' } },
    )

    await waitFor(() => {
      expect(result.current.playback?.current).toBe('session-a')
    })

    let pauseDone!: Promise<void>
    await act(async () => {
      pauseDone = result.current.pause()
    })

    await act(async () => {
      rejectPause(new Error('Playback request failed: 503'))
      await pauseDone
    })

    expect(result.current.playbackError).toBe('Playback request failed: 503')

    rerender({ sessionKey: '222' })

    await waitFor(() => {
      expect(result.current.playback?.current).toBe('session-b')
    })

    expect(result.current.playbackError).toBeNull()
  })
})
