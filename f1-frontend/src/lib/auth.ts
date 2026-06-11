import { API_URL } from '../config/session'
import { useRaceStore } from '../store/raceStore'

interface LoginResponse {
  token: string
  expiresInMs: number
}

export async function login(username: string, password: string): Promise<string> {
  const response = await fetch(`${API_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })

  if (!response.ok) {
    throw new Error(`Login failed: ${response.status}`)
  }

  const data = (await response.json()) as LoginResponse
  useRaceStore.getState().setAuthToken(data.token)
  return data.token
}

/** Dev-only auto-login when explicitly opted in via VITE_DEV_AUTOLOGIN. */
export async function ensureDevAuth(): Promise<void> {
  if (!import.meta.env.DEV) {
    return
  }
  if (useRaceStore.getState().authToken) {
    return
  }
  if (import.meta.env.VITE_DEV_AUTOLOGIN !== 'true') {
    return
  }
  const username = import.meta.env.VITE_DEV_AUTH_USER
  const password = import.meta.env.VITE_DEV_AUTH_PASS
  if (!username || !password) {
    console.warn(
      'Dev auto-login enabled but VITE_DEV_AUTH_USER / VITE_DEV_AUTH_PASS are not set',
    )
    return
  }
  try {
    await login(username, password)
  } catch (error) {
    console.warn('Dev auto-login failed:', error)
  }
}
