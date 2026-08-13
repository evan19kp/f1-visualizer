import { API_URL } from '../config/session'
import { useRaceStore } from '../store/raceStore'

interface LoginResponse {
  token: string
  expiresInMs: number
}

interface StoredAuthSession {
  token: string
  username: string
  expiresAt: number
}

const AUTH_STORAGE_KEY = 'f1.auth.session'

function readStoredSession(): StoredAuthSession | null {
  try {
    const raw = sessionStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as StoredAuthSession
    if (
      typeof parsed.token !== 'string' ||
      typeof parsed.username !== 'string' ||
      typeof parsed.expiresAt !== 'number'
    ) {
      return null
    }
    if (parsed.expiresAt <= Date.now()) {
      sessionStorage.removeItem(AUTH_STORAGE_KEY)
      return null
    }
    return parsed
  } catch {
    sessionStorage.removeItem(AUTH_STORAGE_KEY)
    return null
  }
}

function persistSession(session: StoredAuthSession): void {
  sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
}

export function restoreAuthFromStorage(): void {
  const stored = readStoredSession()
  if (stored) {
    useRaceStore.getState().setAuthSession(stored.token, stored.username)
  }
}

export function logout(): void {
  sessionStorage.removeItem(AUTH_STORAGE_KEY)
  useRaceStore.getState().setAuthSession(null, null)
}

export async function login(username: string, password: string): Promise<string> {
  const trimmedUsername = username.trim()
  if (!trimmedUsername || !password) {
    throw new Error('Username and password are required')
  }

  const response = await fetch(`${API_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: trimmedUsername, password }),
  })

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Invalid username or password')
    }
    throw new Error(`Login failed: ${response.status}`)
  }

  const data = (await response.json()) as LoginResponse
  const expiresAt = Date.now() + data.expiresInMs
  persistSession({
    token: data.token,
    username: trimmedUsername,
    expiresAt,
  })
  useRaceStore.getState().setAuthSession(data.token, trimmedUsername)
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

export function clearAuthIfUnauthorized(status: number): void {
  if (status === 401) {
    logout()
  }
}
