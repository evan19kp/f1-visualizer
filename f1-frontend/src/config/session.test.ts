import { afterEach, describe, expect, it } from 'vitest'
import {
  DEFAULT_SESSION_KEY,
  SESSION_KEY_STORAGE,
  getStoredSessionKey,
  isValidSessionKey,
  persistSessionKey,
  resolveInitialSessionKey,
} from './session'

describe('isValidSessionKey', () => {
  it('accepts positive integer strings', () => {
    expect(isValidSessionKey('9161')).toBe(true)
    expect(isValidSessionKey(' 42 ')).toBe(true)
  })

  it('rejects empty and non-numeric values', () => {
    expect(isValidSessionKey('')).toBe(false)
    expect(isValidSessionKey('   ')).toBe(false)
    expect(isValidSessionKey('abc')).toBe(false)
    expect(isValidSessionKey('9161a')).toBe(false)
  })
})

describe('session key persistence', () => {
  afterEach(() => {
    localStorage.clear()
  })

  it('reads a valid stored session key', () => {
    localStorage.setItem(SESSION_KEY_STORAGE, '9161')
    expect(getStoredSessionKey()).toBe('9161')
  })

  it('ignores invalid stored values', () => {
    localStorage.setItem(SESSION_KEY_STORAGE, 'invalid')
    expect(getStoredSessionKey()).toBeNull()
  })

  it('persists and resolves the initial session key', () => {
    persistSessionKey('1234')
    expect(resolveInitialSessionKey()).toBe('1234')
  })

  it('falls back to the default when nothing is stored', () => {
    expect(resolveInitialSessionKey()).toBe(DEFAULT_SESSION_KEY)
  })
})
