export const DEFAULT_SESSION_KEY = import.meta.env.VITE_SESSION_KEY ?? '9161'
export const SESSION_KEY_STORAGE = 'f1.sessionKey'
export const WS_URL = import.meta.env.VITE_WS_URL ?? 'http://localhost:8080/ws'
/** Unset = relative `/api` URLs; Vite dev proxy forwards to localhost:8080. Set VITE_API_URL for prod. */
export const API_URL = import.meta.env.VITE_API_URL ?? ''

export function isValidSessionKey(value: string): boolean {
  const trimmed = value.trim()
  return trimmed.length > 0 && /^\d+$/.test(trimmed)
}

export function getStoredSessionKey(): string | null {
  try {
    const stored = localStorage.getItem(SESSION_KEY_STORAGE)
    if (stored && isValidSessionKey(stored)) {
      return stored.trim()
    }
  } catch {
    /* storage unavailable (e.g. private browsing) */
  }
  return null
}

export function persistSessionKey(key: string): void {
  try {
    localStorage.setItem(SESSION_KEY_STORAGE, key)
  } catch {
    /* storage unavailable */
  }
}

export function resolveInitialSessionKey(): string {
  return getStoredSessionKey() ?? DEFAULT_SESSION_KEY
}
