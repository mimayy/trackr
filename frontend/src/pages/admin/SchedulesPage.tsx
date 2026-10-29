import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Alert, LoadingPanel, Modal, Pill, Spinner } from '../../components/ui'
import { ApiError, api } from '../../lib/api'
import { formatDate, formatLocalTime } from '../../lib/format'
import type { Branch, HolidayRow, ScheduleRow } from '../../lib/types'

const DAYS = [
  { value: 'MONDAY', short: 'Mon' },
  { value: 'TUESDAY', short: 'Tue' },
  { value: 'WEDNESDAY', short: 'Wed' },
  { value: 'THURSDAY', short: 'Thu' },
  { value: 'FRIDAY', short: 'Fri' },
  { value: 'SATURDAY', short: 'Sat' },
  { value: 'SUNDAY', short: 'Sun' },
]

/**
 * Work schedules and non-working dates.
 *
 * These are what attendance is measured against - the lunch break in
 * particular. Nothing about "one hour for lunch" is hard-coded anywhere; the
 * calculation reads whatever is set here.
 */
export function SchedulesPage() {
  const [schedules, setSchedules] = useState<ScheduleRow[]>([])
  const [holidays, setHolidays] = useState<HolidayRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [editing, setEditing] = useState<ScheduleRow | 'new' | null>(null)
  const [addingHoliday, setAddingHoliday] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [scheduleList, holidayList] = await Promise.all([
        api.get<ScheduleRow[]>('/api/admin/schedules'),
        api.get<HolidayRow[]>('/api/admin/holidays'),
      ])
      setSchedules(scheduleList)
      setHolidays(holidayList)
      setError(null)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'We could not load schedules.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function removeHoliday(holiday: HolidayRow) {
    try {
      await api.del(`/api/admin/holidays/${holiday.id}`)
      setNotice(`${holiday.name} removed.`)
      await load()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'That date could not be removed.')
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Work schedules</h1>
          <p className="page-subtitle">
            Hours worked are calculated as time out minus time in, minus the unpaid break set here.
          </p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setEditing('new')}>
          + Create schedule
        </button>
      </div>

      {notice && <Alert tone="success">{notice}</Alert>}
      {error && <Alert tone="error">{error}</Alert>}
      {loading && <LoadingPanel />}

      {!loading && (
        <div className="stack-lg">
          <div className="stack">
            {schedules.map((schedule) => (
              <div key={schedule.id} className="card">
                <div className="row-between row-wrap">
                  <div>
                    <div className="row" style={{ gap: 10 }}>
                      <span className="strong" style={{ fontSize: 'var(--text-lg)' }}>{schedule.name}</span>
                      {schedule.active ? <Pill tone="success">Active</Pill> : <Pill tone="neutral">Inactive</Pill>}
                    </div>
                    <div className="muted" style={{ marginTop: 6 }}>
                      {formatLocalTime(schedule.startTime)} – {formatLocalTime(schedule.endTime)} ·{' '}
                      {schedule.workingDays.map((day) => day.slice(0, 3).toLowerCase()).join(', ')}
                    </div>
                    <div className="small muted" style={{ marginTop: 4 }}>
                      {schedule.breakStart && schedule.breakEnd
                        ? `${schedule.breakPaid ? 'Paid' : 'Unpaid'} break ${formatLocalTime(schedule.breakStart)} – ${formatLocalTime(schedule.breakEnd)}`
                        : 'No break configured'}
                      {' · '}
                      {schedule.scheduledHours} paid hours a day
                      {' · '}
                      {schedule.timezone}
                      {' · '}
                      {schedule.employeeCount} {schedule.employeeCount === 1 ? 'employee' : 'employees'}
                    </div>
                  </div>
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditing(schedule)}>
                    Edit
                  </button>
                </div>
              </div>
            ))}
          </div>

          <section>
            <div className="row-between" style={{ marginBottom: 'var(--space-3)' }}>
              <div>
                <h2>Non-working dates</h2>
                <p className="small muted" style={{ margin: '4px 0 0' }}>
                  Nobody is marked absent on these days.
                </p>
              </div>
              <button type="button" className="btn btn-secondary" onClick={() => setAddingHoliday(true)}>
                + Add date
              </button>
            </div>

            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Name</th>
                    <th>Applies to</th>
                    <th>Paid</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {holidays.map((holiday) => (
                    <tr key={holiday.id}>
                      <td className="strong">{formatDate(holiday.date)}</td>
                      <td>{holiday.name}</td>
                      <td>{holiday.branch ? (holiday.branch === 'MANILA' ? 'Manila' : 'Cebu') : 'Company-wide'}</td>
                      <td>{holiday.paid ? 'Paid' : 'Unpaid'}</td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          onClick={() => void removeHoliday(holiday)}
                        >
                          Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                  {holidays.length === 0 && (
                    <tr>
                      <td colSpan={5} className="table-empty">No non-working dates configured.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </div>
      )}

      {editing && (
        <ScheduleEditor
          schedule={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(saved) => {
            setEditing(null)
            setNotice(`${saved.name} saved.`)
            void load()
          }}
        />
      )}

      {addingHoliday && (
        <HolidayEditor
          onClose={() => setAddingHoliday(false)}
          onSaved={(saved) => {
            setAddingHoliday(false)
            setNotice(`${saved.name} added.`)
            void load()
          }}
        />
      )}
    </div>
  )
}

function ScheduleEditor({
  schedule,
  onClose,
  onSaved,
}: {
  schedule: ScheduleRow | null
  onClose: () => void
  onSaved: (saved: ScheduleRow) => void
}) {
  const [name, setName] = useState(schedule?.name ?? '')
  const [startTime, setStartTime] = useState((schedule?.startTime ?? '08:00').slice(0, 5))
  const [endTime, setEndTime] = useState((schedule?.endTime ?? '17:00').slice(0, 5))
  const [workingDays, setWorkingDays] = useState<string[]>(
    schedule?.workingDays ?? ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  )
  const [hasBreak, setHasBreak] = useState(Boolean(schedule?.breakStart) || !schedule)
  const [breakStart, setBreakStart] = useState((schedule?.breakStart ?? '12:00').slice(0, 5))
  const [breakEnd, setBreakEnd] = useState((schedule?.breakEnd ?? '13:00').slice(0, 5))
  const [breakPaid, setBreakPaid] = useState(schedule?.breakPaid ?? false)
  const [timezone, setTimezone] = useState(schedule?.timezone ?? 'Asia/Manila')
  const [active, setActive] = useState(schedule?.active ?? true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)

    const payload = {
      name: name.trim(),
      startTime: `${startTime}:00`,
      endTime: `${endTime}:00`,
      workingDays,
      breakStart: hasBreak ? `${breakStart}:00` : undefined,
      breakEnd: hasBreak ? `${breakEnd}:00` : undefined,
      breakPaid,
      timezone,
      active,
    }

    try {
      const saved = schedule
        ? await api.patch<ScheduleRow>(`/api/admin/schedules/${schedule.id}`, payload)
        : await api.post<ScheduleRow>('/api/admin/schedules', payload)
      onSaved(saved)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'That schedule could not be saved.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={schedule ? `Edit ${schedule.name}` : 'Create schedule'} onClose={onClose} width={560}>
      <form onSubmit={handleSubmit} className="stack">
        {error && <Alert tone="error">{error}</Alert>}

        <div className="field">
          <label className="label" htmlFor="sc-name">Schedule name</label>
          <input id="sc-name" className="input" required value={name} onChange={(e) => setName(e.target.value)} placeholder="Standard Workday" />
        </div>

        <div className="grid-form">
          <div className="field">
            <label className="label" htmlFor="sc-start">Start time</label>
            <input id="sc-start" className="input" type="time" required value={startTime} onChange={(e) => setStartTime(e.target.value)} />
          </div>
          <div className="field">
            <label className="label" htmlFor="sc-end">End time</label>
            <input id="sc-end" className="input" type="time" required value={endTime} onChange={(e) => setEndTime(e.target.value)} />
          </div>
        </div>

        <div className="field">
          <span className="label">Working days</span>
          {DAYS.map((day) => (
            <label key={day.value} className="checkbox-row">
              <input
                type="checkbox"
                checked={workingDays.includes(day.value)}
                onChange={(event) =>
                  setWorkingDays((current) =>
                    event.target.checked
                      ? [...current, day.value]
                      : current.filter((value) => value !== day.value),
                  )
                }
              />
              <span>{day.short}</span>
            </label>
          ))}
        </div>

        <label className="checkbox-row">
          <input type="checkbox" checked={hasBreak} onChange={(e) => setHasBreak(e.target.checked)} />
          <span>This schedule has a break</span>
        </label>

        {hasBreak && (
          <>
            <div className="grid-form">
              <div className="field">
                <label className="label" htmlFor="sc-bstart">Break start</label>
                <input id="sc-bstart" className="input" type="time" value={breakStart} onChange={(e) => setBreakStart(e.target.value)} />
              </div>
              <div className="field">
                <label className="label" htmlFor="sc-bend">Break end</label>
                <input id="sc-bend" className="input" type="time" value={breakEnd} onChange={(e) => setBreakEnd(e.target.value)} />
              </div>
            </div>
            <label className="checkbox-row">
              <input type="checkbox" checked={breakPaid} onChange={(e) => setBreakPaid(e.target.checked)} />
              <span>The break is paid (not deducted from hours worked)</span>
            </label>
          </>
        )}

        <div className="field">
          <label className="label" htmlFor="sc-tz">Timezone</label>
          <input id="sc-tz" className="input" value={timezone} onChange={(e) => setTimezone(e.target.value)} />
          <span className="field-hint">Uni-Ace operates in Asia/Manila.</span>
        </div>

        <label className="checkbox-row">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          <span>Active — can be assigned to employees</span>
        </label>

        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? <Spinner light label="Saving…" /> : 'SAVE'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function HolidayEditor({
  onClose,
  onSaved,
}: {
  onClose: () => void
  onSaved: (saved: HolidayRow) => void
}) {
  const [date, setDate] = useState('')
  const [name, setName] = useState('')
  const [branch, setBranch] = useState<Branch | ''>('')
  const [paid, setPaid] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onSaved(
        await api.post<HolidayRow>('/api/admin/holidays', {
          date,
          name: name.trim(),
          branch: branch || undefined,
          paid,
        }),
      )
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'That date could not be added.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Add non-working date" onClose={onClose}>
      <form onSubmit={handleSubmit} className="stack">
        {error && <Alert tone="error">{error}</Alert>}

        <div className="field">
          <label className="label" htmlFor="h-date">Date</label>
          <input id="h-date" className="input" type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
        </div>
        <div className="field">
          <label className="label" htmlFor="h-name">Name</label>
          <input id="h-name" className="input" required value={name} onChange={(e) => setName(e.target.value)} placeholder="Christmas Day" />
        </div>
        <div className="field">
          <label className="label" htmlFor="h-branch">Applies to</label>
          <select id="h-branch" className="select" value={branch} onChange={(e) => setBranch(e.target.value as Branch | '')}>
            <option value="">Company-wide</option>
            <option value="MANILA">Manila only</option>
            <option value="CEBU">Cebu only</option>
          </select>
        </div>
        <label className="checkbox-row">
          <input type="checkbox" checked={paid} onChange={(e) => setPaid(e.target.checked)} />
          <span>Paid holiday</span>
        </label>

        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? <Spinner light label="Adding…" /> : 'ADD DATE'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
