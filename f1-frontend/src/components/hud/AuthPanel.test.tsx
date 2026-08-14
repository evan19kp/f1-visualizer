import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthPanel } from './AuthPanel'
import { useRaceStore } from '../../store/raceStore'
import * as auth from '../../lib/auth'

describe('AuthPanel', () => {
  beforeEach(() => {
    sessionStorage.clear()
    useRaceStore.getState().setAuthSession(null, null)
    vi.restoreAllMocks()
  })

  it('shows a login button when signed out', () => {
    render(<AuthPanel />)
    expect(screen.getByRole('button', { name: 'Log in' })).toBeInTheDocument()
  })

  it('expands into a login form and submits credentials', async () => {
    const user = userEvent.setup()
    const loginSpy = vi.spyOn(auth, 'login').mockImplementation(async (username) => {
      useRaceStore.getState().setAuthSession('jwt-abc', username.trim())
      return 'jwt-abc'
    })

    render(<AuthPanel />)
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    await user.type(screen.getByPlaceholderText('Username'), 'admin')
    await user.type(screen.getByPlaceholderText('Password'), 'secret')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(loginSpy).toHaveBeenCalledWith('admin', 'secret')
    await screen.findByRole('button', { name: 'Log out' })
    expect(screen.getByText(/Signed in as/)).toHaveTextContent('admin')
  })

  it('shows login errors without updating auth state', async () => {
    const user = userEvent.setup()
    vi.spyOn(auth, 'login').mockRejectedValue(new Error('Invalid username or password'))

    render(<AuthPanel />)
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    await user.type(screen.getByPlaceholderText('Username'), 'admin')
    await user.type(screen.getByPlaceholderText('Password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')
    expect(useRaceStore.getState().authToken).toBeNull()
  })

  it('logs out and returns to the collapsed login button', async () => {
    const user = userEvent.setup()
    useRaceStore.getState().setAuthSession('jwt-abc', 'admin')

    render(<AuthPanel />)
    await user.click(screen.getByRole('button', { name: 'Log out' }))

    expect(useRaceStore.getState().authToken).toBeNull()
    expect(screen.getByRole('button', { name: 'Log in' })).toBeInTheDocument()
  })
})
