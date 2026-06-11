import type { ReactNode } from 'react'

interface HudLayoutProps {
  canvas: ReactNode
  left?: ReactNode
  topRight?: ReactNode
  bottomLeft?: ReactNode
  bottomRight?: ReactNode
}

export function HudLayout({
  canvas,
  left,
  topRight,
  bottomLeft,
  bottomRight,
}: HudLayoutProps): React.JSX.Element {
  return (
    <div className="relative h-full w-full">
      <div className="h-full w-full">{canvas}</div>
      <div className="pointer-events-none absolute inset-0 flex flex-col justify-between p-3">
        <div className="flex items-start justify-between gap-3">
          {left ? <div className="pointer-events-auto">{left}</div> : <span />}
          {topRight ? (
            <div className="pointer-events-auto flex flex-col items-end gap-2">{topRight}</div>
          ) : (
            <span />
          )}
        </div>
        <div className="flex items-end justify-between gap-3">
          {bottomLeft ? <div className="pointer-events-auto">{bottomLeft}</div> : <span />}
          {bottomRight ? (
            <div className="pointer-events-auto w-full max-w-sm">{bottomRight}</div>
          ) : (
            <span />
          )}
        </div>
      </div>
    </div>
  )
}
