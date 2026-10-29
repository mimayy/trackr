import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, EmptyState, LoadingPanel, Modal, SeverityPill, Spinner } from '../../components/ui'
import { ApiError, api, query } from '../../lib/api'
import { formatDateTime } from '../../lib/format'
import type { ExceptionRow, FlagCategory } from '../../lib/types'
import { useConsole } from './useConsole'

const CATEGORIES: Array<{ value: FlagCategory | ''; label: string }> = [
  { value: '', label: 'All categories' },
  { value: 'ATTENDANCE', label: 'Attendance' },
  { value: 'LOCATION', label: 'Location' },
  { value: 'AUTHENTICATION', label: 'Authentication' },
  { value: 'OFFLINE_SYNC', label: 'Offline synchronisation' },
  { value: 'WORKFLOW', label: 'Workflow' },
]

/**
 * Records that need a human look.
 *
 * The wording matters here: these are things the system could not verify, not
 * accusations. "Requires review", never "cheating". A supervisor decides what
 * actually happened, and the original record is never altered.
 */
export function ExceptionsPage() {
  const console = useConsole()

  const [rows, setRows] = useState<ExceptionRow[]>([])
  const [category, setCategory] = useState<FlagCategory | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [reviewing, setReviewing] = useState<ExceptionRow | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await api.get<ExceptionRow[]>(`${console.api}/exceptions${query({ category })}`))
      setError(null)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'We could not load the exception list.')
    } finally {
      setLoading(false)
    }
  }, [console.api, category])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Exceptions</h1>
          <p className="page-subtitle">
            Activities the system could not fully verify. Each one is recorded and kept — nothing is
            discarded.
          </p>
        </div>
      </div>

      <div className="filters">
        <div className="field" style={{ minWidth: 230 }}>
          <label className="label" htmlFor="category">Category</label>
          <select
            id="category"
            className="select"
            value={category}
            onChange={(event) => setCategory(event.target.value as FlagCategory | '')}
          >
            {CATEGORIES.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
      </div>

      {notice && <Alert tone="success">{notice}</Alert>}
      {error && <Alert tone="error">{error}</Alert>}
      {loading && <LoadingPanel />}

      {!loading && rows.length === 0 && (
        <EmptyState icon="✓" title="Nothing needs review">
          Every recorded activity in your scope verified cleanly.
        </EmptyState>
      )}

      {!loading && rows.length > 0 && (
        <div className="stack">
          {rows.map((row) => (
            <div key={row.eventId} className="card stack">
              <div className="row-between row-wrap">
                <div>
                  <div className="strong" style={{ fontSize: 'var(--text-lg)' }}>
                    {row.employeeName}{' '}
                    <span className="muted" style={{ fontWeight: 400 }}>— {row.eventLabel}</span>
                  </div>
                  <div className="small muted">
                    {row.employeeNo} · {formatDateTime(row.eventTime)}
                    {row.locationName ? ` · ${row.locationName}` : ''}
                    {row.captureMode === 'OFFLINE' ? ' · recorded offline' : ''}
                  </div>
                </div>
                <SeverityPill severity={row.highestSeverity} />
              </div>

              <div className="stack-sm">
                {row.flags.map((flag) => (
                  <div
                    key={flag.code}
                    style={{
                      borderLeft: '3px solid var(--warning-600)',
                      paddingLeft: 'var(--space-3)',
                    }}
                  >
                    <div className="strong small">⚠ {flag.label}</div>
                    {flag.detail && <div className="small muted">{flag.detail}</div>}
                  </div>
                ))}
              </div>

              <div className="row row-wrap">
                <button type="button" className="btn btn-primary btn-sm" onClick={() => setReviewing(row)}>
                  Review
                </button>
                <Link className="btn btn-secondary btn-sm" to={`${console.base}/employees/${row.employeeId}`}>
                  Open employee
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}

      {reviewing && (
        <ReviewModal
          row={reviewing}
          apiPrefix={console.api}
          onClose={() => setReviewing(null)}
          onDone={(message) => {
            setReviewing(null)
            setNotice(message)
            void load()
          }}
        />
      )}
    </div>
  )
}

function ReviewModal({
  row,
  apiPrefix,
  onClose,
  onDone,
}: {
  row: ExceptionRow
  apiPrefix: string
  onClose: () => void
  onDone: (message: string) => void
}) {
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(reviewStatus: 'REVIEWED_OK' | 'REVIEWED_ADJUSTED') {
    setBusy(true)
    setError(null)
    try {
      await api.post(`${apiPrefix}/work-events/${row.eventId}/review`, {
        reviewStatus,
        notes: notes.trim() || undefined,
      })
      onDone(
        reviewStatus === 'REVIEWED_OK'
          ? `Marked ${row.employeeName}'s ${row.eventLabel.toLowerCase()} as reviewed.`
          : `Marked ${row.employeeName}'s ${row.eventLabel.toLowerCase()} as needing a correction.`,
      )
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'That review could not be saved.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      title={`Review ${row.eventLabel}`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => void submit('REVIEWED_ADJUSTED')}
            disabled={busy}
          >
            Needs a correction
          </button>
          <button
            type="button"
            className="btn btn-success"
            onClick={() => void submit('REVIEWED_OK')}
            disabled={busy}
          >
            {busy ? <Spinner light label="Saving…" /> : 'Looks correct'}
          </button>
        </>
      }
    >
      <div className="stack">
        {error && <Alert tone="error">{error}</Alert>}

        <div>
          <div className="strong">{row.employeeName}</div>
          <div className="small muted">{formatDateTime(row.eventTime)}</div>
        </div>

        <div className="stack-sm">
          {row.flags.map((flag) => (
            <div key={flag.code} className="small">
              <span className="strong">⚠ {flag.label}</span>
              {flag.detail && <div className="muted">{flag.detail}</div>}
            </div>
          ))}
        </div>

        <div className="field">
          <label className="label" htmlFor="review-notes">Notes (optional)</label>
          <textarea
            id="review-notes"
            className="textarea"
            maxLength={1000}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            placeholder="What did you find? For example: employee confirmed the warehouse gate is 150 m from the office pin."
          />
        </div>

        <p className="small muted" style={{ margin: 0 }}>
          Reviewing does not change the recorded activity. If the record itself needs correcting,
          mark it as needing a correction and raise an adjustment — the original stays intact.
        </p>
      </div>
    </Modal>
  )
}
