# Attendance Tracker — Master Product & Engineering Specification

**Version:** 1.3
**Date:** 2026-11-11  
**Status:** Approved product direction; ready for architecture and implementation planning  
**Primary platform:** Native Android  
**Purpose of this file:** The single source of truth for requirements, rules, non-goals, and acceptance criteria. An implementation agent must read this entire file before planning or coding.

> **Priority rule:** When a suggestion conflicts with an explicit rule in this document, the explicit rule wins. Surface material conflicts or truly blocking questions in the planning phase; do not silently change attendance policy.

## 1. Product vision

Build a genuinely polished, fast, reliable, **offline-only Android attendance tracker** intended for daily use by college students. At the beginning of a semester, students define courses and a recurring Monday–Friday timetable. At the end of each college day, they record attendance with as little effort as possible. The app shows attendance **for each course separately**, approved and pending duty-leave credit, and how many further attendance-counting periods can be missed while remaining at **90% or higher**.

Three non-negotiable priorities:

1. **Correctness and trust:** A beautiful app that miscalculates attendance is a failed app.
2. **Minimal-effort UX:** The most common workflows must be fast, obvious, and forgiving.
3. **Professional quality:** Intentional design, accessible interactions, maintainable code, disciplined Git history, and focused automated verification proportionate to the change.

The app is for multiple independent users, but **does not** have accounts, a server, cloud sync, or shared databases. Each user's data resides on their own device.

## 2. Definitive institutional attendance rules

### 2.1 One period = one attendance unit

Attendance is counted **per academic period**, not by elapsed clock minutes. A 45-minute Friday period and a 60-minute Monday period each count as **one** attendance unit. Every period is recorded independently, including multiple consecutive periods of the same course.

- Normal days have **six designated periods**.
- The attendance threshold is fixed at **90%, inclusive**, **per individual course**. It is not editable by the user and not computed merely as a semester aggregate.
- Courses can appear in more than one period on a day.
- A lab is a **separate course**, not a subsection of the corresponding theory course.
- A usual three-period lab contributes **three independent units**. A student can be present for 2 and absent for 1; it is not always all-or-nothing.
- Displayed time and actual duration are scheduling/UX information, **not weights in the attendance calculation**.

### 2.2 Standard times

| Day | Period | Local time | Attendance weight |
| --- | --- | --- | --- |
| Monday–Thursday | P1 | 09:00–10:00 | 1 |
| Monday–Thursday | P2 | 10:00–11:00 | 1 |
| Monday–Thursday | P3 | 11:00–12:00 | 1 |
| Monday–Thursday | Lunch | 12:00–13:00 | 0 |
| Monday–Thursday | P4 | 13:00–14:00 | 1 |
| Monday–Thursday | P5 | 14:00–15:00 | 1 |
| Monday–Thursday | P6 | 15:00–16:00 | 1 |
| Friday | P1 | 09:00–09:45 | 1 |
| Friday | P2 | 09:45–10:30 | 1 |
| Friday | P3 | 10:30–11:15 | 1 |
| Friday | P4 | 11:15–12:00 | 1 |
| Friday | Lunch | 12:00–14:00 | 0 |
| Friday | P5 | 14:00–15:00 | 1 |
| Friday | P6 | 15:00–16:00 | 1 |

Friday's P5/P6 times are consistent with the stated 14:00–16:00 block. The regular six-slot interface should remain simple. Timings must not accidentally change period-count calculations.

### 2.3 Regular timetable, special days, and extras

- The recurring timetable is set for **Monday–Friday**. The ordinary schedule does not automatically create attendance obligations for Saturday or Sunday.
- Some Saturdays or other exceptional dates follow **another weekday's full timetable**. Let the user pick any date and copy a chosen weekday's **six-period schedule**; those copied sessions **do count** toward attendance when recorded as conducted.
- Permit date-specific substitutions, removal/cancellation of individual periods, and edits without changing the weekly template or historical records.
- Permit **manually added individual extra classes** on any date. These are **non-attendance-counting by default**, because ordinary extra classes generally do not count at this college. Any exceptional counted makeup session must require an explicit, clearly communicated decision; never automatically count extras just because they are on the calendar.
- A copied weekday is different from an ordinary extra class: a copied full academic timetable is attendance-counting when its periods are conducted and logged.
- Special-day schedule copying must not duplicate existing dated period records. Reapplying or modifying a copied timetable must be safe and explain conflicts if entries already contain attendance.
- A one-tap **Mark as Holiday / No Classes** action must be reversible. Holiday dates and individually cancelled/not-conducted periods contribute **zero** to the attendance denominator. Never silently erase previously entered records; preserve or explicitly confirm changes so reversal is safe.
- The six standard slots are fixed for normal schedules. Supplemental activities may be displayed separately from those six slots if appropriate; do not compromise the attendance unit model.

## 3. Semester and course setup

- Create a semester with a name and start date. Do not ask for, store, or use a planned semester end date. The semester stays active until the user explicitly marks it done; completion is a state, not a calculated date boundary.
- Marking a semester done preserves all attendance and allows browsing its records. Permit reopening it to correct an accidental completion. No schedule or calculation may depend on a semester end date.
- Create/edit courses using a name and optional color. Do not require or display a separate short name or course code. Keep names unique for new or renamed courses within the same semester; distinguish **lab** courses from theory/other courses for grouping/presentation, while calculating every course independently.
- One active semester at a time; archive and browse earlier semesters without losing data.
- Provide thoughtful first-run onboarding that creates a semester, courses, and weekly timetable quickly.
- Support **starting midway through a semester**: for each course, enter opening cumulative counts for **conducted**, **physically attended**, **approved DL**, and **pending DL** periods. These represent historical attendance before the user began logging dated periods.
- Opening counts must be nonnegative, and `physically attended + approved DL + pending DL <= conducted` must always hold. Remaining conducted periods in the opening balance are effectively absent. Do not double-count opening balances and dated sessions. Opening balances must be reviewable/editable with safeguards.
- A starting balance is an aggregate historical snapshot, **not** a fabricated set of dated classes. It must coexist cleanly with subsequent dated attendance and import/export.

## 4. Dated sessions, logging, and edits

### 4.1 Record model and statuses

The recurring timetable is a **template**; each academic period on a particular date is a **separate dated session**. Keep a stable session identity so edits, exceptions, and backup/restore remain reliable.

A dated, attendance-eligible period can be:

- **Unrecorded** — user has not entered a result; must be excluded from both the numerator and denominator.
- **Present** — conducted and physically attended.
- **Absent** — conducted and not credited.
- **Duty leave: Pending** — conducted; provisional attendance credit.
- **Duty leave: Approved** — conducted; confirmed credit.
- **Duty leave: Rejected** — conducted; no credit (equivalent to absent for attendance math, but preserve DL history).
- **Cancelled / Not conducted** — no attendance unit; excluded from the numerator and denominator.

An entire **Holiday / No Classes** date also contributes zero units. Do not confuse *scheduled*, *generated*, *conducted*, and *recorded*; only **recorded, conducted, attendance-counting** periods enter the percentage. Future scheduled periods do not enter totals. For a non-counting supplemental class, tracking its occurrence must not affect attendance statistics.

### 4.2 Low-friction daily workflow

- Today-first home dashboard: six normal slots where relevant, with compact present/absent/DL controls and clear progress through today's records.
- **Mark All Present** is a prominent action that marks **only eligible, unrecorded, attendance-counting** sessions. It must **never overwrite** previously recorded absences, duty leave, present entries, holidays, or cancellations.
- Lab UX: present a typical three-consecutive-period lab as one appealing grouped card with **one-tap group actions** (present, absent, DL), plus an **expand/edit per period** option. Never merge the underlying three attendance units.
- Easy full-day and selected-period duty leave logging.
- View and correct any previous date; offer safe undo for recent accidental changes.
- Calendar/history makes missing, incomplete, holiday, and special timetable days obvious.
- An incomplete previous day remains **unrecorded**, not automatically absent; clearly flag incomplete historical dates and disclose limitations in statistics derived from partial data.
- No unnecessary confirmation dialogs for reversible, nondestructive actions. Use deliberate confirmation when changing or replacing existing records/data.

### 4.3 Timetable revisions and historical integrity

- Timetable edits affect the chosen effective dates/future schedule, **not** recorded past sessions.
- Date-specific substitutions and copied days must be distinct from recurring timetable rules.
- Avoid silently rewriting attendance on already recorded dates. If a schedule edit conflicts with existing attendance, present a precise and safe resolution.
- Date identity must be stable under device timezone changes: represent a college day as a local calendar date rather than inferring it solely from a UTC timestamp.

## 5. Duty leave (DL)

DL can cover one period, several selected periods, a three-period lab, or a whole day. A DL application/event may relate to multiple course periods, but each period must receive at most **one** attendance outcome/credit; overlapping events cannot double-count credit.

- Status lifecycle: **Pending -> Approved or Rejected**, with the ability to correct earlier entries.
- **Pending DL counts as attendance provisionally**, because it is usually approved at this college. The **primary percentage and primary safe-skip calculation must include pending + approved DL**, as explicitly requested.
- Approved DL is confirmed attendance credit without being physically present.
- Rejected DL loses its credit and is counted as uncredited absence for that period unless the user corrects the underlying entry.
- Retain a distinct, visible indication when displayed numbers rely on **pending** approval. Do not misrepresent provisional safe skips as certain.
- Optionally show a **confirmed-only comparison** (excluding pending DL), but this is **secondary**; it must not replace the requested provisional primary figure.
- Per-course statistics: physical presence, pending DL periods, approved DL periods, rejected DL periods, total credited attendance, effective percentage.
- Semester statistics: **distinct dates with DL**, DL **events/requests**, and total DL **periods** (with breakdown by status). These are different metrics; name them clearly and avoid double-counting a day with several DL periods.
- Retroactive DL status updates must immediately recalculate affected courses and safe-skip allowances.

## 6. Exact attendance calculations

Perform integer arithmetic for decisions and threshold comparisons; percent display may be rounded for presentation only.

For each course:

- `N0` = opening balance conducted periods.
- `P0` = opening balance physically present periods.
- `DA0` = opening balance approved DL periods.
- `DP0` = opening balance pending DL periods.
- `N1` = number of **dated, attendance-counting, recorded and conducted** periods (present, absent, or DL of any status).
- `P1` = dated physically present periods.
- `DA1` = dated approved DL periods.
- `DP1` = dated pending DL periods.
- `N = N0 + N1` (conducted denominator).
- `P = P0 + P1` (physical attendance).
- `DA = DA0 + DA1` (approved DL).
- `DP = DP0 + DP1` (pending DL).
- `A = P + DA + DP` (effective credited attendance **including pending DL**).
- `C = P + DA` (confirmed credited attendance, informational).

Rejected DL remains in the denominator but not `A` or `C`; cancelled/not-conducted, unrecorded, future, and non-counting supplemental sessions contribute to **none** of these totals.

For `N > 0`:

- **Primary attendance percentage:** `100 * A / N`.
- **Confirmed comparison:** `100 * C / N`.
- **Meets minimum:** `10 * A >= 9 * N` (exact, inclusive 90%).
- **Safe skips (additional absent, attendance-counting periods):** `max(0, floor((10*A - 9*N) / 9))`.
- **Recovery (additional consecutive attended, attendance-counting periods to reach 90%):** `max(0, 9*N - 10*A)`.

The safe-skip formula assumes each additional missed period counts as conducted and receives no attendance credit; the recovery formula assumes each additional conducted period is fully attended and no other changes occur. Both are for the **same course**, never total semester attendance. With pending DL, both are provisional.

For `N = 0`, show **No attendance recorded**, and do not misleadingly display `0%`, `100%`, or a meaningful skip allowance. Show safe skips as unavailable/zero with an explanatory state.

Always distinguish **safe absent periods** from **calendar days**. For example, a three-period lab consumes three units if all three are missed. The UI can optionally offer a contextual future-schedule forecast, but must never portray a period allowance as a day allowance.

Incomplete past records must carry a visible **incomplete-data** warning on affected calculations. They do not enter `N` until marked.

### Required calculation test examples

| Scenario | Expected behavior |
| --- | --- |
| `N=40, A=38` | `95%`, safe skips `2` |
| `N=10, A=9` | `90%`, safe skips `0`, threshold met |
| `N=10, A=8` | `80%`, recovery requires `10` consecutive attended periods |
| Three-period lab, two present and one absent | Adds `N=3`, `A=2` |
| One Friday 45-minute period marked present | Adds `N=1`, `A=1` |
| One pending DL period | Adds `N=1`, `A=1` provisionally; confirmed `C` does not include it |
| Rejected pending DL | Numerator decreases by 1, denominator unchanged |
| Unrecorded or cancelled period | Adds neither `N` nor `A` |
| Mid-semester balance plus new sessions | Both contribute exactly once |
| Copied Saturday counted; supplemental extra not counted | Only counted, recorded Saturday periods affect totals |

Test exact boundary inequalities, partial labs, status transitions, holidays, and edits as well as typical examples.

## 7. Experience and design requirements

**Visual quality is a product requirement, not a later decoration pass.** Aim for a premium, elegant, modern Android app that feels intentionally designed by skilled professionals.

- **Today screen:** scheduled periods; one-tap logging; lab grouping; progress/completion; compact high-value stats.
- **Courses/insights:** per-course effective percentage, physical vs DL credit, minimum threshold, safe period skips, recovery requirement, pending/incomplete warnings.
- **Timetable editor:** fast weekly setup and clear day-specific overrides.
- **History/calendar:** find and repair unrecorded days, see holidays and special days, adjust old entries.
- **DL view:** enter, update, and inspect leave by date/event/course and approval status.
- **Semester/settings/backup:** clear but not dominant in everyday navigation.
- Minimal copy, excellent typographic hierarchy, harmonious colors, semantic iconography, consistent spacing, light and dark themes, responsive touch targets, polished empty states.
- Smooth purposeful motion, subtle micro-interactions, and optional appropriate haptics; avoid animation overload; respect reduced-motion preferences.
- Accessibility: contrast, scalable text, screen-reader labels, and sensible focus/order.
- Reversible mistakes, informative snackbars/undo, no dense forms during everyday logging.
- Optional on-device end-of-day reminders; ask for notification permission only when enabling. Must operate without network access.
- Let design and engineering make small UX improvements autonomously, while keeping the primary workflows extraordinarily simple.

Do **not** prescribe every screen/layout before the planning/design phase. The agent should propose a coherent information architecture, design tokens, interaction patterns, and at least the key user journeys.

### 7.1 Owner-approved visual direction and Phase 1 design gate (2026-11-11)

Use neutral whites/charcoal with restrained blue actions, selection, and focus; no purple or lavender surfaces and no wallpaper-derived dynamic palette overrides.

| Role | Light | Dark |
|---|---|---|
| Background | `#F7F8FA` | `#101113` |
| Surface | `#FFFFFF` | `#191B1F` |
| Main text | `#17191D` | `#F3F4F6` |
| Secondary text | `#626975` | `#A5ACB8` |
| Border/divider | `#E3E6EB` | `#30343B` |
| Primary action | `#2457D6` | `#8AACFF` |

Choose contrasting foregrounds and stronger interactive outlines where needed. Validate actual enabled component text at 4.5:1 (3:1 for large text) and essential control boundaries/focus at 3:1 in both themes. Quiet decorative dividers need not meet the interactive-boundary threshold. Green, amber, and red are reserved for meaningful status, always with text/icons. Provisional DL and incomplete records must remain distinct without large warning-colored panels.

Refinement of existing Phase 1 screens is a **Phase 1 exit criterion**: compact typography, intentional spacing/hierarchy, usable forms/buttons/feedback, empty/error/loading states, and restrained neutral surfaces. Avoid excessive cards, oversized headings, decoration, and large tinted panels. Review available local previews/screenshots in both themes, including 200% text, keyboard handling, 48 dp targets, and scrolling; address visible defects. A connected phone and physical-device visual approval are not phase exit requirements. Phase 7 retains broader refinement of later screens.

## 8. Preferred implementation approach

Native Android, **Kotlin** unless a compelling reason is documented and accepted. Preferred stack:

- Jetpack Compose + Material 3
- Room (SQLite) for the structured offline database
- DataStore for user preferences
- Coroutines and Flow for async/reactive work
- MVVM or another well-justified, simple maintainable architecture
- A separately testable **pure calculation/domain layer**

Use **current stable** Android tooling and dependencies chosen during planning after checking compatibility; do not assume hard-coded version numbers in this spec are current. Keep architecture pragmatic: avoid both giant UI-driven logic and needless multi-module overengineering.

The domain should explicitly represent:

1. Semester and active/archive state.
2. Course and course type (e.g., lab vs theory).
3. Recurring day/period timetable entry, including effective dating/versioning as needed.
4. Dated class/period session, its origin (recurring, copied/special, manual), its countability, and its resolution.
5. Attendance outcome per countable period.
6. Optional visual **lab grouping** referencing underlying periods.
7. Duty leave event/request, approval status, and covered periods.
8. Opening balance per course.
9. Holiday/day override and individual cancellations.
10. Backup schema version and local preferences.

This is a **conceptual model**, not a mandate for one-to-one database tables. Produce a proposed relational schema and explain referential integrity, uniqueness, and transaction boundaries during planning.

Important invariants:

- One distinct attendance result per dated course-period session.
- A period cannot receive overlapping present/absent/DL credit.
- The same dated session is never materialized twice.
- Holiday/cancellation operations do not silently lose prior data.
- Timetable changes never retroactively mutate confirmed records without explicit user action.
- Course and semester ownership is validated.
- Backup restore and bulk day actions are atomic from the user's perspective.
- Date/period identity is deterministic and time-zone-safe.

## 9. Data ownership, backups, and privacy

- Completely functional offline; no server, user accounts, cloud database, analytics/telemetry, or unnecessary internet permission.
- Local storage only; **disable Android automatic cloud backup** for app data. Do not confuse this with the explicitly requested user-initiated local export/import.
- Export an entire portable, **versioned** backup with semesters, courses, opening balances, recurring schedules, overrides, holidays, countable and supplemental sessions, attendance, DL events/statuses, and relevant settings.
- Use Android's Storage Access Framework/system picker and share intents where sensible; don't require broad filesystem permissions.
- Restore flow: select file -> validate full contents/version/referential consistency -> preview relevant information and clearly warn that this is a **replacement**, not a merge -> obtain explicit confirmation -> perform an **all-or-nothing** restore. Invalid/corrupt/incompatible files must not damage existing data.
- Preserve IDs and logical relationships correctly through export/import; prove the round trip with tests.
- Provide useful error messages and safe behavior if storage is full, an import fails, or the app process is interrupted.
- Protect user data during schema migrations and application upgrades. No implicit destructive Room migrations.

## 10. Testing, release quality, and Git discipline

### 10.1 Testing

**Owner-approved verification amendment (2026-11-11):** The owner will not connect a mobile phone. Continue with Phase 2 using focused local verification; do not block development or phase completion on physical hardware, exhaustive device journeys, repeated screenshot runs, or hardware performance measurements. This amendment supersedes earlier phone-based gates in the specification and plan. Preserve the exact attendance rules, validation, and data-integrity requirements.

Write correct code for the specified cases and boundaries, backed by proportionate checks. Run focused calculation/domain tests when rules change, repository/migration tests when persistence changes, and relevant build/lint checks for implementation changes. Add focused UI regression tests when interaction behavior changes; do not expand testing solely to satisfy the former device-review gate. Reuse existing coverage and avoid redundant reruns without a change or failure that warrants them. Device/emulator checks are optional when available; report unverified behavior honestly. Never guarantee correctness for all possible cases merely from code review or passing tests.

The following is a coverage reference for relevant implemented features, not a requirement to execute every category for every phase:

- Pure unit tests for every formula and 90% edge/boundary condition.
- DL transitions, provisional vs confirmed calculations, opening balances, holiday/cancellation, unrecorded handling.
- Lab all-or-nothing and mixed-period attendance.
- Timetable effective-date changes, historical integrity, copied Saturday, manual non-counting extras, and duplicate prevention.
- Room data integrity/migration tests and transaction safety.
- Backup/export/import validation, malformed files, versioning, and replace/restore rollback/atomicity.
- Compose/UI tests for setup, Mark All Present, one-tap lab logging, corrections/undo, DL, holiday, and import confirmation.
- App restart persistence, offline operation, reminders where enabled, accessibility/light/dark layouts.
- Relevant build/lint/static checks. Emulator or device journeys are optional when available and are not prerequisites for implementation or phase completion.

**Never claim zero bugs or that tests ran when they did not.** Provide commands, evidence of execution, results, remaining risks, and any relevant deferred device checks.

### 10.1.1 Personal-device test safety

If personal-device instrumentation is explicitly requested in the future, supported runs must set `android.injected.androidTest.leaveApksInstalledAfterRun=true` where applicable and verify installation/data retention on the actual runner. Routine tests must never uninstall the app, clear app storage, or reset the personal database. Use in-memory Room or explicitly named test-only databases and clean up only those fixtures. Do not enable an orchestrator clear-data mode. Document the supported command, fixture isolation, before/after retention verification, and any runner limitations. Stop a run if a runner cannot preserve personal data; use a disposable emulator instead.

### 10.2 Git and public project

- Initialize and use Git thoughtfully, with small logical commits representing working increments.
- Meaningful conventional-style commit messages are welcome, but clarity matters more than a particular naming convention.
- `.gitignore` must exclude local IDE files, build outputs, private credentials, and signing material.
- README should cover project purpose, features, screenshots once available, prerequisites, local build/run/test instructions, backup and privacy behavior, and contribution basics.
- Add an appropriate license **after the repository owner selects/approves one**; public visibility alone does not imply an open-source license.
- Add practical CI (such as GitHub Actions) for clean builds, lint, and automated tests.
- Do not commit generated secrets and do not push to a remote repository or publish a release without explicit authorization.
- Prefer atomic, reviewable changes and explain milestone boundaries in the plan.

## 11. Out of scope for the initial version

Unless later approved, **do not** add user accounts, cloud sync, attendance scraping from university portals, faculty/admin panels, multiplayer collaboration, calendar syncing, timetable OCR, AI chat features, ads, payments, or analytics services. Avoid proliferating settings that undermine the deliberately fixed institutional 90% rule. Do not convert time durations into weighted attendance.

## 12. Planning expectations and delivery milestones

An autonomous coding agent must first enter **PLAN ONLY** mode:

1. Read this document **in full** and inspect the repository and existing build environment, if any.
2. Restate the binding product decisions and identify any inconsistencies or genuinely blocking unknowns. Make narrow, clearly labeled assumptions for nonblocking details.
3. Propose architecture, domain model/Room schema, invariants, calculation API, backup format, and migration strategy.
4. Propose information architecture, key screens, setup/logging flows, lab interaction, DL UX, holiday UX, and a coherent visual design system.
5. Produce a **phased implementation plan** with dependencies, concrete tasks, files/modules likely to change, test/acceptance criteria, and suggested Git commit boundaries.
6. Include a traceability matrix mapping every section of this spec to implementation phases and automated/manual verification.
7. Identify technical/release risks and give mitigations; propose a realistic device-testing strategy.
8. **Stop after producing the plan.** Do not generate code, modify repository files, run setup commands that mutate it, make commits, or push anything in plan mode. Await user approval to implement.

Suggested phases (agent may improve sequencing with reasoning):

- **Phase 0 — Discovery and architecture:** repo/tooling inspection, requirements, schema, design/navigation plan, acceptance matrix.
- **Phase 1 — App foundation:** Kotlin/Compose shell, local storage, theming, navigation, CI/test harness.
- **Phase 2 — Semester, course, and timetable management:** onboarding, six-period weekly editor, Friday schedule, opening balances.
- **Phase 3 — Dated sessions and attendance engine:** deterministic instances, attendance statuses, pure calculations, historical integrity, unit tests.
- **Phase 4 — Logging UX and exceptions:** quick actions, labs, past edits, calendar, holidays, special/copy days, manual extras.
- **Phase 5 — Duty leave and actionable insights:** DL events, provisional credit, analytics, safe skips, recovery, warnings.
- **Phase 6 — Portable backup/restore and preferences:** local export/import, all-or-nothing replacement, optional reminders.
- **Phase 7 — Polish and hardening:** refined animations, accessibility, dark mode, performance, error handling, instrumented tests.
- **Phase 8 — Device validation and public project readiness:** USB-debugging tests, fixes, documentation, screenshots, APK, CI/release checks.

Agent should choose working vertical slices over disconnected scaffolding and attach **measurable exit gates** to each phase. Prioritize correctness before visual extras, but establish a coherent design system from the start.

## 13. Definition of done

A release candidate is acceptable only when:

- Each course calculates independently and accurately, including 45-minute Friday periods and three-period labs.
- Unrecorded, cancelled, holiday, future, and non-counting sessions are never silently counted.
- Pending DL drives the **primary** percentage/skip calculation with clear provisional labeling; approval/rejection updates stats correctly.
- One-tap all-present, grouped lab actions, partial lab edits, date corrections, and reversible holiday marking behave correctly.
- Copied full special days count, individually added supplemental classes do not count by default, and no duplicates are created.
- Mid-semester opening balances work and cannot be double-counted.
- Import/export works fully offline and a failed restore never corrupts existing records.
- App data survives restarts and migrations; auto cloud backup is disabled.
- Build, relevant tests, and accessible UX checks have been executed and their actual status reported.
- Repo history/documentation is public-project quality and no unapproved push/publish has occurred.

**End-of-file marker: ATTENDANCE_TRACKER_SPEC_V1_COMPLETE**
