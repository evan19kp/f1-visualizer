import { useEffect } from 'react'
import { CameraSelector } from './components/hud/CameraSelector'
import { GapTower } from './components/hud/GapTower'
import { HudLayout } from './components/hud/HudLayout'
import { InsightFeed } from './components/hud/InsightFeed'
import { TireWidget } from './components/hud/TireWidget'
import { RaceCanvas } from './components/scene/RaceCanvas'
import { DEFAULT_SESSION_KEY } from './config/session'
import { useInitialPositions } from './hooks/useInitialPositions'
import { useStompPositions } from './hooks/useStompPositions'
import { useRaceStore } from './store/raceStore'

const STATUS_LABEL = {
  connecting: 'Connecting…',
  connected: 'Connected',
  disconnected: 'Disconnected',
} as const

const STATUS_COLOR = {
  connecting: 'text-amber-400',
  connected: 'text-emerald-400',
  disconnected: 'text-zinc-500',
} as const

export default function App(): React.JSX.Element {
  const sessionKey = useRaceStore((s) => s.sessionKey)
  const connectionStatus = useRaceStore((s) => s.connectionStatus)
  const positions = useRaceStore((s) => s.positions)
  const setSessionKey = useRaceStore((s) => s.setSessionKey)

  useEffect(() => {
    setSessionKey(DEFAULT_SESSION_KEY)
  }, [setSessionKey])

  useStompPositions(sessionKey)
  useInitialPositions(sessionKey)

  return (
    <div className="flex h-screen flex-col">
      <header className="flex items-center gap-6 border-b border-zinc-800 px-4 py-3">
        <h1 className="text-sm font-semibold tracking-wide text-f1red">F1 Visualizer</h1>
        <span className={`text-sm ${STATUS_COLOR[connectionStatus]}`}>
          {STATUS_LABEL[connectionStatus]}
        </span>
        <span className="text-sm text-zinc-400">
          Session <span className="font-mono text-zinc-200">{sessionKey || '—'}</span>
        </span>
        <span className="text-sm text-zinc-400">
          Drivers <span className="font-mono text-zinc-200">{positions.size}</span>
        </span>
      </header>
      <main className="flex flex-1 p-4">
        <HudLayout
          canvas={<RaceCanvas />}
          left={<GapTower />}
          topRight={<CameraSelector />}
          bottomLeft={<TireWidget />}
          bottomRight={<InsightFeed />}
        />
      </main>
    </div>
  )
}
