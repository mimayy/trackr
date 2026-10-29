# End-to-end tests

Playwright is installed and `npm run test:e2e` is wired up, but the specs are not
written yet — this is the honest state of the MVP rather than a placeholder that
passes without testing anything.

## Before writing any

E2E needs the whole stack running with demo data:

```bash
docker compose up --build          # from the repository root
```

Then add `playwright.config.ts` in `frontend/` pointing `baseURL` at
`http://localhost:5173`.

Two things make these tests awkward and are worth knowing up front:

- **Geolocation** must be faked. Playwright can do it:
  `context.grantPermissions(['geolocation'])` plus
  `context.setGeolocation({ latitude: 14.554729, longitude: 121.024445 })` — the
  Manila Office pin, which is inside its geofence.
- **WebAuthn** needs Chrome DevTools Protocol
  (`WebAuthn.enable` / `addVirtualAuthenticator`). It is doable but fiddly; the
  flows below are all reachable without a fingerprint, since an event with no
  fingerprint is recorded and simply marked unverified.

## The flows worth covering

| # | Flow | What it should prove |
|---|---|---|
| 1 | Admin creates an employee | Account starts `PENDING_ACTIVATION` with no device; the activation link can be copied |
| 2 | Activation and device registration | The link works once, the employee lands signed in, the fingerprint step can be skipped |
| 3 | Office employee: Time In → Time Out | Exactly one action is offered at each step |
| 4 | Field employee full day | Time In → Office Departure → Field Arrival → Field Departure → Office Arrival → Time Out, with the destination picker in the middle |
| 5 | Direct-to-field employee | Time In at a warehouse offers Field Arrival, not Office Departure |
| 6 | Offline Time In, then reconnect | The event queues, Sync status shows it, and it uploads by itself with no duplicate |
| 7 | Device replacement | Admin resets the credential; the old one stops working; a new activation link is issued |
| 8 | Field Arrival outside the geofence | Refused with "You're not at … yet", and TRY AGAIN is offered |
| 9 | Supervisor creates an assignment | It appears on the employee's home screen |
| 10 | Admin changes a work schedule | Attendance figures for the affected employee change accordingly |

Flows 3, 4, 5, 8 and 9 are already covered at the service and HTTP level by
`WorkEventServiceIntegrationTest` and `RbacIntegrationTest`; flows 1 and 2 by
`ActivationFlowIntegrationTest`. What E2E would add on top is the browser
behaviour — the offline queue, the camera prompt, and the one-button-at-a-time
rendering.
