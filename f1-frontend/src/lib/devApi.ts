import { API_URL } from '../config/session'

interface TrackAssetResponse {
  url: string
  circuitSlug: string
}

export interface SessionResetResponse {
  clearedKeys: string[]
  reingestTriggered: boolean
}

export interface BackfillResponse {
  sessionKey: number
  samplesAppended: number
  success: boolean
  error: string
}

async function devPost<T>(path: string, authToken: string): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${authToken}`,
      'Content-Type': 'application/json',
    },
  })

  if (!response.ok) {
    const body = await response.text()
    throw new Error(body || `Request failed: ${response.status}`)
  }

  return (await response.json()) as T
}

export function generateTrackMesh(
  sessionKey: string,
  authToken: string,
): Promise<TrackAssetResponse> {
  return devPost<TrackAssetResponse>(
    `/api/dev/sessions/${sessionKey}/track-mesh/generate`,
    authToken,
  )
}

export function resetSessionData(
  sessionKey: string,
  authToken: string,
): Promise<SessionResetResponse> {
  return devPost<SessionResetResponse>(`/api/dev/sessions/${sessionKey}/reset`, authToken)
}

export function backfillSessionHistory(
  sessionKey: string,
  authToken: string,
): Promise<BackfillResponse> {
  return devPost<BackfillResponse>(
    `/api/dev/sessions/${sessionKey}/history/backfill`,
    authToken,
  )
}
