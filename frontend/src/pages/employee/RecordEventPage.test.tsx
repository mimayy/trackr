import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RecordEventPage } from './RecordEventPage'
import * as api from '../../lib/api'
import * as geo from '../../lib/geo'
import * as webauthn from '../../lib/webauthn'
import type { WorkflowStatus } from '../../lib/types'

vi.mock('../../components/useOnline', () => ({
  useOnline: () => true,
  useQueue: () => [],
}))

const STATUS: WorkflowStatus = {
  state: 'NOT_STARTED',
  statusLabel: 'NOT STARTED',
  statusDetail: 'Ready to start your workday?',
  allowedActions: [],
  requiresDestination: false,
  fingerprintAvailable: true,
  photoSupported: true,
  recentSiteNames: [],
  todaysEvents: [],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/record/TIME_IN']}>
      <Routes>
        <Route path="/record/:eventType" element={<RecordEventPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('RecordEventPage', () => {
  beforeEach(() => {
    vi.spyOn(geo, 'getCurrentPosition').mockResolvedValue({
      latitude: 14.5547,
      longitude: 121.0244,
      accuracy: 10,
      capturedAt: new Date().toISOString(),
    })
    vi.spyOn(api.api, 'post').mockResolvedValue({
      event: { id: 'ev1', eventLabel: 'Time In' },
      duplicate: false,
    } as never)
  })

  it('asks for the fingerprint even when the button is pressed before the status arrives', async () => {
    // The real failure this guards. The button is the first thing on the screen
    // and it is deliberately large, so on a slow phone connection an employee
    // presses it while /api/me/status is still in flight. The page used to read
    // its status state directly, see null, and quietly record the event with no
    // fingerprint at all - and nothing on screen said so.
    let releaseStatus: (value: WorkflowStatus) => void = () => {}
    const pending = new Promise<WorkflowStatus>((resolve) => {
      releaseStatus = resolve
    })
    vi.spyOn(api.api, 'get').mockReturnValue(pending as never)

    const verify = vi.spyOn(webauthn, 'verifyFingerprint').mockResolvedValue({
      credentialId: 'c1',
      clientDataJSON: 'x',
      authenticatorData: 'y',
      signature: 'z',
    })

    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /time in/i }))

    // Only now does the status land - after the press, as it would on a phone.
    releaseStatus(STATUS)

    await waitFor(() => expect(verify).toHaveBeenCalledTimes(1))
  })

  it('sends the employee to set up a fingerprint when no device is registered', async () => {
    vi.spyOn(api.api, 'get').mockResolvedValue({
      ...STATUS,
      fingerprintAvailable: false,
    } as never)
    const verify = vi.spyOn(webauthn, 'verifyFingerprint')

    renderPage()

    // Every activity needs a fingerprint, so there is nothing to press but setup.
    await screen.findByRole('button', { name: /set up fingerprint/i })
    expect(screen.queryByRole('button', { name: /^time in$/i })).toBeNull()
    expect(verify).not.toHaveBeenCalled()
  })

  it('records nothing when the fingerprint is not confirmed', async () => {
    vi.spyOn(api.api, 'get').mockResolvedValue(STATUS as never)
    vi.spyOn(webauthn, 'verifyFingerprint').mockResolvedValue(null)
    const post = vi.spyOn(api.api, 'post')

    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /time in/i }))

    // Cancelled or not recognised: stop and offer another go, never save unverified.
    await screen.findByText(/fingerprint wasn't confirmed/i)
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('asks which warehouse before a field arrival, and offers the recent ones', async () => {
    vi.spyOn(api.api, 'get').mockResolvedValue({
      ...STATUS,
      recentSiteNames: ['Northgate Cold Storage', 'Bagong Bodega'],
    } as never)
    vi.spyOn(webauthn, 'verifyFingerprint').mockResolvedValue(null)

    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/record/FIELD_ARRIVAL']}>
        <Routes>
          <Route path="/record/:eventType" element={<RecordEventPage />} />
        </Routes>
      </MemoryRouter>,
    )

    // Nothing can be recorded until a site is named.
    const continueButton = await screen.findByRole('button', { name: /continue/i })
    expect(continueButton).toBeDisabled()

    // Tapping a previous name is enough - no typing on a phone.
    await user.click(await screen.findByRole('button', { name: 'Northgate Cold Storage' }))
    expect(await screen.findByRole('button', { name: /continue/i })).toBeEnabled()
  })

  it('lets an employee type a warehouse that is not on any list', async () => {
    // The warehouses change, so the box must accept a name the system has
    // never seen rather than forcing a choice from configured locations.
    vi.spyOn(api.api, 'get').mockResolvedValue({ ...STATUS, recentSiteNames: [] } as never)
    vi.spyOn(webauthn, 'verifyFingerprint').mockResolvedValue(null)

    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/record/FIELD_ARRIVAL']}>
        <Routes>
          <Route path="/record/:eventType" element={<RecordEventPage />} />
        </Routes>
      </MemoryRouter>,
    )

    await user.type(await screen.findByLabelText(/warehouse or site name/i), 'Somewhere New')
    await user.click(screen.getByRole('button', { name: /continue/i }))

    expect(await screen.findByText(/checking in at Somewhere New/i)).toBeInTheDocument()
  })
})
