import { describe, expect, it } from 'vitest'
import { trackAssetUrlForSession } from './trackAssetUrl'

describe('trackAssetUrlForSession', () => {
  it('returns null when the loaded asset belongs to a different session', () => {
    expect(
      trackAssetUrlForSession('222', { sessionKey: '111', url: 'https://example.test/old.glb' }),
    ).toBeNull()
  })

  it('returns the url when the loaded asset matches the active session', () => {
    expect(
      trackAssetUrlForSession('111', { sessionKey: '111', url: 'https://example.test/track.glb' }),
    ).toBe('https://example.test/track.glb')
  })

  it('returns null when session key or loaded asset is missing', () => {
    expect(trackAssetUrlForSession('', { sessionKey: '111', url: 'https://example.test/a.glb' })).toBeNull()
    expect(trackAssetUrlForSession('111', null)).toBeNull()
  })
})
