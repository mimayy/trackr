import { describe, expect, it } from 'vitest'
import { addDays, formatLocalTime, formatMinutes, initials, titleCase } from './format'

describe('formatLocalTime', () => {
  it('renders a wall-clock time the way a person would say it', () => {
    expect(formatLocalTime('08:00')).toBe('8:00 AM')
    expect(formatLocalTime('13:30')).toBe('1:30 PM')
    expect(formatLocalTime('17:00:00')).toBe('5:00 PM')
  })

  it('renders midnight and noon without a zero hour', () => {
    expect(formatLocalTime('00:15')).toBe('12:15 AM')
    expect(formatLocalTime('12:00')).toBe('12:00 PM')
  })

  it('falls back to the raw value rather than showing NaN', () => {
    expect(formatLocalTime('not-a-time')).toBe('not-a-time')
    expect(formatLocalTime(undefined)).toBe('--')
  })

  it('never renders NaN, even when handed something already formatted', () => {
    // This is a real bug that reached the screen as "9:NaN AM": the API used to
    // send a pre-formatted "9:30 AM", which was then formatted a second time.
    // The API now sends the raw time, and this guards the display either way.
    for (const input of ['9:30 AM', '12:05 pm', '08:00:00.000', 'noon', '::']) {
      expect(formatLocalTime(input)).not.toContain('NaN')
    }
  })
})

describe('formatMinutes', () => {
  it('uses minutes below an hour and hours above it', () => {
    expect(formatMinutes(20)).toBe('20 min')
    expect(formatMinutes(60)).toBe('1h')
    expect(formatMinutes(100)).toBe('1h 40m')
  })

  it('shows nothing for zero or missing values', () => {
    expect(formatMinutes(0)).toBe('--')
    expect(formatMinutes(undefined)).toBe('--')
  })
})

describe('addDays', () => {
  it('moves a date forward and back without drifting across a timezone', () => {
    expect(addDays('2026-09-05', 1)).toBe('2026-09-06')
    expect(addDays('2026-09-05', -5)).toBe('2026-08-31')
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31')
  })

  it('handles a leap day', () => {
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29')
  })
})

describe('small helpers', () => {
  it('turns an enum constant into readable words', () => {
    expect(titleCase('FIELD_ARRIVAL')).toBe('Field Arrival')
    expect(titleCase(undefined)).toBe('')
  })

  it('builds initials from a name', () => {
    expect(initials('Juan Dela Cruz')).toBe('JD')
  })
})
