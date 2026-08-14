/** Return a loaded track asset URL only when it belongs to the active session. */
export function trackAssetUrlForSession(
  sessionKey: string,
  loaded: { sessionKey: string; url: string } | null,
): string | null {
  if (!sessionKey || loaded == null || loaded.sessionKey !== sessionKey) {
    return null
  }
  return loaded.url
}

/** In dev, route LocalStack presigned URLs through the Vite proxy to avoid browser CORS blocks. */
export function resolveTrackAssetUrl(url: string): string {
  if (!import.meta.env.DEV) {
    return url
  }

  try {
    const parsed = new URL(url)
    const isLocalStack =
      (parsed.hostname === 'localhost' || parsed.hostname === '127.0.0.1') &&
      parsed.port === '4566'

    if (isLocalStack) {
      return `/track-assets${parsed.pathname}${parsed.search}`
    }
  } catch {
    /* keep original url */
  }

  return url
}
