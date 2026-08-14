import { useEffect, useState } from 'react'
import { API_URL } from '../../config/session'
import { useRaceStore } from '../../store/raceStore'
import type { Stint } from '../../types/stint'

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

function formatCompound(compound: string | null | undefined): string {
  if (compound == null || compound === '') {
    return '—'
  }
  return compound.charAt(0) + compound.slice(1).toLowerCase()
}

export function TireWidget(): React.JSX.Element {
  const sessionKey = useRaceStore((s) => s.sessionKey)
  const selectedDriver = useRaceStore((s) => s.selectedDriver)
  const [stint, setStint] = useState<Stint | null>(null)

  useEffect(() => {
    if (!sessionKey || selectedDriver == null) {
      return
    }

    const controller = new AbortController()
    let active = true

    void (async () => {
      try {
        const response = await fetch(
          `${API_URL}/api/sessions/${sessionKey}/stints/${selectedDriver}`,
          { signal: controller.signal },
        )
        if (!response.ok) {
          if (active) {
            setStint(null)
          }
          return
        }

        const data = (await response.json()) as Stint
        if (active) {
          setStint(data)
        }
      } catch (error) {
        if (!active || isAbortError(error)) {
          return
        }
        setStint(null)
      }
    })()

    return () => {
      active = false
      controller.abort()
    }
  }, [sessionKey, selectedDriver])

  // Bind visible stint to the current session/driver so a prior selection cannot flash.
  const activeStint =
    sessionKey &&
    selectedDriver != null &&
    stint != null &&
    String(stint.sessionKey) === sessionKey &&
    stint.driverNumber === selectedDriver
      ? stint
      : null
  const driverLabel = selectedDriver != null ? `#${selectedDriver}` : '—'
  const compoundLabel = activeStint != null ? formatCompound(activeStint.compound) : '—'
  const ageLabel =
    activeStint?.tyreAgeAtStart != null
      ? `${activeStint.tyreAgeAtStart} lap${activeStint.tyreAgeAtStart === 1 ? '' : 's'}`
      : null

  return (
    <div className="rounded-lg border border-zinc-800 bg-zinc-950/85 px-3 py-2 backdrop-blur-sm">
      <p className="text-xs uppercase tracking-wide text-zinc-500">Tires</p>
      <p className="font-mono text-sm text-zinc-200">
        Driver {driverLabel} · Compound: {compoundLabel}
        {ageLabel != null ? ` · Age: ${ageLabel}` : ''}
      </p>
    </div>
  )
}
