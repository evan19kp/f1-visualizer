import { usePlayback } from '../../hooks/usePlayback'

function formatRaceTime(iso: string): string {
  if (!iso) {
    return '—'
  }
  const date = new Date(iso)
  return date.toISOString().slice(11, 19)
}

interface PlaybackBarProps {
  sessionKey: string
}

export function PlaybackBar({ sessionKey }: PlaybackBarProps): React.JSX.Element | null {
  const { playback, play, pause, seek } = usePlayback(sessionKey)

  if (!playback?.historyLoaded) {
    return null
  }

  const startMs = new Date(playback.start).getTime()
  const endMs = new Date(playback.end).getTime()
  const currentMs = new Date(playback.current).getTime()
  const range = Math.max(endMs - startMs, 1)
  const sliderValue = Math.min(Math.max(currentMs - startMs, 0), range)
  const playing = playback.state === 'PLAYING'

  const onSliderChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const nextMs = startMs + Number(event.target.value)
    void seek(new Date(nextMs).toISOString())
  }

  return (
    <div className="flex flex-wrap items-center gap-3 border-t border-zinc-800 bg-zinc-950/80 px-4 py-2">
      <button
        type="button"
        onClick={() => void (playing ? pause() : play(playback.speed))}
        className="rounded border border-zinc-700 px-3 py-1 text-xs font-medium text-zinc-100 transition-colors hover:border-zinc-500 hover:bg-zinc-800"
      >
        {playing ? 'Pause' : 'Play'}
      </button>
      <div className="flex items-center gap-2">
        {([1, 2, 4] as const).map((speed) => (
          <button
            key={speed}
            type="button"
            onClick={() => void play(speed)}
            className={`rounded border px-2 py-0.5 text-xs ${
              playback.speed === speed
                ? 'border-f1red text-f1red'
                : 'border-zinc-700 text-zinc-400 hover:border-zinc-500'
            }`}
          >
            {speed}x
          </button>
        ))}
      </div>
      <input
        type="range"
        min={0}
        max={range}
        value={sliderValue}
        onChange={onSliderChange}
        className="min-w-[12rem] flex-1 accent-red-600"
        aria-label="Playback timeline"
      />
      <span className="font-mono text-xs text-zinc-400">
        {formatRaceTime(playback.current)} / {formatRaceTime(playback.end)}
      </span>
      {!playing && (
        <span className="text-xs text-zinc-500">Press play to start replay</span>
      )}
    </div>
  )
}
