import { useState, type FormEvent } from 'react'
import { login, logout } from '../../lib/auth'
import { useRaceStore } from '../../store/raceStore'

export function AuthPanel(): React.JSX.Element {
  const authToken = useRaceStore((s) => s.authToken)
  const authUsername = useRaceStore((s) => s.authUsername)
  const [expanded, setExpanded] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleLogout = (): void => {
    logout()
    setUsername('')
    setPassword('')
    setExpanded(false)
    setError(null)
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault()
    setLoading(true)
    setError(null)
    try {
      await login(username, password)
      setPassword('')
      setExpanded(false)
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  if (authToken) {
    return (
      <div className="flex items-center gap-2">
        <span className="text-xs text-zinc-400">
          Signed in as <span className="text-zinc-200">{authUsername ?? 'user'}</span>
        </span>
        <button
          type="button"
          onClick={handleLogout}
          className="rounded border border-zinc-700 px-2 py-1 text-xs text-zinc-200 transition-colors hover:border-zinc-500 hover:bg-zinc-800"
        >
          Log out
        </button>
      </div>
    )
  }

  if (!expanded) {
    return (
      <button
        type="button"
        onClick={() => setExpanded(true)}
        className="rounded border border-zinc-700 px-2 py-1 text-xs text-zinc-200 transition-colors hover:border-zinc-500 hover:bg-zinc-800"
      >
        Log in
      </button>
    )
  }

  return (
    <form
      onSubmit={(event) => void handleSubmit(event)}
      className="flex flex-wrap items-center gap-2"
      aria-label="Login"
    >
      <input
        type="text"
        name="username"
        value={username}
        onChange={(event) => setUsername(event.target.value)}
        placeholder="Username"
        autoComplete="username"
        disabled={loading}
        className="rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-xs text-zinc-100 placeholder:text-zinc-500 disabled:opacity-50"
      />
      <input
        type="password"
        name="password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        placeholder="Password"
        autoComplete="current-password"
        disabled={loading}
        className="rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-xs text-zinc-100 placeholder:text-zinc-500 disabled:opacity-50"
      />
      <button
        type="submit"
        disabled={loading}
        className="rounded border border-f1red/70 px-2 py-1 text-xs font-medium text-f1red transition-colors enabled:hover:border-f1red enabled:hover:bg-f1red/10 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {loading ? 'Signing in…' : 'Sign in'}
      </button>
      <button
        type="button"
        onClick={() => {
          setExpanded(false)
          setError(null)
        }}
        disabled={loading}
        className="rounded border border-zinc-700 px-2 py-1 text-xs text-zinc-400 transition-colors enabled:hover:border-zinc-500 enabled:hover:bg-zinc-800 disabled:opacity-50"
      >
        Cancel
      </button>
      {error ? (
        <p className="text-xs text-red-400" role="alert">
          {error}
        </p>
      ) : null}
    </form>
  )
}
