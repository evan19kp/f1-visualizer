import { useEffect, useState } from 'react'
import {
  DEFAULT_SESSION_KEY,
  isValidSessionKey,
  persistSessionKey,
} from '../../config/session'
import { useRaceStore } from '../../store/raceStore'

export function SessionPicker(): React.JSX.Element {
  const sessionKey = useRaceStore((s) => s.sessionKey)
  const setSessionKey = useRaceStore((s) => s.setSessionKey)
  const [input, setInput] = useState(sessionKey)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setInput(sessionKey)
    setError(null)
  }, [sessionKey])

  const trimmed = input.trim()
  const unchanged = trimmed === sessionKey
  const showIngestionWarning = Boolean(sessionKey) && sessionKey !== DEFAULT_SESSION_KEY

  const apply = (): void => {
    if (!isValidSessionKey(trimmed)) {
      setError('Enter a numeric session key')
      return
    }
    setError(null)
    persistSessionKey(trimmed)
    setSessionKey(trimmed)
  }

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center gap-2">
        <label htmlFor="session-key" className="text-sm text-zinc-400">
          Session
        </label>
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
      </div>
      {error && (
        <p id="session-key-error" className="text-xs text-red-400">
          {error}
        </p>
      )}
      {showIngestionWarning && (
        <p className="max-w-md text-xs text-amber-400/90">
          Live ingestion uses backend OPENF1_SESSION_KEY — positions may be stale for this
          session.
        </p>
      )}
    </div>
  )
}
