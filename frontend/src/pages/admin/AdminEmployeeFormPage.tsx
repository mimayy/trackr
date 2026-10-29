import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Alert, LoadingPanel, Spinner } from '../../components/ui'
import { ApiError, api } from '../../lib/api'
import { formatDateTime } from '../../lib/format'
import type {
  Branch,
  EmployeeDetail,
  InvitationResponse,
  LocationRow,
  ScheduleRow,
  SupervisorRow,
  WorkCategory,
} from '../../lib/types'

/**
 * Create or edit an employee.
 *
 * Notice what this form does not ask for: any device. The administrator never
 * registers a fingerprint or picks a phone - the employee does that themselves
 * during activation, on their own device. The form says so plainly rather than
 * leaving a confusing empty field.
 */
export function AdminEmployeeFormPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const editing = Boolean(id)

  const [employeeNo, setEmployeeNo] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [contactNumber, setContactNumber] = useState('')
  const [branch, setBranch] = useState<Branch>('MANILA')
  const [workCategory, setWorkCategory] = useState<WorkCategory>('OFFICE_BASED')
  const [supervisorId, setSupervisorId] = useState('')
  const [workScheduleId, setWorkScheduleId] = useState('')
  const [homeOfficeId, setHomeOfficeId] = useState('')

  const [supervisors, setSupervisors] = useState<SupervisorRow[]>([])
  const [schedules, setSchedules] = useState<ScheduleRow[]>([])
  const [offices, setOffices] = useState<LocationRow[]>([])

  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [created, setCreated] = useState<EmployeeDetail | null>(null)
  const [invitation, setInvitation] = useState<InvitationResponse | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const [supervisorList, scheduleList, locationList] = await Promise.all([
          api.get<SupervisorRow[]>('/api/admin/supervisors'),
          api.get<ScheduleRow[]>('/api/admin/schedules'),
          api.get<LocationRow[]>('/api/admin/locations'),
        ])

        if (cancelled) return
        setSupervisors(supervisorList.filter((supervisor) => supervisor.active))
        setSchedules(scheduleList.filter((schedule) => schedule.active))
        setOffices(locationList.filter((location) => location.type === 'OFFICE' && location.active))

        // A sensible default beats an empty dropdown the administrator has to
        // think about on every single hire.
        const standard = scheduleList.find((schedule) => schedule.name === 'Standard Workday')
        if (standard && !editing) {
          setWorkScheduleId(standard.id)
        }

        if (editing && id) {
          const detail = await api.get<EmployeeDetail>(`/api/admin/employees/${id}`)
          if (cancelled) return
          const summary = detail.summary
          setEmployeeNo(summary.employeeNo)
          setFirstName(summary.fullName.split(' ')[0] ?? '')
          setLastName(summary.fullName.split(' ').slice(1).join(' '))
          setEmail(summary.email)
          setContactNumber(summary.contactNumber ?? '')
          setBranch(summary.branch)
          setWorkCategory(summary.workCategory)
          setSupervisorId(summary.supervisorId ?? '')
          setWorkScheduleId(summary.scheduleId ?? '')
        }
      } catch (caught) {
        if (!cancelled) {
          setError(caught instanceof ApiError ? caught.message : 'We could not load the form.')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [editing, id])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setFieldErrors({})

    const payload = {
      employeeNo: employeeNo.trim(),
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      email: email.trim(),
      contactNumber: contactNumber.trim() || undefined,
      branch,
      workCategory,
      supervisorId: supervisorId || undefined,
      workScheduleId: workScheduleId || undefined,
      homeOfficeId: homeOfficeId || undefined,
    }

    try {
      if (editing && id) {
        await api.patch<EmployeeDetail>(`/api/admin/employees/${id}`, payload)
        navigate(`/admin/employees/${id}`)
        return
      }
      setCreated(await api.post<EmployeeDetail>('/api/admin/employees', payload))
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message)
        setFieldErrors(caught.fields ?? {})
      } else {
        setError('We could not save this employee. Please try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  async function generateInvitation() {
    if (!created) return
    setBusy(true)
    try {
      setInvitation(
        await api.post<InvitationResponse>(`/api/admin/employees/${created.summary.id}/invitation`),
      )
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The invitation could not be created.')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <LoadingPanel />

  /* ---- After creation: the activation step ---- */
  if (created) {
    const summary = created.summary
    return (
      <div style={{ maxWidth: 620 }}>
        <div className="card stack-lg">
          <div>
            <div style={{ fontSize: '2rem', color: 'var(--success-600)' }} aria-hidden="true">✓</div>
            <h1 className="page-title">Employee account created</h1>
          </div>

          <div className="card card-tight" style={{ background: 'var(--ink-050)', border: 'none' }}>
            <div className="stack-sm">
              <Line label="Name" value={summary.fullName} />
              <Line label="Employee ID" value={summary.employeeNo} />
              <Line label="Branch" value={summary.branch === 'MANILA' ? 'Manila' : 'Cebu'} />
              <Line
                label="Work category"
                value={summary.workCategory === 'FIELD_BASED' ? 'Field-based' : 'Office-based'}
              />
              <Line label="Supervisor" value={summary.supervisorName ?? 'Not assigned'} />
              <Line label="Status" value="Pending activation" />
              <Line label="Registered device" value="None — the employee registers their own phone" />
            </div>
          </div>

          {error && <Alert tone="error">{error}</Alert>}

          {invitation ? (
            <div className="stack">
              <Alert tone="success" title="Activation link ready">
                This link works once and expires {formatDateTime(invitation.expiresAt)}. It is shown
                only now and cannot be looked up again — if it is lost, generate a new one.
              </Alert>
              <input
                className="input"
                readOnly
                value={invitation.activationUrl}
                onFocus={(event) => event.target.select()}
              />
              <div className="row row-wrap">
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={() => void navigator.clipboard?.writeText(invitation.activationUrl)}
                >
                  COPY ACTIVATION LINK
                </button>
                <button type="button" className="btn btn-secondary" onClick={generateInvitation} disabled={busy}>
                  Generate a new link
                </button>
              </div>
              {invitation.emailNote && <p className="small muted">{invitation.emailNote}</p>}
            </div>
          ) : (
            <div className="row row-wrap">
              <button type="button" className="btn btn-primary" onClick={generateInvitation} disabled={busy}>
                {busy ? <Spinner light label="Creating…" /> : 'CREATE ACTIVATION LINK'}
              </button>
            </div>
          )}

          <div className="row row-wrap">
            <Link className="btn btn-secondary" to={`/admin/employees/${summary.id}`}>
              Open employee
            </Link>
            <Link className="btn btn-ghost" to="/admin/employees">Back to all employees</Link>
          </div>
        </div>
      </div>
    )
  }

  /* ---- The form ---- */
  return (
    <div style={{ maxWidth: 720 }}>
      <div className="page-header">
        <div>
          <Link className="btn btn-ghost btn-sm" to="/admin/employees">← All employees</Link>
          <h1 className="page-title" style={{ marginTop: 8 }}>
            {editing ? 'Edit employee' : 'Create employee'}
          </h1>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="stack-lg">
        {error && <Alert tone="error">{error}</Alert>}

        <section className="card stack">
          <div className="card-title" style={{ marginBottom: 0 }}>Personal information</div>

          <div className="grid-form">
            <Field label="Employee ID" htmlFor="employeeNo" error={fieldErrors.employeeNo}>
              <input
                id="employeeNo"
                className={`input${fieldErrors.employeeNo ? ' input-error' : ''}`}
                required
                disabled={editing}
                value={employeeNo}
                onChange={(event) => setEmployeeNo(event.target.value)}
                placeholder="EMP-012"
              />
            </Field>
            <Field label="Contact number" htmlFor="contact">
              <input
                id="contact"
                className="input"
                value={contactNumber}
                onChange={(event) => setContactNumber(event.target.value)}
                placeholder="+63 917 000 0000"
              />
            </Field>
            <Field label="First name" htmlFor="firstName" error={fieldErrors.firstName}>
              <input
                id="firstName"
                className={`input${fieldErrors.firstName ? ' input-error' : ''}`}
                required
                value={firstName}
                onChange={(event) => setFirstName(event.target.value)}
              />
            </Field>
            <Field label="Last name" htmlFor="lastName" error={fieldErrors.lastName}>
              <input
                id="lastName"
                className={`input${fieldErrors.lastName ? ' input-error' : ''}`}
                required
                value={lastName}
                onChange={(event) => setLastName(event.target.value)}
              />
            </Field>
          </div>

          <Field label="Email" htmlFor="email" error={fieldErrors.email} hint="The employee signs in with this address.">
            <input
              id="email"
              className={`input${fieldErrors.email ? ' input-error' : ''}`}
              type="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </Field>
        </section>

        <section className="card stack">
          <div className="card-title" style={{ marginBottom: 0 }}>Work information</div>

          <Field label="Branch" htmlFor="branch">
            <select
              id="branch"
              className="select"
              value={branch}
              onChange={(event) => setBranch(event.target.value as Branch)}
            >
              <option value="MANILA">Manila</option>
              <option value="CEBU">Cebu</option>
            </select>
          </Field>

          <div className="field">
            <span className="label">Work category</span>
            <span className="field-hint">
              How this person is employed — not where they happen to work today. A field-based
              employee often works at the office on days with no site visit.
            </span>
            <label className={`radio-row${workCategory === 'OFFICE_BASED' ? ' selected' : ''}`}>
              <input
                type="radio"
                name="workCategory"
                checked={workCategory === 'OFFICE_BASED'}
                onChange={() => setWorkCategory('OFFICE_BASED')}
              />
              <span>Office-based</span>
            </label>
            <label className={`radio-row${workCategory === 'FIELD_BASED' ? ' selected' : ''}`}>
              <input
                type="radio"
                name="workCategory"
                checked={workCategory === 'FIELD_BASED'}
                onChange={() => setWorkCategory('FIELD_BASED')}
              />
              <span>Field-based</span>
            </label>
          </div>

          <div className="grid-form">
            <Field label="Supervisor" htmlFor="supervisor">
              <select
                id="supervisor"
                className="select"
                value={supervisorId}
                onChange={(event) => setSupervisorId(event.target.value)}
              >
                <option value="">Not assigned</option>
                {supervisors.map((supervisor) => (
                  <option key={supervisor.id} value={supervisor.id}>
                    {supervisor.fullName} ({supervisor.branch === 'MANILA' ? 'Manila' : 'Cebu'})
                  </option>
                ))}
              </select>
            </Field>

            <Field label="Work schedule" htmlFor="schedule">
              <select
                id="schedule"
                className="select"
                value={workScheduleId}
                onChange={(event) => setWorkScheduleId(event.target.value)}
              >
                <option value="">Not assigned</option>
                {schedules.map((schedule) => (
                  <option key={schedule.id} value={schedule.id}>
                    {schedule.name} ({schedule.startTime.slice(0, 5)}–{schedule.endTime.slice(0, 5)})
                  </option>
                ))}
              </select>
            </Field>

            <Field label="Home office" htmlFor="office" hint="Defaults to the branch office.">
              <select
                id="office"
                className="select"
                value={homeOfficeId}
                onChange={(event) => setHomeOfficeId(event.target.value)}
              >
                <option value="">Use the branch office</option>
                {offices.map((office) => (
                  <option key={office.id} value={office.id}>{office.name}</option>
                ))}
              </select>
            </Field>
          </div>
        </section>

        {!editing && (
          <section className="card stack-sm">
            <div className="card-title" style={{ marginBottom: 0 }}>Account and device</div>
            <div className="row-between">
              <span className="muted">Account status</span>
              <span className="pill pill-warning">Pending activation</span>
            </div>
            <div className="row-between">
              <span className="muted">Registered device</span>
              <span className="strong">None</span>
            </div>
            <p className="small muted" style={{ margin: 0 }}>
              The device will be registered by the employee during activation. You will get a
              one-time activation link to send them once this account is created.
            </p>
          </section>
        )}

        <div className="row row-wrap">
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? <Spinner light label="Saving…" /> : editing ? 'SAVE CHANGES' : 'CREATE EMPLOYEE'}
          </button>
          <Link className="btn btn-secondary" to="/admin/employees">Cancel</Link>
        </div>
      </form>
    </div>
  )
}

function Field({
  label,
  htmlFor,
  hint,
  error,
  children,
}: {
  label: string
  htmlFor: string
  hint?: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="field">
      <label className="label" htmlFor={htmlFor}>{label}</label>
      {children}
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && <span className="field-error">{error}</span>}
    </div>
  )
}

function Line({ label, value }: { label: string; value: string }) {
  return (
    <div className="row-between">
      <span className="small muted">{label}</span>
      <span className="strong" style={{ textAlign: 'right' }}>{value}</span>
    </div>
  )
}
