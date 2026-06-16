import { useEffect } from 'react'
import { ConnectionBanner } from './components/hud/ConnectionBanner'
import { DevToolsPanel } from './components/hud/DevToolsPanel'
import { PlaybackBar } from './components/hud/PlaybackBar'
import { CameraSelector } from './components/hud/CameraSelector'
import { GapTower } from './components/hud/GapTower'
import { HudLayout } from './components/hud/HudLayout'
import { InsightFeed } from './components/hud/InsightFeed'
import { SessionPicker } from './components/hud/SessionPicker'
import { TireWidget } from './components/hud/TireWidget'
import { RaceCanvas } from './components/scene/RaceCanvas'
import { resolveInitialSessionKey } from './config/session'
import { useInitialPositions } from './hooks/useInitialPositions'
import { useIngestionStatus, usePlayback } from './hooks/usePlayback'
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
    setSessionKey(resolveInitialSessionKey())
  }, [setSessionKey])

  useStompPositions(sessionKey)
  useInitialPositions(sessionKey)
  const ingestionStatus = useIngestionStatus()
  const { playback, playbackError, play, pause, seek } = usePlayback(sessionKey)
  const historyReady = ingestionStatus?.historyReady ?? playback?.historyLoaded ?? false

  return (
    <div className="flex h-screen flex-col">
      <header className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-zinc-800 px-4 py-3 sm:gap-6">
        <h1 className="text-sm font-semibold tracking-wide text-f1red">F1 Visualizer</h1>
        <span className={`text-sm ${STATUS_COLOR[connectionStatus]}`}>
          {STATUS_LABEL[connectionStatus]}
        </span>
        <SessionPicker />
        <DevToolsPanel />
        <span className="text-sm text-zinc-400">
          Drivers <span className="font-mono text-zinc-200">{positions.size}</span>
        </span>
      </header>
      <PlaybackBar
        playback={playback}
        ingestionStatus={ingestionStatus}
        playbackError={playbackError}
        play={play}
        pause={pause}
        seek={seek}
      />
      <ConnectionBanner />
      <main className="flex min-h-0 flex-1 p-2 sm:p-4">
        <HudLayout
          canvas={<RaceCanvas historyLoaded={historyReady} />}
          left={<GapTower />}
          topRight={<CameraSelector />}
          bottomLeft={<TireWidget />}
          bottomRight={<InsightFeed />}
        />
      </main>
    </div>
  )
}
