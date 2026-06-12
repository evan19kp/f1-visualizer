import { useEffect, useState } from 'react'
import {
  API_URL,
  DEFAULT_SESSION_KEY,
  isValidSessionKey,
  persistSessionKey,
} from '../../config/session'
import { useRaceStore } from '../../store/raceStore'
import type { RaceSession } from '../../types/session'

function formatSessionLabel(session: RaceSession): string {
  const circuit = session.circuitName?.trim()
  const name = session.sessionName?.trim() || 'Session'
  return circuit ? `${session.sessionKey} — ${name} (${circuit})` : `${session.sessionKey} — ${name}`
}

export function SessionPicker(): React.JSX.Element {
  const sessionKey = useRaceStore((s) => s.sessionKey)
  const setSessionKey = useRaceStore((s) => s.setSessionKey)
  const [input, setInput] = useState(sessionKey)
  const [error, setError] = useState<string | null>(null)
  const [sessions, setSessions] = useState<RaceSession[]>([])
  const [listError, setListError] = useState<string | null>(null)

  useEffect(() => {
    setInput(sessionKey)
    setError(null)
  }, [sessionKey])

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    void (async () => {
      try {
        const response = await fetch(`${API_URL}/api/sessions`, { signal: controller.signal })
        if (!response.ok) {
          if (active) {
            setListError(`Could not load sessions (HTTP ${response.status})`)
          }
          return
        }
        const data = (await response.json()) as RaceSession[]
        if (!active) {
          return
        }
        setSessions(data)
        setListError(null)
      } catch (fetchError) {
        if (!active || (fetchError instanceof DOMException && fetchError.name === 'AbortError')) {
          return
        }
        setListError(
          fetchError instanceof Error ? fetchError.message : 'Failed to load session list',
        )
      }
    })()

    return () => {
      active = false
      controller.abort()
    }
  }, [])

  const trimmed = input.trim()
  const unchanged = trimmed === sessionKey
  const showIngestionWarning = Boolean(sessionKey) && sessionKey !== DEFAULT_SESSION_KEY
  const hasSessions = sessions.length > 0
  const sessionOptions: RaceSession[] = hasSessions
    ? sessions.some((session) => String(session.sessionKey) === sessionKey)
      ? sessions
      : [
          {
            sessionKey: Number(sessionKey),
            meetingKey: 0,
            sessionName: 'Custom key',
            circuitName: '',
            dateStart: '',
          },
          ...sessions,
        ]
    : []

  const applyKey = (key: string): void => {
    if (!isValidSessionKey(key)) {
      setError('Enter a numeric session key')
      return
    }
    setError(null)
    persistSessionKey(key)
    setSessionKey(key)
  }

  const apply = (): void => {
    applyKey(trimmed)
  }

  const handleSelectChange = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    applyKey(event.target.value)
  }

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center gap-2">
        <label
          htmlFor={hasSessions ? 'session-select' : 'session-key'}
          className="text-sm text-zinc-400"
        >
          Session
        </label>
        {hasSessions ? (
          <select
            id="session-select"
            value={sessionKey}
            onChange={handleSelectChange}
            className="max-w-xs rounded border border-zinc-700 bg-zinc-900 px-2 py-1 font-mono text-sm text-zinc-100 outline-none focus:border-zinc-500"
          >
            {sessionOptions.map((session) => (
              <option key={session.sessionKey} value={String(session.sessionKey)}>
                {formatSessionLabel(session)}
              </option>
            ))}
          </select>
        ) : (
          <>
            <input
              id="session-key"
              type="text"
              inputMode="numeric"
              value={input}
              onChange={(event) => {
                setInput(event.target.value)
                if (error) {
                  setError(null)
                }
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !unchanged) {
                  apply()
                }
              }}
              className="w-24 rounded border border-zinc-700 bg-zinc-900 px-2 py-1 font-mono text-sm text-zinc-100 outline-none focus:border-zinc-500"
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? 'session-key-error' : undefined}
            />
            <button
              type="button"
              onClick={apply}
              disabled={unchanged}
              className="rounded border border-zinc-700 px-2 py-1 text-xs font-medium text-zinc-200 transition-colors enabled:hover:border-zinc-500 enabled:hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Apply
            </button>
          </>
        )}
      </div>
      {listError && (
        <p className="text-xs text-zinc-500">Session list unavailable — enter a key manually.</p>
      )}
      {error && (
        <p id="session-key-error" className="text-xs text-red-400">
          {error}
        </p>
      )}
      {showIngestionWarning && (
        <p className="max-w-md text-xs text-amber-400/90">
          Live ingestion follows backend OPENF1_SESSION_KEY — selected session may be replay/historical
          only.
        </p>
      )}
    </div>
  )
}
