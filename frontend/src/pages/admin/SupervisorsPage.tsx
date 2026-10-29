import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { AccountStatusPill, Alert, LoadingPanel, Modal, Spinner } from '../../components/ui'
import { ApiError, api } from '../../lib/api'
import { formatDateTime } from '../../lib/format'
import type { Branch, EmployeeRow, InvitationResponse, SupervisorRow } from '../../lib/types'

/**
 * Supervisor accounts and the employees they are responsible for.
 *
 * Assigning employees here is how an administrator changes a supervisor's
 * scope; the server uses exactly this assignment to decide what they can see.
 */
export function SupervisorsPage() {
  const [rows, setRows] = useState<SupervisorRow[]>([])
  const [employees, setEmployees] = useState<EmployeeRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [invitation, setInvitation] = useState<InvitationResponse | null>(null)

  const [creating, setCreating] = useState(false)
  const [assigning, setAssigning] = useState<SupervisorRow | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [supervisorList, employeeList] = await Promise.all([
        api.get<SupervisorRow[]>('/api/admin/supervisors'),
        api.get<EmployeeRow[]>('/api/admin/employees'),
      ])
      setRows(supervisorList)
      setEmployees(employeeList)
      setError(null)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'We could not load supervisors.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function toggle(supervisor: SupervisorRow) {
    const action = supervisor.accountStatus === 'DISABLED' ? 'enable' : 'disable'
    try {
      await api.post(`/api/admin/supervisors/${supervisor.id}/${action}`)
      setNotice(
        action === 'disable'
          ? `${supervisor.fullName} can no longer sign in.`
          : `${supervisor.fullName} can sign in again.`,
      )
      await load()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'That change could not be saved.')
    }
  }

  async function invite(supervisor: SupervisorRow) {
    try {
      setInvitation(await api.post<InvitationResponse>(`/api/admin/supervisors/${supervisor.id}/invitation`))
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The invitation could not be created.')
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Supervisors</h1>
          <p className="page-subtitle">
            A supervisor sees the employees assigned to them, and no one else.
          </p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
          + Add supervisor
        </button>
      </div>

      {notice && <Alert tone="success">{notice}</Alert>}
      {error && <Alert tone="error">{error}</Alert>}

      {invitation && (
        <Alert tone="info" title="Activation link ready">
          <p className="small">Expires {formatDateTime(invitation.expiresAt)}. Shown only once.</p>
          <input className="input" readOnly value={invitation.activationUrl} onFocus={(e) => e.target.select()} />
          <div className="row" style={{ marginTop: 8 }}>
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => void navigator.clipboard?.writeText(invitation.activationUrl)}
            >
              Copy activation link
            </button>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setInvitation(null)}>
              Dismiss
            </button>
          </div>
        </Alert>
      )}

      {loading && <LoadingPanel />}

      {!loading && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Branch</th>
                <th>Scope</th>
                <th>Employees</th>
                <th>Account</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td className="strong">{row.fullName}</td>
                  <td>{row.email}</td>
                  <td>{row.branch === 'MANILA' ? 'Manila' : 'Cebu'}</td>
                  <td>{row.scopeNote ?? <span className="muted">—</span>}</td>
                  <td>{row.employeeCount}</td>
                  <td><AccountStatusPill status={row.accountStatus} /></td>
                  <td>
                    <div className="row row-wrap" style={{ gap: 6 }}>
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => setAssigning(row)}>
                        Assign employees
                      </button>
                      {row.accountStatus === 'PENDING_ACTIVATION' && (
                        <button type="button" className="btn btn-secondary btn-sm" onClick={() => void invite(row)}>
                          Activation link
                        </button>
                      )}
                      <button
                        type="button"
                        className={row.accountStatus === 'DISABLED' ? 'btn btn-success btn-sm' : 'btn btn-danger btn-sm'}
                        onClick={() => void toggle(row)}
                      >
                        {row.accountStatus === 'DISABLED' ? 'Enable' : 'Disable'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={7} className="table-empty">No supervisors yet.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {creating && (
        <CreateSupervisorModal
          onClose={() => setCreating(false)}
          onCreated={(supervisor) => {
            setCreating(false)
            setNotice(`${supervisor.fullName} has been created. Send them an activation link to finish setup.`)
            void load()
          }}
        />
      )}

      {assigning && (
        <AssignEmployeesModal
          supervisor={assigning}
          employees={employees}
          onClose={() => setAssigning(null)}
          onSaved={(count) => {
            setAssigning(null)
            setNotice(`${count} ${count === 1 ? 'employee' : 'employees'} now report to ${assigning.fullName}.`)
            void load()
          }}
        />
      )}
    </div>
  )
}

function CreateSupervisorModal({
  onClose,
  onCreated,
}: {
  onClose: () => void
  onCreated: (supervisor: SupervisorRow) => void
}) {
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [contactNumber, setContactNumber] = useState('')
  const [branch, setBranch] = useState<Branch>('MANILA')
  const [scopeNote, setScopeNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onCreated(
        await api.post<SupervisorRow>('/api/admin/supervisors', {
          firstName: firstName.trim(),
          lastName: lastName.trim(),
          email: email.trim(),
          contactNumber: contactNumber.trim() || undefined,
          branch,
          scopeNote: scopeNote.trim() || undefined,
        }),
      )
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The supervisor could not be created.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Add supervisor" onClose={onClose}>
      <form onSubmit={handleSubmit} className="stack">
        {error && <Alert tone="error">{error}</Alert>}

        <div className="grid-form">
          <div className="field">
            <label className="label" htmlFor="s-first">First name</label>
            <input id="s-first" className="input" required value={firstName} onChange={(e) => setFirstName(e.target.value)} />
          </div>
          <div className="field">
            <label className="label" htmlFor="s-last">Last name</label>
            <input id="s-last" className="input" required value={lastName} onChange={(e) => setLastName(e.target.value)} />
          </div>
        </div>

        <div className="field">
          <label className="label" htmlFor="s-email">Email</label>
          <input id="s-email" className="input" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>

        <div className="grid-form">
          <div className="field">
            <label className="label" htmlFor="s-contact">Contact number</label>
            <input id="s-contact" className="input" value={contactNumber} onChange={(e) => setContactNumber(e.target.value)} />
          </div>
          <div className="field">
            <label className="label" htmlFor="s-branch">Branch</label>
            <select id="s-branch" className="select" value={branch} onChange={(e) => setBranch(e.target.value as Branch)}>
              <option value="MANILA">Manila</option>
              <option value="CEBU">Cebu</option>
            </select>
          </div>
        </div>

        <div className="field">
          <label className="label" htmlFor="s-scope">Responsibility (optional)</label>
          <input
            id="s-scope"
            className="input"
            maxLength={300}
            value={scopeNote}
            onChange={(e) => setScopeNote(e.target.value)}
            placeholder="Manila field and office team"
          />
        </div>

        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? <Spinner light label="Creating…" /> : 'CREATE SUPERVISOR'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function AssignEmployeesModal({
  supervisor,
  employees,
  onClose,
  onSaved,
}: {
  supervisor: SupervisorRow
  employees: EmployeeRow[]
  onClose: () => void
  onSaved: (count: number) => void
}) {
  const [selected, setSelected] = useState<string[]>(
    employees.filter((employee) => employee.supervisorId === supervisor.id).map((employee) => employee.id),
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function save() {
    setBusy(true)
    setError(null)
    try {
      await api.post(`/api/admin/supervisors/${supervisor.id}/employees`, { employeeIds: selected })
      onSaved(selected.length)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'That assignment could not be saved.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      title={`Employees reporting to ${supervisor.fullName}`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="button" className="btn btn-primary" onClick={save} disabled={busy}>
            {busy ? <Spinner light label="Saving…" /> : 'SAVE'}
          </button>
        </>
      }
    >
      <div className="stack-sm">
        {error && <Alert tone="error">{error}</Alert>}
        <p className="small muted">
          This is exactly what {supervisor.fullName} will be able to see. Unticking someone removes
          them from this supervisor's scope immediately.
        </p>

        {employees.map((employee) => (
          <label key={employee.id} className="checkbox-row">
            <input
              type="checkbox"
              checked={selected.includes(employee.id)}
              onChange={(event) =>
                setSelected((current) =>
                  event.target.checked
                    ? [...current, employee.id]
                    : current.filter((id) => id !== employee.id),
                )
              }
            />
            <span>
              <span className="strong">{employee.fullName}</span>
              <span className="small muted">
                {' '}— {employee.employeeNo} · {employee.branch === 'MANILA' ? 'Manila' : 'Cebu'}
                {employee.supervisorId && employee.supervisorId !== supervisor.id
                  ? ` · currently reports to ${employee.supervisorName}`
                  : ''}
              </span>
            </span>
          </label>
        ))}
      </div>
    </Modal>
  )
}
