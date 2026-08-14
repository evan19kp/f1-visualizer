import { useEffect, useState } from 'react'
import { API_URL } from '../config/session'
import { trackAssetUrlForSession } from '../utils/trackAssetUrl'

interface TrackAssetResponse {
  url: string
  circuitSlug: string
}

function isTrackAssetResponse(value: unknown): value is TrackAssetResponse {
  if (!value || typeof value !== 'object') {
    return false
  }
  const payload = value as Record<string, unknown>
  return typeof payload.url === 'string' && typeof payload.circuitSlug === 'string'
}

export function useTrackAssetUrl(sessionKey: string, assetVersion: number): string | null {
  const [trackAsset, setTrackAsset] = useState<{ sessionKey: string; url: string } | null>(null)

  useEffect(() => {
    if (!sessionKey) {
      return
    }

    const controller = new AbortController()
    let active = true

    void (async () => {
      try {
        const response = await fetch(`${API_URL}/api/sessions/${sessionKey}/track-asset`, {
          signal: controller.signal,
        })
        if (response.status === 404) {
          if (active) {
            setTrackAsset(null)
          }
          return
        }
        if (!response.ok) {
          if (import.meta.env.DEV) {
            console.warn(
              `TrackMesh: track-asset request failed (${response.status}) for session ${sessionKey}`,
            )
          }
          if (active) {
            setTrackAsset(null)
          }
          return
        }

        const payload: unknown = await response.json()
        if (!active) {
          return
        }
        if (isTrackAssetResponse(payload)) {
          setTrackAsset({ sessionKey, url: payload.url })
          return
        }

        if (import.meta.env.DEV) {
          console.error(`TrackMesh: invalid track-asset payload for session ${sessionKey}`, payload)
        }
        setTrackAsset(null)
      } catch (error) {
        if (controller.signal.aborted || !active) {
          return
        }
        if (import.meta.env.DEV) {
          console.error(`TrackMesh: failed to fetch track-asset for session ${sessionKey}`, error)
        }
        setTrackAsset(null)
      }
    })()

    return () => {
      active = false
      controller.abort()
    }
  }, [sessionKey, assetVersion])

  return trackAssetUrlForSession(sessionKey, trackAsset)
}
