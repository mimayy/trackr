import { useEffect, useState } from 'react'
import { Alert, LoadingPanel, Modal, Pill } from '../../components/ui'
import { ApiError, api, query } from '../../lib/api'
import { formatDateTime } from '../../lib/format'
import type { AuditRow, Page } from '../../lib/types'

const RESULT_TONE = {
  SUCCESS: 'success',
  FAILURE: 'danger',
  FLAGGED: 'warning',
  DENIED: 'danger',
} as const

/**
 * The audit trail: who did what, when, and whether it worked.
 *
 * Append-only. Nothing in the application updates or deletes a row here, which
 * is the point - a log you can edit is not evidence of anything.
 */
export function AuditLogsPage() {
  const [page, setPage] = useState<Page<AuditRow> | null>(null)
  const [actions, setActions] = useState<string[]>([])
  const [pageNumber, setPageNumber] = useState(0)
  const [action, setAction] = useState('')
  const [result, setResult] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<AuditRow | null>(null)

  useEffect(() => {
    void api
      .get<string[]>('/api/admin/audit-logs/actions')
      .then(setActions)
      .catch(() => undefined)
  }, [])

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      try {
        const found = await api.get<Page<AuditRow>>(
          `/api/admin/audit-logs${query({ action, result, page: pageNumber, size: 50 })}`,
        )
        if (!cancelled) {
          setPage(found)
          setError(null)
        }
      } catch (caught) {
        if (!cancelled) {
          setError(caught instanceof ApiError ? caught.message : 'We could not load the audit log.')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [action, result, pageNumber])

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Audit logs</h1>
          <p className="page-subtitle">
            Every account change, device revocation, review and flagged sync, in order.
          </p>
        </div>
      </div>

      <div className="filters">
        <div className="field" style={{ minWidth: 240 }}>
          <label className="label" htmlFor="a-action">Action</label>
          <select
            id="a-action"
            className="select"
            value={action}
            onChange={(event) => {
              setAction(event.target.value)
              setPageNumber(0)
            }}
          >
            <option value="">All actions</option>
            {actions.map((value) => (
              <option key={value} value={value}>{value.replace(/_/g, ' ').toLowerCase()}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label className="label" htmlFor="a-result">Result</label>
          <select
            id="a-result"
            className="select"
            value={result}
            onChange={(event) => {
              setResult(event.target.value)
              setPageNumber(0)
            }}
          >
            <option value="">All</option>
            <option value="SUCCESS">Success</option>
            <option value="FAILURE">Failure</option>
            <option value="FLAGGED">Flagged</option>
            <option value="DENIED">Denied</option>
          </select>
        </div>
      </div>

      {error && <Alert tone="error">{error}</Alert>}
      {loading && <LoadingPanel />}

      {!loading && page && (
        <div className="stack">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Actor</th>
                  <th>Action</th>
                  <th>Target</th>
                  <th>Result</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {page.content.map((row) => (
                  <tr key={row.id}>
                    <td>{formatDateTime(row.occurredAt)}</td>
                    <td>
                      {row.actorLabel}
                      <div className="tiny muted">{row.actorRole.toLowerCase()}</div>
                    </td>
                    <td>{row.action.replace(/_/g, ' ').toLowerCase()}</td>
                    <td>
                      {row.targetLabel ?? row.targetId ?? <span className="muted">—</span>}
                      {row.targetType && <div className="tiny muted">{row.targetType}</div>}
                    </td>
                    <td><Pill tone={RESULT_TONE[row.result]}>{row.result.toLowerCase()}</Pill></td>
                    <td>
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => setSelected(row)}>
                        Details
                      </button>
                    </td>
                  </tr>
                ))}
                {page.content.length === 0 && (
                  <tr>
                    <td colSpan={6} className="table-empty">No entries match these filters.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="row-between">
            <span className="small muted">
              Page {page.number + 1} of {Math.max(page.totalPages, 1)} · {page.totalElements} entries
            </span>
            <div className="row">
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={page.number === 0}
                onClick={() => setPageNumber((value) => Math.max(0, value - 1))}
              >
                Previous
              </button>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={page.number + 1 >= page.totalPages}
                onClick={() => setPageNumber((value) => value + 1)}
              >
                Next
              </button>
            </div>
          </div>
        </div>
      )}

      {selected && (
        <Modal title="Audit entry" onClose={() => setSelected(null)}>
          <div className="stack-sm">
            <Line label="When" value={formatDateTime(selected.occurredAt)} />
            <Line label="Actor" value={`${selected.actorLabel} (${selected.actorRole.toLowerCase()})`} />
            <Line label="Action" value={selected.action} />
            <Line label="Target type" value={selected.targetType} />
            <Line label="Target" value={selected.targetLabel ?? selected.targetId} />
            <Line label="Result" value={selected.result} />
            <Line label="IP address" value={selected.ipAddress} />
            {selected.detail && (
              <div>
                <div className="small muted">Detail</div>
                <p style={{ marginTop: 4 }}>{selected.detail}</p>
              </div>
            )}
          </div>
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
