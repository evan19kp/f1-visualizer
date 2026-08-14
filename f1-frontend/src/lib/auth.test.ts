import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearAuthIfUnauthorized,
  login,
  logout,
  restoreAuthFromStorage,
} from './auth'
import { useRaceStore } from '../store/raceStore'

const AUTH_STORAGE_KEY = 'f1.auth.session'

describe('auth', () => {
  beforeEach(() => {
    sessionStorage.clear()
    useRaceStore.getState().setAuthSession(null, null)
    vi.restoreAllMocks()
  })

  it('rejects empty credentials before calling the API', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
    await expect(login('  ', 'secret')).rejects.toThrow('Username and password are required')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('persists a successful login and updates the store', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ token: 'jwt-123', expiresInMs: 60_000 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const token = await login('admin', 'password')
    expect(token).toBe('jwt-123')

    const stored = JSON.parse(sessionStorage.getItem(AUTH_STORAGE_KEY) ?? '{}') as {
      token: string
      username: string
      expiresAt: number
    }
    expect(stored.token).toBe('jwt-123')
    expect(stored.username).toBe('admin')
    expect(stored.expiresAt).toBeGreaterThan(Date.now())

    const state = useRaceStore.getState()
    expect(state.authToken).toBe('jwt-123')
    expect(state.authUsername).toBe('admin')
  })

  it('maps 401 responses to a friendly error', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 401 }))
    await expect(login('admin', 'wrong')).rejects.toThrow('Invalid username or password')
  })

  it('restores a non-expired session from storage', () => {
    sessionStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        token: 'stored-token',
        username: 'admin',
        expiresAt: Date.now() + 60_000,
      }),
    )

    restoreAuthFromStorage()

    const state = useRaceStore.getState()
    expect(state.authToken).toBe('stored-token')
    expect(state.authUsername).toBe('admin')
  })

  it('drops expired sessions during restore', () => {
    sessionStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        token: 'expired-token',
        username: 'admin',
        expiresAt: Date.now() - 1,
      }),
    )

    restoreAuthFromStorage()

    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    expect(useRaceStore.getState().authToken).toBeNull()
  })

  it('clears storage and store on logout', () => {
    sessionStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        token: 'token',
        username: 'admin',
        expiresAt: Date.now() + 60_000,
      }),
    )
    useRaceStore.getState().setAuthSession('token', 'admin')

    logout()

    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    expect(useRaceStore.getState().authToken).toBeNull()
    expect(useRaceStore.getState().authUsername).toBeNull()
  })

  it('logs out on unauthorized API responses', () => {
    useRaceStore.getState().setAuthSession('token', 'admin')
    sessionStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        token: 'token',
        username: 'admin',
        expiresAt: Date.now() + 60_000,
      }),
    )

    clearAuthIfUnauthorized(401)

    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    expect(useRaceStore.getState().authToken).toBeNull()
  })
})
