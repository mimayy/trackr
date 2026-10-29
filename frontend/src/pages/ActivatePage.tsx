import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Alert, LoadingPanel, Spinner } from '../components/ui'
import { ApiError, api } from '../lib/api'
import { useAuth } from '../lib/auth'
import type { ActivationDetails, SessionResponse } from '../lib/types'
import { isFingerprintSupported, registerFingerprint } from '../lib/webauthn'

type Step = 'loading' | 'welcome' | 'password' | 'fingerprint' | 'done' | 'invalid'

/**
 * Activation, in three short screens.
 *
 * The employee does not choose a branch, a supervisor, a work category or a
 * schedule - the administrator already did. All they do is confirm who they
 * are, choose a password and register their fingerprint.
 */
export function ActivatePage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { adoptSession } = useAuth()

  const token = params.get('token') ?? ''

  const [step, setStep] = useState<Step>('loading')
  const [details, setDetails] = useState<ActivationDetails | null>(null)
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [fingerprintPossible, setFingerprintPossible] = useState(false)

  useEffect(() => {
    if (!token) {
      setStep('invalid')
      setError('This activation link is missing information. Please ask your administrator for a new one.')
      return
    }

    let cancelled = false

    async function load() {
      try {
        const found = await api.anonymousGet<ActivationDetails>(
          `/api/activation/details?token=${encodeURIComponent(token)}`,
        )
        if (!cancelled) {
          setDetails(found)
          setStep('welcome')
        }
      } catch (caught) {
        if (!cancelled) {
          setStep('invalid')
          setError(
            caught instanceof ApiError
              ? caught.message
              : 'This activation link is not valid. Please ask your administrator for a new one.',
          )
        }
      }
    }

    void load()
    void isFingerprintSupported().then((supported) => {
      if (!cancelled) {
        setFingerprintPossible(supported)
      }
    })

    return () => {
      cancelled = true
    }
  }, [token])

  async function handleCreateAccount() {
    setError(null)

    if (password.length < 10) {
      setError('Please choose a password with at least 10 characters.')
      return
    }
    if (password !== confirmPassword) {
      setError('The two passwords do not match. Please type them again.')
      return
    }

    setBusy(true)
    try {
      const session = await api.anonymousPost<SessionResponse>('/api/activation/complete', {
        token,
        password,
      })
      // Activation signs the employee straight in, so there is no second login
      // screen between the link and registering a fingerprint.
      adoptSession(session)
      setStep('fingerprint')
    } catch (caught) {
      setError(
        caught instanceof ApiError ? caught.message : 'We could not finish setting up your account.',
      )
    } finally {
      setBusy(false)
    }
  }

  async function handleRegisterFingerprint() {
    setError(null)
    setBusy(true)
    try {
      await registerFingerprint()
      setStep('done')
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : "Your phone did not finish the fingerprint setup. You can try again, or skip and set it up later from your Profile.",
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100dvh',
        background: 'var(--page-bg)',
        padding: 'var(--space-6) var(--space-4)',
      }}
    >
      <div style={{ maxWidth: 460, margin: '0 auto' }}>
        <div className="center" style={{ marginBottom: 'var(--space-6)' }}>
          <div style={{ fontSize: 'var(--text-xl)', fontWeight: 700, color: 'var(--brand-700)' }}>
            Trackr@Uni-Ace
          </div>
        </div>

        {step === 'loading' && <LoadingPanel label="Checking your link…" />}

        {step === 'invalid' && (
          <div className="card stack">
            <Alert tone="error" title="This link cannot be used">
              {error}
            </Alert>
            <button type="button" className="btn btn-secondary btn-block" onClick={() => navigate('/login')}>
              Go to sign in
            </button>
          </div>
        )}

        {step === 'welcome' && details && (
          <div className="card stack-lg">
            <div>
              <h1 style={{ fontSize: 'var(--text-3xl)' }}>Welcome, {details.firstName}! 👋</h1>
              <p style={{ marginTop: 'var(--space-3)', fontSize: 'var(--text-lg)' }}>
                Your Uni-Ace account has been created.
              </p>
            </div>

            <div className="card card-tight" style={{ background: 'var(--brand-050)', border: 'none' }}>
              <div className="stack-sm">
                <SummaryLine label="Name" value={details.fullName} />
                {details.employeeNo && <SummaryLine label="Employee ID" value={details.employeeNo} />}
                {details.branch && <SummaryLine label="Branch" value={titleFor(details.branch)} />}
                {details.workCategory && (
                  <SummaryLine
                    label="Work category"
                    value={details.workCategory === 'FIELD_BASED' ? 'Field-based' : 'Office-based'}
                  />
                )}
                {details.supervisorName && (
                  <SummaryLine label="Supervisor" value={details.supervisorName} />
                )}
              </div>
            </div>

            <button
              type="button"
              className="btn btn-primary btn-hero"
              onClick={() => setStep('password')}
            >
              ACTIVATE ACCOUNT
            </button>
          </div>
        )}

        {step === 'password' && (
          <div className="card stack-lg">
            <div>
              <h1>Choose a password</h1>
              <p className="muted" style={{ marginTop: 8 }}>
                You will use this with your email to sign in.
              </p>
            </div>

            {error && <Alert tone="error">{error}</Alert>}

            <div className="field">
              <label className="label" htmlFor="new-password">New password</label>
              <input
                id="new-password"
                className="input"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              <span className="field-hint">At least 10 characters.</span>
            </div>

            <div className="field">
              <label className="label" htmlFor="confirm-password">Type it again</label>
              <input
                id="confirm-password"
                className="input"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
            </div>

            <label className="checkbox-row">
              <input
                type="checkbox"
                checked={showPassword}
                onChange={(event) => setShowPassword(event.target.checked)}
              />
              <span>Show passwords</span>
            </label>

            <button
              type="button"
              className="btn btn-primary btn-hero"
              onClick={handleCreateAccount}
              disabled={busy}
            >
              {busy ? <Spinner light label="Saving…" /> : 'CONTINUE'}
            </button>
          </div>
        )}

        {step === 'fingerprint' && (
          <div className="card stack-lg">
            <div>
              <h1>Secure your account</h1>
              <p style={{ marginTop: 'var(--space-3)', fontSize: 'var(--text-lg)' }}>
                Use your phone's fingerprint to verify important work activities.
              </p>
            </div>

            {error && <Alert tone="warning">{error}</Alert>}

            {/* Honesty about what the platform can do: if the browser has no
                fingerprint support, say so rather than showing a button that
                will fail. */}
            {!fingerprintPossible && (
              <Alert tone="info" title="Fingerprint is not available on this device">
                You can still use Trackr normally. Your activities will be recorded
                with your location and the time, and marked as not
                fingerprint-verified.
              </Alert>
            )}

            <div className="card card-tight" style={{ background: 'var(--ink-050)', border: 'none' }}>
              <p className="small muted" style={{ margin: 0 }}>
                Your fingerprint never leaves your phone. Trackr only receives
                confirmation from your phone that it recognised you.
              </p>
            </div>

            {fingerprintPossible && (
              <button
                type="button"
                className="btn btn-primary btn-hero"
                onClick={handleRegisterFingerprint}
                disabled={busy}
              >
                {busy ? <Spinner light label="Waiting for your phone…" /> : 'REGISTER FINGERPRINT'}
              </button>
            )}

            <button
              type="button"
              className="btn btn-secondary btn-block"
              onClick={() => setStep('done')}
              disabled={busy}
            >
              {fingerprintPossible ? 'Skip for now' : 'Continue'}
            </button>
          </div>
        )}

        {step === 'done' && (
          <div className="card stack-lg center">
            <div style={{ fontSize: '3rem' }} aria-hidden="true">✓</div>
            <div>
              <h1>You're all set!</h1>
              <p style={{ marginTop: 'var(--space-3)', fontSize: 'var(--text-lg)' }}>
                Your account is ready.
              </p>
            </div>
            <button
              type="button"
              className="btn btn-primary btn-hero"
              onClick={() => navigate('/home', { replace: true })}
            >
              GET STARTED
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

function SummaryLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="row-between">
      <span className="small muted">{label}</span>
      <span className="strong">{value}</span>
    </div>
  )
}

function titleFor(branch: string): string {
  return branch.charAt(0) + branch.slice(1).toLowerCase()
}
