import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Alert, Spinner } from '../../components/ui'
import { useOnline } from '../../components/useOnline'
import { ApiError, api } from '../../lib/api'
import { formatTime } from '../../lib/format'
import { getCurrentPosition, LocationError, type Position } from '../../lib/geo'
import { computeIntegrityHash, enqueue, syncNow } from '../../lib/offline'
import type {
  Destination,
  EmployeeEventView,
  EventType,
  LocationRow,
  WorkflowStatus,
} from '../../lib/types'
import { verifyFingerprint, type DeviceAssertion } from '../../lib/webauthn'

type Step =
  | 'destination'
  | 'site'
  | 'confirm'
  | 'locating'
  | 'fingerprint'
  | 'photo'
  | 'sending'
  | 'success'
  | 'failed'

const EVENT_TITLE: Record<EventType, string> = {
  TIME_IN: 'Time In',
  OFFICE_DEPARTURE: 'Office Departure',
  FIELD_ARRIVAL: 'Field Arrival',
  FIELD_DEPARTURE: 'Field Departure',
  OFFICE_ARRIVAL: 'Office Arrival',
  TIME_OUT: 'Time Out',
}

const EVENT_BUTTON: Record<EventType, string> = {
  TIME_IN: 'TIME IN',
  OFFICE_DEPARTURE: "I'M LEAVING THE OFFICE",
  FIELD_ARRIVAL: "I'M HERE",
  FIELD_DEPARTURE: "I'M LEAVING",
  OFFICE_ARRIVAL: "I'M BACK AT THE OFFICE",
  TIME_OUT: 'TIME OUT',
}

/**
 * Recording one work event, full screen, one step at a time.
 *
 * The sequence is deliberately the same every time - confirm, fingerprint,
 * location, optional photo, done - so it becomes muscle memory. Nothing here
 * asks the employee to type.
 */
export function RecordEventPage() {
  const { eventType } = useParams<{ eventType: string }>()
  const navigate = useNavigate()
  const online = useOnline()
  const fileInput = useRef<HTMLInputElement>(null)

  const type = (eventType ?? 'TIME_IN').toUpperCase() as EventType

  const [status, setStatus] = useState<WorkflowStatus | null>(null)
  const [locations, setLocations] = useState<LocationRow[]>([])
  // A Field Arrival opens by asking which warehouse; everything else opens on
  // the confirm screen.
  const [step, setStep] = useState<Step>(type === 'FIELD_ARRIVAL' ? 'site' : 'confirm')
  const [position, setPosition] = useState<Position | null>(null)
  const [assertion, setAssertion] = useState<DeviceAssertion | null>(null)
  const [destination, setDestination] = useState<Destination | null>(null)
  const [destinationLocationId, setDestinationLocationId] = useState<string>('')
  const [siteName, setSiteName] = useState('')
  const [photoId, setPhotoId] = useState<string | null>(null)
  const [photoBusy, setPhotoBusy] = useState(false)
  const [savedOffline, setSavedOffline] = useState(false)
  const [recorded, setRecorded] = useState<EmployeeEventView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [retryable, setRetryable] = useState(true)

  // A stable id, generated here on the device. Re-sending it can never create a
  // second event, which is what makes the offline queue safe to retry.
  const eventId = useMemo(() => crypto.randomUUID(), [])

  // The same load, held so that pressing the button before it finishes waits
  // for it instead of racing it. See the note in start().
  const statusLoad = useRef<Promise<WorkflowStatus | null> | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load(): Promise<WorkflowStatus | null> {
      try {
        const loaded = await api.get<WorkflowStatus>('/api/me/status')
        if (cancelled) return loaded
        setStatus(loaded)
        if (type === 'FIELD_DEPARTURE') {
          setStep('destination')
        }
        return loaded
      } catch {
        // Offline: the employee can still record. Field Departure still needs a
        // destination, so ask for it regardless.
        if (!cancelled && type === 'FIELD_DEPARTURE') {
          setStep('destination')
        }
        return null
      }
    }

    statusLoad.current = load()
    return () => {
      cancelled = true
    }
  }, [type])

  async function loadLocationsOnce() {
    if (locations.length > 0) return
    try {
      setLocations(await api.get<LocationRow[]>('/api/supervisor/locations'))
    } catch {
      // Employees cannot list locations. An empty list is fine - Office and
      // Home cover almost every departure.
      setLocations([])
    }
  }

  /* ---------------------------------------------------------------------- */
  /* The recording sequence                                                 */
  /* ---------------------------------------------------------------------- */

  /**
   * The recording sequence: fingerprint, then location, then the optional photo.
   *
   * Locating starts immediately even though it is shown second. A cold GPS fix
   * takes several seconds on a phone, and there is no reason to make someone
   * wait through that after they have already put their finger on the sensor -
   * by then the fix is usually in hand and the step passes in an instant.
   */
  async function start() {
    setError(null)

    // Kick off locating now. Handlers are attached straight away so a failure
    // waits quietly to be reported rather than surfacing as an unhandled
    // rejection.
    const locating = getCurrentPosition().then(
      (fix) => ({ located: true as const, fix }),
      (cause: unknown) => ({ located: false as const, cause }),
    )

    // ---- 1. Fingerprint ---------------------------------------------------
    // Required whenever there is a connection: the server refuses an online
    // activity without one. Offline there is no challenge to sign, so the
    // activity is queued unverified and a supervisor reviews it.
    //
    // Wait for the status if it is still in flight. The button is the first
    // thing on the screen and it is deliberately large, so on a slow phone
    // connection it is entirely normal to press it before this arrives.
    // Reading `status` directly would then see null, quietly skip the
    // fingerprint, and save the event unverified - the employee would have no
    // way of knowing they had been let through without it.
    const current = status ?? (await statusLoad.current)

    if (online) {
      if (!current) {
        fail('We could not reach the office just now. Please try again.', true)
        return
      }
      if (!current.fingerprintAvailable) {
        fail('Please set up your fingerprint in Profile before recording an activity.', false)
        return
      }
      setStep('fingerprint')
      const confirmed = await verifyFingerprint()
      if (!confirmed) {
        fail("Your fingerprint wasn't confirmed. Please try again.", true)
        return
      }
      setAssertion(confirmed)
    } else {
      setAssertion(null)
    }

    // ---- 2. Location ------------------------------------------------------
    setStep('locating')
    const result = await locating

    if (!result.located) {
      fail(
        result.cause instanceof LocationError
          ? result.cause.message
          : "We can't get your location. Please turn on Location Services and try again.",
        true,
      )
      return
    }
    setPosition(result.fix)

    // ---- 3. Optional photo ------------------------------------------------
    setStep('photo')
  }

  function fail(message: string, canRetry: boolean) {
    setError(message)
    setRetryable(canRetry)
    setStep('failed')
  }

  async function submit(withPhotoId: string | null) {
    setStep('sending')
    setError(null)

    const now = new Date().toISOString()
    const payload = {
      eventId,
      eventType: type,
      eventTime: now,
      deviceTimestamp: now,
      latitude: position?.latitude,
      longitude: position?.longitude,
      gpsAccuracyM: position?.accuracy,
      siteName: type === 'FIELD_ARRIVAL' ? siteName.trim() : undefined,
      destination: destination ?? undefined,
      destinationLocationId: destinationLocationId || undefined,
      photoId: withPhotoId ?? undefined,
      deviceAssertion: assertion ?? undefined,
      captureMode: 'ONLINE' as const,
      clientAppVersion: '1.0.0',
    }

    if (!online) {
      await saveOffline(payload)
      return
    }

    try {
      const response = await api.post<{ event: EmployeeEventView; duplicate: boolean }>(
        '/api/me/work-events',
        payload,
      )
      setRecorded(response.event)
      setSavedOffline(false)
      setStep('success')
    } catch (caught) {
      if (caught instanceof ApiError && caught.isOffline) {
        // The connection dropped mid-request. Queue it rather than lose it.
        await saveOffline(payload)
        return
      }

      setError(
        caught instanceof ApiError
          ? caught.message
          : 'We could not record that just now. Please try again.',
      )
      // An accuracy problem or a fingerprint that did not take is worth
      // another try; a rule violation is not.
      setRetryable(
        caught instanceof ApiError &&
          ['GPS_ACCURACY_TOO_LOW', 'GPS_UNAVAILABLE', 'FINGERPRINT_REQUIRED', 'FINGERPRINT_FAILED'].includes(
            caught.code,
          ),
      )
      setStep('failed')
    }
  }

  async function saveOffline(payload: Record<string, unknown>) {
    await enqueue({
      eventId,
      eventType: type,
      eventLabel: EVENT_TITLE[type],
      eventTime: payload.eventTime as string,
      deviceTimestamp: payload.deviceTimestamp as string,
      latitude: position?.latitude,
      longitude: position?.longitude,
      gpsAccuracyM: position?.accuracy,
      siteName: type === 'FIELD_ARRIVAL' ? siteName.trim() : undefined,
      destination: destination ?? undefined,
      destinationLocationId: destinationLocationId || undefined,
      photoId: photoId ?? undefined,
      deviceAssertion: assertion ?? undefined,
      integrityHash: await computeIntegrityHash(payload),
      clientAppVersion: '1.0.0',
      locationName: siteName.trim() || status?.currentLocationName,
      fingerprintVerified: Boolean(assertion),
      state: 'PENDING',
      attempts: 0,
      queuedAt: new Date().toISOString(),
    })

    setSavedOffline(true)
    setStep('success')
    void syncNow()
  }

  async function handlePhotoChosen(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return

    setPhotoBusy(true)
    try {
      const form = new FormData()
      form.append('file', file)
      const uploaded = await api.upload<{ photoId: string }>('/api/me/photos', form)
      setPhotoId(uploaded.photoId)
      await submit(uploaded.photoId)
    } catch {
      // A failed photo must never cost the employee their attendance record.
      setError('That photo could not be saved, so the activity was recorded without it.')
      await submit(null)
    } finally {
      setPhotoBusy(false)
    }
  }

  /* ---------------------------------------------------------------------- */
  /* Screens                                                                */
  /* ---------------------------------------------------------------------- */

  return (
    <div style={{ minHeight: '100dvh', background: 'var(--page-bg)' }}>
      <header className="employee-header">
        <button
          type="button"
          className="btn btn-ghost"
          style={{ color: 'var(--white)' }}
          onClick={() => navigate('/home')}
          disabled={step === 'sending'}
        >
          ← Back
        </button>
        <span className="brand" style={{ fontSize: 'var(--text-base)' }}>{EVENT_TITLE[type]}</span>
        <span style={{ width: 60 }} />
      </header>

      <main className="employee-main" style={{ paddingBottom: 'var(--space-8)' }}>
        {/* ---- Choose where you are going ---- */}
        {step === 'destination' && (
          <div className="stack-lg">
            <h1>Where are you going next?</h1>

            <button
              type="button"
              className="btn btn-secondary btn-hero"
              onClick={() => {
                setDestination('OFFICE')
                setStep('confirm')
              }}
            >
              🏢 OFFICE
            </button>

            <button
              type="button"
              className="btn btn-secondary btn-hero"
              onClick={() => {
                setDestination('HOME')
                setStep('confirm')
              }}
            >
              🏠 HOME
            </button>

            <button
              type="button"
              className="btn btn-secondary btn-hero"
              onClick={() => {
                setDestination('ANOTHER_FIELD_LOCATION')
                void loadLocationsOnce()
              }}
            >
              📍 ANOTHER FIELD LOCATION
            </button>

            {destination === 'ANOTHER_FIELD_LOCATION' && (
              <div className="card stack">
                <div className="field">
                  <label className="label" htmlFor="destination-location">
                    Which location?
                  </label>
                  <select
                    id="destination-location"
                    className="select"
                    value={destinationLocationId}
                    onChange={(event) => setDestinationLocationId(event.target.value)}
                  >
                    <option value="">Choose a location</option>
                    {locations
                      .filter((location) => location.type !== 'OFFICE')
                      .map((location) => (
                        <option key={location.id} value={location.id}>
                          {location.name}
                        </option>
                      ))}
                  </select>
                </div>
                <button
                  type="button"
                  className="btn btn-primary btn-hero"
                  disabled={!destinationLocationId}
                  onClick={() => setStep('confirm')}
                >
                  CONTINUE
                </button>
              </div>
            )}
          </div>
        )}

        {/* ---- Which site ---- */}
        {step === 'site' && (
          <div className="stack-lg">
            <h1>Which warehouse are you at?</h1>

            {/* Names this employee has used before, offered as taps. Typing on a
                phone is the friction this screen exists to avoid, so the second
                visit to a site should not need the keyboard at all. */}
            {(status?.recentSiteNames ?? []).length > 0 && (
              <div className="stack">
                <div className="small muted">Recently used</div>
                <div className="chip-row">
                  {(status?.recentSiteNames ?? []).map((name) => (
                    <button
                      key={name}
                      type="button"
                      className={`chip${siteName.trim() === name ? ' chip-selected' : ''}`}
                      onClick={() => setSiteName(name)}
                    >
                      {name}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <div className="field">
              <label className="label" htmlFor="site-name">
                Warehouse or site name
              </label>
              <input
                id="site-name"
                className="input"
                type="text"
                inputMode="text"
                autoComplete="off"
                maxLength={120}
                placeholder="Type the name"
                value={siteName}
                onChange={(event) => setSiteName(event.target.value)}
              />
            </div>

            <button
              type="button"
              className="btn btn-primary btn-hero"
              disabled={!siteName.trim()}
              onClick={() => setStep('confirm')}
            >
              CONTINUE
            </button>

            <button type="button" className="btn btn-secondary btn-block" onClick={() => navigate('/home')}>
              Cancel
            </button>
          </div>
        )}

        {/* ---- Confirm ---- */}
        {step === 'confirm' && (
          <div className="stack-lg">
            <div className="card center stack">
              <div style={{ fontSize: 'var(--text-4xl)', fontWeight: 700 }}>
                {formatTime(new Date().toISOString())}
              </div>
              <div className="muted">
                {type === 'FIELD_ARRIVAL' && siteName.trim()
                  ? `You are checking in at ${siteName.trim()}.`
                  : type === 'TIME_OUT'
                    ? 'This will end your workday.'
                    : type === 'TIME_IN'
                      ? 'This will start your workday.'
                      : 'Your location will be recorded with this activity.'}
              </div>


              {destination && (
                <div className="strong">
                  Going to:{' '}
                  {destination === 'OFFICE'
                    ? 'Office'
                    : destination === 'HOME'
                      ? 'Home'
                      : (locations.find((l) => l.id === destinationLocationId)?.name ??
                         'Another field location')}
                </div>
              )}
            </div>

            {!online && (
              <Alert tone="warning" title="You're offline">
                Your work activity can still be recorded. Your fingerprint can't
                be checked offline, so it will sync when you're back online and
                your supervisor will review it.
              </Alert>
            )}

            {online && status && !status.fingerprintAvailable ? (
              <>
                <Alert tone="warning" title="Fingerprint not set up">
                  Every activity is confirmed with your fingerprint. Set it up
                  once in Profile, then come back here.
                </Alert>
                <button type="button" className="btn btn-primary btn-hero" onClick={() => navigate('/profile')}>
                  SET UP FINGERPRINT
                </button>
              </>
            ) : (
              <button type="button" className="btn btn-primary btn-hero" onClick={start}>
                {EVENT_BUTTON[type]}
              </button>
            )}

            <button type="button" className="btn btn-secondary btn-block" onClick={() => navigate('/home')}>
              Cancel
            </button>
          </div>
        )}

        {/* ---- Working ---- */}
        {(step === 'locating' || step === 'fingerprint' || step === 'sending') && (
          <div className="card center stack-lg" style={{ paddingTop: 'var(--space-8)', paddingBottom: 'var(--space-8)' }}>
            <div className="row" style={{ justifyContent: 'center' }}>
              <Spinner />
            </div>
            <div style={{ fontSize: 'var(--text-lg)' }}>
              {step === 'locating' && 'Finding your location…'}
              {step === 'fingerprint' && 'Waiting for your fingerprint…'}
              {step === 'sending' && 'Saving your activity…'}
            </div>
            {step === 'locating' && (
              <p className="muted small" style={{ margin: 0 }}>
                Please stay where you are for a moment.
              </p>
            )}
          </div>
        )}

        {/* ---- Optional photo ---- */}
        {step === 'photo' && (
          <div className="stack-lg">
            <div className="card center stack">
              <div style={{ fontSize: '2rem', color: 'var(--success-600)' }} aria-hidden="true">✓</div>
              {/* Listed in the order they happened, so the ticks match what the
                  employee just did rather than reading back to front. */}
              {assertion && (
                <div style={{ fontSize: 'var(--text-lg)', fontWeight: 600 }}>Fingerprint verified</div>
              )}
              <div style={{ fontSize: 'var(--text-lg)', fontWeight: 600 }}>Location verified</div>
            </div>

            <div className="card stack">
              <div className="center">
                <div style={{ fontSize: 'var(--text-lg)', fontWeight: 600 }}>
                  Would you like to add a photo?
                </div>
                <p className="muted small" style={{ marginTop: 6 }}>
                  This is optional.
                </p>
              </div>

              {/* `capture` opens the camera directly. Choosing an old picture
                  from the gallery is deliberately not offered for evidence. */}
              <input
                ref={fileInput}
                type="file"
                accept="image/*"
                capture="environment"
                className="visually-hidden"
                onChange={handlePhotoChosen}
              />

              <button
                type="button"
                className="btn btn-secondary btn-hero"
                onClick={() => fileInput.current?.click()}
                disabled={photoBusy || !online}
              >
                {photoBusy ? <Spinner label="Saving photo…" /> : '📷 TAKE PHOTO'}
              </button>

              {!online && (
                <p className="small muted center" style={{ margin: 0 }}>
                  Photos need a connection. You can record this activity now and
                  it will still be saved.
                </p>
              )}

              <button
                type="button"
                className="btn btn-primary btn-hero"
                onClick={() => submit(photoId)}
                disabled={photoBusy}
              >
                SKIP AND SAVE
              </button>
            </div>
          </div>
        )}

        {/* ---- Done ---- */}
        {step === 'success' && (
          <div className="stack-lg">
            <div className="card center stack">
              <div style={{ fontSize: '3rem', color: 'var(--success-600)' }} aria-hidden="true">✓</div>
              <h1>{EVENT_TITLE[type]} recorded</h1>
              <div style={{ fontSize: 'var(--text-3xl)', fontWeight: 700 }}>
                {formatTime(recorded?.eventTime ?? new Date().toISOString())}
              </div>
              {(recorded?.siteName ?? recorded?.locationName) && (
                <div style={{ fontSize: 'var(--text-lg)' }}>
                  📍 {recorded?.siteName ?? recorded?.locationName}
                </div>
              )}
            </div>

            {savedOffline ? (
              <Alert tone="warning" title="Saved on this phone">
                This activity is safely saved and will be sent automatically when
                you're back online.
              </Alert>
            ) : recorded?.needsReview ? (
              <Alert tone="warning" title="Saved — a supervisor will review this">
                Something about this activity could not be fully verified. It has
                been recorded and your supervisor will take a look.
              </Alert>
            ) : null}

            {error && <Alert tone="info">{error}</Alert>}

            <button
              type="button"
              className="btn btn-primary btn-hero"
              onClick={() => navigate('/home', { replace: true })}
            >
              DONE
            </button>
          </div>
        )}

        {/* ---- Could not record ---- */}
        {step === 'failed' && (
          <div className="stack-lg">
            <Alert tone="error" title="We could not record that">
              {error}
            </Alert>

            {retryable && (
              <button type="button" className="btn btn-primary btn-hero" onClick={start}>
                TRY AGAIN
              </button>
            )}

            <button
              type="button"
              className="btn btn-secondary btn-block"
              onClick={() => navigate('/home')}
            >
              Back to home
            </button>

            <p className="small muted center">
              Still stuck? Open <strong>Help</strong> from the menu, or call your
              supervisor.
            </p>
          </div>
        )}
      </main>
    </div>
  )
}

