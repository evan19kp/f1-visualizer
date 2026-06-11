import { useRaceStore, type CameraMode } from '../../store/raceStore'

const MODES: { mode: CameraMode; label: string }[] = [
  { mode: 'orbit', label: 'Orbit' },
  { mode: 'follow', label: 'Follow' },
  { mode: 'tv', label: 'TV' },
]

export function CameraSelector(): React.JSX.Element {
  const cameraMode = useRaceStore((s) => s.cameraMode)
  const setCameraMode = useRaceStore((s) => s.setCameraMode)

  return (
    <div className="flex gap-1 rounded-lg border border-zinc-800 bg-zinc-950/85 p-1 backdrop-blur-sm">
      {MODES.map(({ mode, label }) => {
        const active = cameraMode === mode
        return (
          <button
            key={mode}
            type="button"
            onClick={() => setCameraMode(mode)}
            className={`rounded px-3 py-1.5 text-xs font-medium transition-colors ${
              active
                ? 'bg-f1red text-white'
                : 'text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100'
            }`}
          >
            {label}
          </button>
        )
      })}
    </div>
  )
}
