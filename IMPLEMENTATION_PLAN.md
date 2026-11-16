# Attendance Tracker — implementation plan

**Owner-approved amendment, 2026-11-11:** Semester end dates are removed throughout the product. Use a start date and explicit done/reopen state. Phase 1 includes this lifecycle and a preserving migration for the earlier development schema. Scheduling uses requested date ranges and elapsed active dates, never a planned semester end date. The affected decisions below are updated; all other phase boundaries remain unchanged.

**Owner-approved design/test amendment, 2026-11-11:** Specification v1.2 §§7.1/10.1.1 reopens Phase 1 until its existing screens pass a proper neutral-palette design review on the phone and supported instrumentation retains installation/personal data. Phase 7 continues refinement of later screens. No subsequent phase is authorized by this amendment.

**Owner-approved verification/scope amendment, 2026-11-11 (specification v1.3 §10.1):** Proceed with Phase 2. No phone will be connected. Physical-device design review, instrumentation retention demonstrations, exhaustive device journeys, and hardware measurements are optional follow-up evidence, not development or exit blockers. Use focused local correctness tests and relevant build/lint checks, scaled to changed behavior. Existing known failures remain recorded; this amendment does not turn failures into passes or certify Phase 1's unfinished review. This policy supersedes conflicting earlier verification gates throughout this roadmap; feature scope and attendance/data-integrity acceptance criteria remain unchanged.

## A. Proof of understanding and requirement audit

### Source and repository findings

I read **all 328 lines** of [`./PROJECT_SPEC.md`](./PROJECT_SPEC.md), **version 1.0**, dated **2026-11-11**. Its final line contains **`ATTENDANCE_TRACKER_SPEC_V1_COMPLETE`**.

The project directory contains the specification and empty environment-provided directories. There is no Android project, build configuration, documentation beyond the spec, or test suite. Git does not recognize a repository; `.git` is empty and currently not writable.

Read-only environment checks found Java **21.0.13** and Git **2.53.0**. Gradle, Kotlin CLI, ADB, and SDK Manager were not on `PATH`; no Android SDK was found in the checked locations. This does not establish that none exists elsewhere.

**No files were changed, tools installed, builds/tests executed, or Git operations performed.**

### Binding decisions

| Spec sections | Implementation consequence |
|---|---|
| §§1, 2.1, 6 | Compute independently per course using integer period counts and an inclusive, fixed 90% threshold. Never weight attendance by duration. |
| §§2.1–2.2 | A Friday 45-minute period and a normal 60-minute period each contribute one unit. A lab remains a separate course with three independently editable periods. |
| §§3, 6 | Opening balances are aggregate historical counts, never fabricated sessions. Their coverage must not overlap dated logging. |
| §§4.1, 6 | Scheduled sessions do not imply conducted sessions. Unrecorded, future, cancelled, holiday-suppressed, and non-counting sessions contribute nothing. |
| §§4.2, 5–6 | Pending DL contributes to the **primary percentage, safe skips, and recovery calculation**. Confirmed-only figures are secondary. |
| §§2.3, 4.3, 8 | Separate recurring schedules, dated snapshots, and exceptions. Preserve stable identities and previously recorded information. |
| §§2.3, 4.2 | Mark All Present fills only eligible unrecorded counted periods. Holidays and cancellations are reversible. |
| §§9, 11 | Offline application with no accounts, network dependency, telemetry, or automatic cloud backup. Manual backup restores replace the whole dataset after validation and confirmation. |
| §§7, 10, 12–13 | Establish design and tests early; deliver working increments with measurable gates and honest execution evidence. |

### Clarifications resolved in this planning run

You selected:

1. **Per-course opening-balance cutoffs**, initially defaulted to the same date.
2. **Source weekday clock times** when copying a timetable.
3. **Whole-date holiday suppression**, including supplemental activities, while retaining all records for reversal.

### Audit of tricky cases and remaining assumptions

The specification contains no contradictory attendance formulas. Its main gaps concern how to preserve and explain data.

| Issue | Classification and decision |
|---|---|
| Opening balances versus dated periods | **Resolved.** Each course has `loggingStartDate`; its opening balance covers dates strictly before that date. Dated attendance is eligible from that date onward. Earlier dates show “Covered by opening balance,” not missing records. |
| Editing the cutoff after logging | **Nonblocking default.** Allow cutoff changes before recorded outcomes exist. Afterwards, keep the cutoff fixed in v1; opening counts remain editable with a before/after impact preview. Do not silently reinterpret dated history. |
| Historical pending DL in opening balances | **Nonblocking default.** Resolve through opening-balance adjustments: approval transfers pending to approved; rejection removes pending credit without changing conducted count. Record the adjustment. Historical DL request/date counts remain unknown. |
| Timetable edits and historical sessions | **Nonblocking default.** Recurring revisions start today or a chosen future date. Past changes use the date editor. Protect every recorded outcome, including absence and pending DL—not only approved records. |
| Multiple lab periods | **Specified.** Group consecutive same-course lab periods visually; preserve individual outcomes. Group actions disclose and confirm changes to existing differing outcomes. |
| Cancelled versus unrecorded | **Specified, with storage decision.** Cancellation suppresses a session while retaining its prior outcome. An unrecorded session has no outcome. Undoing cancellation restores the prior state. |
| Friday duration | **Specified.** Use the exact §2.2 time profiles, with no duration multiplier anywhere in calculations. |
| Copied Saturdays versus extras | **Specified.** Copied assigned slots are counted when logged. Manually added extras default to non-counting; counted makeup requires an explicit choice. |
| Which weekly revision a copied date uses | **Nonblocking default.** Use the weekday template effective on the target date, preview it, then store a snapshot. Subsequent weekly revisions do not alter the copy. |
| Holidays over existing attendance | **Resolved scope; nonblocking interaction.** Confirm the impact when records exist, suppress all sessions, and preserve them for reversal. An empty day needs no confirmation. |
| Future periods | **Nonblocking default.** Future calendar dates are editable for scheduling but cannot receive attendance outcomes. Same-day logging is permitted without waiting for the displayed clock time; logging asserts that the period was conducted. |
| Partial DL decisions within a request | **Nonblocking default.** Store approval status per covered period. A request can display “Mixed”; bulk approval updates its selected/current DL periods. |
| Missing records on dates never opened | **Hidden dependency addressed.** Completeness must be derived from the full effective schedule, not only screens the user visited. |
| Atomic restore including settings | **Hidden dependency addressed.** Store every restorable setting in Room with attendance data. DataStore is limited to expendable device-local UI state. |
| Local backup picker showing cloud providers | **Nonblocking default.** Request local-only documents where supported, recommend device storage, and explain that the user controls the selected destination. No automatic upload or app network integration. |
| Git initialization | **Blocking for Phase 1 exit.** Provide writable Git metadata in this workspace or an approved writable checkout. Do not bypass the current restriction or create a disconnected history. |
| Toolchain availability | **Blocking for executing build gates.** Provision/locate the SDK and compatible build tools in the implementation run. |
| License and publication | **Release blockers only.** Owner chooses the license and explicitly authorizes any push/publication. They do not block local product development. |

---

## B. Architecture and data integrity

### Application structure and dependencies

Use two modules initially:

- **`:domain`** — Kotlin/JVM only: entities, eligibility rules, attendance mathematics, schedule resolution, and validation. No Android, Room, Compose, or device clock dependencies.
- **`:app`** — Android application: Room persistence, repositories, ViewModels, Compose screens, backup handling, and platform integrations.

Within `:app`, organize by `data`, `platform`, `ui/designsystem`, and feature packages: `setup`, `today`, `courses`, `timetable`, `history`, `duty leave`, and `settings`.

Choose:

| Area | Decision |
|---|---|
| Minimum Android | **API 26 / Android 8.0**: practical support range and native `java.time`; validate dependencies against this minimum. |
| UI | Jetpack Compose and Material 3, with stable APIs. |
| State | ViewModel + immutable `StateFlow` UI state; unidirectional actions and lifecycle-aware collection. |
| Persistence | Room/SQLite; repository methods own transactions and validation. |
| Preferences | Restorable preferences in Room; DataStore only for disposable device-local UI state such as scroll restoration. |
| Navigation | Stable Navigation Compose with typed routes; four top-level destinations and nested detail flows. |
| Dependency injection | Constructor injection with a small application container; avoid a DI framework initially. |
| Async | Coroutines and Flow; database, import/export, and generation work off the main thread. |
| Serialization | Kotlin serialization for versioned portable backups and structured change records. |
| Tests | JUnit, coroutine tests, Room instrumentation/migration tests, Compose UI tests; generated/property-style domain cases. |
| Reminders | WorkManager, approximate scheduling, no exact-alarm permission. |
| Build | Kotlin Gradle DSL, version catalog, pinned Gradle wrapper, dependency verification, exported Room schemas. |

Official documentation currently identifies **Room 2.8.5** and illustrates stable **Compose BOM 2026.09.00**. The AGP 9.3 documentation describes its Gradle/JDK/API compatibility. These establish viable candidates, **not a locally verified complete toolchain**. Exact AGP, Kotlin, KSP, Gradle, SDK, Navigation, coroutine, and test-library pins remain **to be confirmed together** before the first build. Select stable releases only, verify the compatibility tables, then freeze the tuple; do not independently upgrade each component to its newest version. [Room releases](https://developer.android.com/jetpack/androidx/releases/room), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [AGP compatibility](https://developer.android.com/build/releases/agp-9-3-0-release-notes), [Kotlin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html).

Working product name: **Ninety**. Proposed initial application ID: **`com.niranjan.ninety`**. Confirm ownership/name before distributing an installed release; no branding work blocks the domain milestone.

### Relational schema

Use UUID identifiers, explicit foreign keys, integer date/time fields, and enum values with validated serialization. Avoid persisting derived percentages or skip allowances.

| Table | Key fields and purpose | Constraints / indices |
|---|---|---|
| `semester` | `id`, name, start epoch-day, completed/archive flag, college zone ID | No end date; explicit completion preserves history. |
| `app_settings` | Singleton row; active semester ID, theme, haptics, reminder configuration, settings revision | One active-semester pointer; its referenced semester must not be archived. |
| `course` | `id`, semester ID, name, code, type, optional color, retired flag | Composite ownership key; unique normalized code within semester. |
| `opening_balance` | Course ID, logging-start date, conducted, present, approved DL, pending DL, revision | One per course; nonnegative counts; credited sum ≤ conducted. |
| `schedule_version` | `id`, semester ID, effective-from date, creation metadata | Unique effective-from per semester. Each version is a complete weekly snapshot; next version defines the end. |
| `template_slot` | Version ID, weekday, P1–P6, nullable course ID | Unique version/weekday/slot; weekdays 1–5 only; course belongs to same semester. |
| `day_instance` | `id`, semester ID, local date, template version, day kind, holiday flag, copied source version/weekday, revision | Unique semester/date. Holiday flag is independent of schedule origin. |
| `session` | `id`, day/semester/course IDs, nullable standard slot, origin, counting flag, start/end local times, cancellation flag, override mask, revision | Unique day/standard slot for non-null slots; extras have independent IDs. Composite FKs prevent cross-semester ownership errors. |
| `attendance_outcome` | Session ID, kind `PRESENT/ABSENT/DL`, nullable DL status/event ID, revision | Session ID is the primary key: at most one current outcome. DL requires an event and status; non-DL must not retain either. |
| `dl_event` | `id`, semester ID, optional title/note, created timestamp | Status derived from its current period outcomes; can be pending, approved, rejected, or mixed. |
| `change_batch` | Operation ID, kind, timestamp, before/after payloads, affected revisions, inverse format version | Append in the same transaction as changes; supports history inspection and guarded undo. |

Important secondary indices:

- Sessions by course/day and day ID.
- Outcomes by DL event and status.
- Schedule versions by semester/effective date.
- Change batches by timestamp.
- All foreign-key child columns.

Use database uniqueness and foreign keys for identity/ownership. Add supported SQLite checks/triggers through the database schema where Room annotations cannot express an invariant, and exercise them in migration tests. Cross-record rules still require transactional repository validation; never rely on UI validation alone.

**Distinct conceptual layers:**

- Templates describe recurring intent.
- Day instances snapshot a date’s schedule and exceptions.
- Sessions represent individual attendance units.
- Outcomes record what the user reported.
- DL events group requests; individual outcomes carry approval status.
- Holidays/cancellations suppress eligibility.
- Opening balances represent undated prior history.

### Dates, generation, and revisions

**Calendar identity**

- Store college days as `LocalDate`/epoch-day and times as minutes since midnight.
- Store UTC timestamps only for audit metadata.
- Set a semester’s college timezone from the device at creation, visibly editable in setup. Subsequent device timezone changes do not shift dates or semester boundaries.
- Inject a `CollegeClock` into orchestration; the calculation engine receives an explicit `asOfDate`.

**Materialization**

- Materialize assigned periods through today and resolve explicitly requested dates on demand, with deterministic identities based on semester/date/slot. There is no semester end boundary; manual completion freezes automatic obligation generation while preserving history.
- Generate no ordinary weekend obligations.
- Empty template slots remain empty UI slots, not attendance units.
- Re-running generation inserts missing identities and safely reconciles only unlocked generated rows.
- A database uniqueness constraint is the final defence against concurrent/double-tap duplication.
- Use a shared resolver for materialization and completeness checks so unopened dates cannot disappear from warnings.

**Revision rules**

- Weekly edits create an immutable full-week version, effective today or a selected future date.
- Earlier snapshots remain unchanged.
- Updating existing generated sessions preserves IDs.
- Preserve recorded outcomes, explicit date overrides, cancellations, and copied snapshots. Present conflicts before applying affected changes.
- Retain removed generated sessions as suppressed records where necessary for stable identity/history; never delete logged information.
- Course substitutions on recorded sessions require a date-specific correction with a before/after course-total preview and retained change record.
- Semester start changes cannot exclude recorded history; completion is explicit and reversible, never an end date. Course removal becomes retirement once referenced.

**Exception precedence**

1. Holiday suppresses the whole date.
2. Individual cancellation and explicit date edits apply.
3. A copied/custom day uses its saved snapshot.
4. Otherwise, use the effective recurring template.

Copying is one transaction. Reapplying an identical copy is a no-op. When recorded slots conflict, the safe default is to leave the date unchanged. The preview may offer **“Keep recorded periods; copy remaining slots”**; that result is labelled a **custom special day**, since it is no longer an exact weekday copy.

### Outcomes, DL, and atomic actions

Repository commands include:

- `recordPeriod`
- `markAllPresent`
- `recordGroup`
- `applyDutyLeave`
- `updateDutyLeaveStatus`
- `setHoliday`
- `cancelPeriod`
- `reviseSchedule`
- `copyWeekday`
- `correctOpeningBalance`
- `undoChange`
- `replaceFromBackup`

Each command validates ownership, eligibility, expected revisions, and its full mutation set inside a Room transaction.

`Mark All Present` selects only sessions that are:

- On a permitted logging date;
- Within semester and course coverage;
- Counted;
- Not holiday-suppressed or cancelled;
- Without an outcome.

It never modifies extras, existing present/absent/DL records, or future dates.

DL event approval changes only outcomes still attached to that event. Correcting a period to Present detaches its current DL link; later approval of the old event cannot overwrite that correction. Overlapping DL selection previews conflicts and requires an explicit replacement decision.

For semester DL metrics:

- Count distinct effective DL dates.
- Count distinct requests represented by those DL periods.
- Count DL periods by current status.
- Show opening-balance DL periods separately and state that their dates/request counts are unavailable.
- Preserve suppressed and superseded records in history without inflating current metrics.

### Undo and concurrency

- Every reversible mutation records its inverse in the same transaction.
- Provide snackbar Undo for recent actions and persistent reversal controls for holidays/cancellations.
- Undo checks affected row revisions. If a subsequent edit changed them, refuse the entire stale undo and open the current records for correction.
- Bulk actions either fully commit or change nothing.
- Announce success only after commit; show a saving state if storage is slow.
- Serialize restore against all writers; use revision checks for screens that may have stale state.
- Do not use SQLite `REPLACE` for routine upserts, since delete/reinsert semantics can damage relationships.

### Pure attendance engine

Proposed interfaces:

```text
AttendanceEngine.calculate(
    opening: OpeningBalance,
    periods: List<PeriodFact>,
    coverage: CoverageSummary,
    asOfDate: LocalDate
) -> AttendanceSummary

ScheduleResolver.resolve(
    semester,
    scheduleVersions,
    dateOverrides,
    date
) -> ResolvedDay
```

`PeriodFact` carries identity, date, course, countability, suppression/cancellation, and outcome. `CoverageSummary` includes unrecorded past periods and affected dates, including unresolved dates never opened.

`AttendanceSummary` returns:

- `N`, `P`, `DA`, `DP`, dated rejected DL;
- Primary credited `A`, confirmed credited `C`;
- Exact ratio operands;
- Threshold result;
- Primary safe absent periods and recovery periods;
- Optional confirmed comparison;
- `hasPendingCredit`, incomplete counts, and no-data state.

Rules from §6:

- `N = N0 + eligible recorded conducted periods`.
- `A = P + DA + DP`; `C = P + DA`.
- Meets threshold iff `10A ≥ 9N`.
- Safe skips: `max(0, floor((10A − 9N) / 9))`.
- Recovery: `max(0, 9N − 10A)`.
- For `N=0`, return a dedicated no-data state, with percentage and allowance unavailable.
- Use checked `Long` arithmetic and validate aggregate bounds before multiplying; reject overflowing imports rather than wrapping.
- Evaluate `max(0, …)` before nonnegative integer division so negative truncation cannot create a boundary error.
- Round only display text. If rounding would display 90% while the exact result is below 90%, show `<90%` with the exact counts.
- Mark primary figures provisional whenever pending DL contributes. Incomplete-data warnings are independent and can coexist.

### Backup, restore, and migrations

Use a **versioned JSON backup**, with format version, creation metadata, record collections, and all restorable settings. Include IDs, schedule versions, snapshots, extras, suppressed records, opening balances, DL associations, and retained change history. Exclude derived totals, temporary files, OS permissions, and transient UI state.

Restore sequence:

1. Read through SAF into private staging storage with bounded parsing.
2. Validate format version, numeric bounds, duplicate IDs, relationships, ownership, dates, slot uniqueness, opening cutoffs, and outcome/DL consistency.
3. Reject unsupported versions, unknown required enum values, corruption, or impossible relationships.
4. Preview semester/course/record counts and state clearly: **“Replace all current data.”**
5. Offer export of the current dataset before confirmation.
6. After confirmation, replace all authoritative tables and settings in one transaction.
7. On success, invalidate navigation/undo tokens and reconcile reminder work from committed settings.
8. On failure or process death before commit, retain the old dataset. After commit, retain the complete new dataset.

Room and restorable settings share the same transaction; no cross-store atomicity claim is needed. DataStore contains no authoritative restored state.

Export reads a consistent transactional snapshot. An interrupted provider write may leave an incomplete external file; report failure, do not mark it as a successful backup, and never alter live data.

Use explicit migration paths, committed Room schema exports, and migration tests. Never enable destructive migration fallback. Backup format versioning is independent of database versioning.

Disable automatic cloud backup with manifest configuration and appropriate legacy/current extraction exclusions. Also exclude device-transfer copies where supported, consistent with manual-only portability; verify actual OEM behaviour on hardware. Android documents that backup and transfer behaviour can differ by platform/device. [Android backup guidance](https://developer.android.com/identity/data/autobackup).

---

## C. Product structure and interaction design

### Navigation and visual direction

Four bottom-navigation destinations:

1. **Today** — date, six slots where applicable, lab groups, completion, Mark All Present.
2. **Courses** — per-course percentages, safe periods, recovery, and detail.
3. **History** — calendar/list, incomplete-date filters, corrections.
4. **Duty Leave** — pending requests first, request details, approval controls.

The top app bar provides semester switching and access to **Timetable**, **Semester/Courses**, and **Settings & Backup**. Use a navigation rail on wider layouts.

Visual direction:

- Neutral foundations exactly as specification §7.1: light background `#F7F8FA`/white surface; dark `#101113`/`#191B1F`; blue `#2457D6`/`#8AACFF` for actions, selection and focus. No purple/lavender or dynamic wallpaper overrides. Stronger accessible control outlines are distinct from subtle dividers.
- Compact system typography, clear numeric hierarchy, deliberate spacing, minimal cards and neutral surfaces. Avoid oversized headings and tinted panels; status colors require meaningful text/icons.
- An 8 dp spacing rhythm, 16–24 dp page margins, and approximately 16 dp card corners.
- Minimum 48 dp targets; icon-plus-text status cues.
- Dedicated light/dark palettes, with stable semantic colors rather than relying on color alone.
- Short 150–220 ms transitions where useful; respect system animation scaling and reduced-motion behaviour.
- Haptics optional, subtle, and disabled when the preference is off.

Required accessibility checks include TalkBack labels/order, keyboard/switch focus, large text without clipped controls, contrast, and understandable announcements after bulk changes.

### Statistics without clutter

Each course card presents:

- Large primary percentage.
- “Can miss **2 periods**” or “Attend next **10 periods**.”
- A **Provisional · 2 pending DL** chip when relevant.
- An independent **Incomplete · 3 missing periods** warning linking to repair.

The course detail expands physical attendance, approved/pending/rejected DL, denominator, opening balance, and the secondary confirmed comparison. Never translate a period allowance into “days.”

### Key journeys

| Journey | Shortest intended flow |
|---|---|
| First launch | Semester name/start date → course entry → five-day timetable → review → Today. Setup is resumable; mark the semester done manually when finished. |
| Mid-semester onboarding | Choose first logging date → enter four counts per course → validate immediately → optional per-course cutoff adjustment → preview totals and coverage → timetable. |
| Timetable creation | Five weekday tabs, six slots each; select course, fill consecutive slots, or copy an existing weekday’s assignments. Display Friday’s own times. |
| Mark All Present | One tap from Today → eligible unrecorded periods update atomically → “4 periods marked present” with Undo. |
| Three-period lab | One grouped card showing all three period statuses → one-tap Present/Absent/DL when unrecorded. |
| Change one lab period | Expand group → tap the specific period’s status → choose correction → confirm only if replacing a recorded result → Undo available. |
| Apply DL | Select one period, lab, or whole day → default Pending → preview existing-result conflicts if any → save one request covering selected periods. |
| Resolve DL | Open pending request → approve/reject all or selected periods → affected course totals update immediately; partial results show Mixed. |
| Holiday | Day menu → Holiday. If records exist, show exact affected counts and obtain confirmation; otherwise apply immediately. “Restore classes” reverses it. |
| Copied Saturday | History → Saturday → Copy weekday → preview source version, courses, and times → create counted special day. |
| Extra class | Day’s Extras section → course/time → non-counting by default. Explicit “Counts toward attendance” choice explains makeup behaviour. |
| Correct the past | History date → period → correction with impact preview where it changes stored results. |
| Repair incomplete history | Tap warning → filtered missing dates → open earliest unresolved day → record known outcomes or mark not conducted. Never infer absence. |
| Restore | Settings → Restore → picker → validation → replacement preview → explicit confirmation → complete dataset or unchanged original. |

**Required for first release:** these journeys, accessible themes, clear feedback, reliable empty/error states, and restrained transitions.

**Optional:** on-device reminders, additional motion refinement, and haptics.

**Future only:** schedule-based “what if” forecasts or widgets. They must not delay the required product or replace period-based allowances.

---

## D. Phased execution roadmap

Phase 0 is this read-only discovery and planning work. Its remaining gate is owner approval and resolution of execution prerequisites.

The sequence moves the engine and storage earlier than the suggested roadmap so no UI is built around temporary or incorrect totals. Basic insights arrive with logging; richer breakdowns arrive with DL.

### Verification commands for the implementation run

These are planned commands, **not commands executed in this run**:

- **U:** `./gradlew :domain:test :app:testDebugUnitTest`
- **Q:** `./gradlew :app:lintDebug :app:assembleDebug`
- **I:** `./gradlew :app:connectedDebugAndroidTest`
- **R:** `./gradlew :app:lintRelease :app:assembleRelease`

I is optional when an emulator or explicitly authorized device is available; no connected phone is required. Run I with the checked-in `android.injected.androidTest.leaveApksInstalledAfterRun=true` property. Never uninstall or clear personal app data; fixtures use in-memory or dedicated test databases. Verify package presence and unchanged logical production data before/after supported device runs (README workflow). Do not enable orchestrator clear-data mode. Stop and use a disposable emulator if a runner cannot preserve data. Add a benchmark target in Phase 7 only when the performance journeys are stable. A release APK remains local; R does not authorize publishing.

### Phase 1 — Trustworthy opening-balance vertical slice

**Objective/dependencies:** Establish a runnable, persistent first feature, not an empty shell. Requires approval, writable Git metadata, and a compatible Android toolchain.

**Tasks/components:**

- Initialize Git with appropriate ignores; preserve the canonical spec.
- Establish `:domain` and `:app`, wrapper/version catalog, Room schema exports, test harness, and local CI workflow.
- Implement the pure engine, all status eligibility rules, overflow validation, and §6 examples.
- Add semester/course/opening-balance persistence and repository transactions.
- Build a minimal setup flow that creates one semester/course, enters balances, and opens a real course summary.
- Ask only for the semester start date. Add explicit done/reopen controls and preserve existing development data while migrating away from the earlier end-date column.
- Establish Material 3 themes, navigation frame, error presentation, and backup opt-out configuration.
- Complete a design pass on setup and summary, loading/empty/error/saving states: compact hierarchy, neutral surfaces, clear labels, keyboard/focus/scroll handling, minimum 48 dp targets, and readable 200% text.
- Apply the §7.1 exact foundations across Compose, launch theme and icon; choose accessible foregrounds and verify actual component contrast in both themes without dynamic overrides.
- Retain safe instrumentation configuration and fixture isolation; verify installation/data retention if a personal-device run is requested.
- Review available local previews/screenshots for light/dark and large-text usability; device review is optional. Address known interaction defects when working on affected screens.

**Demonstrable result:** Enter `N=40, P=38`; see 95% and 2 safe periods; restart and see identical values.

**Acceptance:**

- Zero data shows “No attendance recorded.”
- Invalid opening totals cannot be saved.
- Pending balance credit changes the primary figure and provisional label.
- Exactly 90% meets the threshold.
- Persistence survives process restart.
- No Internet permission; no destructive migration fallback.
- Existing Phase 1 screens satisfy the visual/interaction requirements through available local review; phone review is optional.
- Component text/control contrast passes in both themes; no purple/lavender surfaces or dynamic colors.
- If instrumentation is run, it leaves the app installed and production data unchanged; fixture cleanup cannot touch personal storage.

**Tests:** Focused U and Q; optional I for persistence, invalid setup, theme/large-text smoke checks. Engine tests already cover future exclusions, Friday units, labs, DL transitions, and all §6 formula examples.

**Commit boundaries:** repository/build harness → pure engine/tests → Room balance persistence/tests → balance setup and themed summary → CI/privacy configuration.

**Exit gate:** Working balance-to-summary journey, green applicable checks with saved evidence, available local design/large-text/keyboard review, with device evidence optional, reviewable Git history, and resolved toolchain/Git blockers. Reopen Phase 1 if the owner rejects the visual result; Phase 7 is not a deferral of these existing screens.

### Phase 2 — Complete onboarding, timetable, and everyday logging

**Objective/dependency:** Extend Phase 1 into a usable regular college day.

**Tasks/components:**

- Complete multi-course setup, course editing, semester start dates, manual completion/archive browsing, and resumable onboarding.
- Implement schedule versions, six-slot weekday editor, exact Friday profile, day resolver, and idempotent materialization.
- Add period Present/Absent actions, Mark All Present, basic course summaries, progress, and transactional Undo.
- Protect cutoff coverage, existing outcomes, and course ownership.

**Demonstrable result:** Create a week, open Today, mark eligible periods, and see per-course totals persist.

**Acceptance:**

- Standard weekdays show six slots, including empty slots; ordinary weekends generate no obligations.
- Friday timings match §2.2 and retain unit weight.
- Repeated generation and repeated Mark All Present do not duplicate or overwrite.
- Future dates cannot be logged.
- Opening balances contribute once; pre-cutoff dates do not produce missing warnings.
- Archived semesters remain browsable without losing data.

**Tests:** Focused U/Q, optional I, for onboarding, generator idempotency/concurrency, dates and cutoffs, Mark All Present exclusions, restart persistence, and active-semester switching.

**Commits:** onboarding/course management → versioned timetable → generator → day logging/Undo → integration coverage.

**Exit gate:** Fresh-semester and mid-semester users can complete a normal week without incorrect totals or duplicate sessions.

### Phase 3 — Labs, historical repair, and schedule conflicts

**Objective/dependency:** Make partial attendance and corrections safe; depends on Phase 2 identities and transactions.

**Tasks/components:**

- Add grouped consecutive lab cards and independent period controls.
- Add history calendar/list, missing-date filters, and course-linked repair.
- Implement individual cancellation/reinstatement.
- Add effective-date weekly revisions and date-specific substitutions.
- Add conflict previews and guarded Undo.

**Demonstrable result:** Mark a lab present, change one period to absent, repair a missed date, and revise next week without rewriting prior records.

**Acceptance:**

- Two present plus one absent gives `N=3, A=2`.
- Nonconsecutive lab periods do not become one misleading group.
- Cancelling a logged period removes its contribution without deleting its outcome.
- Missing dates appear even if never opened.
- Template changes preserve recorded outcomes and explicit overrides.
- Stale Undo cannot overwrite a newer correction.

**Tests:** Focused U/Q, optional I, for partial labs, grouped-action conflicts, missing-history discovery, effective dates, recorded-slot protection, cancellation restoration, and stale Undo.

**Commits:** lab interaction → history/completeness → revisions/conflict handling → cancellation/Undo hardening.

**Exit gate:** Every historical edit has predictable effects, retained prior information, and a tested correction path.

### Phase 4 — Duty leave and complete course insights

**Objective/dependency:** Add full DL workflows atop stable session identities and corrections.

**Tasks/components:**

- Add period/selection/lab/full-day DL creation.
- Add request list/detail, bulk and selected-period approval/rejection, mixed-status requests, and overlap handling.
- Complete per-course breakdowns and provisional primary allowances.
- Add semester DL metrics with explicit opening-history limitations.
- Add opening pending-DL resolution controls.

**Demonstrable result:** Apply pending DL across courses, approve part, reject part, and see each course and request update.

**Acceptance:**

- Pending contributes to both primary percentage and safe skips.
- Approval preserves primary credit and increases confirmed credit.
- Rejection reduces credit without changing conducted count.
- Corrected-away periods are not resurrected by later request updates.
- Multiple periods on one date count as one distinct DL date.
- Opening DL periods do not invent historical requests/dates.
- Pending and incomplete warnings can appear together.

**Tests:** Focused U/Q, optional I, for all transitions, overlaps, cross-course requests, mixed status, baseline adjustments, metrics, and immediate reactive recalculation.

**Commits:** DL data/commands → creation workflows → review/transitions → complete insights/metrics.

**Exit gate:** No duplicate credit, no misleading confirmed main figure, and complete DL lifecycle coverage.

### Phase 5 — Holidays, copied days, and supplemental classes

**Objective/dependency:** Complete institutional schedule exceptions; depends on phases 2–4.

**Tasks/components:**

- Add reversible whole-date holiday suppression.
- Add copy-weekday preview/application with source times and conflict handling.
- Add dated substitutions and supplemental classes with explicit countability.
- Distinguish exact copied schedules, mixed custom days, and extras in history.

**Demonstrable result:** Copy Friday onto Saturday, log its counted periods, add a non-counting extra, then apply and reverse a holiday.

**Acceptance:**

- Copying twice is idempotent.
- Copied Friday times are retained on Saturday.
- Recorded conflicts are preserved unless explicitly corrected.
- Default extras affect neither statistics nor missing-data counts.
- Counted makeup requires an explicit decision.
- Holiday suppresses standard periods and extras; reversal restores every prior state.
- Changing countability on an existing logged extra previews its impact.

**Tests:** Focused U/Q, optional I, for repeated copy, mixed conflict resolutions, source-version snapshots, extras, holiday with DL/partial labs, and exact restoration.

**Commits:** holidays → copied schedules/conflicts → extras/countability → end-to-end exception tests.

**Exit gate:** All §2.3 behaviours work without duplicates, denominator errors, or lost outcomes.

### Phase 6 — Portable backup and upgrade resilience

**Objective/dependency:** Protect the complete dataset from phases 1–5.

**Tasks/components:**

- Implement versioned export, validation, preview, replace confirmation, and atomic restore.
- Include all authoritative settings and suppressed/history records.
- Add migration fixtures and failure injection.
- Complete theme/haptics/settings flows and privacy text.

**Demonstrable result:** Export a mixed semester, replace another installation’s dataset, and recover the same logical records and calculations.

**Acceptance:**

- Round trip preserves IDs, links, source schedules, cutoffs, outcomes, settings, and calculated results.
- Invalid/incompatible input changes nothing.
- Process termination before commit leaves the old dataset; after commit leaves the new dataset.
- Disk-full/provider failures produce useful errors without altering live data.
- Restored settings and attendance cannot disagree due to a partial restore.
- A migration failure does not trigger database deletion.

**Tests:** Focused U/Q, optional I, for backup validation, duplicate/orphan/overflow cases, transaction rollback, settings atomicity, process interruption, and migration preservation.

**Commits:** backup DTO/validation → export → restore transaction → confirmation/errors → migrations/failure tests.

**Exit gate:** Full backup and replace/restore pass correctness and fault-injection tests.

**Feature-complete first-release candidate:** all mandatory product capabilities now exist. It still requires phases 7–8 quality gates before release readiness.

### Phase 7 — Accessibility, performance, and optional reminders

**Objective/dependency:** Reach measurable product quality on the complete flows.

**Tasks/components:**

- Audit/refine the complete later-screen set: typography, focus, TalkBack, dark mode, large text, empty/error states, and reduced motion. Phase 1 already gates its existing screens; repeat those checks for regression rather than deferring their design here.
- Refine feedback and transitions without changing attendance rules.
- Measure startup, scrolling, logging latency, and long-history behaviour.
- Optionally add local reminders, disabled by default, requesting notification permission only on enablement.
- Reminders inspect unresolved eligible records, skip holidays/completed days, and never mark attendance.

**Acceptance:**

- Core workflows remain usable at 200% font scale and with TalkBack.
- No essential control relies on color or animation.
- On the chosen reference device: target p95 action feedback ≤100 ms and durable single-day updates ≤250 ms; slow writes show progress.
- Target cold startup ≤2 seconds and <5% janky frames in scripted daily navigation; record hardware, build type, and measurements.
- Reminders are described as approximate, handle denial/reboot/time changes, and generate no duplicates.

WorkManager timing is subject to system scheduling; it must not be presented as an exact alarm. [Work scheduling documentation](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

**Tests:** Relevant U/Q/R and local accessibility inspection; optional I and hardware benchmarks. Focused reminder tests if implemented.

**Commits:** accessibility corrections → measured performance fixes → motion refinement → optional reminders/tests.

**Exit gate:** Required accessibility/quality defects resolved through available local checks; record performance measurements when available, otherwise defer them explicitly. Optional reminders may be omitted rather than delaying the mandatory release.

### Phase 8 — Release validation and public-project readiness

**Objective/dependency:** Validate the release candidate with available local checks and prepare a professional public project; hardware validation is optional.

**Tasks/components:**

- Optionally run the physical-device checklist below if hardware becomes available; document deferrals without blocking completion.
- Verify upgrade preservation with isolated migration fixtures; an actual device upgrade is optional.
- Verify merged manifest, backup rules, offline behaviour, release shrinking, and SAF flows.
- Complete README, architecture/rules notes, build/test instructions, privacy/backup documentation, contribution guidance, and synthetic-data screenshots.
- Add the owner-approved license; review tracked files for secrets/signing material.
- Produce a local release artifact and execution report.

**Acceptance:**

- Relevant local automated checks have recorded results; optional device journeys are recorded as executed or deferred.
- No unresolved critical correctness or data-loss defect.
- Git history contains focused working increments.
- Documentation reproduces setup and tests on a clean environment.
- Hosted CI status is reported honestly: workflow prepared versus actually run.
- No push, public repository creation, signing-key publication, or release upload without separate authorization.

**Tests:** Relevant U/Q/R for release changes; optional I and physical upgrade, offline, backup, accessibility, and reminder checks when hardware is available.

**Commits:** device-discovered fixes → documentation/screenshots → approved license → release-readiness evidence.

**Exit gate:** §§10 and 13 satisfied, with residual risks documented and publication still explicitly gated.

---

## E. Test strategy and traceability

### Requirements → phase → verification

| Spec section | Primary phases | Verification |
|---|---|---|
| §1 Vision and daily trust | 1–8 | Offline onboarding-to-logging journeys, per-course independence, measured interaction quality. |
| §2.1 Period units, labs, threshold | 1–3 | Formula tests, Friday weight, three independent lab outcomes, theory/lab separation. |
| §2.2 Clock schedules | 2, 5 | Exact normal/Friday schedule fixtures and copied-Friday UI checks. |
| §2.3 Copies, extras, holidays | 3, 5 | Idempotency, conflict handling, counted makeup, default extra exclusion, reversal. |
| §3 Semester/course/opening setup | 1–2, 4 | Validation, cutoffs, editing safeguards, archive/active state, baseline DL resolution. |
| §4.1 Status model | 1–5 | Eligibility truth table, future exclusions, one-outcome uniqueness. |
| §4.2 Logging and history | 2–5 | Mark All Present, lab actions, corrections, Undo, unopened missing dates. |
| §4.3 Revision/history integrity | 2–3, 5 | Effective dates, override precedence, timezone stability, protected recorded slots. |
| §5 Duty leave | 1, 4–6 | Transitions, overlaps, primary provisional allowances, request/date/period metrics. |
| §6 Exact calculations | 1 onward | Every required example, exhaustive bounded inputs, boundary/maximality properties, no-data state. |
| §§7–7.1 Experience/accessibility/reminders | 1–8 | Available local Phase 1 design review (phone optional), exact neutral tokens, actual component contrast, both themes/200% text/IME/48 dp/scroll; later full TalkBack, reduced motion and optional reminders. |
| §8 Architecture/invariants | 1–6 | Pure-domain isolation, schema constraints, ownership, atomic commands, migration tests. |
| §9 Privacy/backups | 1, 6–8 | Manifest inspection, no-network operation, SAF round trip, validation/rollback, settings atomicity. |
| §§10.1–10.1.1 Testing/safety | Every phase | Executed results; retention property and actual package/data survival; isolated fixtures, no personal-data clears/uninstalls; optional physical design evidence when available. |
| §10.2 Git/public project | 1, 8 | Commit review, ignores, secrets review, reproducible README, CI, license/publication approvals. |
| §11 Non-goals | Every phase | Dependency/permission/scope review; no cloud, accounts, scraping, telemetry, or duration weights. |
| §12 Planning/milestones | 0–8 | This plan, approval gate, phase acceptance evidence, no planning-run mutations. |
| §13 Definition of done | 6–8 | Release acceptance checklist spanning all preceding tests; explicit remaining-risk report. |

### Acceptance test matrix

| Area | Cases and expected result |
|---|---|
| Required math | `(40,38)` → 95%, 2 skips; `(10,9)` → threshold met, 0 skips; `(10,8)` → 10 recovery periods. |
| Exact boundaries | Test just below/at/above 90%; rounding never controls eligibility. `N=0` yields no percentage/allowance. |
| Mathematical properties | For eligible totals, `0≤C≤A≤N`; permitted skips preserve threshold, one extra exceeds it; recovery is minimal; adding a present period cannot lower the ratio. |
| Opening balances | Nonnegative and sum validation; baseline plus dated outcomes once; pre-cutoff dates excluded; post-cutoff missing dates warned; invalid cutoff edits rejected. |
| Lab periods | All present, all absent, 2/1 mixed, partial DL, cancelled middle period, group correction and Undo. |
| Friday | Each of six slots contributes exactly one unit despite unequal durations. |
| DL | Pending→approved leaves primary credit unchanged; pending→rejected removes one credit only; rejection correction restores appropriate credit; mixed events and overlapping selections do not double-count. |
| Holiday/cancellation | Suppress previously logged mixed outcomes; no denominator contribution; restore exact prior outcomes and relationships. |
| Copied days | Saturday copy counts when recorded; repeat is a no-op; changing source templates later leaves snapshot intact; conflicts never silently overwrite. |
| Extras | Default extra contributes nothing, including to incompleteness; explicit counted makeup contributes once; countability corrections are deliberate. |
| Incomplete records | Unrecorded adds neither numerator nor denominator; unopened historical dates still warn; holidays, empty slots, baseline-covered dates, and ordinary weekends do not warn. |
| Bulk/Undo | Mark All Present preserves existing absent/DL/present/cancelled outcomes; rollback on failure; stale Undo refuses safely. |
| History | Future-effective edits leave past untouched; course substitution requires explicit correction; timezone change leaves identities/dates unchanged. |
| Restore | Exact logical round trip; duplicate IDs, orphan FKs, invalid ownership, overlapping coverage, bad counts, unsupported version, and corrupt file leave live data unchanged. |
| Failure/upgrade | Low storage, export provider failure, interruption around restore commit, migration failure, app restart, and concurrent stale edits preserve integrity. |

Use an independent rational-arithmetic oracle for bounded generated math cases; avoid tests that merely repeat the production formula verbatim. Instrumented tests must prove actual SQLite constraints and transactions, not only mocked repository behaviour.

### Automation, emulator, and physical-device responsibilities

**Automated locally/CI:**

- Domain and repository unit tests.
- Lint, clean debug/release builds, dependency/manifest checks.
- Room constraints, migrations, rollback, serialization and restore validation.
- Compose functional journeys.
- Emulator coverage at API 26 and the chosen current target API.
- Failure injection and large synthetic semesters.

**Manual/emulator checks before hardware is available:**

- Empty/error states, text scale, theme screenshots, navigation/back behaviour.
- TalkBack structure and labels.
- Airplane-mode workflows, process death, rotation, timezone changes.
- Import cancellation and corrupt-file messaging.

**Optional hardware follow-up (not a phase completion gate):**

- Real touch ergonomics, haptic quality, perceived speed, and outdoor readability.
- Trustworthy cold-start, frame, storage-latency, and thermal measurements.
- OEM SAF providers, actual low-storage behaviour, and local export/share interactions.
- OEM notification permission, battery restrictions, Doze, reboot, and reminder behaviour.
- Actual upgrade persistence and device backup/transfer behaviour where testable.
- Final TalkBack and large-text daily journeys on hardware.

Local checks do not establish hardware-specific behavior. Record unavailable hardware checks as deferred, without blocking phase completion or claiming they passed. No build or test success is claimed in this planning run.

---

## F. Risks, milestones, and approval gate

### Ranked risks and mitigations

| Rank | Risk | Mitigation |
|---|---|---|
| 1 | Incorrect denominator or double-counted baseline | Central eligibility engine, explicit course cutoff, one outcome per session, independent mathematical oracle. |
| 2 | Restore/holiday/edit destroys history | Transactional replacement, retained suppressed records, guarded Undo, conflict previews, process-death tests. |
| 3 | Recurring revisions silently rewrite records | Immutable versions, stable dated identities, explicit overrides, recorded-slot protection, historical fixtures. |
| 4 | Provisional allowances look certain | Persistent pending-DL labels on primary numbers; secondary confirmed comparison; separate incomplete warning. |
| 5 | Missing history disappears from statistics | Completeness resolver covers all expected dates, not only materialized/viewed screens. |
| 6 | Complex corrections undermine one-tap UX | Optimize unrecorded daily actions; reserve confirmations for replacements; exact affected-count previews. |
| 7 | Toolchain/Git environment delays delivery | Resolve before Phase 1 exit; freeze compatible stable dependencies and document provisioning. |
| 8 | “Polish later” becomes a redesign | Tokens, accessible layouts, dark mode, and realistic states in Phase 1; measured refinement in Phase 7. |
| 9 | Hardware/OS differences invalidate assumptions | Minimum/current API emulator coverage plus physical OEM testing; document reminder and transfer limitations. |
| 10 | Public release exposes secrets or lacks permissions | Ignore/signing review, synthetic screenshots, approved license, separate push/publication authorization. |

### Trade-offs and recommended choices

| Trade-off | Recommendation |
|---|---|
| Single module versus many feature modules | Two modules: JVM domain plus Android app. Add modules only for demonstrated build/test isolation needs. |
| Lazy versus upfront dated generation | Materialize elapsed active dates and requested dates using one completeness resolver; no fixed semester end date or unbounded future generation. |
| Entirely immutable ledger versus editable rows | Current-state tables plus transactional change history. Full event sourcing adds unnecessary reconstruction/migration complexity. |
| Room versus DataStore for restorable settings | Room for authoritative/restorable settings; DataStore only for disposable local UI state. |
| Raw database backup versus logical format | Versioned JSON for portability, validation, and schema evolution. |
| Exact reminders versus approximate background work | Approximate WorkManager reminders, optional and clearly described. |
| Broad retrospective weekly edits versus date corrections | Future-effective weekly revisions; explicit date-specific historical corrections. |

### First implementation milestone

**Milestone: a persistent, trustworthy opening-balance course summary.**

Exact work:

1. Resolve writable Git metadata and establish a verified stable toolchain.
2. Initialize the project and Git ignores without altering the canonical spec.
3. Create the two modules, dependency pins, test harness, and CI configuration.
4. Implement the pure engine and every §6 required mathematical example.
5. Implement semester/course/opening-balance Room storage and validation.
6. Deliver minimal setup and a themed course summary with pending/no-data states.
7. Verify restart persistence, execute U/Q and applicable I tests, and record results.
8. Make focused commits representing those working increments.

**Exit:** `40 conducted / 38 present` persists as 95% with 2 safe absent periods; exact 90%, zero-data, invalid-balance, and pending-credit cases behave correctly; applicable checks have real execution evidence; Git history is usable.

### Owner decisions and approval gate

There are **no unresolved core attendance-policy questions** after your three selections.

Before Phase 1 execution:

- Explicit approval of this plan is required.
- Writable Git metadata and Android build tooling must be available.
- The proposed application identity can be changed before distribution; license selection and publication authorization remain later release gates.

**Planning stops here. Please approve Phase 1 in a separate implementation run after switching out of Plan mode. No implementation has begun.**
