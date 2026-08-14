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

  it('does not start a poll while a playback control is in flight', async () => {
    let resolveSeek!: (value: Response) => void
    let resolveMidControlPoll!: (value: Response) => void
    const seekResponse = new Promise<Response>((resolve) => {
      resolveSeek = resolve
    })
    const midControlPoll = new Promise<Response>((resolve) => {
      resolveMidControlPoll = resolve
    })
    let getCount = 0
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (method === 'GET' && url.includes('/api/sessions/111/playback')) {
        getCount += 1
        if (getCount === 1) {
          return Promise.resolve(
            new Response(JSON.stringify(playbackState('initial-poll')), {
              status: 200,
              headers: { 'Content-Type': 'application/json' },
            }),
          )
        }
        return midControlPoll
      }
      if (method === 'POST' && url.includes('/api/sessions/111/playback/seek')) {
        return seekResponse
      }
      return Promise.reject(new Error(`unexpected url: ${method} ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => usePlayback('111'))
    await waitFor(() => {
      expect(result.current.playback?.current).toBe('initial-poll')
    })

    let seekDone!: Promise<void>
    await act(async () => {
      seekDone = result.current.seek('2023-01-01T00:30:00Z')
      await Promise.resolve()
    })

    await act(async () => {
      vi.advanceTimersByTime(1_000)
    })
    expect(getCount).toBe(1)

    await act(async () => {
      resolveSeek(
        new Response(JSON.stringify(playbackState('seek-result')), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      await seekDone
    })

    await act(async () => {
      resolveMidControlPoll(
        new Response(JSON.stringify(playbackState('stale-poll')), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      await midControlPoll
      await Promise.resolve()
    })

    expect(result.current.playback?.current).toBe('seek-result')
  })

  it('aborts an in-flight poll when a control starts and resumes polling', async () => {
    let getCount = 0
    let firstPollAborted = false
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (method === 'GET' && url.includes('/api/sessions/111/playback')) {
        getCount += 1
        if (getCount === 1) {
          return new Promise<Response>((_resolve, reject) => {
            init?.signal?.addEventListener('abort', () => {
              firstPollAborted = true
              reject(new DOMException('Aborted', 'AbortError'))
            })
          })
        }
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('resumed-poll')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
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
    expect(getCount).toBe(1)

    await act(async () => {
      await result.current.seek('2023-01-01T00:30:00Z')
    })
    expect(firstPollAborted).toBe(true)
    expect(result.current.playback?.current).toBe('seek-result')

    await act(async () => {
      vi.advanceTimersByTime(1_000)
    })

    expect(getCount).toBe(2)
    expect(result.current.playback?.current).toBe('resumed-poll')
  })

  it('times out a stalled poll and starts a later poll', async () => {
    let getCount = 0
    const fetchMock = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      getCount += 1
      if (getCount === 1) {
        return new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => {
            reject(new DOMException('Aborted', 'AbortError'))
          })
        })
      }
      return Promise.resolve(
        new Response(JSON.stringify(playbackState('recovered-poll')), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
    })
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => usePlayback('111'))
    expect(getCount).toBe(1)

    await act(async () => {
      vi.advanceTimersByTime(8_000)
      await Promise.resolve()
    })
    expect(getCount).toBe(1)

    await act(async () => {
      vi.advanceTimersByTime(1_000)
    })

    expect(getCount).toBe(2)
    expect(result.current.playback?.current).toBe('recovered-poll')
  })

  it('times out a stalled control and resumes polling', async () => {
    let getCount = 0
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (method === 'GET' && url.includes('/api/sessions/111/playback')) {
        getCount += 1
        return Promise.resolve(
          new Response(JSON.stringify(playbackState(getCount === 1 ? 'initial-poll' : 'recovered-poll')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      if (method === 'POST' && url.includes('/api/sessions/111/playback/seek')) {
        return new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => {
            reject(new DOMException('Aborted', 'AbortError'))
          })
        })
      }
      return Promise.reject(new Error(`unexpected url: ${method} ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => usePlayback('111'))
    await waitFor(() => {
      expect(result.current.playback?.current).toBe('initial-poll')
    })

    let seekDone!: Promise<void>
    await act(async () => {
      seekDone = result.current.seek('2023-01-01T00:30:00Z')
      await Promise.resolve()
    })

    await act(async () => {
      vi.advanceTimersByTime(8_000)
      await seekDone
    })
    expect(getCount).toBe(1)
    expect(result.current.playbackError).toBe('Playback request timed out')

    await act(async () => {
      vi.advanceTimersByTime(1_000)
    })

    expect(getCount).toBe(2)
    expect(result.current.playback?.current).toBe('recovered-poll')
  })

  it('ignores a delayed abort from a superseded control', async () => {
    let rejectFirstSeek!: (reason?: unknown) => void
    let firstSeekAborted = false
    let postCount = 0
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (method === 'GET' && url.includes('/api/sessions/111/playback')) {
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('initial-poll')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      if (method === 'POST' && url.includes('/api/sessions/111/playback/seek')) {
        postCount += 1
        if (postCount === 1) {
          return new Promise<Response>((_resolve, reject) => {
            rejectFirstSeek = reject
            init?.signal?.addEventListener('abort', () => {
              firstSeekAborted = true
            })
          })
        }
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('seek-2')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      return Promise.reject(new Error(`unexpected url: ${method} ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => usePlayback('111'))
    await waitFor(() => {
      expect(result.current.playback?.current).toBe('initial-poll')
    })

    let firstSeekDone!: Promise<void>
    await act(async () => {
      firstSeekDone = result.current.seek('2023-01-01T00:10:00Z')
      await Promise.resolve()
    })

    await act(async () => {
      await result.current.seek('2023-01-01T00:20:00Z')
    })
    expect(firstSeekAborted).toBe(true)
    expect(result.current.playback?.current).toBe('seek-2')
    expect(result.current.playbackError).toBeNull()

    await act(async () => {
      rejectFirstSeek(new DOMException('Aborted', 'AbortError'))
      await firstSeekDone
    })

    expect(result.current.playback?.current).toBe('seek-2')
    expect(result.current.playbackError).toBeNull()
  })

  it('does not let a superseded pause disable replay mode after play succeeds', async () => {
    let resolvePause!: (value: Response) => void
    let pauseAborted = false
    const pauseResponse = new Promise<Response>((resolve) => {
      resolvePause = resolve
    })
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (method === 'GET' && url.includes('/api/sessions/111/playback')) {
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('initial-poll')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      if (method === 'POST' && url.includes('/api/sessions/111/playback/pause')) {
        init?.signal?.addEventListener('abort', () => {
          pauseAborted = true
        })
        return pauseResponse
      }
      if (method === 'POST' && url.includes('/api/sessions/111/playback/play')) {
        return Promise.resolve(
          new Response(JSON.stringify(playbackState('play-result')), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      return Promise.reject(new Error(`unexpected url: ${method} ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => usePlayback('111'))
    await waitFor(() => {
      expect(result.current.playback?.current).toBe('initial-poll')
    })

    let pauseDone!: Promise<void>
    await act(async () => {
      pauseDone = result.current.pause()
      await Promise.resolve()
    })

    await act(async () => {
      await result.current.play()
    })
    expect(pauseAborted).toBe(true)
    expect(result.current.playback?.current).toBe('play-result')
    expect(useRaceStore.getState().replayMode).toBe(true)

    await act(async () => {
      resolvePause(
        new Response(JSON.stringify(playbackState('late-pause')), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      await pauseDone
    })

    expect(result.current.playback?.current).toBe('play-result')
    expect(useRaceStore.getState().replayMode).toBe(true)
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
