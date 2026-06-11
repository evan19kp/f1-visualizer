import { useEffect, useState } from 'react'
import { API_URL } from '../../config/session'
import { ensureDevAuth } from '../../lib/auth'
import { useRaceStore } from '../../store/raceStore'
import type { RaceInsight } from '../../types/insight'

const POLL_MS = 10_000
const REQUEST_TIMEOUT_MS = 8_000

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

export function InsightFeed(): React.JSX.Element {
  const sessionKey = useRaceStore((s) => s.sessionKey)
  const authToken = useRaceStore((s) => s.authToken)
  const [insights, setInsights] = useState<RaceInsight[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void ensureDevAuth()
  }, [])

  useEffect(() => {
    if (!sessionKey || !authToken) {
      return
    }

    let cancelled = false
    let activeController: AbortController | null = null
    let requestTimeout: number | null = null
    let pollTimeout: number | null = null

    async function fetchInsights(): Promise<void> {
      const controller = new AbortController()
      activeController = controller
      requestTimeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

      try {
        const response = await fetch(
          `${API_URL}/api/sessions/${sessionKey}/insights?limit=5`,
          {
            headers: { Authorization: `Bearer ${authToken}` },
            signal: controller.signal,
          },
        )
        if (!response.ok) {
          throw new Error(`Insights request failed: ${response.status}`)
        }
        const data = (await response.json()) as RaceInsight[]
        if (!cancelled) {
          setInsights(data)
          setError(null)
        }
      } catch (fetchError) {
        if (isAbortError(fetchError) || cancelled) {
          return
        }
        setError(fetchError instanceof Error ? fetchError.message : 'Failed to load insights')
      } finally {
        if (requestTimeout != null) {
          window.clearTimeout(requestTimeout)
          requestTimeout = null
        }
        activeController = null
      }
    }

    async function poll(): Promise<void> {
      await fetchInsights()
      if (!cancelled) {
        pollTimeout = window.setTimeout(() => void poll(), POLL_MS)
      }
    }

    void poll()

    return () => {
      cancelled = true
      activeController?.abort()
      if (requestTimeout != null) {
        window.clearTimeout(requestTimeout)
      }
      if (pollTimeout != null) {
        window.clearTimeout(pollTimeout)
      }
    }
  }, [sessionKey, authToken])

  return (
    <div className="rounded-lg border border-zinc-800 bg-zinc-950/85 p-3 backdrop-blur-sm">
      <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-zinc-400">
        AI Insights
      </h2>
      {!authToken ? (
        <p className="text-sm text-zinc-500">Authenticating…</p>
      ) : error ? (
        <p className="text-sm text-amber-400">{error}</p>
      ) : insights.length === 0 ? (
        <p className="text-sm text-zinc-500">No insights yet</p>
      ) : (
        <ul className="max-h-40 space-y-2 overflow-y-auto text-sm text-zinc-200">
          {insights.map((insight) => (
            <li key={`${insight.timestamp}-${insight.eventType}`} className="border-l-2 border-f1red/60 pl-2">
              <span className="text-xs uppercase text-zinc-500">{insight.eventType}</span>
              <p>{insight.commentary}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
