import { useEffect, useState } from 'react'
import { MapView, type MapPin } from '../../components/MapView'
import { Alert, EmptyState, LoadingPanel } from '../../components/ui'
import { api, query } from '../../lib/api'
import { addDays, formatRelativeDay, formatTime, todayInZone } from '../../lib/format'
import type { EmployeeEventView, EventType } from '../../lib/types'

const TONE: Record<EventType, MapPin['tone']> = {
  TIME_IN: 'success',
  OFFICE_DEPARTURE: 'brand',
  FIELD_ARRIVAL: 'warning',
  FIELD_DEPARTURE: 'warning',
  OFFICE_ARRIVAL: 'brand',
  TIME_OUT: 'neutral',
}

/**
 * The employee's own recorded work locations for one day.
 *
 * The heading says "recorded locations" and the note underneath says why -
 * because this is a set of points captured at specific moments, not a trail of
 * where someone has been. Nothing here should suggest continuous tracking.
 *
 * Pins are numbered here, and only here: on one person's single day the order
 * is the story. On a map of several people a number would read as a count.
 */
export function MyMapPage() {
  const [date, setDate] = useState(todayInZone())
  const [events, setEvents] = useState<EmployeeEventView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      try {
        const found = await api.get<EmployeeEventView[]>(
          `/api/me/work-events${query({ from: date, to: date })}`,
        )
        if (!cancelled) {
          setEvents(found)
          setError(null)
        }
      } catch {
        if (!cancelled) setError('We could not load your map just now.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [date])

  const pins: MapPin[] = events
    .filter((event) => event.latitude !== undefined && event.longitude !== undefined)
    .map((event) => ({
      id: event.id,
      latitude: event.latitude as number,
      longitude: event.longitude as number,
      title: event.eventLabel,
      subtitle: `${formatTime(event.eventTime)}${event.locationName ? ` · ${event.locationName}` : ''}`,
      tone: TONE[event.eventType],
    }))

  return (
    <div className="stack-lg">
      <div>
        <h1>My recorded locations</h1>
        <p className="muted small" style={{ marginTop: 6 }}>
          Trackr records where you are only when you tap an activity — never in
          the background.
        </p>
      </div>

      <div className="field">
        <label className="label" htmlFor="map-date">Choose a day</label>
        <input
          id="map-date"
          className="input"
          type="date"
          value={date}
          max={todayInZone()}
          min={addDays(todayInZone(), -90)}
          onChange={(event) => setDate(event.target.value)}
        />
      </div>

      {error && <Alert tone="warning">{error}</Alert>}
      {loading && <LoadingPanel />}

      {!loading && pins.length === 0 && (
        <EmptyState icon="🗺️" title="No locations for this day">
          Nothing was recorded on {formatRelativeDay(date)}.
        </EmptyState>
      )}

      {!loading && pins.length > 0 && (
        <>
          <div className="map-panel">
            <MapView pins={pins} height="100%" numbered />
          </div>

          <div className="card stack-sm">
            <div className="card-title" style={{ marginBottom: 0 }}>
              {formatRelativeDay(date)}
            </div>
            {events.map((event, index) => (
              <div key={event.id} className="row-between">
                <span className="row" style={{ gap: 10 }}>
                  <span
                    aria-hidden="true"
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: 22,
                      height: 22,
                      borderRadius: '50%',
                      background: 'var(--brand-100)',
                      color: 'var(--brand-800)',
                      fontSize: 12,
                      fontWeight: 700,
                    }}
                  >
                    {index + 1}
                  </span>
                  <span>
                    <span className="strong">{event.eventLabel}</span>
                    {event.locationName && (
                      <span className="small muted"> · {event.locationName}</span>
                    )}
                  </span>
                </span>
                <span className="small muted">{formatTime(event.eventTime)}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
