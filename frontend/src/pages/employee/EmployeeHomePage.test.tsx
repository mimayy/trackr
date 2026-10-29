import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { EmployeeHomePage } from './EmployeeHomePage'
import * as api from '../../lib/api'
import type { SessionUser, WorkflowStatus } from '../../lib/types'

const USER: SessionUser = {
  userId: 'u1',
  email: 'juan.delacruz@uni-ace.example',
  displayName: 'Juan Dela Cruz',
  firstName: 'Juan',
  role: 'EMPLOYEE',
  accountStatus: 'ACTIVE',
  employeeId: 'e1',
  employeeNo: 'EMP-001',
  branch: 'MANILA',
  workCategory: 'FIELD_BASED',
  deviceRegistered: true,
}

vi.mock('../../lib/auth', () => ({
  useAuth: () => ({ user: USER, loading: false }),
}))

vi.mock('../../components/useOnline', () => ({
  useOnline: () => true,
  useQueue: () => [],
}))

function statusFor(overrides: Partial<WorkflowStatus>): WorkflowStatus {
  return {
    state: 'NOT_STARTED',
    statusLabel: 'NOT STARTED',
    statusDetail: 'Ready to start your workday?',
    allowedActions: [],
    requiresDestination: false,
    fingerprintAvailable: true,
    photoSupported: true,
    recentSiteNames: [],
    todaysEvents: [],
    ...overrides,
  }
}

function renderPage() {
  return render(
    <MemoryRouter>
      <EmployeeHomePage />
    </MemoryRouter>,
  )
}

describe('EmployeeHomePage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('offers exactly one action before the workday has started', async () => {
    vi.spyOn(api.api, 'get').mockResolvedValue(
      statusFor({
        allowedActions: [
          {
            eventType: 'TIME_IN',
            label: 'TIME IN',
            helper: 'Start your workday.',
            requiresLocation: true, requiresSiteName: false,
          },
        ],
        nextAction: {
          eventType: 'TIME_IN',
          label: 'TIME IN',
          helper: 'Start your workday.',
          requiresLocation: true, requiresSiteName: false,
        },
      }),
    )

    renderPage()

    await waitFor(() => expect(screen.getAllByText('TIME IN').length).toBeGreaterThan(0))

    // The employee is never shown a menu of six event types: exactly one
    // primary button, and none of the other events.
    expect(screen.getByRole('button', { name: 'TIME IN' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /TIME OUT/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /I'M HERE/i })).not.toBeInTheDocument()
  })

  it('offers the departure action once timed in at the office', async () => {
    vi.spyOn(api.api, 'get').mockResolvedValue(
      statusFor({
        state: 'AT_OFFICE',
        statusLabel: 'WORKING',
        statusDetail: 'You are timed in.',
        workdayStartedAt: '2026-09-05T00:00:00Z',
        currentLocationName: 'Manila Office',
        allowedActions: [
          {
            eventType: 'OFFICE_DEPARTURE',
            label: 'LEAVE THE OFFICE',
            helper: 'Record that you are leaving the office.',
            requiresLocation: true, requiresSiteName: false,
          },
          {
            eventType: 'TIME_OUT',
            label: 'TIME OUT',
            helper: 'End your workday.',
            requiresLocation: true, requiresSiteName: false,
          },
        ],
        nextAction: {
          eventType: 'OFFICE_DEPARTURE',
          label: 'LEAVE THE OFFICE',
          helper: 'Record that you are leaving the office.',
          requiresLocation: true, requiresSiteName: false,
        },
        recentSiteNames: ['Warehouse 2'],
      }),
    )

    renderPage()

    // The status pill renders a bullet before the label, so match on the word.
    await waitFor(() => expect(screen.getByText(/WORKING/)).toBeInTheDocument())
    // Leaving the office no longer depends on anything being booked for today.
    expect(screen.getByRole('button', { name: 'LEAVE THE OFFICE' })).toBeInTheDocument()
  })

  it('says the day is done rather than offering an action that would be refused', async () => {
    vi.spyOn(api.api, 'get').mockResolvedValue(
      statusFor({
        state: 'DAY_ENDED',
        statusLabel: 'FINISHED FOR TODAY',
        statusDetail: 'Your workday is complete. See you tomorrow.',
        allowedActions: [],
      }),
    )

    renderPage()

    await waitFor(() => expect(screen.getByText('Nothing left to do today.')).toBeInTheDocument())

    // Nothing is offered that the server would refuse.
    expect(screen.queryByRole('button', { name: /TIME/i })).not.toBeInTheDocument()
  })
})
