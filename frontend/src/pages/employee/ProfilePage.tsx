import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert, Spinner } from '../../components/ui'
import { useQueue } from '../../components/useOnline'
import { ApiError } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { isFingerprintSupported, registerFingerprint } from '../../lib/webauthn'

/** The employee's own details, their registered phone, and how to sign out. */
export function ProfilePage() {
  const { user, logout, refreshUser } = useAuth()
  const navigate = useNavigate()
  const queue = useQueue()

  const [fingerprintPossible, setFingerprintPossible] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void isFingerprintSupported().then(setFingerprintPossible)
  }, [])

  async function handleRegister() {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await registerFingerprint()
      await refreshUser()
      setMessage('Your fingerprint is set up on this phone.')
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : 'Your phone did not finish the fingerprint setup. Please try again.',
      )
    } finally {
      setBusy(false)
    }
  }

  async function handleSignOut() {
    if (queue.length > 0) {
      const proceed = window.confirm(
        `You have ${queue.length} ${queue.length === 1 ? 'activity' : 'activities'} still waiting to be sent. ` +
          'They stay saved on this phone. Sign out anyway?',
      )
      if (!proceed) return
    }
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="stack-lg">
      <h1>My profile</h1>

      {message && <Alert tone="success">{message}</Alert>}
      {error && <Alert tone="error">{error}</Alert>}

      <div className="card stack-sm">
        <Line label="Name" value={user?.displayName} />
        {user?.employeeNo && <Line label="Employee ID" value={user.employeeNo} />}
        <Line label="Email" value={user?.email} />
        {user?.branch && <Line label="Branch" value={titleCase(user.branch)} />}
        {user?.workCategory && (
          <Line
            label="Work category"
            value={user.workCategory === 'FIELD_BASED' ? 'Field-based' : 'Office-based'}
          />
        )}
        {user?.supervisorName && <Line label="Supervisor" value={user.supervisorName} />}
        {user?.scheduleName && <Line label="Work schedule" value={user.scheduleName} />}
      </div>

      <section>
        <div className="card-title">This phone</div>
        <div className="card stack">
          <div className="row-between">
            <span>Fingerprint</span>
            {user?.deviceRegistered
              ? <span className="pill pill-success">Set up</span>
              : <span className="pill pill-warning">Not set up</span>}
          </div>

          {user?.deviceRegistered && (
            <p className="small muted" style={{ margin: 0 }}>
              {user.deviceLabel ?? 'Registered device'}. Your fingerprint stays on
              your phone — Trackr never receives it.
            </p>
          )}

          {!fingerprintPossible ? (
            <p className="small muted" style={{ margin: 0 }}>
              This phone's browser cannot use a fingerprint for Trackr. Your
              activities are still recorded with your location and the time.
            </p>
          ) : (
            <>
              {/* Offered whether or not a device is already registered. Someone
                  on a new phone needs this, and so does anyone whose old phone
                  is gone - waiting on an administrator to notice would leave
                  them unable to verify anything in the meantime. */}
              <button
                type="button"
                className={user?.deviceRegistered ? 'btn btn-secondary btn-hero' : 'btn btn-primary btn-hero'}
                onClick={handleRegister}
                disabled={busy}
              >
                {busy
                  ? <Spinner label="Waiting for your phone…" />
                  : user?.deviceRegistered ? 'USE THIS PHONE INSTEAD' : 'SET UP FINGERPRINT'}
              </button>

              {user?.deviceRegistered && (
                <p className="small muted" style={{ margin: 0 }}>
                  Got a new phone? Set it up here. Your old phone stops being able
                  to verify activities, and everything you have already recorded
                  stays exactly as it is.
                </p>
              )}
            </>
          )}
        </div>
      </section>

      <section>
        <div className="card-title">Shortcuts</div>
        <div className="card stack-sm">
          <Link to="/my-map" className="btn btn-secondary btn-block">My recorded locations</Link>
          <Link to="/sync" className="btn btn-secondary btn-block">Sync status</Link>
          <Link to="/help" className="btn btn-secondary btn-block">Help</Link>
        </div>
      </section>

      <section>
        <div className="card-title">Your privacy</div>
        <div className="card">
          <p className="small" style={{ margin: 0 }}>
            Trackr records your location <strong>only</strong> at the moment you
            tap an activity such as Time In or Field Arrival. It does not follow
            you during the day and does not run in the background. Your
            fingerprint never leaves your phone. Your supervisor and the
            administrator can see the activities you record; other employees
            cannot.
          </p>
        </div>
      </section>

      <button type="button" className="btn btn-secondary btn-hero" onClick={handleSignOut}>
        SIGN OUT
      </button>
    </div>
  )
}

function Line({ label, value }: { label: string; value?: string | null }) {
  if (!value) return null
  return (
    <div className="row-between">
      <span className="small muted">{label}</span>
      <span className="strong">{value}</span>
    </div>
  )
}

function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase()
}
