import { afterEach, describe, expect, it, vi } from 'vitest'
import { resolveTrackAssetUrl } from './trackAssetUrl'

describe('resolveTrackAssetUrl', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('returns the original URL in production', () => {
    vi.stubEnv('DEV', false)
    const url = 'http://localhost:4566/bucket/track.glb?X-Amz-Signature=abc'
    expect(resolveTrackAssetUrl(url)).toBe(url)
  })

  it('rewrites LocalStack URLs through the Vite proxy in dev', () => {
    vi.stubEnv('DEV', true)
    const url = 'http://localhost:4566/f1-tracks/9161/track.glb?version=2'
    expect(resolveTrackAssetUrl(url)).toBe('/track-assets/f1-tracks/9161/track.glb?version=2')
  })

  it('rewrites 127.0.0.1 LocalStack URLs in dev', () => {
    vi.stubEnv('DEV', true)
    const url = 'http://127.0.0.1:4566/bucket/asset.glb'
    expect(resolveTrackAssetUrl(url)).toBe('/track-assets/bucket/asset.glb')
  })

  it('leaves non-LocalStack URLs unchanged in dev', () => {
    vi.stubEnv('DEV', true)
    const url = 'https://cdn.example.com/track.glb'
    expect(resolveTrackAssetUrl(url)).toBe(url)
  })

  it('returns invalid URLs unchanged', () => {
    vi.stubEnv('DEV', true)
    expect(resolveTrackAssetUrl('not-a-url')).toBe('not-a-url')
  })
})
