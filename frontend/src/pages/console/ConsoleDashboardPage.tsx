import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { MapView, type MapPin } from '../../components/MapView'
import { Alert, LoadingPanel, Pill } from '../../components/ui'
import { api, query } from '../../lib/api'
import { formatDateTime, formatTime, todayInZone } from '../../lib/format'
import type { DashboardResponse, WorkflowState } from '../../lib/types'
import { useConsole } from './useConsole'

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
 * The dashboard for supervisors and administrators.
 *
 * Every figure on this page is counted from work events in the database for the
 * selected date. Nothing is hard-coded, so an empty system honestly shows
 * zeros.
 */
export function ConsoleDashboardPage() {
  const console = useConsole()

  const [date, setDate] = useState(todayInZone())
  const [data, setData] = useState<DashboardResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      try {
        const found = await api.get<DashboardResponse>(`${console.api}/dashboard${query({ date })}`)
        if (!cancelled) {
          setData(found)
          setError(null)
        }
      } catch {
        if (!cancelled) setError('We could not load the dashboard just now. Please try again.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [console.api, date])

  const pins: MapPin[] = (data?.latestLocations ?? [])
    .filter((entry) => entry.latitude !== undefined && entry.longitude !== undefined)
    .map((entry) => ({
      id: entry.employeeId,
      latitude: entry.latitude as number,
      longitude: entry.longitude as number,
      title: entry.employeeName,
      subtitle: `${entry.lastEventLabel ?? 'No activity'} · ${formatTime(entry.lastEventTime)}`,
      detail: entry.locationName ? `Latest recorded location: ${entry.locationName}` : undefined,
      tone: entry.workflowState === 'AT_FIELD' ? 'warning' : 'brand',
    }))

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Dashboard</h1>
          <p className="page-subtitle">
            {console.isAdminArea ? 'Company-wide' : 'Your team'} · counted from recorded work events
          </p>
        </div>
        <div className="field" style={{ minWidth: 180 }}>
          <label className="label" htmlFor="dashboard-date">Date</label>
          <input
            id="dashboard-date"
            className="input"
            type="date"
            value={date}
            max={todayInZone()}
            onChange={(event) => setDate(event.target.value)}
          />
        </div>
      </div>

      {error && <Alert tone="error">{error}</Alert>}
      {loading && <LoadingPanel />}

      {!loading && data && (
        <div className="stack-lg">
          <div className="grid-cards">
            <Metric label="Total employees" value={data.totalEmployees} />
            <Metric label="Present" value={data.present} accent="success" />
            <Metric label="Late" value={data.late} accent="warning" />
            <Metric label="Absent" value={data.absent} accent="danger" />
            <Metric label="In field" value={data.inField} accent="info" />
            <Metric label="At office" value={data.atOffice} />
            <Metric label="Finished" value={data.finished} />
            <Metric label="Needs review" value={data.requiresReview} accent="warning" />
          </div>

          {console.isAdminArea && data.pendingActivation > 0 && (
            <Alert tone="info" title={`${data.pendingActivation} account${data.pendingActivation === 1 ? '' : 's'} not activated yet`}>
              <Link to={`${console.base}/employees`}>Open Employees</Link> to send or copy an
              activation link.
            </Alert>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 'var(--space-5)' }}>
            <section>
              <h2 style={{ marginBottom: 'var(--space-3)' }}>Latest recorded locations</h2>
              <p className="small muted" style={{ marginTop: -8, marginBottom: 'var(--space-3)' }}>
                Where each person was last recorded — not a live position.
              </p>

              {pins.length > 0 ? (
                <div className="map-panel">
                  <MapView pins={pins} height="100%" />
                </div>
              ) : (
                <div className="card center" style={{ padding: 'var(--space-6)' }}>
                  <p className="muted" style={{ margin: 0 }}>
                    No recorded locations for this date yet.
                  </p>
                </div>
              )}
            </section>

            <section>
              <h2 style={{ marginBottom: 'var(--space-3)' }}>Employee status</h2>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Employee</th>
                      <th>Status</th>
                      <th>Latest recorded location</th>
                      <th>Last event</th>
                      <th>Time</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.latestLocations.map((entry) => (
                      <tr key={entry.employeeId}>
                        <td>
                          <Link to={`${console.base}/employees/${entry.employeeId}`}>
                            {entry.employeeName}
                          </Link>
                          <div className="tiny muted">{entry.employeeNo}</div>
                        </td>
                        <td><Pill tone={STATE_TONE[entry.workflowState]}>{entry.statusLabel}</Pill></td>
                        <td>{entry.locationName ?? <span className="muted">—</span>}</td>
                        <td>{entry.lastEventLabel ?? <span className="muted">—</span>}</td>
                        <td>{entry.lastEventTime ? formatTime(entry.lastEventTime) : '—'}</td>
                      </tr>
                    ))}
                    {data.latestLocations.length === 0 && (
                      <tr>
                        <td colSpan={5} className="table-empty">No employees in scope yet.</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>

            <section>
              <h2 style={{ marginBottom: 'var(--space-3)' }}>Recent activity</h2>
              <div className="card stack-sm">
                {data.recentActivity.map((event) => (
                  <div key={event.id} className="row-between">
                    <span>
                      <span className="strong">{event.employeeName}</span>
                      <span className="muted"> — {event.eventLabel}</span>
                      {event.location && <span className="muted"> — {event.location.name}</span>}
                    </span>
                    <span className="small muted">{formatDateTime(event.eventTime)}</span>
                  </div>
                ))}
                {data.recentActivity.length === 0 && (
                  <p className="muted" style={{ margin: 0 }}>Nothing recorded yet.</p>
                )}
              </div>
            </section>
          </div>
        </div>
      )}
    </div>
  )
}

function Metric({
  label,
  value,
  accent,
}: {
  label: string
  value: number
  accent?: 'success' | 'warning' | 'danger' | 'info'
}) {
  return (
    <div className={`metric${accent ? ` metric-accent-${accent}` : ''}`}>
      <div className="metric-value">{value}</div>
      <div className="metric-label">{label}</div>
    </div>
  )
}
