# Implementation Prompts — UI/UX Audit

One self-contained prompt per phase of `UI_UX_AUDIT_REPORT.md` (§10 roadmap).
Each prompt is complete on its own — paste it verbatim as the first message of
a fresh agent session. Run the phases in order; each assumes the previous one
is committed and green.

Before starting a new phase, verify the previous one landed:
`./gradlew :app:testDebugUnitTest :app:lintDebug` with
`GRADLE_USER_HOME=$PWD/.toolchain/gradle-home`.

---

## Prompt — Phase 0: Labels and wording

```
UI_UX_AUDIT_REPORT.md in this repo is a UI/UX audit of this Android
(Jetpack Compose + Kotlin) attendance app. Every issue has a location,
evidence with file/line references, a recommended fix, priority and
complexity. PROJECT_SPEC.md holds the authoritative product rules and
DESIGN.md the design rationale — read all three before writing code.

Implement Phase 0 of the roadmap (§10): the "Labels and wording" quick
wins — items 1–5, 7, 11–20 and 22 in §8. That covers issues A1, A3, B1,
B5, B8, C3, C8, D1, D2, D5, E2, F3, F5, G1 (labels only), G4, G5 and H1.

Specifically:
- Show labels on all four/five bottom tabs at every font scale (A1).
- Replace unlabeled icon-only record actions with labelled controls: the
  period sheet action row in DayScreen (B1), approve/reject on Duty Leave
  cards (E2), and the semester-card icons in Settings (G1 — text buttons
  "View", "Finish semester", "Reopen"; the dedicated Semester screen is a
  later phase).
- Give "Not recorded", "Not conducted", "Not counted" and the duty-leave
  states distinct text chips (B5).
- Replace the unlabeled calendar icon on Courses with a labelled
  "Timetable" row (A3).
- Rename day-menu items per §7.6 and add the weekend empty-state action
  (B8).
- Truncate displayed percentages instead of rounding so a failing course
  never shows 90.0% (C3 — display only, do not touch the decision math).
- Numeric keyboards on all count fields (C8, F3); validate name/counts
  inside the setup course dialog (F3).
- Restrict the timetable effective-date picker to today onward, add the
  helper text (D1), confirm before discarding timetable edits (D2), and
  open the editor on today's weekday (D5).
- Dated backup file name, plain "Backup saved" message, formatted restore
  date, labelled theme segmented control (G4, G5).
- Fix the PeriodProgress accessibility description for setup steps (F5).
- Apply the §7.6 glossary and string table verbatim across all user-facing
  text (H1). Move edited strings to strings.xml with plurals where
  practical.

Hard constraints:
- UI and copy only. Do not change attendance calculation rules, the
  :domain module, or repository behavior.
- Keep the spec §7.1 palette and Manrope type system.
- Every touch target ≥48dp; every action icon needs a contentDescription.
- No functionality may be removed.
- Update tests that depend on labels or test tags (V1UiTest,
  AppJourneyDeviceTest, RedesignDeviceTest) so they stay green.

Process:
- Commit in small logical groups, each message naming the issue ID, e.g.
  "fix(ui): label period sheet actions (B1)".
- When done, run ./gradlew :app:testDebugUnitTest :app:lintDebug with
  GRADLE_USER_HOME=$PWD/.toolchain/gradle-home and fix every failure.
- Report which quick-win items were done, skipped or blocked, and what
  needs visual verification on a device.
```

---

## Prompt — Phase 1: Trust in the numbers

```
UI_UX_AUDIT_REPORT.md in this repo is a UI/UX audit of this Android
(Jetpack Compose + Kotlin) attendance app, and Phase 0 (labels and
wording) is already implemented and committed. Every issue has a
location, evidence, recommended fix, priority and complexity.
PROJECT_SPEC.md holds the authoritative product rules and DESIGN.md the
design rationale — read them before writing code.

Implement Phase 1 of the roadmap (§10): "Trust in the numbers" —
quick-win items 8, 9, 10 and 21 in §8, plus issues C1, C2, C4, C10, E1,
E3 and A7.

Specifically:
- C1: when earlier days have unmarked periods, show a strip on Today —
  "3 earlier days not marked · Catch up" — that opens the oldest
  incomplete day. On Courses, replace the "Past records missing" text
  with a tappable line opening that course's unmarked dates, and add a
  header note when anything is unmarked. For now, "Catch up" may navigate
  to the oldest unmarked day through the existing day route; the stepping
  flow comes in Phase 2.
- C2: under any percentage that includes pending duty leave, show the
  confirmed-only figure ("90.5% if leave is rejected") using the
  existing confirmedCredited value, and qualify the allowance line.
- C4: three standing tiers — below minimum (error), at the limit (amber,
  no safe skips), safe (neutral) — each with icon and text, plus a header
  summary ("1 below 90% · 1 at the limit"). Add the amber "caution" role
  to the theme per §7.3 and verify 4.5:1 text contrast in both themes.
- C10: an info action beside "90% minimum" on Courses opening a short
  sheet explaining what counts, what doesn't, and that "can miss" is in
  periods; link the same content from Settings/About.
- E1: calendar legend plus shape-coded marks — filled dot (marked),
  hollow ring or coloured date (not marked), "H"/strikethrough (holiday),
  corner mark (different timetable). The holiday cell fill must reach
  ~3:1 against the card or carry a second cue.
- E3: show all incomplete days (remove the 12-item cap), newest first,
  each as "Mon 9 Nov · 4 of 6 not marked".
- A7: while viewing a non-active semester, show a persistent banner on
  every tab — "Viewing {name} · Back to {active}" — with a one-tap exit.

Hard constraints:
- Do not change attendance calculation rules, the :domain module, or
  repository behavior. Use values the engine already exposes
  (confirmedCredited, missingPastPeriods, safeSkips, recovery,
  observeIncompleteDates) — do not recompute.
- Keep the spec §7.1 palette apart from the added caution role.
- ≥48dp touch targets; contentDescription on every action icon.
- No functionality removed; update affected tests to stay green.

Process:
- Commit in small logical groups, each message naming the issue ID.
- When done, run ./gradlew :app:testDebugUnitTest :app:lintDebug with
  GRADLE_USER_HOME=$PWD/.toolchain/gradle-home and fix every failure.
- Report done/skipped/blocked per issue ID and what needs device
  verification.
```

---

## Prompt — Phase 2: Daily recording

```
UI_UX_AUDIT_REPORT.md in this repo is a UI/UX audit of this Android
(Jetpack Compose + Kotlin) attendance app. Phases 0 (labels/wording) and
1 (trust in the numbers) are already implemented and committed — reuse
their components and glossary (§7.6). PROJECT_SPEC.md holds the
authoritative product rules and DESIGN.md the design rationale.

Implement Phase 2 of the roadmap (§10): "Daily recording" — redesigns R1
and R2 in §9, plus issues B4, B7, B10, and optionally B11.

Specifically:
- R1: build a single persistent two-segment mark control (Present |
  Absent), each segment ≥56dp wide inside a 48dp touch area, showing the
  current outcome as the selected segment. Use it on every writable,
  attendance-counting period row so a correction is one tap with no
  sheet. Duty-leave states show as their own non-interactive filled
  state. Define an icon-only variant for narrow screens and large font
  scales, with full contentDescriptions and custom accessibility actions
  on the row. The trailing ⋯/row tap opens the labelled action sheet from
  Phase 0 (duty leave, class not held, different course, clear mark).
- B4: use the same control on expanded lab sub-period rows; keep the
  group-level control for "all periods"; show per-slot outcome labels
  (P3 ✓ P4 ✓ P5 ✕); keep the group expanded while the user works.
- R2: make Today a day pager — ‹ date › arrows, a "Today" chip when off
  today, and a date picker on tap — changing the date via the existing
  DayViewModel.setDate() instead of pushing routes. When a day is opened
  from the calendar, push one entry whose arrows replace the date rather
  than stacking. Remove the collapsed-complete state: on completion show
  one compact line ("✓ All 6 marked · 5 present · 1 absent") and keep the
  list visible. Keep holiday collapsing. Wire the Phase 1 "Catch up"
  strip to step through unmarked days with a "Next unmarked day" action
  after each completes.
- B7: with the persistent control in place, stop showing the undo
  snackbar for single taps; keep it (SnackbarDuration.Long) for bulk and
  structural actions, with specific labels.
- B10: rebuild the extra-class dialog — outlined course dropdown, two
  tappable time fields opening a time picker, and helper text when the
  class doesn't count.
- B11 (optional, do last): a subtle per-course standing line under each
  course name on today/future rows ("92.7% · can miss 1" / error-coloured
  "89.2% · attend next 3").

Hard constraints:
- Do not change attendance calculation rules, the :domain module, or
  repository behavior — UI and navigation only.
- Keep the spec §7.1 palette and the caution role added in Phase 1.
- ≥48dp targets; no icon-only actions that change records.
- Removing the collapsed review state departs from PROJECT_SPEC.md §4.2's
  "review action" wording — implement the report's recommendation and
  note it in the commit message.
- Update all tests depending on the today/day routes, mark_all,
  review_attendance, group_* and period_* tags so they stay green.

Process:
- Commit in small logical groups, each message naming the issue ID.
- When done, run ./gradlew :app:testDebugUnitTest :app:lintDebug with
  GRADLE_USER_HOME=$PWD/.toolchain/gradle-home and fix every failure.
- Report done/skipped/blocked per issue ID, the spec-wording deviation,
  and what needs device verification.
```

---

## Prompt — Phase 3: Information architecture

```
UI_UX_AUDIT_REPORT.md in this repo is a UI/UX audit of this Android
(Jetpack Compose + Kotlin) attendance app. Phases 0–2 are already
implemented and committed — reuse their components (MarkControl, labelled
ActionRow pattern, day pager, glossary in §7.6). PROJECT_SPEC.md holds
the authoritative product rules and DESIGN.md the design rationale.

Implement Phase 3 of the roadmap (§10): redesign R3 in §9 — the four-tab
information architecture in §5 — plus issues A2, A6, A8, A9, C5, C7, C9,
E5 and E6.

Specifically:
- R3: reduce the bottom navigation to four always-labelled tabs —
  Today · Courses · Calendar · Settings.
  - Courses gains two labelled rows above the course list: "Timetable ›"
    (with a one-line summary like "Mon–Fri · 28 classes a week") and
    "Duty leave ›" (with the pending count).
  - Add a per-course detail screen reached by tapping a course row:
    percentage with qualifiers, breakdown (credited, attended in person,
    duty leave by status), unmarked dates, leave for this course, and
    labelled actions — Rename, Attendance before tracking, Stop tracking.
    Remove the per-row ⋮ menu and chevron from the list.
  - Move the Duty Leave screen out of the tab bar to that row; add an
    "+ Add duty leave" action (pick a date, default today, then the
    existing period picker) and a "From before you started tracking"
    section for pending opening leave (C9).
  - Rename the History tab and heading to "Calendar" (A8).
  - Top bar: replace the "Ninety" title with the screen's own title and
    keep the semester name as a tappable label that opens a semester
    switcher when more than one exists (A6).
- A2: keep duty leave reachable from all existing entry points — day
  menu, period sheet, lab group — plus the new screen's add action.
- A9: re-tapping the selected tab pops to that tab's root.
- C7: give the add-course dialog the optional "Attendance so far" section
  from the setup dialog, and after saving offer "Put it on the
  timetable?" → Timetable.
- E5: add "Remove request" to a duty-leave card (confirm; periods return
  to unmarked; undoable via the existing batch mechanism), and hide
  empty requests.
- E6: duty-leave summary wording per §7.6 ("3 periods on 1 day · 1
  request"), an always-visible status line instead of the ⓘ toggle,
  period rows that open their day, month navigation bounded at the
  semester start with a "Today" button, and the "before semester start"
  day message.

Hard constraints:
- Every existing capability must remain reachable via a path in §5.3 —
  nothing is removed.
- Do not change calculation rules or repository behavior; the new
  screens consume existing observe* flows.
- Keep the §7.1 palette, Phase 1's caution role, ≥48dp targets, and the
  no-unlabeled-actions rule.
- Update all tests depending on tab routes, tab_leave, history naming
  and course-row tags; update DESIGN.md, PRODUCT.md "Operating Context"
  and README to match the new structure.

Process:
- Commit in small logical groups, each message naming the issue ID.
- When done, run ./gradlew :app:testDebugUnitTest :app:lintDebug with
  GRADLE_USER_HOME=$PWD/.toolchain/gradle-home and fix every failure.
- Report done/skipped/blocked per issue ID and what needs device
  verification.
```

---

## Prompt — Phase 4: Setup, timetable and semesters

```
UI_UX_AUDIT_REPORT.md in this repo is a UI/UX audit of this Android
(Jetpack Compose + Kotlin) attendance app. Phases 0–3 are already
implemented and committed — reuse their components (chip-then-tap
patterns, labelled ActionRows, glossary in §7.6). PROJECT_SPEC.md holds
the authoritative product rules and DESIGN.md the design rationale.

Implement Phase 4 of the roadmap (§10): redesigns R5 and R6 in §9 — setup
and semester management — plus issues D3, D4, C6, F1, F2, F4, G2 and G3.

Specifically:
- F1: when the chosen semester start date is more than a few days in the
  past, ask the mid-semester question from the report ("Enter my
  attendance so far (recommended)" vs "Mark every past day myself") and
  pre-fill the per-course "Attendance so far" section; add the one-line
  intro under the wizard title.
- F2: default the college timezone to the device zone collapsed to one
  line ("Times follow this phone · Change") with a searchable full list.
- F4: warn on Continue with an empty timetable and flag unscheduled
  courses in the review.
- D3: replace per-slot dropdowns with the "arm a course chip, then tap
  periods" editor in both setup step 3 and the timetable screen; add
  "Copy from…" per weekday; keep the dropdown as the accessible fallback.
- D4: call the existing repository method previewTemplateConflicts
  before saving when the effective date is today, and show affected
  periods with "Save anyway / Cancel"; reword the post-save dialog per
  §7.6.
- R6/G1 follow-up/G2: a Semester screen per semester (from Settings)
  with labelled actions — View records, Rename/change start date/zone
  (wire the existing repository.updateSemester, which already guards
  recorded history), Finish semester, Reopen. Show only Current and
  Finished states (G3): when a new semester is created, ask "Finish {old
  name}?"; label any middle-state semester "Paused" with "Make current".
- C6: rename "Retire course" to "Stop tracking this course", reword the
  confirmation per §7.6, and group stopped courses under a collapsed
  "No longer tracked" section.

Hard constraints:
- updateSemester and previewTemplateConflicts already exist in the
  repository — wire them; do not reimplement or weaken their guards.
- Do not change calculation rules or any other repository behavior.
- Do not add semester deletion in this phase.
- Keep the §7.1 palette, ≥48dp targets, and labelled consequential
  actions.
- Update affected tests; update DESIGN.md and PRODUCT.md where the
  flows changed.

Process:
- Commit in small logical groups, each message naming the issue ID.
- When done, run ./gradlew :app:testDebugUnitTest :app:lintDebug with
  GRADLE_USER_HOME=$PWD/.toolchain/gradle-home and fix every failure.
- Report done/skipped/blocked per issue ID and what needs device
  verification.
```

---

## Prompt — Phase 5: Deeper capabilities (repository changes)

```
UI_UX_AUDIT_REPORT.md in this repo is a UI/UX audit of this Android
(Jetpack Compose + Kotlin) attendance app. Phases 0–4 are already
implemented and committed. PROJECT_SPEC.md holds the authoritative
product rules — especially §4 (dated sessions), §5 (duty leave) and
§2.3 (special days) — and DESIGN.md the rationale. This phase touches
repository logic, so write or extend tests for every behavior change;
the in-memory DB pattern in RoomNinetyRepositoryTest is the template.

Implement the Phase 5 items in §10, in this order:

1. E4 — multi-day duty leave requests: add an optional eventId parameter
   to NinetyRepository.applyDutyLeave so a new application can attach to
   an existing pending request instead of creating a new event. UI: in
   the duty-leave dialog add "Part of: New request / {recent pending
   request names}". Preserve the one-credit-per-period rule and the
   conflict/replace flow.

2. B9 — holidays on future dates: relax the same-day restriction in
   setHoliday so a future date can be marked a holiday (records it as
   zero-unit either way), while copied days and extras stay same-day.
   Verify against materialization (ensureDay/ceiling) so future holidays
   do not generate or remove obligations incorrectly, and confirm
   percentages are unchanged for recorded history. Add the "You can
   change this day once it arrives" line for other future-day actions.

3. C6 — resume a stopped course: repository method to clear the retired
   flag and a "Resume tracking" action on the Course screen, after which
   the course can be re-added to timetable slots. Validate that
   validateTemplate now accepts it and that opening balance rules still
   hold.

4. B12 — reminder upgrade: offer the daily reminder on the last setup
   step (default off), and add a notification action "All present" that
   calls markAllPresent for today then updates the notification. Keep
   the permission request behind the enable switch and no network use.

5. B7 (recent changes): a "Recent changes" list in Settings exposing the
   retained undo batches (label + time + Undo) for entries still
   undoable.

6. G4 — last-backup timestamp: persist the time of the last successful
   export in settings, show "Last backup: …" under the backup buttons,
   and a quiet reminder hint after ~30 days.

7. H2 — user-facing exception type: route user-meaningful rule
   violations through a dedicated exception (like BackupException) so
   raw "Check failed."/"Unknown class period." style messages never
   reach the snackbar; everything else shows "Something went wrong.
   Your data is safe."

Hard constraints:
- Every item must preserve attendance calculation results for existing
  data; add regression tests demonstrating that.
- Keep the undo-batch mechanism intact — new mutations record batches.
- Backup format: new persisted fields must be covered by export/restore
  or explicitly documented as device-local (e.g. last-backup timestamp
  may stay device-local).
- Keep the §7.1 palette and labelled-actions rule in any new UI.
- Update PROJECT_SPEC.md only where behavior genuinely changed (e.g.
  future holidays); do not rewrite approved rules silently.

Process:
- One commit per numbered item, message naming the issue ID.
- When done, run ./gradlew :app:testDebugUnitTest :app:lintDebug
  :domain:test with GRADLE_USER_HOME=$PWD/.toolchain/gradle-home and fix
  every failure.
- Report done/skipped/blocked per item and what needs device
  verification.
```

---

## Optional final prompt — Verification pass

```
The UI/UX redesign tracked in UI_UX_AUDIT_REPORT.md is now implemented.
Do a verification pass only — no new features.

- Run ./gradlew :app:testDebugUnitTest :app:lintDebug :domain:test with
  GRADLE_USER_HOME=$PWD/.toolchain/gradle-home and fix failures caused by
  the redesign (do not weaken tests to make them pass).
- Diff every screen against §5.3: confirm each pre-redesign feature is
  still reachable and write down the current path.
- Check the audit's acceptance table (§10): 0 unlabeled record-changing
  icons; correction = 1 tap; yesterday = 2 taps; lab partial = 3 taps
  no modal; colour never the only state signal; no 90.0% for a failing
  course; unmarked past days visible from Today; tab labels at 2.0x.
- Update DESIGN.md, PRODUCT.md, README.md and docs/screenshots notes so
  docs match the shipped structure.
- Write VERIFICATION_SUMMARY.md: per-screen check results, remaining
  gaps, and what still needs a physical device (large text, TalkBack,
  ≥600dp rail, motion).
```
