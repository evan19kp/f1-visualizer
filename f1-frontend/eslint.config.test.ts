import { ESLint } from 'eslint'
import { describe, expect, it } from 'vitest'

describe('ESLint flat config', () => {
  it('loads eslint.config.js for TypeScript React sources', async () => {
    const eslint = new ESLint()
    const config = await eslint.calculateConfigForFile('src/App.tsx')

    expect(config.languageOptions?.parser).toBeDefined()
    expect(config.plugins?.['@typescript-eslint']).toBeDefined()
    expect(config.plugins?.['react-hooks']).toBeDefined()
    // Guard against the v7 flat-config pitfall where the plugin loads but rules stay empty.
    // ESLint exposes severities as numbers: 0 off, 1 warn, 2 error.
    expect(config.rules?.['react-hooks/rules-of-hooks']).toEqual([2])
    expect(config.rules?.['react-hooks/exhaustive-deps']).toEqual([1])
    expect(config.rules?.['react-hooks/set-state-in-effect']).toEqual([2])
  })

  it('lints the project with zero errors and warnings', async () => {
    const eslint = new ESLint({ errorOnUnmatchedPattern: false })
    const results = await eslint.lintFiles(['.'])

    const errors = results.reduce((count, result) => count + result.errorCount, 0)
    const warnings = results.reduce((count, result) => count + result.warningCount, 0)

    expect(errors).toBe(0)
    expect(warnings).toBe(0)
  })
})
