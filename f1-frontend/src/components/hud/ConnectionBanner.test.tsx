import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ConnectionBanner } from './ConnectionBanner'
import { useRaceStore } from '../../store/raceStore'

beforeEach(() => {
  vi.useFakeTimers()
  useRaceStore.setState({
    connectionStatus: 'connected',
    positionsFetchError: null,
  })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('ConnectionBanner', () => {
  it('stays hidden for a healthy connection', () => {
    render(<ConnectionBanner />)

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('shows guidance only after a connection has been slow for ten seconds', () => {
    useRaceStore.setState({ connectionStatus: 'connecting' })
    render(<ConnectionBanner />)

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    act(() => vi.advanceTimersByTime(10_000))
    expect(screen.getByRole('status')).toHaveTextContent('Cannot reach race server')
  })

  it('lets users dismiss a disconnected notice', () => {
    useRaceStore.setState({ connectionStatus: 'disconnected' })
    render(<ConnectionBanner />)

    fireEvent.click(screen.getByRole('button', { name: 'Dismiss connection notice' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('keeps position fetch errors visible because they require action', () => {
    useRaceStore.setState({
      connectionStatus: 'disconnected',
      positionsFetchError: 'Positions request failed: 503',
    })
    render(<ConnectionBanner />)

    fireEvent.click(screen.getByRole('button', { name: 'Dismiss connection notice' }))
    expect(screen.getByRole('status')).toHaveTextContent('Positions request failed: 503')
  })
})
