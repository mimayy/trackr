import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { MapView } from '../../components/MapView'
import { Alert, LoadingPanel, SyncPill, VerificationLine } from '../../components/ui'
import { api } from '../../lib/api'
import { formatDate, formatTime } from '../../lib/format'
import type { EmployeeEventView } from '../../lib/types'

/**
 * One activity, explained in the employee's own terms.
 *
 * Verification is shown as ticks, never as coordinates, accuracy figures,
 * credential ids or sync internals - all of which exist, and all of which
 * belong on the supervisor's screen instead.
 */
export function ActivityDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [event, setEvent] = useState<EmployeeEventView | null>(null)
  const [photoUrl, setPhotoUrl] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    let objectUrl: string | null = null

    async function load() {
      try {
        const found = await api.get<EmployeeEventView>(`/api/me/work-events/${id}`)
        if (cancelled) return
        setEvent(found)

        if (found.photoId) {
          objectUrl = await api.blobUrl(`/api/me/photos/${found.photoId}`)
          if (!cancelled) setPhotoUrl(objectUrl)
        }
      } catch {
        if (!cancelled) setError('We could not load this activity.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [id])

  if (loading) return <LoadingPanel />
  if (error || !event) {
    return (
      <div className="stack">
        <Alert tone="error">{error ?? 'That activity could not be found.'}</Alert>
        <button type="button" className="btn btn-secondary btn-block" onClick={() => navigate(-1)}>
          Go back
        </button>
      </div>
    )
  }

  return (
    <div className="stack-lg">
      <button type="button" className="btn btn-ghost" onClick={() => navigate(-1)} style={{ alignSelf: 'flex-start' }}>
        ← Back
      </button>

      <div className="card stack">
        <div>
          <h1>{event.eventLabel}</h1>
          <p className="muted" style={{ marginTop: 4 }}>
            {formatDate(event.businessDate)}
          </p>
        </div>

        <div style={{ fontSize: 'var(--text-4xl)', fontWeight: 700, lineHeight: 1.1 }}>
          {formatTime(event.eventTime)}
        </div>

        {event.locationName && (
          <div style={{ fontSize: 'var(--text-lg)' }}>📍 {event.locationName}</div>
        )}
        {event.destinationName && (
          <div style={{ fontSize: 'var(--text-lg)' }}>→ Going to {event.destinationName}</div>
        )}
        {event.siteName && (
          <div className="muted">Site: {event.siteName}</div>
        )}
      </div>

      <div className="card stack-sm">
        <div className="card-title" style={{ marginBottom: 0 }}>Checks</div>
        <VerificationLine verified={event.locationVerified} label="Location verified" />
        <VerificationLine verified={event.fingerprintVerified} label="Fingerprint verified" />
        <VerificationLine verified={event.hasPhoto} label="Photo attached" />
        <div className="row-between" style={{ marginTop: 6 }}>
          <span className="small">Saved to the office</span>
          <SyncPill status={event.syncStatus} />
        </div>
        {event.captureMode === 'OFFLINE' && (
          <p className="small muted" style={{ margin: '6px 0 0' }}>
            This activity was recorded while you were offline and sent later.
          </p>
        )}
      </div>

      {event.needsReview && (
        <Alert tone="warning" title="A supervisor will review this">
          Something about this activity could not be fully verified. It has been
          recorded and someone will take a look. You do not need to do anything.
        </Alert>
      )}

      {photoUrl && (
        <div className="card stack-sm">
          <div className="card-title" style={{ marginBottom: 0 }}>Photo</div>
          <img
            src={photoUrl}
            alt={`Photo taken at ${event.eventLabel}`}
            style={{ borderRadius: 'var(--radius-md)', width: '100%' }}
          />
        </div>
      )}

      {event.latitude !== undefined && event.longitude !== undefined && (
        <div className="stack-sm">
          <div className="card-title" style={{ marginBottom: 0 }}>Where this was recorded</div>
          <div className="map-panel map-panel-sm">
            <MapView
              height="100%"
              zoom={16}
              center={[event.latitude, event.longitude]}
              pins={[
                {
                  id: event.id,
                  latitude: event.latitude,
                  longitude: event.longitude,
                  title: event.eventLabel,
                  subtitle: formatTime(event.eventTime),
                  tone: event.locationVerified ? 'success' : 'warning',
                },
              ]}
            />
          </div>
          <Link to="/my-map" className="btn btn-secondary btn-block">
            See all my recorded locations
          </Link>
        </div>
      )}
    </div>
  )
}
