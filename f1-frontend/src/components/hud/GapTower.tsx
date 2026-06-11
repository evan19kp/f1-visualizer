import { useMemo } from 'react'
import { useRaceStore } from '../../store/raceStore'
import type { Position } from '../../types/position'
import { angleGapToLeader, trackAngle } from '../../utils/trackProgress'

interface RankedDriver {
  driverNumber: number
  position: number
  gapRadians: number
}

function rankDrivers(positions: Map<number, Position>): RankedDriver[] {
  const entries = Array.from(positions.values()).map((position) => ({
    driverNumber: position.driverNumber,
    angle: trackAngle(position),
  }))

  entries.sort((a, b) => b.angle - a.angle)

  const leaderAngle = entries[0]?.angle ?? 0
  return entries.map((entry, index) => ({
    driverNumber: entry.driverNumber,
    position: index + 1,
    gapRadians: index === 0 ? 0 : angleGapToLeader(entry.angle, leaderAngle),
  }))
}

function formatGap(gapRadians: number): string {
  if (gapRadians === 0) {
    return '—'
  }
  return `+${gapRadians.toFixed(2)}`
}

export function GapTower(): React.JSX.Element {
  const positions = useRaceStore((s) => s.positions)
  const selectedDriver = useRaceStore((s) => s.selectedDriver)
  const setSelectedDriver = useRaceStore((s) => s.setSelectedDriver)

  const ranked = useMemo(() => rankDrivers(positions), [positions])

  return (
    <div className="w-44 rounded-lg border border-zinc-800 bg-zinc-950/85 p-2 backdrop-blur-sm">
      <h2 className="mb-2 px-1 text-xs font-semibold uppercase tracking-wide text-zinc-400">
        Gap Tower
      </h2>
      <ul className="max-h-64 space-y-1 overflow-y-auto text-sm">
        {ranked.length === 0 ? (
          <li className="px-2 py-1 text-zinc-500">No drivers</li>
        ) : (
          ranked.map((row) => {
            const selected = selectedDriver === row.driverNumber
            return (
              <li key={row.driverNumber}>
                <button
                  type="button"
                  onClick={() => setSelectedDriver(row.driverNumber)}
                  className={`flex w-full items-center justify-between rounded px-2 py-1 text-left font-mono transition-colors ${
                    selected
                      ? 'bg-f1red/20 text-zinc-100'
                      : 'text-zinc-300 hover:bg-zinc-800/80'
                  }`}
                >
                  <span>
                    P{row.position} #{row.driverNumber}
                  </span>
                  <span className="text-xs text-zinc-500">{formatGap(row.gapRadians)}</span>
                </button>
              </li>
            )
          })
        )}
      </ul>
    </div>
  )
}
