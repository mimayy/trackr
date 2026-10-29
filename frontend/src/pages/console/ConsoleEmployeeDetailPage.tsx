import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { MapView, type MapPin } from '../../components/MapView'
import { AccountStatusPill, Alert, LoadingPanel, Modal, Pill, Spinner } from '../../components/ui'
import { ApiError, api, query } from '../../lib/api'
import { formatDate, formatDateTime, formatTime, todayInZone } from '../../lib/format'
import type { EmployeeDetail, InvitationResponse, StaffEventView } from '../../lib/types'
import { useConsole } from './useConsole'

/**
 * One employee: their profile, their registered device, and the activity they
 * have recorded.
 *
 * The security panel is the administrator's tool for a lost or replaced phone.
 * Revoking a device never touches the account or the attendance history - it
 * only stops that phone from verifying anything new.
 */
export function ConsoleEmployeeDetailPage() {
  const { id } = useParams<{ id: string }>()
  const console = useConsole()

  const [detail, setDetail] = useState<EmployeeDetail | null>(null)
  const [events, setEvents] = useState<StaffEventView[]>([])
  const [date, setDate] = useState(todayInZone())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [invitation, setInvitation] = useState<InvitationResponse | null>(null)
  const [confirming, setConfirming] = useState<'revoke' | 'reset' | 'disable' | null>(null)

  const load = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const path = console.canManage
        ? `${console.adminApi}/employees/${id}`
        : `${console.api}/employees/${id}`
      setDetail(await api.get<EmployeeDetail>(path))
      setError(null)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'We could not load this employee.')
    } finally {
      setLoading(false)
    }
  }, [console.adminApi, console.api, console.canManage, id])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!id) return
    let cancelled = false

    async function loadEvents() {
      try {
        const found = await api.get<StaffEventView[]>(
          `${console.api}/employees/${id}/work-events${query({ date })}`,
        )
        if (!cancelled) setEvents(found)
      } catch {
        if (!cancelled) setEvents([])
      }
    }

    void loadEvents()
    return () => {
      cancelled = true
    }
  }, [console.api, date, id])

  async function act(action: 'revoke' | 'reset' | 'disable' | 'enable' | 'invite') {
    if (!id) return
    setBusy(true)
    setNotice(null)
    setError(null)

    try {
      switch (action) {
        case 'invite': {
          const created = await api.post<InvitationResponse>(
            `${console.adminApi}/employees/${id}/invitation`,
          )
          setInvitation(created)
          break
        }
        case 'revoke':
          await api.post(`${console.adminApi}/employees/${id}/device/revoke`, {
            reason: 'Revoked by administrator',
          })
          setNotice('The registered device has been revoked. The account is still active.')
          break
        case 'reset': {
          const result = await api.post<{ invitation: InvitationResponse }>(
            `${console.adminApi}/employees/${id}/device/reset`,
            { reason: 'Device reset by administrator' },
          )
          setInvitation(result.invitation)
          setNotice('Device reset. Send the new activation link so the employee can register their phone.')
          break
        }
        case 'disable':
          await api.post(`${console.adminApi}/employees/${id}/disable`, { reason: 'Disabled by administrator' })
          setNotice('This employee can no longer sign in. Their records are unchanged.')
          break
        case 'enable':
          await api.post(`${console.adminApi}/employees/${id}/enable`)
          setNotice('This employee can sign in again.')
          break
      }
      await load()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'That action could not be completed.')
    } finally {
      setBusy(false)
      setConfirming(null)
    }
  }

  if (loading) return <LoadingPanel />
  if (!detail) {
    return <Alert tone="error">{error ?? 'That employee could not be found.'}</Alert>
  }

  const summary = detail.summary
  const pins: MapPin[] = events
    .filter((event) => event.latitude !== undefined && event.longitude !== undefined)
    .map((event) => ({
      id: event.id,
      latitude: event.latitude as number,
      longitude: event.longitude as number,
      title: event.eventLabel,
      subtitle: `${formatTime(event.eventTime)}${event.location ? ` · ${event.location.name}` : ''}`,
      detail: (
        <>
          <div>GPS accuracy: {event.gpsAccuracyM ? `${Math.round(event.gpsAccuracyM)} m` : 'unknown'}</div>
          <div>Verification: {event.biometricVerified ? 'Fingerprint verified' : 'Not fingerprint-verified'}</div>
        </>
      ),
      tone: event.withinGeofence === false ? 'warning' : 'brand',
    }))

  return (
    <div className="stack-lg">
      <div className="page-header">
        <div>
          <Link className="btn btn-ghost btn-sm" to={`${console.base}/employees`}>← All employees</Link>
          <h1 className="page-title" style={{ marginTop: 8 }}>{summary.fullName}</h1>
          <p className="page-subtitle">
            {summary.employeeNo} · {summary.branch === 'MANILA' ? 'Manila' : 'Cebu'} ·{' '}
            {summary.workCategory === 'FIELD_BASED' ? 'Field-based' : 'Office-based'}
          </p>
        </div>
        <div className="row row-wrap">
          <AccountStatusPill status={summary.accountStatus} />
          {console.canManage && (
            <Link className="btn btn-secondary btn-sm" to={`${console.base}/employees/${id}/edit`}>
              Edit
            </Link>
          )}
        </div>
      </div>

      {notice && <Alert tone="success">{notice}</Alert>}
      {error && <Alert tone="error">{error}</Alert>}

      {invitation && (
        <Alert tone="info" title="Activation link ready">
          <p className="small" style={{ marginBottom: 8 }}>
            This link works once and expires {formatDateTime(invitation.expiresAt)}. It is shown
            only now — it cannot be looked up again.
          </p>
          <input className="input" readOnly value={invitation.activationUrl} onFocus={(e) => e.target.select()} />
          <div className="row" style={{ marginTop: 8 }}>
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => void navigator.clipboard?.writeText(invitation.activationUrl)}
            >
              Copy activation link
            </button>
            {invitation.emailNote && <span className="small muted">{invitation.emailNote}</span>}
          </div>
        </Alert>
      )}

      <div className="grid-form">
        <section className="card stack-sm">
          <div className="card-title" style={{ marginBottom: 0 }}>Profile</div>
          <Line label="Email" value={summary.email} />
          <Line label="Contact" value={summary.contactNumber} />
          <Line label="Supervisor" value={summary.supervisorName ?? 'Not assigned'} />
          <Line label="Work schedule" value={summary.scheduleName ?? 'Not assigned'} />
          <Line label="Home office" value={detail.homeOfficeName} />
          <Line label="Hired" value={detail.hiredOn ? formatDate(detail.hiredOn) : undefined} />
          <Line label="Last signed in" value={detail.lastLoginAt ? formatDateTime(detail.lastLoginAt) : 'Never'} />
        </section>

        <section className="card stack">
          <div className="card-title" style={{ marginBottom: 0 }}>Security</div>

          {detail.device ? (
            <div className="stack-sm">
              <div className="row-between">
                <span className="small muted">Registered device</span>
                <span className="strong">{detail.device.deviceLabel ?? 'Registered phone'}</span>
              </div>
              <div className="row-between">
                <span className="small muted">Credential</span>
                <Pill tone={detail.device.status === 'ACTIVE' ? 'success' : 'danger'}>
                  {detail.device.status === 'ACTIVE' ? 'Active' : 'Revoked'}
                </Pill>
              </div>
              <Line label="Registered" value={formatDateTime(detail.device.registeredAt)} />
              <Line
                label="Last used"
                value={detail.device.lastUsedAt ? formatDateTime(detail.device.lastUsedAt) : 'Never used'}
              />
              {detail.device.revokedReason && (
                <Line label="Revoked reason" value={detail.device.revokedReason} />
              )}
            </div>
          ) : (
            <p className="muted" style={{ margin: 0 }}>
              No device registered yet. The employee registers their own phone during activation.
            </p>
          )}

          {console.canManage && (
            <div className="row row-wrap" style={{ marginTop: 'var(--space-2)' }}>
              {detail.device?.status === 'ACTIVE' && (
                <button
                  type="button"
                  className="btn btn-danger btn-sm"
                  onClick={() => setConfirming('revoke')}
                  disabled={busy}
                >
                  Revoke device
                </button>
              )}
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={() => setConfirming('reset')}
                disabled={busy}
              >
                Reset device credential
              </button>
              {summary.accountStatus !== 'ACTIVE' && (
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => void act('invite')}
                  disabled={busy}
                >
                  {busy ? <Spinner label="Working…" /> : 'Generate activation link'}
                </button>
              )}
              {summary.accountStatus === 'DISABLED' ? (
                <button
                  type="button"
                  className="btn btn-success btn-sm"
                  onClick={() => void act('enable')}
                  disabled={busy}
                >
                  Enable account
                </button>
              ) : (
                <button
                  type="button"
                  className="btn btn-danger btn-sm"
                  onClick={() => setConfirming('disable')}
                  disabled={busy}
                >
                  Disable account
                </button>
              )}
            </div>
          )}
        </section>
      </div>

      <section>
        <div className="row-between" style={{ marginBottom: 'var(--space-3)' }}>
          <h2>Work events</h2>
          <div className="field" style={{ minWidth: 180 }}>
            <label className="label" htmlFor="event-date">Date</label>
            <input
              id="event-date"
              className="input"
              type="date"
              value={date}
              max={todayInZone()}
              onChange={(event) => setDate(event.target.value)}
            />
          </div>
        </div>

        {pins.length > 0 && (
          <div className="map-panel" style={{ marginBottom: 'var(--space-4)' }}>
            <MapView pins={pins} height="100%" />
          </div>
        )}

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Event</th>
                <th>Location</th>
                <th>Accuracy</th>
                <th>Geofence</th>
                <th>Fingerprint</th>
                <th>Captured</th>
                <th>Sync</th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.id}>
                  <td className="strong">{formatTime(event.eventTime)}</td>
                  <td>{event.eventLabel}</td>
                  <td>
                    {event.location?.name ?? <span className="muted">Not recognised</span>}
                    {event.destinationName && (
                      <div className="tiny muted">→ {event.destinationName}</div>
                    )}
                  </td>
                  <td>{event.gpsAccuracyM ? `${Math.round(event.gpsAccuracyM)} m` : '—'}</td>
                  <td>
                    {event.withinGeofence === undefined ? (
                      <span className="muted">—</span>
                    ) : event.withinGeofence ? (
                      <Pill tone="success">Inside</Pill>
                    ) : (
                      <Pill tone="warning">
                        {event.distanceToLocationM ? `${Math.round(event.distanceToLocationM)} m away` : 'Outside'}
                      </Pill>
                    )}
                  </td>
                  <td>
                    {event.biometricVerified ? (
                      <Pill tone="success">Verified</Pill>
                    ) : (
                      <Pill tone="neutral">Not verified</Pill>
                    )}
                  </td>
                  <td>{event.captureMode === 'OFFLINE' ? 'Offline' : 'Online'}</td>
                  <td>
                    {event.reviewStatus === 'REQUIRES_REVIEW' ? (
                      <Pill tone="warning">Needs review</Pill>
                    ) : (
                      <Pill tone="success">Synced</Pill>
                    )}
                  </td>
                </tr>
              ))}
              {events.length === 0 && (
                <tr>
                  <td colSpan={8} className="table-empty">Nothing recorded on this date.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {confirming && (
        <Modal
          title={
            confirming === 'revoke'
              ? 'Revoke this device?'
              : confirming === 'reset'
                ? 'Reset device credential?'
                : 'Disable this account?'
          }
          onClose={() => setConfirming(null)}
          footer={
            <>
              <button type="button" className="btn btn-secondary" onClick={() => setConfirming(null)}>
                Cancel
              </button>
              <button
                type="button"
                className={confirming === 'reset' ? 'btn btn-primary' : 'btn btn-danger'}
                onClick={() => void act(confirming)}
                disabled={busy}
              >
                {busy ? <Spinner light label="Working…" /> : 'Confirm'}
              </button>
            </>
          }
        >
          {confirming === 'revoke' && (
            <p>
              The phone registered to {summary.fullName} will no longer be able to verify work
              activities. Their account stays active and every past record is untouched.
            </p>
          )}
          {confirming === 'reset' && (
            <p>
              This revokes the current device and creates a new one-time activation link so{' '}
              {summary.fullName} can register a different phone. Use this when someone gets a new
              phone or loses theirs.
            </p>
          )}
          {confirming === 'disable' && (
            <p>
              {summary.fullName} will not be able to sign in. All their attendance history is kept
              exactly as it is, and you can enable the account again at any time.
            </p>
          )}
        </Modal>
      )}
    </div>
  )
}

function Line({ label, value }: { label: string; value?: string | null }) {
  if (!value) return null
  return (
    <div className="row-between">
      <span className="small muted">{label}</span>
      <span className="strong" style={{ textAlign: 'right' }}>{value}</span>
    </div>
  )
}
