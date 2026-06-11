export const DEFAULT_SESSION_KEY = import.meta.env.VITE_SESSION_KEY ?? '9161'
export const WS_URL = import.meta.env.VITE_WS_URL ?? 'http://localhost:8080/ws'
/** Unset = relative `/api` URLs; Vite dev proxy forwards to localhost:8080. Set VITE_API_URL for prod. */
export const API_URL = import.meta.env.VITE_API_URL ?? ''
