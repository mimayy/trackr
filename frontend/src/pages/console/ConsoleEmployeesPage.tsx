import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AccountStatusPill, Alert, LoadingPanel, Pill } from '../../components/ui'
import { api, query } from '../../lib/api'
import { formatDateTime } from '../../lib/format'
import type { AccountStatus, Branch, EmployeeRow, WorkCategory } from '../../lib/types'
import { useConsole } from './useConsole'

/**
 * The employee list.
 *
 * Administrators see every employee and can add one; supervisors see only the
 * people assigned to them and can add nobody. That difference is enforced by
 * the API - this page simply reflects it.
 */
export function ConsoleEmployeesPage() {
  const console = useConsole()

  const [rows, setRows] = useState<EmployeeRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [branch, setBranch] = useState<Branch | ''>('')
  const [category, setCategory] = useState<WorkCategory | ''>('')
  const [status, setStatus] = useState<AccountStatus | ''>('')
  const [search, setSearch] = useState('')

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      try {
        const path = console.canManage
          ? `${console.adminApi}/employees${query({ branch, workCategory: category, status })}`
          : `${console.api}/employees${query({ branch, workCategory: category })}`

        const found = await api.get<EmployeeRow[]>(path)
        if (!cancelled) {
          setRows(found)
          setError(null)
        }
      } catch {
        if (!cancelled) setError('We could not load the employee list just now.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [console.api, console.adminApi, console.canManage, branch, category, status])

  const visible = rows.filter((row) => {
    if (!search.trim()) return true
    const needle = search.trim().toLowerCase()
    return (
      row.fullName.toLowerCase().includes(needle) ||
      row.employeeNo.toLowerCase().includes(needle) ||
      row.email.toLowerCase().includes(needle)
    )
  })

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Employees</h1>
          <p className="page-subtitle">
            {console.canManage
              ? 'Every employee at Uni-Ace.'
              : 'The employees assigned to you.'}
          </p>
        </div>
        {console.canManage && (
          <Link className="btn btn-primary" to={`${console.base}/employees/new`}>
            + Add employee
          </Link>
        )}
      </div>

      <div className="filters">
        <div className="field" style={{ minWidth: 220 }}>
          <label className="label" htmlFor="search">Search</label>
          <input
            id="search"
            className="input"
            type="search"
            placeholder="Name, ID or email"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
        <div className="field">
          <label className="label" htmlFor="branch">Branch</label>
          <select
            id="branch"
            className="select"
            value={branch}
            onChange={(event) => setBranch(event.target.value as Branch | '')}
          >
            <option value="">All</option>
            <option value="MANILA">Manila</option>
            <option value="CEBU">Cebu</option>
          </select>
        </div>
        <div className="field">
          <label className="label" htmlFor="category">Work category</label>
          <select
            id="category"
            className="select"
            value={category}
            onChange={(event) => setCategory(event.target.value as WorkCategory | '')}
          >
            <option value="">All</option>
            <option value="OFFICE_BASED">Office-based</option>
            <option value="FIELD_BASED">Field-based</option>
          </select>
        </div>
        {console.canManage && (
          <div className="field">
            <label className="label" htmlFor="status">Account status</label>
            <select
              id="status"
              className="select"
              value={status}
              onChange={(event) => setStatus(event.target.value as AccountStatus | '')}
            >
              <option value="">All</option>
              <option value="ACTIVE">Active</option>
              <option value="PENDING_ACTIVATION">Pending activation</option>
              <option value="DISABLED">Disabled</option>
            </select>
          </div>
        )}
      </div>

      {error && <Alert tone="error">{error}</Alert>}
      {loading && <LoadingPanel />}

      {!loading && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Employee ID</th>
                <th>Name</th>
                <th>Branch</th>
                <th>Work category</th>
                <th>Supervisor</th>
                <th>Account</th>
                <th>Device</th>
                <th>Latest recorded location</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {visible.map((row) => (
                <tr key={row.id}>
                  <td className="strong">{row.employeeNo}</td>
                  <td>
                    <Link to={`${console.base}/employees/${row.id}`}>{row.fullName}</Link>
                    <div className="tiny muted">{row.email}</div>
                  </td>
                  <td>{row.branch === 'MANILA' ? 'Manila' : 'Cebu'}</td>
                  <td>{row.workCategory === 'FIELD_BASED' ? 'Field-based' : 'Office-based'}</td>
                  <td>{row.supervisorName ?? <span className="muted">Not assigned</span>}</td>
                  <td><AccountStatusPill status={row.accountStatus} /></td>
                  <td>
                    {row.deviceStatus === 'ACTIVE' ? (
                      <Pill tone="success">Registered</Pill>
                    ) : (
                      <Pill tone="neutral">None</Pill>
                    )}
                  </td>
                  <td>
                    {row.latestLocationName ?? <span className="muted">—</span>}
                    {row.latestEventTime && (
                      <div className="tiny muted">
                        {row.latestEventLabel} · {formatDateTime(row.latestEventTime)}
                      </div>
                    )}
                  </td>
                  <td>
                    <Link className="btn btn-secondary btn-sm" to={`${console.base}/employees/${row.id}`}>
                      View
                    </Link>
                  </td>
                </tr>
              ))}
              {visible.length === 0 && (
                <tr>
                  <td colSpan={9} className="table-empty">
                    No employees match these filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
