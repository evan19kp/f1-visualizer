import { useEffect, useState } from 'react'
import { API_URL } from '../../config/session'
import { ensureDevAuth } from '../../lib/auth'
import { useRaceStore } from '../../store/raceStore'
import type { RaceInsight } from '../../types/insight'

const POLL_MS = 10_000

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

    async function fetchInsights(): Promise<void> {
      try {
        const response = await fetch(
          `${API_URL}/api/sessions/${sessionKey}/insights?limit=5`,
          { headers: { Authorization: `Bearer ${authToken}` } },
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
        if (!cancelled) {
          setError(fetchError instanceof Error ? fetchError.message : 'Failed to load insights')
        }
      }
    }

    void fetchInsights()
    const interval = window.setInterval(() => void fetchInsights(), POLL_MS)
    return () => {
      cancelled = true
      window.clearInterval(interval)
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
