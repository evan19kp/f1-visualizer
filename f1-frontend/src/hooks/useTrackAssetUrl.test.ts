import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useTrackAssetUrl } from './useTrackAssetUrl'

afterEach(() => {
  vi.unstubAllGlobals()
})

function trackAssetResponse(url: string): Response {
  return new Response(JSON.stringify({ url, circuitSlug: 'test-circuit' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('useTrackAssetUrl', () => {
  it('ignores a late response from the previous session after the new asset loads', async () => {
    let resolveSessionA!: (response: Response) => void
    const sessionAResponse = new Promise<Response>((resolve) => {
      resolveSessionA = resolve
    })
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/api/sessions/111/track-asset')) {
        return sessionAResponse
      }
      if (url.includes('/api/sessions/222/track-asset')) {
        return Promise.resolve(trackAssetResponse('https://example.test/session-b.glb'))
      }
      return Promise.reject(new Error(`unexpected url: ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { result, rerender } = renderHook(
      ({ sessionKey }: { sessionKey: string }) => useTrackAssetUrl(sessionKey, 0),
      { initialProps: { sessionKey: '111' } },
    )

    rerender({ sessionKey: '222' })

    await waitFor(() => {
      expect(result.current).toBe('https://example.test/session-b.glb')
    })

    await act(async () => {
      resolveSessionA(trackAssetResponse('https://example.test/session-a.glb'))
      await sessionAResponse
    })

    expect(result.current).toBe('https://example.test/session-b.glb')
  })
})
