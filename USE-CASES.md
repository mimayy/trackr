# End-to-end use cases

**Write your corrections in this file.** Tell me when you have saved it and I
will read it and implement the changes.

Below is what the app does **right now**, written out step by step. Edit it
directly — strike things out, rewrite a step, add a rule, leave a question in
brackets. You do not need to describe *how* to build anything; describe what the
person does and what should happen.

Plain language is fine. `[LIKE THIS]` is fine for anything you are unsure about.

---

## What helps me most

Last time the thing I had to guess at was **what should be refused**. So
wherever you can, say what happens when something goes wrong or out of order:

- What should the app *stop* someone from doing?
- What should it *allow but flag* for a supervisor?
- What should it just accept quietly?

If you don't say, I will ask rather than guess.

---

## 1. Field-based employee: office → warehouse → office

The main flow. Current behaviour:

| # | Employee does | App does |
|---|---|---|
| 1 | Opens the app, signs in | Home screen shows their status and **one** button |
| 2 | Taps **TIME IN** | Asks for fingerprint → captures GPS → offers optional photo → saves |
| 3 | Taps **LEAVE THE OFFICE** | Fingerprint → GPS → optional photo → saves |
| 4 | Taps **I'M HERE** on arrival | **Asks which warehouse** (type it, or tap a name used before) → fingerprint → GPS → optional photo → saves |
| 5 | Taps **I'M LEAVING** | Asks where they're going next: **OFFICE**, **HOME**, or **ANOTHER FIELD LOCATION** → fingerprint → GPS → photo → saves |
| 6 | Taps **I'M BACK AT THE OFFICE** | Fingerprint → GPS → optional photo → saves |
| 7 | Taps **TIME OUT** | Fingerprint → GPS → optional photo → saves. Day ends. |

**Rules currently enforced:**

- Only one button is offered at a time. The server decides which.
- Steps must happen in order. Recording an arrival before leaving the office is refused.
- Time In twice in a day is refused: *"You have already timed in today."*
- Field Arrival **requires** a warehouse name. Nothing else does.
- There is **no geofence**. An employee can record anywhere.
- Time Out is refused while a trip is unfinished (still at a site, or on the way back).
- Photos are always optional.
- Every activity needs a successful fingerprint and the phone's GPS position. No fingerprint set up → nothing can be recorded until it is set up in Profile. Fingerprint cancelled or not recognised → nothing is saved; try again.
- Offline → the activity is queued on the phone with its GPS position and uploads later. The saved time is when they *tapped*, not when it synced. A fingerprint can't be checked offline, so it goes to the supervisor for review.

**Your corrections:**

<!-- write here -->

---

## 2. Office-based employee

Currently: **TIME IN** in the morning, **TIME OUT** at the end. Nothing else is
offered.

**Your corrections:**

<!-- write here -->

---

## 3. Straight to a warehouse (no office first)

Currently supported: `TIME IN → I'M HERE → I'M LEAVING → TIME OUT`, with the
Time In recorded at the site.

**Your corrections:**

<!-- write here -->

---

## 4. Several warehouses in one day

Currently: on **I'M LEAVING**, choosing **ANOTHER FIELD LOCATION** puts them back
into "on the way", so they can record another arrival. Repeats as often as needed.

**Your corrections:**

<!-- write here -->

---

## 5. Things that go wrong

How should each of these behave? Current behaviour in brackets.

| Situation | What should happen |
|---|---|
| Phone has no signal at the warehouse | [queued on the phone, uploads later] |
| Employee forgets to record leaving a site | [nothing chases them; the report shows "Still there"] |
| Employee forgets to Time Out | [day stays open; supervisor sees it in the exceptions queue] |
| Employee taps the wrong button | [it records; a supervisor can correct it afterwards, original kept] |
| Phone battery dies mid-day | [whatever was queued uploads when they sign in again] |
| Employee is at a warehouse that has never been used before | [they type the name; it is accepted] |
| GPS is very inaccurate indoors | [refused with "move to an open area"] |
| Two people share one phone | [each signs in separately; fingerprint is per-device, so the second person re-registers] |

**Your corrections:**

<!-- write here -->

---

## 6. Supervisor and administrator

Currently:

- **Supervisor** — sees only their own team. Dashboard, latest recorded
  locations on a map, exceptions queue, reports, and can correct an event
  (the original is always kept alongside the correction).
- **Administrator** — everything above company-wide, plus employees,
  supervisors, locations, schedules, holidays, audit logs and settings.

**Your corrections:**

<!-- write here -->

---

## 7. Anything else

New screens, different wording, things to remove, anything that does not fit
above.

<!-- write here -->
