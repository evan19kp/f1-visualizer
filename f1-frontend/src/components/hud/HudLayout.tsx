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
    <div className="relative h-full w-full min-w-0">
      <div className="h-full w-full min-w-0">{canvas}</div>
      <div className="pointer-events-none absolute inset-0 overflow-hidden p-2 sm:p-3">
        <div className="flex h-full min-w-0 flex-col justify-between gap-2 md:gap-3">
          <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            {left ? (
              <div className="pointer-events-auto min-w-0 w-full max-w-full sm:w-auto sm:max-w-[11rem]">
                {left}
              </div>
            ) : null}
            {topRight ? (
              <div className="pointer-events-auto flex min-w-0 flex-col items-stretch gap-2 sm:items-end">
                {topRight}
              </div>
            ) : null}
          </div>
          <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
            {bottomLeft ? (
              <div className="pointer-events-auto min-w-0 w-full max-w-full sm:w-auto">
                {bottomLeft}
              </div>
            ) : null}
            {bottomRight ? (
              <div className="pointer-events-auto min-w-0 w-full max-w-full sm:max-w-sm">
                {bottomRight}
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  )
}
