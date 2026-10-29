import type { ReactNode } from 'react'
import type {
  AccountStatus,
  AttendanceStatus,
  FlagSeverity,
  SyncStatus,
} from '../lib/types'

/* -------------------------------------------------------------------------- */
/* Feedback                                                                   */
/* -------------------------------------------------------------------------- */

type AlertTone = 'error' | 'warning' | 'success' | 'info'

const ALERT_ICON: Record<AlertTone, string> = {
  error: '⚠',
  warning: '⚠',
  success: '✓',
  info: 'ℹ',
}

/**
 * Errors are always shown as a sentence the reader can act on. The tone maps to
 * colour and icon; the message itself comes from the API, which never sends
 * status codes or stack traces.
 */
export function Alert({
  tone = 'info',
  title,
  children,
  action,
}: {
  tone?: AlertTone
  title?: string
  children?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className={`alert alert-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <span className="alert-icon" aria-hidden="true">
        {ALERT_ICON[tone]}
      </span>
      <div style={{ flex: 1 }}>
        {title && <div className="strong" style={{ marginBottom: 4 }}>{title}</div>}
        {children}
        {action && <div style={{ marginTop: 12 }}>{action}</div>}
      </div>
    </div>
  )
}

export function Spinner({ light = false, label }: { light?: boolean; label?: string }) {
  return (
    <span className="row" style={{ gap: 10 }}>
      <span className={`spinner${light ? ' spinner-light' : ''}`} aria-hidden="true" />
      {label && <span className={light ? '' : 'muted'}>{label}</span>}
      <span className="visually-hidden">Loading</span>
    </span>
  )
}

export function LoadingPanel({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="card center" style={{ padding: 'var(--space-7)' }}>
      <div className="row" style={{ justifyContent: 'center' }}>
        <Spinner label={label} />
      </div>
    </div>
  )
}

export function EmptyState({
  icon = '—',
  title,
  children,
  action,
}: {
  icon?: string
  title: string
  children?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="card">
      <div className="empty-state">
        <div className="empty-icon" aria-hidden="true">{icon}</div>
        <h3 style={{ marginBottom: 8 }}>{title}</h3>
        {children && <p className="muted">{children}</p>}
        {action && <div style={{ marginTop: 16 }}>{action}</div>}
      </div>
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/* Status pills                                                               */
/* -------------------------------------------------------------------------- */

type PillTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

export function Pill({ tone, children }: { tone: PillTone; children: ReactNode }) {
  return <span className={`pill pill-${tone}`}>{children}</span>
}

const ATTENDANCE_TONE: Record<AttendanceStatus, PillTone> = {
  ON_TIME: 'success',
  LATE: 'warning',
  UNDERTIME: 'warning',
  OVERTIME: 'info',
  ABSENT: 'danger',
  INCOMPLETE: 'warning',
  REST_DAY: 'neutral',
  HOLIDAY: 'neutral',
}

const ATTENDANCE_LABEL: Record<AttendanceStatus, string> = {
  ON_TIME: 'On time',
  LATE: 'Late',
  UNDERTIME: 'Undertime',
  OVERTIME: 'Overtime',
  ABSENT: 'Absent',
  INCOMPLETE: 'No time out',
  REST_DAY: 'Rest day',
  HOLIDAY: 'Holiday',
}

export function AttendancePill({ status }: { status: AttendanceStatus }) {
  return <Pill tone={ATTENDANCE_TONE[status]}>{ATTENDANCE_LABEL[status]}</Pill>
}

export function AccountStatusPill({ status }: { status: AccountStatus }) {
  const tone: PillTone =
    status === 'ACTIVE' ? 'success' : status === 'PENDING_ACTIVATION' ? 'warning' : 'danger'
  const label =
    status === 'ACTIVE' ? 'Active' : status === 'PENDING_ACTIVATION' ? 'Pending activation' : 'Disabled'
  return <Pill tone={tone}>{label}</Pill>
}

export function SyncPill({ status }: { status: SyncStatus }) {
  const tone: PillTone =
    status === 'SYNCED' ? 'success'
      : status === 'FLAGGED' ? 'warning'
      : status === 'FAILED' ? 'danger'
      : 'neutral'
  const label =
    status === 'SYNCED' ? 'Synced'
      : status === 'FLAGGED' ? 'Needs review'
      : status === 'FAILED' ? 'Failed'
      : status === 'SYNCING' ? 'Uploading'
      : 'Waiting to sync'
  return <Pill tone={tone}>{label}</Pill>
}

export function SeverityPill({ severity }: { severity: FlagSeverity }) {
  const tone: PillTone = severity === 'CRITICAL' ? 'danger' : severity === 'WARNING' ? 'warning' : 'info'
  const label = severity === 'CRITICAL' ? 'Critical' : severity === 'WARNING' ? 'Needs review' : 'For information'
  return <Pill tone={tone}>{label}</Pill>
}

/* -------------------------------------------------------------------------- */
/* Verification ticks (employee-facing)                                       */
/* -------------------------------------------------------------------------- */

/**
 * How an employee is told what was verified. Deliberately no coordinates, no
 * accuracy figures, no credential ids - just whether it checked out.
 */
export function VerificationLine({ verified, label }: { verified: boolean; label: string }) {
  return (
    <span className="row" style={{ gap: 6, fontSize: 'var(--text-sm)' }}>
      <span
        aria-hidden="true"
        style={{
          color: verified ? 'var(--success-600)' : 'var(--ink-500)',
          fontWeight: 700,
        }}
      >
        {verified ? '✓' : '–'}
      </span>
      <span style={{ color: verified ? 'var(--ink-800)' : 'var(--ink-600)' }}>{label}</span>
    </span>
  )
}

/* -------------------------------------------------------------------------- */
/* Modal                                                                      */
/* -------------------------------------------------------------------------- */

export function Modal({
  title,
  onClose,
  children,
  footer,
  width = 520,
}: {
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  width?: number
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 60,
        background: 'rgba(20, 24, 29, 0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 'var(--space-4)',
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <div
        className="card"
        style={{
          width: '100%',
          maxWidth: width,
          maxHeight: '90dvh',
          overflowY: 'auto',
          boxShadow: 'var(--shadow-lg)',
        }}
      >
        <div className="row-between" style={{ marginBottom: 'var(--space-4)' }}>
          <h2>{title}</h2>
          <button type="button" className="btn btn-ghost" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>
        {children}
        {footer && (
          <div className="row" style={{ justifyContent: 'flex-end', marginTop: 'var(--space-5)' }}>
            {footer}
          </div>
        )}
      </div>
    </div>
  )
}
