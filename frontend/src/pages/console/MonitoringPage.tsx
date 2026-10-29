import { useEffect, useState } from 'react'
import { MapView, type MapPin } from '../../components/MapView'
import { Alert, LoadingPanel, Pill } from '../../components/ui'
import { api, query } from '../../lib/api'
import { formatDateTime, formatTime, todayInZone } from '../../lib/format'
import type {
  Branch,
  EmployeeRow,
  EventType,
  LocationRow,
  StaffEventView,
  WorkCategory,
} from '../../lib/types'
import { useConsole } from './useConsole'

const EVENT_TYPES: Array<{ value: EventType; label: string }> = [
  { value: 'TIME_IN', label: 'Time In' },
  { value: 'OFFICE_DEPARTURE', label: 'Office Departure' },
  { value: 'FIELD_ARRIVAL', label: 'Field Arrival' },
  { value: 'FIELD_DEPARTURE', label: 'Field Departure' },
  { value: 'OFFICE_ARRIVAL', label: 'Office Arrival' },
  { value: 'TIME_OUT', label: 'Time Out' },
]

const TONE: Record<EventType, MapPin['tone']> = {
  TIME_IN: 'success',
  OFFICE_DEPARTURE: 'brand',
  FIELD_ARRIVAL: 'warning',
  FIELD_DEPARTURE: 'warning',
  OFFICE_ARRIVAL: 'brand',
  TIME_OUT: 'neutral',
}

/**
 * The monitoring map.
 *
 * Every pin is a work event that someone recorded deliberately - a Time In, an
 * arrival, a departure. There is no trail between them because Trackr does not
 * record one, and the wording on this page is careful never to imply otherwise.
 */
export function MonitoringPage() {
  const console = useConsole()

  const [events, setEvents] = useState<StaffEventView[]>([])
  const [employees, setEmployees] = useState<EmployeeRow[]>([])
  const [locations, setLocations] = useState<LocationRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [from, setFrom] = useState(todayInZone())
  const [to, setTo] = useState(todayInZone())
  const [employeeId, setEmployeeId] = useState('')
  const [branch, setBranch] = useState<Branch | ''>('')
  const [workCategory, setWorkCategory] = useState<WorkCategory | ''>('')
  const [locationId, setLocationId] = useState('')
  const [eventType, setEventType] = useState<EventType | ''>('')

  useEffect(() => {
    let cancelled = false

    async function loadReferences() {
      try {
        const [people, sites] = await Promise.all([
          api.get<EmployeeRow[]>(`${console.api}/employees`),
          api.get<LocationRow[]>(`${console.api}/locations`),
        ])
        if (!cancelled) {
          setEmployees(people)
          setLocations(sites)
        }
      } catch {
        /* filters simply offer less; the map still works */
      }
    }

    void loadReferences()
    return () => {
      cancelled = true
    }
  }, [console.api])

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      try {
        const found = await api.get<StaffEventView[]>(
          `${console.api}/monitoring/work-events${query({
            from,
            to,
            employeeId,
            branch,
            workCategory,
            locationId,
            eventType,
          })}`,
        )
        if (!cancelled) {
          setEvents(found)
          setError(null)
        }
      } catch {
        if (!cancelled) setError('We could not load the map data just now.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [console.api, from, to, employeeId, branch, workCategory, locationId, eventType])

  const pins: MapPin[] = events
    .filter((event) => event.latitude !== undefined && event.longitude !== undefined)
    .map((event) => ({
      id: event.id,
      latitude: event.latitude as number,
      longitude: event.longitude as number,
      title: event.employeeName,
      subtitle: `${event.eventLabel} · ${formatTime(event.eventTime)}`,
      tone: TONE[event.eventType],
      detail: (
        <div style={{ display: 'grid', gap: 2 }}>
          <div>Location: {event.location?.name ?? 'Not recognised'}</div>
          <div>GPS accuracy: {event.gpsAccuracyM ? `${Math.round(event.gpsAccuracyM)} m` : 'unknown'}</div>
          <div>
            Verification: {event.biometricVerified ? 'Fingerprint verified' : 'Not fingerprint-verified'}
          </div>
          <div>Recorded: {event.captureMode === 'OFFLINE' ? 'Offline, synced later' : 'Online'}</div>
        </div>
      ),
    }))

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Monitoring</h1>
          <p className="page-subtitle">
            Recorded work-event locations. Trackr captures a position only when an employee records
            an activity — it does not follow anyone during the day.
          </p>
        </div>
      </div>

      <div className="filters">
        <div className="field">
          <label className="label" htmlFor="m-from">From</label>
          <input id="m-from" className="input" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="field">
          <label className="label" htmlFor="m-to">To</label>
          <input id="m-to" className="input" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        <div className="field" style={{ minWidth: 200 }}>
          <label className="label" htmlFor="m-employee">Employee</label>
          <select id="m-employee" className="select" value={employeeId} onChange={(e) => setEmployeeId(e.target.value)}>
            <option value="">All</option>
            {employees.map((employee) => (
              <option key={employee.id} value={employee.id}>{employee.fullName}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label className="label" htmlFor="m-branch">Branch</label>
          <select id="m-branch" className="select" value={branch} onChange={(e) => setBranch(e.target.value as Branch | '')}>
            <option value="">All</option>
            <option value="MANILA">Manila</option>
            <option value="CEBU">Cebu</option>
          </select>
        </div>
        <div className="field">
          <label className="label" htmlFor="m-category">Work category</label>
          <select
            id="m-category"
            className="select"
            value={workCategory}
            onChange={(e) => setWorkCategory(e.target.value as WorkCategory | '')}
          >
            <option value="">All</option>
            <option value="OFFICE_BASED">Office-based</option>
            <option value="FIELD_BASED">Field-based</option>
          </select>
        </div>
        <div className="field" style={{ minWidth: 190 }}>
          <label className="label" htmlFor="m-location">Location</label>
          <select id="m-location" className="select" value={locationId} onChange={(e) => setLocationId(e.target.value)}>
            <option value="">All</option>
            {locations.map((location) => (
              <option key={location.id} value={location.id}>{location.name}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label className="label" htmlFor="m-event">Event type</label>
          <select
            id="m-event"
            className="select"
            value={eventType}
            onChange={(e) => setEventType(e.target.value as EventType | '')}
          >
            <option value="">All</option>
            {EVENT_TYPES.map((type) => (
              <option key={type.value} value={type.value}>{type.label}</option>
            ))}
          </select>
        </div>
      </div>

      {error && <Alert tone="error">{error}</Alert>}
      {loading && <LoadingPanel />}

      {!loading && (
        <div className="stack-lg">
          {pins.length > 0 ? (
            <div className="map-panel" style={{ height: 520 }}>
              <MapView pins={pins} height="100%" />
            </div>
          ) : (
            <div className="card center" style={{ padding: 'var(--space-7)' }}>
              <p className="muted" style={{ margin: 0 }}>No recorded locations match these filters.</p>
            </div>
          )}

          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Employee</th>
                  <th>Event</th>
                  <th>Time</th>
                  <th>Location</th>
                  <th>Accuracy</th>
                  <th>Geofence</th>
                  <th>Verification</th>
                  <th>Captured</th>
                </tr>
              </thead>
              <tbody>
                {events.map((event) => (
                  <tr key={event.id}>
                    <td>
                      {event.employeeName}
                      <div className="tiny muted">{event.employeeNo}</div>
                    </td>
                    <td>{event.eventLabel}</td>
                    <td>{formatDateTime(event.eventTime)}</td>
                    <td>{event.location?.name ?? <span className="muted">Not recognised</span>}</td>
                    <td>{event.gpsAccuracyM ? `${Math.round(event.gpsAccuracyM)} m` : '—'}</td>
                    <td>
                      {event.withinGeofence === undefined ? (
                        <span className="muted">—</span>
                      ) : event.withinGeofence ? (
                        <Pill tone="success">Inside</Pill>
                      ) : (
                        <Pill tone="warning">Outside</Pill>
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
                  </tr>
                ))}
                {events.length === 0 && (
                  <tr>
                    <td colSpan={8} className="table-empty">Nothing recorded for these filters.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
