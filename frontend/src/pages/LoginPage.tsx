import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Spinner } from '../components/ui'
import { ApiError } from '../lib/api'
import { homePathFor, useAuth } from '../lib/auth'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)

    try {
      const user = await login(email.trim(), password)
      navigate(homePathFor(user), { replace: true })
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : 'We could not sign you in. Please try again.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 'var(--space-5) var(--space-4)',
        background: 'linear-gradient(170deg, var(--brand-800) 0%, var(--brand-900) 100%)',
      }}
    >
      <div style={{ width: '100%', maxWidth: 420 }}>
        <div className="center" style={{ marginBottom: 'var(--space-6)', color: 'var(--white)' }}>
          <div style={{ fontSize: 'var(--text-3xl)', fontWeight: 700, letterSpacing: '-0.02em' }}>
            Trackr@Uni-Ace
          </div>
          <div style={{ opacity: 0.85, marginTop: 6 }}>Uni-Ace Adjusters Inc.</div>
        </div>

        <div className="card">
          <h1 style={{ marginBottom: 4 }}>Sign in</h1>
          <p className="muted small" style={{ marginBottom: 'var(--space-5)' }}>
            Use the email address your administrator set up for you.
          </p>

          <form onSubmit={handleSubmit} className="stack">
            {error && <Alert tone="error">{error}</Alert>}

            <div className="field">
              <label className="label" htmlFor="email">Email</label>
              <input
                id="email"
                className="input"
                type="email"
                inputMode="email"
                autoComplete="username"
                autoCapitalize="none"
                autoCorrect="off"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="you@uni-ace.example"
              />
            </div>

            <div className="field">
              <label className="label" htmlFor="password">Password</label>
              <input
                id="password"
                className="input"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              {/* Typing a password on a phone is error-prone, so let people
                  check what they typed rather than guess. */}
              <label className="checkbox-row" style={{ minHeight: 36 }}>
                <input
                  type="checkbox"
                  checked={showPassword}
                  onChange={(event) => setShowPassword(event.target.checked)}
                />
                <span className="small">Show password</span>
              </label>
            </div>

            <button type="submit" className="btn btn-primary btn-hero" disabled={busy}>
              {busy ? <Spinner light label="Signing in…" /> : 'SIGN IN'}
            </button>
          </form>

          <hr className="divider" />

          <p className="small muted" style={{ margin: 0 }}>
            New here? Open the activation link your administrator sent you to set
            up your account and fingerprint.
          </p>
        </div>
      </div>
    </div>
  )
}
