import { useRaceStore } from '../../store/raceStore'

export function TireWidget(): React.JSX.Element {
  const selectedDriver = useRaceStore((s) => s.selectedDriver)

  // Placeholder until OpenF1 stint ingestion adds compound data (Sprint 6+).
  const label = selectedDriver != null ? `#${selectedDriver}` : '—'

  return (
    <div className="rounded-lg border border-zinc-800 bg-zinc-950/85 px-3 py-2 backdrop-blur-sm">
      <p className="text-xs uppercase tracking-wide text-zinc-500">Tires</p>
      <p className="font-mono text-sm text-zinc-200">
        Driver {label} · Compound: —
      </p>
    </div>
  )
}
