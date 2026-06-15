import { useEffect, useState } from 'react'
import { ensureDevAuth } from '../../lib/auth'
import { backfillSessionHistory, generateTrackMesh, resetSessionData } from '../../lib/devApi'
import { useRaceStore } from '../../store/raceStore'

type ActionState = 'idle' | 'loading' | 'success' | 'error'

function isDevToolsEnabled(): boolean {
  return import.meta.env.DEV || import.meta.env.VITE_DEV_TOOLS === 'true'
}

export function DevToolsPanel(): React.JSX.Element | null {
  const sessionKey = useRaceStore((s) => s.sessionKey)
  const authToken = useRaceStore((s) => s.authToken)
  const bumpTrackAssetVersion = useRaceStore((s) => s.bumpTrackAssetVersion)
  const clearPositions = useRaceStore((s) => s.clearPositions)

  const [generateState, setGenerateState] = useState<ActionState>('idle')
  const [resetState, setResetState] = useState<ActionState>('idle')
  const [backfillState, setBackfillState] = useState<ActionState>('idle')
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    void ensureDevAuth()
  }, [])

  if (!isDevToolsEnabled()) {
    return null
  }

  const runGenerate = async (): Promise<void> => {
    if (!sessionKey || !authToken) {
      setMessage('Dev auth required — set VITE_DEV_AUTOLOGIN and credentials, or log in.')
      setGenerateState('error')
      return
    }

    setGenerateState('loading')
    setResetState('idle')
    setMessage(null)
    try {
      const result = await generateTrackMesh(sessionKey, authToken)
      bumpTrackAssetVersion()
      setGenerateState('success')
      setMessage(`Track uploaded (${result.circuitSlug}). Reloading mesh…`)
    } catch (error) {
      setGenerateState('error')
      setMessage(error instanceof Error ? error.message : 'Track generation failed')
    }
  }

  const runReset = async (): Promise<void> => {
    if (!sessionKey || !authToken) {
      setMessage('Dev auth required — set VITE_DEV_AUTOLOGIN and credentials, or log in.')
      setResetState('error')
      return
    }

    setResetState('loading')
    setGenerateState('idle')
    setMessage(null)
    try {
      const result = await resetSessionData(sessionKey, authToken)
      clearPositions()
      setResetState('success')
      setMessage(
        result.reingestTriggered
          ? 'Session cache cleared. Re-ingest started — positions should return in ~10s.'
          : 'Session cache cleared. Re-ingest only runs when OPENF1_SESSION_KEY matches this session.',
      )
    } catch (error) {
      setResetState('error')
      setMessage(error instanceof Error ? error.message : 'Session reset failed')
    }
  }

  const runBackfill = async (): Promise<void> => {
    if (!sessionKey || !authToken) {
      setMessage('Dev auth required — set VITE_DEV_AUTOLOGIN and credentials, or log in.')
      setBackfillState('error')
      return
    }

    setBackfillState('loading')
    setGenerateState('idle')
    setResetState('idle')
    setMessage(null)
    try {
      const result = await backfillSessionHistory(sessionKey, authToken)
      setBackfillState('success')
      setMessage(
        result.success
          ? `History backfilled (${result.samplesAppended} samples). Press Play on the timeline.`
          : `Backfill failed: ${result.error}`,
      )
    } catch (error) {
      setBackfillState('error')
      setMessage(error instanceof Error ? error.message : 'History backfill failed')
    }
  }

  const busy =
    generateState === 'loading' || resetState === 'loading' || backfillState === 'loading'

  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs font-medium uppercase tracking-wide text-zinc-500">Dev</span>
        <button
          type="button"
          onClick={() => void runGenerate()}
          disabled={busy || !sessionKey}
          className="rounded border border-zinc-700 px-2 py-1 text-xs text-zinc-200 transition-colors enabled:hover:border-zinc-500 enabled:hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {generateState === 'loading' ? 'Generating…' : 'Generate track'}
        </button>
        <button
          type="button"
          onClick={() => void runBackfill()}
          disabled={busy || !sessionKey}
          className="rounded border border-zinc-700 px-2 py-1 text-xs text-zinc-200 transition-colors enabled:hover:border-zinc-500 enabled:hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {backfillState === 'loading' ? 'Backfilling…' : 'Backfill history'}
        </button>
        <button
          type="button"
          onClick={() => void runReset()}
          disabled={busy || !sessionKey}
          className="rounded border border-zinc-700 px-2 py-1 text-xs text-zinc-200 transition-colors enabled:hover:border-zinc-500 enabled:hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {resetState === 'loading' ? 'Resetting…' : 'Reset session'}
        </button>
      </div>
      {message && (
        <p
          className={`max-w-md text-xs ${
            generateState === 'error' || resetState === 'error' || backfillState === 'error'
              ? 'text-red-400'
              : 'text-zinc-400'
          }`}
        >
          {message}
        </p>
      )}
    </div>
  )
}
