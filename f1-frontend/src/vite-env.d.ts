/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_SESSION_KEY?: string
  readonly VITE_WS_URL?: string
  readonly VITE_API_URL?: string
  readonly VITE_DEV_AUTOLOGIN?: string
  readonly VITE_DEV_AUTH_USER?: string
  readonly VITE_DEV_AUTH_PASS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
