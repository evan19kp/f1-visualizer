import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useRaceStore } from '../../store/raceStore'
import { TireWidget } from './TireWidget'

beforeEach(() => {
  useRaceStore.setState({
    sessionKey: '9161',
    selectedDriver: 1,
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('TireWidget', () => {
  it('accepts a numeric backend session key for a leading-zero manual key', async () => {
    useRaceStore.setState({ sessionKey: '09161' })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            driverNumber: 1,
            sessionKey: 9161,
            compound: 'MEDIUM',
            stintNumber: 1,
            lapStart: 1,
            lapEnd: null,
            tyreAgeAtStart: 0,
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    )

    render(<TireWidget />)

    await waitFor(() => {
      expect(screen.getByText(/Compound: Medium/)).toBeInTheDocument()
    })
  })

  it('does not show the previous driver compound after the selection changes', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/stints/1')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              driverNumber: 1,
              sessionKey: 9161,
              compound: 'SOFT',
              stintNumber: 1,
              lapStart: 1,
              lapEnd: null,
              tyreAgeAtStart: 0,
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        )
      }
      if (url.endsWith('/stints/44')) {
        return new Promise<Response>(() => {
          /* keep driver 44 loading */
        })
      }
      return Promise.reject(new Error(`unexpected url: ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { rerender } = render(<TireWidget />)

    await waitFor(() => {
      expect(screen.getByText(/Compound: Soft/)).toBeInTheDocument()
    })

    useRaceStore.setState({ selectedDriver: 44 })
    rerender(<TireWidget />)

    expect(screen.getByText(/Driver #44/)).toBeInTheDocument()
    expect(screen.queryByText(/Compound: Soft/)).not.toBeInTheDocument()
    expect(screen.getByText(/Compound: —/)).toBeInTheDocument()
  })
})
