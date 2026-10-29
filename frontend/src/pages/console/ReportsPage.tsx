import { useEffect, useState } from 'react'
import { Alert, AttendancePill, EmptyState, Spinner } from '../../components/ui'
import { ApiError, api, query } from '../../lib/api'
import {
  addDays,
  formatDate,
  formatLocalTime,
  formatMinutes,
  formatTime,
  todayInZone,
} from '../../lib/format'
import type {
  AttendanceReport,
  Branch,
  EmployeeRow,
  EmployeeActivityReport,
  FieldActivityReport,
  LateReport,
  OfficeActivityReport,
  WorkCategory,
} from '../../lib/types'
import { useConsole } from './useConsole'

type ReportType = 'attendance' | 'late' | 'field-activity' | 'office-activity' | 'employee-activity'

const REPORTS: Array<{ value: ReportType; label: string; description: string }> = [
  { value: 'attendance', label: 'Attendance', description: 'Hours worked, lateness, undertime and overtime.' },
  { value: 'late', label: 'Late', description: 'Attendance lateness against the scheduled start.' },
  { value: 'field-activity', label: 'Field activity', description: 'Site visits: where they went and how long they stayed.' },
  { value: 'office-activity', label: 'Office activity', description: 'Office departures paired with returns.' },
  { value: 'employee-activity', label: 'Employee activity', description: 'One person’s full work-event timeline.' },
]

type ReportData =
  | { type: 'attendance'; data: AttendanceReport }
  | { type: 'late'; data: LateReport }
  | { type: 'field-activity'; data: FieldActivityReport }
  | { type: 'office-activity'; data: OfficeActivityReport }
  | { type: 'employee-activity'; data: EmployeeActivityReport }

/**
 * Reports, generated from stored work events every time they are run.
 *
 * Nothing is pre-aggregated or cached, so the same range always produces the
 * same figures and any correction is reflected immediately.
 */
export function ReportsPage() {
  const console = useConsole()

  const [reportType, setReportType] = useState<ReportType>('attendance')
  const [from, setFrom] = useState(addDays(todayInZone(), -13))
  const [to, setTo] = useState(todayInZone())
  const [branch, setBranch] = useState<Branch | ''>('')
  const [workCategory, setWorkCategory] = useState<WorkCategory | ''>('')
  const [employeeId, setEmployeeId] = useState('')

  const [employees, setEmployees] = useState<EmployeeRow[]>([])
  const [report, setReport] = useState<ReportData | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    void api
      .get<EmployeeRow[]>(`${console.api}/employees`)
      .then((found) => {
        if (!cancelled) setEmployees(found)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [console.api])

  async function generate() {
    setBusy(true)
    setError(null)
    setReport(null)

    try {
      if (reportType === 'employee-activity' && !employeeId) {
        setError('Please choose an employee for this report.')
        return
      }

      const params = query({ from, to, branch, workCategory, employeeId })
      const path = `${console.api}/reports/${reportType}${params}`

      switch (reportType) {
        case 'attendance':
          setReport({ type: 'attendance', data: await api.get<AttendanceReport>(path) })
          break
        case 'late':
          setReport({ type: 'late', data: await api.get<LateReport>(path) })
          break
        case 'field-activity':
          setReport({ type: 'field-activity', data: await api.get<FieldActivityReport>(path) })
          break
        case 'office-activity':
          setReport({ type: 'office-activity', data: await api.get<OfficeActivityReport>(path) })
          break
        case 'employee-activity':
          setReport({ type: 'employee-activity', data: await api.get<EmployeeActivityReport>(path) })
          break
      }
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'That report could not be generated.')
    } finally {
      setBusy(false)
    }
  }

  function exportCsv() {
    if (!report) return
    const { header, rows } = toCsv(report)
    const csv = [header, ...rows]
      .map((line) => line.map(csvCell).join(','))
      .join('\r\n')

    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `trackr-${report.type}-${from}-to-${to}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  const selected = REPORTS.find((entry) => entry.value === reportType)

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Reports</h1>
          <p className="page-subtitle">{selected?.description}</p>
        </div>
        {report && (
          <button type="button" className="btn btn-secondary" onClick={exportCsv}>
            Export CSV
          </button>
        )}
      </div>

      <div className="filters">
        <div className="field" style={{ minWidth: 200 }}>
          <label className="label" htmlFor="r-type">Report type</label>
          <select
            id="r-type"
            className="select"
            value={reportType}
            onChange={(event) => setReportType(event.target.value as ReportType)}
          >
            {REPORTS.map((entry) => (
              <option key={entry.value} value={entry.value}>{entry.label}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label className="label" htmlFor="r-from">From</label>
          <input id="r-from" className="input" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="field">
          <label className="label" htmlFor="r-to">To</label>
          <input id="r-to" className="input" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        {reportType !== 'employee-activity' && (
          <>
            <div className="field">
              <label className="label" htmlFor="r-branch">Branch</label>
              <select id="r-branch" className="select" value={branch} onChange={(e) => setBranch(e.target.value as Branch | '')}>
                <option value="">All</option>
                <option value="MANILA">Manila</option>
                <option value="CEBU">Cebu</option>
              </select>
            </div>
            <div className="field">
              <label className="label" htmlFor="r-category">Work category</label>
              <select
                id="r-category"
                className="select"
                value={workCategory}
                onChange={(e) => setWorkCategory(e.target.value as WorkCategory | '')}
              >
                <option value="">All</option>
                <option value="OFFICE_BASED">Office-based</option>
                <option value="FIELD_BASED">Field-based</option>
              </select>
            </div>
          </>
        )}
        <div className="field" style={{ minWidth: 200 }}>
          <label className="label" htmlFor="r-employee">
            Employee{reportType === 'employee-activity' ? '' : ' (optional)'}
          </label>
          <select id="r-employee" className="select" value={employeeId} onChange={(e) => setEmployeeId(e.target.value)}>
            <option value="">All</option>
            {employees.map((employee) => (
              <option key={employee.id} value={employee.id}>{employee.fullName}</option>
            ))}
          </select>
        </div>
        <button type="button" className="btn btn-primary" onClick={generate} disabled={busy}>
          {busy ? <Spinner light label="Generating…" /> : 'GENERATE REPORT'}
        </button>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {!report && !busy && !error && (
        <EmptyState icon="📊" title="Choose a range and generate a report">
          Every figure is calculated from recorded work events and the employee's work schedule.
        </EmptyState>
      )}

      {report?.type === 'attendance' && (
        <div className="stack">
          <div className="grid-cards">
            <Metric label="Hours worked" value={report.data.totals.totalHoursWorked.toFixed(2)} />
            <Metric label="Days present" value={report.data.totals.daysPresent} />
            <Metric label="Days absent" value={report.data.totals.daysAbsent} />
            <Metric label="Total late" value={formatMinutes(report.data.totals.totalLateMinutes)} />
            <Metric label="Total overtime" value={formatMinutes(report.data.totals.totalOvertimeMinutes)} />
            <Metric label="Total undertime" value={formatMinutes(report.data.totals.totalUndertimeMinutes)} />
          </div>

          <Table
            head={['Employee', 'Date', 'Schedule', 'Time in', 'Time out', 'Hours worked', 'Late', 'Undertime', 'Overtime', 'Status']}
            rows={report.data.rows.map((row) => [
              <>
                {row.employeeName}
                <div className="tiny muted">{row.employeeNo}</div>
              </>,
              formatDate(row.date),
              row.scheduleName ?? '—',
              row.timeIn ? formatTime(row.timeIn) : '—',
              row.timeOut ? formatTime(row.timeOut) : '—',
              row.hoursWorkedLabel,
              row.lateMinutes > 0 ? formatMinutes(row.lateMinutes) : '—',
              row.undertimeMinutes > 0 ? formatMinutes(row.undertimeMinutes) : '—',
              row.overtimeMinutes > 0 ? formatMinutes(row.overtimeMinutes) : '—',
              <span className="row row-wrap" style={{ gap: 4 }}>
                {row.statuses.map((status) => (
                  <AttendancePill key={status} status={status} />
                ))}
              </span>,
            ])}
          />
        </div>
      )}

      {report?.type === 'late' && (
        <Table
          head={['Employee', 'Date', 'Scheduled start', 'Actual time in', 'Minutes late']}
          rows={report.data.rows.map((row) => [
            <>
              {row.employeeName}
              <div className="tiny muted">{row.employeeNo}</div>
            </>,
            formatDate(row.date),
            formatLocalTime(row.scheduledStart),
            row.actualTimeIn ? formatTime(row.actualTimeIn) : '—',
            <span style={{ color: 'var(--warning-700)', fontWeight: 600 }}>
              {formatMinutes(row.minutesLate)}
            </span>,
          ])}
        />
      )}

      {report?.type === 'field-activity' && (
        <Table
          head={['Employee', 'Site', 'Date', 'Arrival', 'Departure', 'Time on site', 'Went to']}
          rows={report.data.rows.map((row) => [
            row.employeeName,
            row.siteName ?? '—',
            formatDate(row.date),
            row.arrivalTime ? formatTime(row.arrivalTime) : '—',
            // No departure means they were still on site when the range ended,
            // or nobody recorded leaving.
            row.departureTime ? formatTime(row.departureTime) : 'Still there',
            row.minutesOnSite === undefined || row.minutesOnSite === null
              ? '—'
              : formatMinutes(row.minutesOnSite),
            row.destination ?? '—',
          ])}
        />
      )}

      {report?.type === 'office-activity' && (
        <Table
          head={['Employee', 'Date', 'Office departure', 'Office arrival', 'Time away', 'Destination']}
          rows={report.data.rows.map((row, index) => [
            <span key={index}>{row.employeeName}</span>,
            formatDate(row.date),
            row.officeDeparture ? formatTime(row.officeDeparture) : '—',
            row.officeArrival ? formatTime(row.officeArrival) : '—',
            row.minutesAway ? formatMinutes(row.minutesAway) : '—',
            row.destination ?? '—',
          ])}
        />
      )}

      {report?.type === 'employee-activity' && (
        <div className="stack">
          <div className="card">
            <div className="strong" style={{ fontSize: 'var(--text-lg)' }}>
              {report.data.employeeName}
            </div>
            <div className="small muted">
              {report.data.employeeNo} · {report.data.meta.rowCount} events
            </div>
          </div>

          <Table
            head={['Date', 'Time', 'Event', 'Location', 'Site', 'Verification', 'Captured', 'Review']}
            rows={report.data.events.map((event) => [
              formatDate(event.businessDate),
              formatTime(event.eventTime),
              event.eventLabel,
              event.location?.name ?? '—',
              event.siteName ?? '—',
              event.biometricVerified ? 'Fingerprint verified' : 'Not verified',
              event.captureMode === 'OFFLINE' ? 'Offline' : 'Online',
              event.reviewStatus === 'REQUIRES_REVIEW' ? 'Needs review' : '—',
            ])}
          />
        </div>
      )}
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="metric">
      <div className="metric-value">{value}</div>
      <div className="metric-label">{label}</div>
    </div>
  )
}

function Table({ head, rows }: { head: string[]; rows: React.ReactNode[][] }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            {head.map((column) => (
              <th key={column}>{column}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={index}>
              {row.map((cell, cellIndex) => (
                <td key={cellIndex}>{cell}</td>
              ))}
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={head.length} className="table-empty">
                No records in this range.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

/** Flattens a report into CSV, so it can be opened in a spreadsheet. */
function toCsv(report: ReportData): { header: string[]; rows: Array<Array<string | number>> } {
  switch (report.type) {
    case 'attendance':
      return {
        header: ['Employee ID', 'Employee', 'Branch', 'Date', 'Schedule', 'Time in', 'Time out',
                 'Hours worked', 'Late (min)', 'Undertime (min)', 'Overtime (min)', 'Status'],
        rows: report.data.rows.map((row) => [
          row.employeeNo, row.employeeName, row.branch, row.date, row.scheduleName ?? '',
          row.timeIn ? formatTime(row.timeIn) : '', row.timeOut ? formatTime(row.timeOut) : '',
          row.hoursWorked, row.lateMinutes, row.undertimeMinutes, row.overtimeMinutes,
          row.statuses.join(' '),
        ]),
      }
    case 'late':
      return {
        header: ['Employee ID', 'Employee', 'Date', 'Scheduled start', 'Actual time in', 'Minutes late'],
        rows: report.data.rows.map((row) => [
          row.employeeNo, row.employeeName, row.date, row.scheduledStart ?? '',
          row.actualTimeIn ? formatTime(row.actualTimeIn) : '', row.minutesLate,
        ]),
      }
    case 'field-activity':
      return {
        header: ['Employee ID', 'Employee', 'Site', 'Date', 'Arrival', 'Departure',
                 'Minutes on site', 'Went to'],
        rows: report.data.rows.map((row) => [
          row.employeeNo, row.employeeName, row.siteName ?? '', row.date,
          row.arrivalTime ? formatTime(row.arrivalTime) : '',
          row.departureTime ? formatTime(row.departureTime) : '',
          row.minutesOnSite ?? '', row.destination ?? '',
        ]),
      }
    case 'office-activity':
      return {
        header: ['Employee ID', 'Employee', 'Date', 'Office departure', 'Office arrival', 'Minutes away', 'Destination'],
        rows: report.data.rows.map((row) => [
          row.employeeNo, row.employeeName, row.date,
          row.officeDeparture ? formatTime(row.officeDeparture) : '',
          row.officeArrival ? formatTime(row.officeArrival) : '',
          row.minutesAway ?? '', row.destination ?? '',
        ]),
      }
    case 'employee-activity':
      return {
        header: ['Date', 'Time', 'Event', 'Location', 'Site', 'Fingerprint verified', 'Captured', 'Review status'],
        rows: report.data.events.map((event) => [
          event.businessDate, formatTime(event.eventTime), event.eventLabel,
          event.location?.name ?? '', event.siteName ?? '',
          event.biometricVerified ? 'Yes' : 'No', event.captureMode, event.reviewStatus,
        ]),
      }
  }
}

function csvCell(value: string | number): string {
  const text = String(value ?? '')
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}
