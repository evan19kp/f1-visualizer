import { useIngestionStatus } from '../../hooks/usePlayback'
import { useRaceStore } from '../../store/raceStore'

function ingestionMessage(
  status: NonNullable<ReturnType<typeof useIngestionStatus>>,
  sessionKey: string,
): { title: string; hints: string[] } {
  if (!status.enabled) {
    return {
      title: 'Ingestion is disabled',
      hints: ['Start the API with INGESTION_ENABLED=true'],
    }
  }
  if (status.lastError === 'openf1_unauthorized') {
    return {
      title: 'OpenF1 authentication required',
      hints: [
        'Set OPENF1_ACCESS_TOKEN or OPENF1_USERNAME / OPENF1_PASSWORD',
        'Live sessions often return 401 without credentials',
      ],
    }
  }
  if (status.lastError === 'openf1_rate_limited') {
    return {
      title: 'OpenF1 rate limit reached',
      hints: ['Wait a minute and retry, or increase OPENF1_POLL_INTERVAL_MS'],
    }
  }
  if (
    status.configuredSessionKey &&
    sessionKey &&
    status.configuredSessionKey !== sessionKey &&
    String(status.resolvedSessionKey) !== sessionKey
  ) {
    return {
      title: 'Session key mismatch',
      hints: [
        `Frontend session ${sessionKey} does not match backend OPENF1_SESSION_KEY=${status.configuredSessionKey}`,
        'Align keys or use Dev Tools → Backfill history + Play for replay',
      ],
    }
  }
  return {
    title: 'Waiting for position data…',
    hints: [
      'Use Dev Tools → Backfill history, then press Play on the timeline',
      'Ingestion polls every few seconds — data should appear within ~10s',
    ],
  }
}

interface EmptyTrackOverlayProps {
  historyLoaded?: boolean
}

export function EmptyTrackOverlay({
  historyLoaded = false,
}: EmptyTrackOverlayProps): React.JSX.Element | null {
  const connectionStatus = useRaceStore((s) => s.connectionStatus)
  const positions = useRaceStore((s) => s.positions)
  const sessionKey = useRaceStore((s) => s.sessionKey)
  const ingestionStatus = useIngestionStatus()

  if (historyLoaded) {
    return null
  }

  if (connectionStatus !== 'connected' || positions.size > 0) {
    return null
  }

  const message = ingestionStatus
    ? ingestionMessage(ingestionStatus, sessionKey)
    : {
        title: 'Waiting for position data…',
        hints: ['Set INGESTION_ENABLED=true when starting the API'],
      }

  return (
    <div className="pointer-events-none absolute inset-0 flex items-center justify-center p-4">
      <div className="max-w-md rounded-lg border border-zinc-700 bg-zinc-950/90 px-4 py-3 text-center backdrop-blur-sm">
        <p className="text-sm font-medium text-zinc-100">{message.title}</p>
        <ul className="mt-2 space-y-1 text-left text-xs leading-relaxed text-zinc-400">
          {message.hints.map((hint) => (
            <li key={hint}>{hint}</li>
          ))}
        </ul>
      </div>
    </div>
  )
}
