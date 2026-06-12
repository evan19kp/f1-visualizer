import { useEffect, useState } from 'react'
import { useRaceStore } from '../../store/raceStore'

const CONNECTING_HINT_MS = 10_000

const SERVER_HINT =
  'Cannot reach race server — is the API running on :8080? Start it with ./mvnw spring-boot:run -pl f1-api'

export function ConnectionBanner(): React.JSX.Element | null {
  const connectionStatus = useRaceStore((s) => s.connectionStatus)
  const positionsFetchError = useRaceStore((s) => s.positionsFetchError)
  const [connectingSlow, setConnectingSlow] = useState(false)
  const [dismissed, setDismissed] = useState(false)

  useEffect(() => {
    if (connectionStatus !== 'connecting') {
      setConnectingSlow(false)
      return
    }
    const timer = window.setTimeout(() => setConnectingSlow(true), CONNECTING_HINT_MS)
    return () => window.clearTimeout(timer)
  }, [connectionStatus])

  useEffect(() => {
    setDismissed(false)
  }, [connectionStatus, positionsFetchError])

  const showDisconnected = connectionStatus === 'disconnected'
  const showSlowConnect = connectionStatus === 'connecting' && connectingSlow
  const showFetchError = positionsFetchError != null

  if (!showDisconnected && !showSlowConnect && !showFetchError) {
    return null
  }

  if (dismissed && !showFetchError) {
    return null
  }

  const message = showFetchError
    ? positionsFetchError
    : SERVER_HINT

  const tone =
    showFetchError ? 'border-amber-700/60 bg-amber-950/90 text-amber-100' : 'border-red-800/60 bg-red-950/90 text-red-100'

  return (
    <div
      className={`flex items-start justify-between gap-3 border-b px-4 py-2 text-sm ${tone}`}
      role="status"
    >
      <p className="min-w-0 flex-1 leading-snug">{message}</p>
      <button
        type="button"
        onClick={() => setDismissed(true)}
        className="pointer-events-auto shrink-0 rounded px-2 py-0.5 text-xs uppercase tracking-wide opacity-80 hover:opacity-100"
        aria-label="Dismiss connection notice"
      >
        Dismiss
      </button>
    </div>
  )
}
