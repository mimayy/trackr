# Trackr@Uni-Ace

**Employee attendance and work activity tracker for Uni-Ace Adjusters Inc.**

An event-based attendance system. It records *where* and *when* work actually
happened — a Time In, an arrival at a warehouse, a departure for the office — and
verifies each one with the employee's location and their phone's fingerprint. It
does **not** follow anybody around during the day.

```
Employee + Work Event + Timestamp + GPS + Fingerprint + Optional photo
                            =
                  Verified work activity record
```

---

## Contents

1. [What it does](#1-what-it-does)
2. [Run it locally](#2-run-it-locally)
3. [Demo accounts](#3-demo-accounts)
4. [Architecture](#4-architecture)
5. [How the important parts work](#5-how-the-important-parts-work)
6. [API reference](#6-api-reference)
7. [Configuration](#7-configuration)
8. [Tests](#8-tests)
9. [Deploy it for free](#9-deploy-it-for-free)
10. [Privacy and security](#10-privacy-and-security)
11. [Known limits](#11-known-limits)

---

## 1. What it does

### Three roles

| | Employee | Supervisor | Administrator |
|---|---|---|---|
| Record work events | yes | — | — |
| See own attendance and map | yes | — | — |
| See a team's attendance, map and exceptions | — | yes (their team only) | yes (everyone) |
| Create employees and supervisors | — | — | yes |
| Configure locations, geofences, schedules, holidays | — | — | yes |
| Revoke and reset device credentials | — | — | yes |
| Read the audit log | — | — | yes |

Scope is enforced on the server, not by hiding buttons. A supervisor who edits a
URL to reach another team's employee gets the same refusal as if that employee did
not exist. There are tests for exactly this.

### Six work events

```
TIME_IN → OFFICE_DEPARTURE → FIELD_ARRIVAL → FIELD_DEPARTURE → OFFICE_ARRIVAL → TIME_OUT
```

Three supported shapes of a workday:

| Day | Sequence |
|---|---|
| Office day | `TIME_IN → TIME_OUT` |
| Office → field → office | `TIME_IN → OFFICE_DEPARTURE → FIELD_ARRIVAL → FIELD_DEPARTURE → OFFICE_ARRIVAL → TIME_OUT` |
| Straight to the field | `TIME_IN → FIELD_ARRIVAL → FIELD_DEPARTURE → TIME_OUT` |

Several field visits in one day are supported: a Field Departure headed for
another field location returns the employee to the "on the way" state.

Field Arrival is the only event that asks for anything extra: **which warehouse
or site** the employee is at. It is typed, not chosen from a list, because the
warehouses are not fixed — with the names that employee has used before offered
as taps so it only has to be typed once.

The **server** decides which event is valid next. The phone shows one big button
because the server said only one thing is possible — never the other way round.

### The employee experience

Many Uni-Ace employees are older and not comfortable with technology, so the phone
interface is built to a different standard from the console:

- one screen, one job, one obvious next action;
- 56px primary buttons, 17px body text, high contrast;
- icons always paired with words;
- almost no typing — tap, confirm, take a photo, use a fingerprint;
- plain sentences instead of errors: *"We can't get your location. Please turn on
  Location Services and try again."* rather than `GEOLOCATION_ERROR`;
- an obvious online/offline indicator on every screen.

---

## 2. Run it locally

### Windows, no Docker needed

**Install first:** [Node 20+](https://nodejs.org) (take the LTS build). That is
the only prerequisite. Everything else is fetched for you on the first run.

```powershell
.\run-local.ps1
```

That finds a JDK and a PostgreSQL, starts them, creates and migrates the
database, seeds demo data, and launches the API and the front end. It prints the
address for this PC and the one to use from a phone.

```powershell
.\run-local.ps1 -SkipBuild     # reuse the existing jar (much faster)
.\run-local.ps1 -Reseed        # wipe the database and regenerate demo data
.\stop-local.ps1               # stop everything
```

The first run downloads what it needs into a portable toolchain under
`%TEMP%\trackr-toolchain` (about 1.5 GB, once):

| Tool | Why | Size |
|---|---|---|
| Temurin JDK 21 | runs the API | ~330 MB |
| PostgreSQL 16 (portable) | the database | ~970 MB |
| cloudflared | only with `-Tunnel` | ~53 MB |

A JDK or PostgreSQL already on your `PATH` is used instead of downloading one.
Nothing is installed system-wide, no service is registered, and nothing outside
that folder and this repository is touched.

> **The toolchain lives in `%TEMP%`, and so does the database.** Windows Disk
> Cleanup and Storage Sense delete old files there, which would take your data
> with them. That is fine for a demo you reseed anyway; if you start entering
> real records, move it somewhere permanent first.

### Or with Docker

```bash
cp .env.example .env
docker compose up --build
```

Then open **http://localhost:5173**.

### Or by hand

Needs **JDK 21**, **Node 20+**, **PostgreSQL 14+**.

```bash
createdb trackr && createuser trackr --pwprompt      # password: trackr

cd backend  && ./mvnw spring-boot:run                # API on :8080
cd frontend && npm install && npm run dev            # app on :5173
```

`./mvnw` downloads Maven on first use, so a JDK is all you need. Flyway builds
the schema and seeds the demo data the first time the API starts.

---

### Looking at the database

The connection details depend on which PostgreSQL the run script used. It says
which one on startup.

| | Portable (downloaded) | Already on your PATH |
|---|---|---|
| Host | `127.0.0.1` | `127.0.0.1` |
| Port | **55432** | 5432 |
| Database | `trackr` | `trackr` |
| User | `trackr` | `trackr` |
| Password | `trackrpw` | `trackr` |

The odd port on the portable build is deliberate: it stays out of the way of a
PostgreSQL you may already have.

**With a GUI** - [pgAdmin](https://www.pgadmin.org/download/) or
[DBeaver](https://dbeaver.io/download/) are both free. Create a connection with
the values above and browse the tables.

**From the command line**, using the psql that came with the portable build:

```powershell
$env:PGPASSWORD = 'trackrpw'
& "$env:TEMP\trackr-toolchain\pgsql\bin\psql.exe" -h 127.0.0.1 -p 55432 -U trackr -d trackr
```

Then `\dt` lists the 18 tables, `\d work_events` describes one, and `\q` quits.
The tables worth starting with:

| Table | Holds |
|---|---|
| `employees` | people, branch, field- or office-based |
| `work_events` | every recorded event: time, coordinates, and the site name typed at arrival |
| `work_event_flags` | why an event was questioned |
| `locations` | offices and warehouses, with geofence radius |
| `device_credentials` | registered fingerprint devices (public keys only) |
| `audit_logs` | who changed what |

A query to see today, the way a supervisor's screen builds it:

```sql
SELECT e.employee_no, e.first_name, w.event_type, w.event_time,
       l.name AS location, w.site_name, w.biometric_verified
FROM work_events w
JOIN employees e ON e.id = w.employee_id
LEFT JOIN locations l ON l.id = w.location_id
WHERE w.business_date = CURRENT_DATE
ORDER BY w.event_time;
```

Note there are no fingerprints in there to look at. `device_credentials` stores
a public key and a counter; the fingerprint itself never leaves the phone. See
[Fingerprint - and an honest caveat](#fingerprint--and-an-honest-caveat).


### Seeing it on your phone

The employee screens are built for a phone, so they are worth looking at on one.

**On the same Wi-Fi** — the run script prints an address like
`http://192.168.1.42:5173`. Open that on the phone. Every screen works.

But **GPS and fingerprint will not**, and that is the browser's rule, not a bug
in Trackr: both are blocked on any origin that is not HTTPS or localhost. Tapping
TIME IN will stop at "We can't get your location".

**To use GPS and fingerprint on a phone**, serve it over HTTPS:

```powershell
.\run-local.ps1 -Tunnel
```

That opens an HTTPS tunnel and prints the address to open on the phone -
something like `https://spray-fantasy-mai-millions.trycloudflare.com`. It
downloads `cloudflared` into the portable toolchain the first time (about
50 MB) and needs nothing installed.

The address is only known once the tunnel has connected, and it decides both the
allowed browser origins and the WebAuthn relying-party domain, so the script
restarts the API for it before telling you the URL. Skipping that restart is
exactly what produces a `403 Invalid CORS request` on sign-in.

Only one tunnel is needed. In development the app calls `/api` on its own origin
and Vite proxies it to the backend, stripping the `Origin` header on the way so
the API sees a genuine same-origin request. The phone only ever talks to one
host, and the API's origin allowlist stays strict for everything else.

To use a tunnel you are running yourself, pass its address instead:

```powershell
.\run-local.ps1 -PublicUrl https://your-own-tunnel.example -SkipBuild
```

> A WebAuthn credential is bound to one domain. A fingerprint registered on
> `localhost` will not work through a tunnel, and vice versa - register again on
> whichever address you are demonstrating from. `-Tunnel` gets a new random
> address each run, so expect to set the fingerprint up again after a restart;
> a tunnel with a fixed name, passed via `-PublicUrl`, avoids that.

**On the PC**, open devtools and switch to a phone viewport. Chrome can also fake
a position: devtools → ⋮ → More tools → Sensors → Location, set to
14.554729, 121.024445 (the Manila Office pin) and the full flow works.

## 3. Demo accounts

Seeded when `SEED_DEMO_DATA=true` (the default locally). Every demo account uses
the password from `SEED_DEMO_PASSWORD`, which defaults to `Trackr123!`.

| Role | Email | Notes |
|---|---|---|
| Administrator | `admin@uni-ace.example` | Efren P. Casingal. Password is `BOOTSTRAP_ADMIN_PASSWORD` (`ChangeMe123!`), not the demo password. |
| Supervisor | `maria.santos@uni-ace.example` | Manila team |
| Supervisor | `ramon.villanueva@uni-ace.example` | Cebu team |
| Employee | `juan.delacruz@uni-ace.example` | Field-based, Manila |
| Employee | `ana.reyes@uni-ace.example` | Office-based, Manila |
| Employee | `carlo.cruz@uni-ace.example` | Field-based, Cebu |
| Employee | `maria.garcia@uni-ace.example` | Office-based, Cebu |
| Employee | `grace.fernandez@uni-ace.example` | **Pending activation** — cannot sign in; use it to try the activation flow |
| Employee | `alfredo.ramos@uni-ace.example` | **Disabled** — shows a disabled account |

Eleven employees in total, plus three weeks of generated attendance including late
arrivals, overtime, field trips, a few offline events and some flagged exceptions —
so the dashboard, reports and exception queue all have real data.

**Juan and Ana start each day unstarted**, so there is always a Time In to press
whatever hour you open the demo. Juan is field-based, so he can walk the whole
office-to-field-and-back sequence; nothing needs to be booked first. Everyone
else has a finished day, which is what the dashboard counts.

**No device is registered for anyone.** That is deliberate — a seeded credential
with no key behind it would show an employee as set up while making every
activity they record silently unverified. Sign in as an employee, open
**Profile**, and tap **SET UP FINGERPRINT**; it takes seconds and the admin
Security panel then shows real data.

**Every dashboard number is counted from those records.** Nothing is hard-coded; an
empty database honestly shows zeros.

### A five-minute tour

1. Sign in as **Juan** in a phone-sized window, then tap **TIME IN**.
2. Watch the home screen offer exactly one next action as the day progresses.
3. Go offline in devtools and record another event — it queues, and the **Sync
   status** page shows it waiting.
4. Go back online — it uploads by itself.
5. Sign in as **Maria** for the dashboard, the map of recorded locations and the
   exceptions queue.
6. Sign in as the **administrator**, create an employee, copy the activation link
   and open it in a private window to activate the account.

---

## 4. Architecture

```
                              INTERNET
                                 │
                  ┌──────────────┴──────────────┐
                  │                             │
            EMPLOYEE PHONE            SUPERVISOR / ADMIN LAPTOP
                  │                             │
                  └──────────────┬──────────────┘
                                 ▼
                    React + TypeScript PWA          (Vercel, free)
                                 │  REST + JWT
                                 ▼
                    Spring Boot 3.3 / Java 21       (Render, free)
                                 │
                  ┌──────────────┴──────────────┐
                  ▼                             ▼
           PostgreSQL 16                  Object storage
           (Neon, free)                (Supabase, free) — photos only
```

### Layout

```
trackr/
├── backend/                        Spring Boot API
│   └── src/main/java/com/uniace/trackr/
│       ├── config/                 security, CORS, storage, properties
│       ├── domain/                 JPA entities + enums
│       ├── repo/                   Spring Data repositories
│       ├── security/               JWT, rate limiting, the authenticated caller
│       ├── service/                business logic
│       │   ├── attendance/         AttendanceCalculator — hours, lateness, overtime
│       │   └── workflow/           WorkflowEngine — which event may come next
│       ├── seed/                   bootstrap admin + demo data
│       ├── storage/                photo storage (local | Supabase)
│       └── web/                    REST controllers + error handling
│   └── src/main/resources/db/migration/   Flyway
├── frontend/                       React PWA
│   └── src/
│       ├── components/             shells, map, shared UI
│       ├── lib/                    api client, auth, offline queue, geo, WebAuthn
│       ├── pages/employee/         the phone interface
│       ├── pages/console/          shared supervisor + admin screens
│       └── pages/admin/            administrator-only screens
├── docker-compose.yml
└── .env.example
```

### Data model

```
UserAccount 1─1 Employee ─* WorkEvent
     │            │  │           │
     │            │  │           ├─0..1 WorkLocation
     │            │  │           ├─0..1 PhotoEvidence
     │            │  │           ├──*   WorkEventFlag      ← why it needs review
     │            │  │           └──*   EventAdjustment    ← corrections, layered on top
     │            │  └──* DeviceCredential  (one ACTIVE at a time)
     │            ├──1 WorkSchedule
     │            └──1 Supervisor
     └──* AuditLog
```

Plus `Holiday`, `SystemSetting`, `ActivationToken`, `RefreshToken` and
`WebAuthnChallenge`.

---

## 5. How the important parts work

### Attendance

```
Hours worked = Time Out − Time In − the unpaid break actually spanned
```

The one-hour lunch is **not** hard-coded; it comes from the employee's work
schedule, which an administrator can change. Only the part of the break inside the
worked window is deducted, so someone who times out at 11:00 does not lose a lunch
they never took.

| Time in | Time out | Worked | Status |
|---|---|---|---|
| 8:00 | 17:00 | 8h | On time |
| 8:30 | 17:00 | 7h 30m | Late 30m, Undertime 30m |
| 8:00 | 18:00 | 9h | On time, Overtime 1h |
| 8:30 | 18:00 | 8h 30m | Late 30m, Overtime 30m |

*Attendance lateness* is Time In against the scheduled start. Travel between
work locations during the day counts as work; it is never subtracted.

### Location

GPS is read **only** at the moment an employee taps an action. There is no
background tracking and deliberately no "watch position" code anywhere in the front
end.

**Nothing is refused on the strength of a position.** Uni-Ace's warehouses are
not a fixed list and change often, so an employee standing at one nobody has
added to the system yet must still be able to record that they arrived. A Field
Arrival instead asks *which warehouse* — a text box, with the names that
employee has used before offered as taps so the second visit needs no typing.

Coordinates are still recorded, and the nearest known location is still matched
and shown, so a supervisor can see where someone was. It is evidence, not a gate.

Everything the UI says is *"Latest Recorded Location"* — never "current" or "live",
because the system cannot honestly claim that.

### Blocked vs flagged

| | Meaning | Examples |
|---|---|---|
| **Blocked** | Cannot be safely accepted; refused with a sentence the employee can act on | account not active, impossible sequence, Field Arrival with no site name, GPS too inaccurate |
| **Flagged** | Stored, and marked for a human to review | poor-but-usable GPS, device clock discrepancy, missing fingerprint, sign-counter anomaly |

**Nothing questionable is ever silently discarded.** An event that syncs from the
offline queue is treated more leniently than one recorded live: the employee has
already done the work, so problems that would be refused online become flags
instead of losing the record.

### Offline

```
Tap → validate locally → fingerprint if available → GPS → unique event id
    → store in IndexedDB → show "Waiting to sync"
    → connection returns → upload → server validates → ACCEPTED / FLAGGED / FAILED
```

The event id is generated **on the device**. That is what makes the upload
idempotent: retrying a batch after a dropped connection cannot create a second Time
In. Each queued event is stored in its own transaction, so one bad record cannot
take down the rest of the batch.

Four timestamps are kept on every event so an auditor can reconstruct what
happened: when the employee says it happened, what the phone's clock said, when the
device uploaded it, and when the server processed it.

### Fingerprint — and an honest caveat

Trackr uses **WebAuthn platform authenticators**. The employee's fingerprint never
leaves their phone: it unlocks a private key, and Trackr stores only the matching
public key. No fingerprint image or template is stored anywhere, and there is no
custom biometric database.

**Every online activity needs a verified fingerprint.** The server refuses one
without a signed assertion, or with one that does not verify. An employee with no
fingerprint set up is sent to Profile to set one up before they can record. A
cancelled or unrecognised prompt (the app gives up after 25 seconds) records
nothing and offers TRY AGAIN.

**What this cannot do:** verify a fingerprint while offline. Verification needs a
challenge issued by the server and a signature checked by the server. So an
activity captured with no connection is queued with its GPS position, stored with
`biometricVerified = false` when it syncs, and flagged `NO_FINGERPRINT` for a
supervisor to review. The phone says so before the employee records it.

The whole path is verified end to end against a Chrome virtual authenticator:
registration stores the public key, and a full six-event field workday records
with `fingerprintVerified = true` on every event — meaning the server checked a
real signature each time.

Nor does Trackr claim offline mode makes cheating impossible — a browser belongs to
the person using it. What it does is make manipulation *detectable*: unique event
ids, four timestamps, integrity hashes and idempotent sync, all surfaced in the
exceptions queue.

### Corrections

An accepted work event is never edited. A correction is recorded as a separate
`EventAdjustment` — original value, corrected value, reason, requester, approver —
and layered on top. The original stays exactly as captured, forever.

---

## 6. API reference

Endpoints are grouped by role, and the grouping *is* the outer access control
(`SecurityConfig`). Individual endpoints add scope checks on top.

### Public

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/login` | Sign in |
| `POST` | `/api/auth/refresh` | Rotate the session |
| `POST` | `/api/auth/logout` | Sign out |
| `GET` | `/api/auth/session` | Who am I (authenticated) |
| `GET` | `/api/activation/details?token=` | Show the employee their details before activating |
| `POST` | `/api/activation/complete` | Set a password, activate, sign in |
| `GET` | `/api/config/public` | Non-secret front-end configuration |

### Employee — `/api/me/**`

No endpoint here takes an employee id: the employee is always the token holder.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/me/status` | Work status, recent site names, the one next action |
| `POST` | `/api/me/work-events` | Record an event |
| `POST` | `/api/me/sync/work-events` | Upload the offline queue (idempotent) |
| `POST` | `/api/me/sync/status` | Ask which queued events the server already has |
| `GET` | `/api/me/work-events` | Own timeline |
| `GET` | `/api/me/work-events/{id}` | One activity |
| `GET` | `/api/me/attendance` | Own attendance history |
| `POST` | `/api/me/photos` | Upload photo evidence |
| `GET` | `/api/me/photos/{id}` | Own photo |
| `POST` | `/api/me/webauthn/register/start` and `/finish` | Register a fingerprint |
| `POST` | `/api/me/webauthn/verify/start` | Challenge for one event |
| `GET` | `/api/me/device` | Registered device status |

### Supervisor — `/api/supervisor/**` (administrators too, company-wide)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/supervisor/dashboard` | Metrics for a date |
| `GET` | `/api/supervisor/employees` | Employees in scope |
| `GET` | `/api/supervisor/employees/{id}` | One employee |
| `GET` | `/api/supervisor/employees/{id}/work-events` | Their timeline |
| `GET` | `/api/supervisor/monitoring/work-events` | Map data, filterable |
| `GET` | `/api/supervisor/monitoring/latest-locations` | Latest recorded location per person |
| `GET` | `/api/supervisor/exceptions` | Review queue |
| `POST` | `/api/supervisor/work-events/{id}/review` | Mark reviewed |
| `GET` | `/api/supervisor/reports/attendance` | Attendance report |
| `GET` | `/api/supervisor/reports/late` | Attendance lateness |
| `GET` | `/api/supervisor/reports/field-activity` | Site visits, and how long each lasted |
| `GET` | `/api/supervisor/reports/office-activity` | Office departures and returns |
| `GET` | `/api/supervisor/reports/employee-activity` | One person's full timeline |
| `GET` | `/api/supervisor/locations` | Locations, for filters and forms |

### Administrator — `/api/admin/**`

| Method | Path | Purpose |
|---|---|---|
| `GET` `POST` | `/api/admin/employees` | List / create |
| `GET` `PATCH` | `/api/admin/employees/{id}` | View / edit |
| `POST` | `/api/admin/employees/{id}/disable` and `/enable` | Account status |
| `POST` | `/api/admin/employees/{id}/invitation` | One-time activation link |
| `POST` | `/api/admin/employees/{id}/device/revoke` and `/reset` | Device security |
| `GET` `POST` | `/api/admin/supervisors` | List / create |
| `PATCH` | `/api/admin/supervisors/{id}` | Edit |
| `POST` | `/api/admin/supervisors/{id}/employees` | Set their scope |
| `GET` `POST` | `/api/admin/locations` | List / create |
| `PATCH` | `/api/admin/locations/{id}` | Edit, including the geofence |
| `GET` `POST` | `/api/admin/schedules` and `/holidays` | Working hours and non-working dates |
| `GET` `POST` | `/api/admin/work-events/{id}/adjustments` | Corrections |
| `POST` | `/api/admin/adjustments/{id}/decision` | Approve or reject |
| `GET` | `/api/admin/audit-logs` | Audit trail |
| `GET` `PATCH` | `/api/admin/settings` | Runtime behaviour |

---

## 7. Configuration

Everything is read from the environment; nothing is hard-coded and no `.env` is
committed. See **`.env.example`** for the full annotated list.

The ones that matter most:

| Variable | Purpose |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL |
| `JWT_SECRET` | 32+ characters. `openssl rand -base64 48` |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | The first administrator, created only if none exists |
| `APP_BASE_URL` | Where activation links point |
| `FRONTEND_URL` | Allowed CORS origins (comma-separated) |
| `WEBAUTHN_RP_ID` | **Domain only** — no scheme, no port |
| `WEBAUTHN_ORIGINS` | Full origins allowed to use credentials |
| `STORAGE_PROVIDER` | `local` (development) or `supabase` (production) |
| `SEED_DEMO_DATA` | `false` for a real deployment |
| `VITE_API_BASE_URL` | Baked into the front-end bundle at build time |

Administrators can also change GPS thresholds, the lateness
grace period, the overtime threshold and clock-skew tolerance at runtime from
**Settings** — no redeploy.

---

## 8. Tests

```bash
cd backend  && ./mvnw test     # 83 tests
cd frontend && npm test        # 17 tests
```

**Backend (83)**

- `AttendanceCalculatorTest` — every worked example from the brief, plus partial
  break overlap, paid breaks, absences, rest days, branch holidays, incomplete
  days, reversed timestamps and the separation of the two kinds of lateness.
- `WorkflowEngineTest` — all three workday shapes, multiple field cycles,
  out-of-order offline batches, and every invalid sequence.
- `GeoUtilTest` — distance, geofence boundaries, accuracy tolerance.
- `WorkEventServiceIntegrationTest` — recording against a real database: sequence
  enforcement, site-name refusal, GPS accuracy refusal, disabled accounts, **sync
  idempotency**, out-of-order batches, offline leniency, clock skew, and one bad
  event not poisoning a batch.
- `RbacIntegrationTest` — authorisation at the HTTP boundary: anonymous refusal, an
  employee refused the consoles, a supervisor scoped to their own team and refused
  another team by id, an administrator seeing everything.
- `ActivationFlowIntegrationTest` — create, invite, activate, sign in; single-use
  links; invalidating an earlier invitation; duplicate employee IDs.

**Frontend (17)** — formatting and geo helpers, plus `EmployeeHomePage` asserting
that exactly one action is offered per workflow state.

**End-to-end** — Playwright is configured (`npm run test:e2e`); specs are not
included in this MVP. The flows worth covering are listed in
`frontend/e2e/README.md`.

---

## 9. Deploy it for free

Total cost: **$0**. No custom domain needed — provider URLs are fine.

### 9.1 Database — Neon

1. Create a project at [neon.tech](https://neon.tech) (free tier).
2. Copy the connection string and convert it to JDBC form:

```
DATABASE_URL=jdbc:postgresql://ep-xxx.ap-southeast-1.aws.neon.tech/trackr?sslmode=require
DATABASE_USERNAME=your_user
DATABASE_PASSWORD=your_password
```

> Use Neon rather than Render's free Postgres — that one expires after 30 days.

### 9.2 Photo storage — Supabase

1. Create a project at [supabase.com](https://supabase.com).
2. **Storage → New bucket**, name it `trackr-photos`, and **keep it private**.
3. Copy the project URL and the *service role* key.

```
STORAGE_PROVIDER=supabase
STORAGE_URL=https://your-ref.supabase.co
STORAGE_BUCKET=trackr-photos
STORAGE_KEY=your-service-role-key
```

The service role key is server-side only. Photos are streamed through the API so
Trackr can check who is asking before handing one over — a public bucket would make
every photo readable by anyone with the URL.

### 9.3 Backend — Render

1. **New → Web Service**, connect the repo.
2. Root directory `backend`, environment **Docker**, instance type **Free**.
3. Add the environment variables:

```
DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD
JWT_SECRET                 openssl rand -base64 48
BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD
APP_BASE_URL=https://trackr-uni-ace.vercel.app
FRONTEND_URL=https://trackr-uni-ace.vercel.app
BACKEND_URL=https://trackr-uni-ace-api.onrender.com
WEBAUTHN_RP_ID=trackr-uni-ace.vercel.app
WEBAUTHN_ORIGINS=https://trackr-uni-ace.vercel.app
STORAGE_PROVIDER=supabase
STORAGE_URL, STORAGE_BUCKET, STORAGE_KEY
SEED_DEMO_DATA=false
```

Free Render services sleep after 15 minutes idle and take about 50 seconds to wake.
The app tolerates this: the front end shows a loading state, JWTs are stateless so
no session is lost, and any event recorded meanwhile is queued offline and uploaded
when the backend answers. For a demo, open the API URL a minute beforehand.

### 9.4 Frontend — Vercel

1. **Add New → Project**, import the repo.
2. Root directory `frontend`. Vercel detects Vite automatically.
3. Environment variable:

```
VITE_API_BASE_URL=https://trackr-uni-ace-api.onrender.com
```

4. Deploy.

### 9.5 Wire up CORS and WebAuthn

Once Vercel gives you the real URL, go back to Render and set `APP_BASE_URL`,
`FRONTEND_URL`, `WEBAUTHN_RP_ID` (**domain only**) and `WEBAUTHN_ORIGINS` to match,
then redeploy. Getting `WEBAUTHN_RP_ID` wrong is the single most common cause of
"fingerprint setup fails in production".

### 9.6 Check it

```bash
curl https://trackr-uni-ace-api.onrender.com/actuator/health     # {"status":"UP"}
```

Then open the Vercel URL, sign in as the bootstrap administrator, **change that
password immediately**, and create your first real employee.

---

## 10. Privacy and security

**Data minimisation.** Location is captured only at configured work events. There is
no background tracking, no continuous location, and no code path that watches
position over time.

**Biometrics.** No fingerprint image or template is ever transmitted or stored —
only a WebAuthn public key.

**Access.** Employees see their own records. Supervisors see the employees assigned
to them. Administrators see the company. Enforced server-side and covered by tests.

**Photos.** Optional, camera-only (no gallery), stored in a private bucket, and
served only after an authorisation check.

**Retention.** Configurable and documented. Work events and audit logs are never
deleted by a background job — removing attendance history is Uni-Ace's decision to
make explicitly.

**Also implemented:** BCrypt password hashing (cost 12), rotating refresh tokens,
single-use hashed activation tokens, rate limiting on authentication endpoints, a
brief lockout after repeated failures, uniform "email or password is incorrect"
responses, server-side validation on everything, database-level constraints, an
append-only audit log, and no secrets in source.

Employees see a plain-language privacy note on their Profile screen.

---

## 11. Known limits

Stated plainly, because a prototype that oversells itself is worse than one that
does not.

- **Offline fingerprint verification is not possible.** It needs the server. See
  [§5](#fingerprint--and-an-honest-caveat).
- **GPS spoofing and clock manipulation cannot be prevented** in a browser. They are
  made *detectable*, not impossible.
- **A fingerprint is tied to one device and one domain.** Registering on
  `localhost` does not carry over to a tunnel URL or to a phone — each has to be
  set up where it will be used. That is WebAuthn working as designed, not a
  shortcut taken here.
- **Rate limiting is per instance**, which suits one free backend. A multi-instance
  deployment would need a shared store.
- **Email is optional.** Activation always works by copying the link; a free Resend
  API key enables sending.
- **E2E specs are not written yet** — the harness is configured and the flows are
  listed.
- **One active device per employee**, by design for this MVP.

---

Built for Uni-Ace Adjusters Inc. · Manila and Cebu
