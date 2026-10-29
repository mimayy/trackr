/**
 * Formatting helpers.
 *
 * Everything the employee reads is written the way a person would say it -
 * "8:00 AM", "Today", "20 minutes late" - never an ISO string or a raw number.
 */

const TIME_ZONE = 'Asia/Manila'

const timeFormat = new Intl.DateTimeFormat('en-PH', {
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
  timeZone: TIME_ZONE,
})

const dateFormat = new Intl.DateTimeFormat('en-PH', {
  month: 'long',
  day: 'numeric',
  year: 'numeric',
  timeZone: TIME_ZONE,
})

const shortDateFormat = new Intl.DateTimeFormat('en-PH', {
  month: 'short',
  day: 'numeric',
  timeZone: TIME_ZONE,
})

const weekdayFormat = new Intl.DateTimeFormat('en-PH', {
  weekday: 'short',
  timeZone: TIME_ZONE,
})

const dateTimeFormat = new Intl.DateTimeFormat('en-PH', {
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
  timeZone: TIME_ZONE,
})

export function formatTime(iso?: string | null): string {
  if (!iso) return '--'
  return timeFormat.format(new Date(iso))
}

export function formatDate(iso?: string | null): string {
  if (!iso) return '--'
  return dateFormat.format(toDate(iso))
}

export function formatShortDate(iso?: string | null): string {
  if (!iso) return '--'
  return shortDateFormat.format(toDate(iso))
}

export function formatWeekday(iso?: string | null): string {
  if (!iso) return '--'
  return weekdayFormat.format(toDate(iso))
}

export function formatDateTime(iso?: string | null): string {
  if (!iso) return '--'
  return dateTimeFormat.format(new Date(iso))
}

/** "Today", "Yesterday", or the date. */
export function formatRelativeDay(iso?: string | null): string {
  if (!iso) return '--'

  const date = toDate(iso)
  const today = todayInZone()
  const target = isoDateInZone(date)

  if (target === today) return 'Today'
  if (target === addDays(today, -1)) return 'Yesterday'
  if (target === addDays(today, 1)) return 'Tomorrow'
  return formatDate(iso)
}

/** A "hh:mm" wall-clock string from the API, shown as "8:00 AM". */
export function formatLocalTime(value?: string | null): string {
  if (!value) return '--'
  const [hourText, minuteText] = value.split(':')
  const hour = Number(hourText)
  const minute = Number(minuteText ?? '0')

  // Anything unparseable comes back untouched. Showing the raw value is
  // recoverable; rendering "9:NaN AM" to an employee is not.
  if (Number.isNaN(hour) || Number.isNaN(minute)) return value

  const suffix = hour >= 12 ? 'PM' : 'AM'
  const display = hour % 12 === 0 ? 12 : hour % 12
  return `${display}:${String(minute).padStart(2, '0')} ${suffix}`
}

export function formatMinutes(minutes?: number | null): string {
  if (minutes === undefined || minutes === null || minutes <= 0) return '--'
  if (minutes < 60) return `${minutes} min`

  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`
}

export function todayInZone(): string {
  return isoDateInZone(new Date())
}

export function isoDateInZone(date: Date): string {
  // en-CA gives ISO-shaped dates, which is exactly what the API expects.
  return new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: TIME_ZONE,
  }).format(date)
}

export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

/** Accepts both plain dates ("2026-09-05") and full timestamps. */
function toDate(iso: string): Date {
  return iso.length === 10 ? new Date(`${iso}T12:00:00+08:00`) : new Date(iso)
}

export function titleCase(value?: string | null): string {
  if (!value) return ''
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('')
}
