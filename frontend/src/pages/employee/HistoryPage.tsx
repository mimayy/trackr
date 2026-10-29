import { useEffect, useState } from 'react'
import { Alert, AttendancePill, EmptyState, LoadingPanel } from '../../components/ui'
import { api, query } from '../../lib/api'
import { addDays, formatMinutes, formatTime, formatWeekday, todayInZone } from '../../lib/format'
import type { AttendanceDayView } from '../../lib/types'

/**
 * Attendance history for the employee, one row per day.
 *
 * Deliberately plain: what time you started, what time you finished, how long
 * you worked, and whether anything was unusual. No percentages, no charts.
 */
export function HistoryPage() {
  const [days, setDays] = useState<AttendanceDayView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [weeks, setWeeks] = useState(2)

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      try {
        const today = todayInZone()
        const found = await api.get<AttendanceDayView[]>(
          `/api/me/attendance${query({ from: addDays(today, -(weeks * 7 - 1)), to: today })}`,
        )
        if (!cancelled) {
          setDays(found)
          setError(null)
        }
      } catch {
        if (!cancelled) setError('We could not load your history just now.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [weeks])

  const worked = days.filter((day) => day.timeIn || !day.restDay)

  return (
    <div className="stack-lg">
      <h1>Attendance history</h1>

      {error && <Alert tone="warning">{error}</Alert>}
      {loading && <LoadingPanel />}

      {!loading && worked.length === 0 && (
        <EmptyState icon="🗓️" title="Nothing recorded yet">
          Your workdays will appear here once you start timing in.
        </EmptyState>
      )}

      {!loading && worked.length > 0 && (
        <div className="stack">
          {worked.map((day) => (
            <div key={day.date} className="card card-tight">
              <div className="row-between" style={{ marginBottom: 'var(--space-3)' }}>
                <div>
                  <div className="strong" style={{ fontSize: 'var(--text-lg)' }}>
                    {formatWeekday(day.date)}
                  </div>
                  <div className="small muted">{day.date}</div>
                </div>
                <div className="row row-wrap" style={{ justifyContent: 'flex-end', gap: 6 }}>
                  {day.statuses.map((status) => (
                    <AttendancePill key={status} status={status} />
                  ))}
                </div>
              </div>

              {day.holiday && (
                <p className="small muted" style={{ margin: 0 }}>
                  {day.holidayName ?? 'Non-working day'}
                </p>
              )}

              {day.timeIn && (
                <div className="row-between">
                  <div>
                    <div className="tiny muted">Time in</div>
                    <div className="strong">{formatTime(day.timeIn)}</div>
                  </div>
                  <div>
                    <div className="tiny muted">Time out</div>
                    <div className="strong">{day.timeOut ? formatTime(day.timeOut) : '--'}</div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div className="tiny muted">Worked</div>
                    <div className="strong">{day.complete ? day.hoursWorkedLabel : '--'}</div>
                  </div>
                </div>
              )}

              {day.lateMinutes > 0 && (
                <p className="small" style={{ margin: '10px 0 0', color: 'var(--warning-700)' }}>
                  {formatMinutes(day.lateMinutes)} late
                </p>
              )}
              {!day.complete && day.timeIn && (
                <p className="small" style={{ margin: '10px 0 0', color: 'var(--warning-700)' }}>
                  No Time Out recorded for this day.
                </p>
              )}
            </div>
          ))}
        </div>
      )}

      {!loading && weeks < 8 && (
        <button type="button" className="btn btn-secondary btn-block" onClick={() => setWeeks((value) => value + 2)}>
          Show more
        </button>
      )}
    </div>
  )
}
