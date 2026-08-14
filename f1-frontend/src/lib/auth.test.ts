import { beforeEach, describe, expect, it, vi } from 'vitest'
import { login } from './auth'
import { useRaceStore } from '../store/raceStore'

beforeEach(() => {
  sessionStorage.clear()
  useRaceStore.getState().setAuthSession(null, null)
})

describe('login', () => {
  it('posts credentials and stores the returned JWT', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ token: 'jwt-token', expiresInMs: 3_600_000 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(login(' admin ', 'secret')).resolves.toBe('jwt-token')
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: 'secret' }),
    })
    expect(useRaceStore.getState().authToken).toBe('jwt-token')
    expect(useRaceStore.getState().authUsername).toBe('admin')
    expect(JSON.parse(sessionStorage.getItem('f1.auth.session') ?? '{}')).toMatchObject({
      token: 'jwt-token',
      username: 'admin',
    })
  })

  it('reports an unsuccessful login without changing auth state', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))

    await expect(login('admin', 'wrong')).rejects.toThrow('Invalid username or password')
    expect(useRaceStore.getState().authToken).toBeNull()
    expect(sessionStorage.getItem('f1.auth.session')).toBeNull()
  })
})
