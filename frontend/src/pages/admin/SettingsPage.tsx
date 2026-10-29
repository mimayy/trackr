import { useCallback, useEffect, useState } from 'react'
import { Alert, LoadingPanel, Spinner } from '../../components/ui'
import { ApiError, api } from '../../lib/api'
import { formatDateTime } from '../../lib/format'
import type { AdjustmentRow, SettingRow } from '../../lib/types'

/** Plain-language names for the settings keys, so the page is readable. */
const SETTING_LABEL: Record<string, string> = {
  'gps.max_accuracy_meters': 'Maximum GPS accuracy (metres)',
  'gps.warn_accuracy_meters': 'GPS accuracy warning threshold (metres)',
  'geofence.enforce_field_arrival': 'Refuse field arrivals outside the geofence',
  'attendance.late_grace_minutes': 'Grace period before a Time In counts as late (minutes)',
  'attendance.overtime_threshold_minutes': 'Minutes beyond schedule before overtime counts',
  'attendance.max_workday_hours': 'Longest believable workday (hours)',
  'sync.max_clock_skew_minutes': 'Allowed device clock difference (minutes)',
  'photo.require_for_field_arrival': 'Require a photo for field arrivals',
  'retention.work_event_days': 'Keep work events for (days)',
  'retention.photo_days': 'Keep photo evidence for (days)',
}

const BOOLEAN_KEYS = new Set([
  'geofence.enforce_field_arrival',
  'photo.require_for_field_arrival',
])

/**
 * System behaviour the administrator can change without a redeploy.
 *
 * These values feed straight into event validation and attendance calculation,
 * so the page says what each one actually does rather than showing a bare key.
 */
export function SettingsPage() {
  const [settings, setSettings] = useState<SettingRow[]>([])
  const [adjustments, setAdjustments] = useState<AdjustmentRow[]>([])
  const [systemInfo, setSystemInfo] = useState<Record<string, string> | null>(null)
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [savingKey, setSavingKey] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [settingList, adjustmentList, info] = await Promise.all([
        api.get<SettingRow[]>('/api/admin/settings'),
        api.get<AdjustmentRow[]>('/api/admin/adjustments'),
        api.get<Record<string, string>>('/api/admin/system-info'),
      ])
      setSettings(settingList)
      setAdjustments(adjustmentList)
      setSystemInfo(info)
      setDrafts(Object.fromEntries(settingList.map((setting) => [setting.key, setting.value])))
      setError(null)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'We could not load settings.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function save(key: string) {
    setSavingKey(key)
    setError(null)
    try {
      await api.patch(`/api/admin/settings/${key}`, { value: drafts[key] })
      setNotice(`${SETTING_LABEL[key] ?? key} updated.`)
      await load()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'That setting could not be saved.')
    } finally {
      setSavingKey(null)
    }
  }

  async function decide(adjustment: AdjustmentRow, status: 'APPROVED' | 'REJECTED') {
    try {
      await api.post(`/api/admin/adjustments/${adjustment.id}/decision`, { status })
      setNotice(status === 'APPROVED' ? 'Correction approved.' : 'Correction rejected.')
      await load()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'That decision could not be saved.')
    }
  }

  if (loading) return <LoadingPanel />

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Settings</h1>
          <p className="page-subtitle">
            These values are used the moment they are saved — no restart needed.
          </p>
        </div>
      </div>

      {notice && <Alert tone="success">{notice}</Alert>}
      {error && <Alert tone="error">{error}</Alert>}

      <div className="stack-lg">
        <section className="stack">
          <h2>System behaviour</h2>
          {settings.map((setting) => (
            <div key={setting.key} className="card">
              <div className="row-between row-wrap" style={{ gap: 'var(--space-4)' }}>
                <div style={{ flex: '1 1 320px' }}>
                  <div className="strong">{SETTING_LABEL[setting.key] ?? setting.key}</div>
                  {setting.description && (
                    <p className="small muted" style={{ margin: '4px 0 0' }}>{setting.description}</p>
                  )}
                  <p className="tiny muted" style={{ margin: '6px 0 0' }}>
                    {setting.key}
                    {setting.updatedByName && ` · last changed by ${setting.updatedByName} ${formatDateTime(setting.updatedAt)}`}
                  </p>
                </div>

                <div className="row" style={{ flex: '0 0 auto' }}>
                  {BOOLEAN_KEYS.has(setting.key) ? (
                    <select
                      className="select"
                      style={{ minWidth: 120 }}
                      value={drafts[setting.key] ?? setting.value}
                      onChange={(event) =>
                        setDrafts((current) => ({ ...current, [setting.key]: event.target.value }))
                      }
                    >
                      <option value="true">Yes</option>
                      <option value="false">No</option>
                    </select>
                  ) : (
                    <input
                      className="input"
                      style={{ maxWidth: 140 }}
                      value={drafts[setting.key] ?? setting.value}
                      onChange={(event) =>
                        setDrafts((current) => ({ ...current, [setting.key]: event.target.value }))
                      }
                    />
                  )}
                  <button
                    type="button"
                    className="btn btn-primary btn-sm"
                    disabled={savingKey === setting.key || drafts[setting.key] === setting.value}
                    onClick={() => void save(setting.key)}
                  >
                    {savingKey === setting.key ? <Spinner light label="Saving…" /> : 'Save'}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </section>

        <section className="stack">
          <div>
            <h2>Corrections awaiting a decision</h2>
            <p className="small muted" style={{ margin: '4px 0 0' }}>
              An approved correction is layered on top of the original event. The original is never
              changed or deleted.
            </p>
          </div>

          {adjustments.length === 0 ? (
            <div className="card center" style={{ padding: 'var(--space-6)' }}>
              <p className="muted" style={{ margin: 0 }}>No corrections are waiting.</p>
            </div>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Requested</th>
                    <th>By</th>
                    <th>Field</th>
                    <th>From</th>
                    <th>To</th>
                    <th>Reason</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {adjustments.map((row) => (
                    <tr key={row.id}>
                      <td>{formatDateTime(row.requestedAt)}</td>
                      <td>{row.requestedByName ?? '—'}</td>
                      <td>{row.field.replace(/_/g, ' ').toLowerCase()}</td>
                      <td>{row.originalValue ?? '—'}</td>
                      <td className="strong">{row.adjustedValue ?? '—'}</td>
                      <td>{row.reason}</td>
                      <td>
                        <div className="row" style={{ gap: 6 }}>
                          <button
                            type="button"
                            className="btn btn-success btn-sm"
                            onClick={() => void decide(row, 'APPROVED')}
                          >
                            Approve
                          </button>
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => void decide(row, 'REJECTED')}
                          >
                            Reject
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {systemInfo && (
          <section className="stack">
            <h2>System</h2>
            <div className="card stack-sm">
              <div className="row-between">
                <span className="small muted">Photo storage</span>
                <span className="strong">{systemInfo.photoStorage}</span>
              </div>
              <div className="row-between">
                <span className="small muted">Server time</span>
                <span className="strong">{formatDateTime(systemInfo.serverTime)}</span>
              </div>
              <p className="small muted" style={{ margin: '8px 0 0' }}>
                Photos are kept in object storage, never in the database and never on the
                application server's own disk.
              </p>
            </div>
          </section>
        )}
      </div>
    </div>
  )
}
