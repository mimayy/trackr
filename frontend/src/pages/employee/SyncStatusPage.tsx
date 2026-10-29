import { useState } from 'react'
import { Alert, EmptyState, Spinner, SyncPill } from '../../components/ui'
import { useOnline, useQueue } from '../../components/useOnline'
import { formatTime } from '../../lib/format'
import { discard, syncNow } from '../../lib/offline'

/**
 * What is still on this phone and what has reached the office.
 *
 * Nothing is ever silently dropped: an activity the server has permanently
 * refused stays visible with the reason, so the employee can tell their
 * supervisor rather than assuming it went through.
 */
export function SyncStatusPage() {
  const online = useOnline()
  const queue = useQueue()
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function handleSync() {
    setBusy(true)
    setMessage(null)
    try {
      const summary = await syncNow()
      if (!summary) {
        setMessage(
          online
            ? 'Nothing is waiting to be sent.'
            : "You're offline. Trackr will try again automatically.",
        )
      } else {
        setMessage(
          `Sent ${summary.accepted + summary.flagged + summary.duplicates} ${
            summary.accepted + summary.flagged + summary.duplicates === 1 ? 'activity' : 'activities'
          }.`,
        )
      }
    } finally {
      setBusy(false)
    }
  }

  const failed = queue.filter((item) => item.state === 'FAILED')
  const pending = queue.filter((item) => item.state !== 'FAILED')

  return (
    <div className="stack-lg">
      <div>
        <h1>Sync status</h1>
        <p className="muted small" style={{ marginTop: 6 }}>
          Activities are saved on this phone first, then sent to the office.
        </p>
      </div>

      <div className="card row-between">
        <span className="strong">{online ? 'You are online' : 'You are offline'}</span>
        <span
          className={`pill ${online ? 'pill-success' : 'pill-warning'}`}
        >
          {online ? '● Connected' : '● No connection'}
        </span>
      </div>

      {message && <Alert tone="info">{message}</Alert>}

      {queue.length === 0 && (
        <EmptyState icon="✓" title="Everything is sent">
          Nothing is waiting on this phone.
        </EmptyState>
      )}

      {pending.length > 0 && (
        <section>
          <div className="card-title">Waiting to sync ({pending.length})</div>
          <div className="card stack">
            {pending.map((item) => (
              <div key={item.eventId} className="stack-sm">
                <div className="row-between">
                  <span className="strong">{item.eventLabel}</span>
                  <SyncPill status={item.state === 'SYNCING' ? 'SYNCING' : 'PENDING'} />
                </div>
                <div className="small muted">
                  {formatTime(item.eventTime)}
                  {item.locationName ? ` · 📍 ${item.locationName}` : ''}
                </div>
                <div className="row row-wrap" style={{ gap: 12 }}>
                  <span className="small" style={{ color: 'var(--success-600)' }}>
                    {item.fingerprintVerified ? '✓ Fingerprint' : '– Fingerprint'}
                  </span>
                  <span className="small" style={{ color: 'var(--success-600)' }}>
                    {item.latitude !== undefined ? '✓ Location' : '– Location'}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {failed.length > 0 && (
        <section>
          <div className="card-title">Could not be saved ({failed.length})</div>
          <div className="card stack">
            {failed.map((item) => (
              <div key={item.eventId} className="stack-sm">
                <div className="row-between">
                  <span className="strong">{item.eventLabel}</span>
                  <SyncPill status="FAILED" />
                </div>
                <div className="small muted">{formatTime(item.eventTime)}</div>
                {item.lastError && <Alert tone="warning">{item.lastError}</Alert>}
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => void discard(item.eventId)}
                >
                  Remove from this phone
                </button>
              </div>
            ))}
            <p className="small muted" style={{ margin: 0 }}>
              Please tell your supervisor about these so they can be corrected.
            </p>
          </div>
        </section>
      )}

      <button
        type="button"
        className="btn btn-primary btn-hero"
        onClick={handleSync}
        disabled={busy || queue.length === 0}
      >
        {busy ? <Spinner light label="Sending…" /> : 'SEND NOW'}
      </button>
    </div>
  )
}
