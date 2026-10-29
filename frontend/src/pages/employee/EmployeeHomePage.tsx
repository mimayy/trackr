import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert, LoadingPanel, Pill } from '../../components/ui'
import { useQueue } from '../../components/useOnline'
import { ApiError, api } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { formatTime } from '../../lib/format'
import { isFingerprintSupported } from '../../lib/webauthn'
import type { WorkflowState, WorkflowStatus } from '../../lib/types'

const STATE_TONE: Record<WorkflowState, 'success' | 'info' | 'warning' | 'neutral'> = {
  NOT_STARTED: 'neutral',
  AT_OFFICE: 'success',
  EN_ROUTE_TO_FIELD: 'info',
  AT_FIELD: 'success',
  EN_ROUTE_TO_OFFICE: 'info',
  EN_ROUTE_HOME: 'info',
  DAY_ENDED: 'neutral',
}

/**
 * The employee home screen.
 *
 * Its whole job is to answer one question: what should I do next? The server
 * decides which actions are valid, so exactly one primary button appears - never
 * a row of six event types for the employee to choose between.
 */
export function EmployeeHomePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queue = useQueue()

  const [status, setStatus] = useState<WorkflowStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [fingerprintPossible, setFingerprintPossible] = useState(false)

  useEffect(() => {
    void isFingerprintSupported().then(setFingerprintPossible)
  }, [])

  const load = useCallback(async () => {
    try {
      setStatus(await api.get<WorkflowStatus>('/api/me/status'))
      setError(null)
    } catch (caught) {
      if (caught instanceof ApiError && caught.isOffline) {
        // Offline is a normal state here, not a failure worth alarming anyone
        // about. The recording buttons keep working.
        setError(null)
      } else {
        setError(
          caught instanceof ApiError ? caught.message : 'We could not load your status just now.',
        )
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  if (loading) {
    return <LoadingPanel label="Getting your workday…" />
  }

  const nextAction = status?.nextAction
  const otherActions = (status?.allowedActions ?? []).slice(1)

  return (
    <div className="stack-lg">
      <div>
        <h1 style={{ fontSize: 'var(--text-2xl)' }}>
          {greeting()}, {user?.firstName ?? 'there'}! 👋
        </h1>
        {status && <p className="muted" style={{ marginTop: 6 }}>{status.statusDetail}</p>}
      </div>

      {error && <Alert tone="warning">{error}</Alert>}

      {/* Without this, an employee with no fingerprint set up is simply never
          asked for one, and has no way of knowing why. Shown only when this
          phone could actually do it, and it disappears once it is set up. */}
      {status && fingerprintPossible && !status.fingerprintAvailable && (
        <div className="card stack-sm" style={{ borderColor: 'var(--warning-600)' }}>
          <div className="strong">Set up your fingerprint</div>
          <p className="small muted" style={{ margin: 0 }}>
            Until you do, your activities are recorded with your location and the
            time, but not verified with your fingerprint.
          </p>
          <Link to="/profile" className="btn btn-secondary btn-block">
            SET IT UP NOW
          </Link>
        </div>
      )}

      {/* ---- Work status ---- */}
      {status && (
        <section>
          <div className="card-title">Work status</div>
          <div className="card center" style={{ paddingTop: 'var(--space-6)', paddingBottom: 'var(--space-6)' }}>
            <Pill tone={STATE_TONE[status.state]}>● {status.statusLabel}</Pill>

            {status.workdayStartedAt && (
              <div style={{ marginTop: 'var(--space-4)' }}>
                <div style={{ fontSize: 'var(--text-4xl)', fontWeight: 700, lineHeight: 1.1 }}>
                  {formatTime(status.workdayStartedAt)}
                </div>
                <div className="muted small" style={{ marginTop: 4 }}>Started</div>
              </div>
            )}

            {status.currentLocationName && (
              <div style={{ marginTop: 'var(--space-4)', fontSize: 'var(--text-lg)' }}>
                📍 {status.currentLocationName}
              </div>
            )}
          </div>
        </section>
      )}


      {/* ---- The one next action ---- */}
      <section>
        <div className="card-title">Next action</div>
        {nextAction ? (
          <div className="card stack">
            <div className="center">
              <div style={{ fontSize: 'var(--text-xl)', fontWeight: 700 }}>{nextAction.label}</div>
              <div className="muted" style={{ marginTop: 6 }}>{nextAction.helper}</div>
            </div>

            <button
              type="button"
              className="btn btn-primary btn-hero"
              onClick={() => navigate(`/record/${nextAction.eventType}`)}
            >
              {nextAction.label}
            </button>

            {/* Secondary options only appear when the workflow genuinely allows
                more than one thing - most of the time this is empty. */}
            {otherActions.map((action) => (
              <button
                key={action.eventType}
                type="button"
                className="btn btn-secondary btn-block"
                onClick={() => navigate(`/record/${action.eventType}`)}
              >
                {action.label}
              </button>
            ))}
          </div>
        ) : (
          <div className="card center" style={{ padding: 'var(--space-6)' }}>
            <div style={{ fontSize: '2rem' }} aria-hidden="true">✓</div>
            {/* The greeting above already carries the full sentence, so this
                says the short version rather than repeating it word for word. */}
            <p style={{ marginTop: 'var(--space-3)', fontSize: 'var(--text-lg)' }}>
              {status?.state === 'DAY_ENDED' ? 'Nothing left to do today.' : 'Nothing to do right now.'}
            </p>
          </div>
        )}
      </section>

      {/* ---- Offline queue ---- */}
      {queue.length > 0 && (
        <section>
          <div className="card-title">Waiting to sync</div>
          <Link to="/sync" className="card stack-sm" style={{ display: 'block', textDecoration: 'none', color: 'inherit' }}>
            <div className="row-between">
              <span className="strong">
                {queue.length} {queue.length === 1 ? 'activity' : 'activities'} saved on this phone
              </span>
              <span aria-hidden="true">›</span>
            </div>
            <span className="small muted">
              They will be sent automatically when you're back online.
            </span>
          </Link>
        </section>
      )}

      {/* ---- Today so far ---- */}
      {status && status.todaysEvents.length > 0 && (
        <section>
          <div className="card-title">Today so far</div>
          <div className="card stack-sm">
            {status.todaysEvents.map((event) => (
              <div key={event.id} className="row-between">
                <span className="row" style={{ gap: 10 }}>
                  <span style={{ color: 'var(--success-600)', fontWeight: 700 }} aria-hidden="true">✓</span>
                  <span>{event.eventLabel}</span>
                </span>
                <span className="muted small">{formatTime(event.eventTime)}</span>
              </div>
            ))}
            <Link to="/activities" className="btn btn-ghost btn-block" style={{ marginTop: 4 }}>
              See all activities
            </Link>
          </div>
        </section>
      )}
    </div>
  )
}

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}
