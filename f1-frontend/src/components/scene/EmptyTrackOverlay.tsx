import { useRaceStore } from '../../store/raceStore'

export function EmptyTrackOverlay(): React.JSX.Element | null {
  const connectionStatus = useRaceStore((s) => s.connectionStatus)
  const positions = useRaceStore((s) => s.positions)
  const sessionKey = useRaceStore((s) => s.sessionKey)

  if (connectionStatus !== 'connected' || positions.size > 0) {
    return null
  }

  return (
    <div className="pointer-events-none absolute inset-0 flex items-center justify-center p-4">
      <div className="max-w-md rounded-lg border border-zinc-700 bg-zinc-950/90 px-4 py-3 text-center backdrop-blur-sm">
        <p className="text-sm font-medium text-zinc-100">Waiting for position data…</p>
        <ul className="mt-2 space-y-1 text-left text-xs leading-relaxed text-zinc-400">
          <li>Set INGESTION_ENABLED=true when starting the API</li>
          <li>Confirm session key {sessionKey || '—'} matches OpenF1 data</li>
          <li>Ingestion polls every few seconds — data should appear within ~10s of startup</li>
        </ul>
      </div>
    </div>
  )
}
