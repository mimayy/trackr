import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, EmptyState, LoadingPanel, SyncPill } from '../../components/ui'
import { useQueue } from '../../components/useOnline'
import { ApiError, api, query } from '../../lib/api'
import { addDays, formatRelativeDay, formatTime, todayInZone } from '../../lib/format'
import type { EmployeeEventView } from '../../lib/types'

/**
 * The employee's own timeline, newest day first.
 *
 * Activities still waiting to sync appear at the top of today, marked plainly,
 * so nobody has to wonder whether the office has their Time In yet.
 */
export function ActivitiesPage() {
  const queue = useQueue()

  const [events, setEvents] = useState<EmployeeEventView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const today = todayInZone()
        const found = await api.get<EmployeeEventView[]>(
          `/api/me/work-events${query({ from: addDays(today, -13), to: today })}`,
        )
        if (!cancelled) {
          setEvents(found)
          setError(null)
        }
      } catch (caught) {
        if (!cancelled) {
          setError(
            caught instanceof ApiError && caught.isOffline
              ? "You're offline, so only activities saved on this phone are shown."
              : 'We could not load your activities just now.',
          )
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [])

  if (loading) {
    return <LoadingPanel label="Loading your activities…" />
  }

  const byDay = groupByDay(events)

  return (
    <div className="stack-lg">
      <h1>My activities</h1>

      {error && <Alert tone="warning">{error}</Alert>}

      {queue.length > 0 && (
        <section>
          <div className="card-title">Waiting to sync</div>
          <div className="card stack-sm">
            {queue.map((item) => (
              <div key={item.eventId} className="row-between">
                <div>
                  <div className="strong">{item.eventLabel}</div>
                  <div className="small muted">
                    {formatTime(item.eventTime)}
                    {item.locationName ? ` · 📍 ${item.locationName}` : ''}
                  </div>
                </div>
                <SyncPill status={item.state === 'FAILED' ? 'FAILED' : 'PENDING'} />
              </div>
            ))}
            <Link to="/sync" className="btn btn-ghost btn-block">View sync status</Link>
          </div>
        </section>
      )}

      {byDay.length === 0 && queue.length === 0 && (
        <EmptyState icon="📋" title="No activities yet">
          Your Time In, arrivals and departures will appear here.
        </EmptyState>
      )}

      {byDay.map(([day, dayEvents]) => (
        <section key={day}>
          <div className="card-title">{formatRelativeDay(day)}</div>
          <div className="card stack">
            {dayEvents.map((event) => (
              <Link
                key={event.id}
                to={`/activities/${event.id}`}
                className="row-between"
                style={{ textDecoration: 'none', color: 'inherit', minHeight: 'var(--tap-min)' }}
              >
                <div className="row" style={{ gap: 12 }}>
                  <span
                    aria-hidden="true"
                    style={{
                      color: event.needsReview ? 'var(--warning-600)' : 'var(--success-600)',
                      fontWeight: 700,
                      fontSize: 'var(--text-lg)',
                    }}
                  >
                    {event.needsReview ? '!' : '✓'}
                  </span>
                  <div>
                    <div className="strong">{event.eventLabel}</div>
                    <div className="small muted">
                      {event.locationName
                        ? `📍 ${event.locationName}`
                        : event.siteName
                          ? `📍 ${event.siteName}`
                          : event.destinationName
                            ? `→ ${event.destinationName}`
                            : event.latitude !== undefined
                              ? '📍 Location recorded'
                              : 'No location recorded'}
                    </div>
                  </div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div className="strong">{formatTime(event.eventTime)}</div>
                  <div className="tiny muted">›</div>
                </div>
              </Link>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}

function groupByDay(events: EmployeeEventView[]): Array<[string, EmployeeEventView[]]> {
  const map = new Map<string, EmployeeEventView[]>()
  for (const event of events) {
    const list = map.get(event.businessDate) ?? []
    list.push(event)
    map.set(event.businessDate, list)
  }
  return [...map.entries()]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([day, list]) => [day, list.sort((a, b) => a.eventTime.localeCompare(b.eventTime))])
}
