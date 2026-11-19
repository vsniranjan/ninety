# Ninety

<!-- impeccable:product-schema 1 -->

## Platform

android

## Product Authority

This record summarizes the existing product direction confirmed by the owner during Impeccable init. [PROJECT_SPEC.md](PROJECT_SPEC.md) remains the detailed authority for requirements, attendance policy, non-goals, and acceptance criteria. Consult it before changing policy. [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) records implementation and verification evidence, including its limits; requirements do not establish that a feature has been verified.

## Users

College students managing their own attendance throughout a semester. Students set up their courses and timetable, then record attendance at the end of each college day with minimal effort. Students joining midway through a semester can enter historical opening balances instead of recreating dated classes. Each person's records stay on their own device; users do not share an account or database.

## Product Purpose

Make daily attendance logging fast and forgiving, and help students understand whether each course meets the fixed, inclusive 90% minimum. Show how many additional periods they can miss or must attend to recover, while making provisional credit and incomplete records explicit. Success means trustworthy calculations, little daily effort, and preserved records.

## Positioning

Ninety follows the institution's period-based attendance rules: a short Friday period and a normal period each count as one unit, labs retain separate outcomes for every period, and pending duty leave contributes to the primary attendance and skip figures provisionally. The minimum is fixed per course, rather than a configurable target or a semester-wide average.

## Operating Context

- Native Android, implemented in Kotlin with Jetpack Compose and Material 3. Room stores local records, WorkManager supports optional reminders, and the separate `:domain` module owns calculation and scheduling rules. The app supports Android 8.0 / API 26 and newer.
- Setup creates a semester with a name and start date, courses, a Monday–Friday timetable, and optional opening balances with a first logging date. A semester ends only when explicitly marked done; completed semesters remain browsable and can be reopened.
- Today is the daily entry point. **Mark all present** records eligible unrecorded periods; **Mark remaining present** finishes a partially recorded day without replacing existing outcomes. Completed days show **Attendance complete**, with records available for review and edits.
- The four labelled tabs are **Today**, **Courses**, **Calendar**, and **Settings**. Tap a selected tab to return to its root. Today returns to the current date. The semester label in the top bar opens a switcher when there is more than one semester.
- **Courses** shows each course’s attendance and how many periods can be missed or must be attended. Tap a course for its credit breakdown, dates that are not marked, duty leave, and course actions. **Timetable** and **Duty leave** are labelled rows above the course list. Duty leave includes dated requests and pending leave from before tracking began.
- **Calendar** shows the month and earlier days that are not marked. Tap a date to open it in the day pager. **Settings** contains semester management, display, reminders, backups, and About Ninety.
- Timetable changes have effective dates. Date-specific substitutions, copied weekday schedules, cancellations, holidays, and extra classes are distinct from the recurring template.
- This is a local sideload app. Build/install instructions are in [README.md](README.md); publication and remote pushes require explicit authorization.

## Capabilities and Constraints

- **One period, one unit.** Normal weekdays have six slots. Labs are separate courses and each constituent period is independently recordable. Clock duration never changes attendance weight; allowances are periods, never calendar days.
- **Exact per-course decisions.** Use integer arithmetic for the inclusive 90% threshold and skip/recovery calculations. Rounded display percentages never determine eligibility. With no recorded attendance, show an explanatory empty state rather than a misleading percentage or allowance.
- **Explicit attendance states.** Present, absent, pending/approved/rejected duty leave, unrecorded, and cancelled/not-conducted remain distinguishable. Only recorded, conducted, attendance-counting periods enter totals. Future, unrecorded, holiday, cancelled, and supplemental non-counting sessions are excluded. Flag incomplete past records without automatically marking them absent.
- **Provisional credit stays visible.** Pending and approved duty leave count in primary attendance and skip calculations. Pending credit must be labeled provisional; a confirmed-only comparison is secondary. Rejection removes credit without removing the conducted period. Requests may span several periods, but no period may receive duplicate credit.
- **Fast logging preserves detail.** Group consecutive lab periods for quick actions while retaining per-period edits. Bulk present actions must preserve existing attendance, leave, holidays, and cancellations. Use undo for reversible mistakes and deliberate confirmation when replacing records or data.
- **Schedules preserve history.** Copied weekday schedules count when conducted and recorded; manually added extras do not count unless explicitly enabled. Stable dated-session identities, local calendar dates, and conflict handling prevent duplicates and unintended historical rewrites.
- **Opening balances are aggregates.** Track conducted, physically attended, approved-DL, and pending-DL counts before the first logging date. Counts must be nonnegative, credited totals cannot exceed conducted totals, and dated records must not double-count the opening history.
- **Semester and course terminology.** One active semester at a time; no planned semester end date. Use full course names without a separate short name or course code. New or renamed course names must be unique within their semester.
- **Offline and private.** No accounts, server, cloud sync, analytics, or Internet permission. Android automatic cloud backup and device transfer are disabled. Optional local reminders request notification permission only when enabled.
- **User-owned backups.** Export/import portable, versioned JSON through Android's file picker. Restore validates the complete backup, previews replacement, asks for explicit confirmation, and replaces data atomically. Preserve identities, relationships, and records through backups, migrations, and upgrades; never use implicit destructive migrations.
- **Scope discipline.** Accounts, portal scraping, faculty/admin panels, collaboration, calendar sync, timetable OCR, AI chat, ads, payments, and analytics services are outside the approved initial scope. The owner has not selected a repository license.

## Brand Commitments

The existing product name is **Ninety**, with the app icon at `app/src/main/res/drawable/ic_ninety.xml`. Preserve the owner-approved neutral light/dark identity with restrained blue actions, selection, and focus; no purple/lavender surfaces or wallpaper-derived palette overrides. The detailed existing visual constraint is in `PROJECT_SPEC.md` §7.1. The owner explicitly requested a whole-app redesign that is minimal, clean, uncluttered, and icon-led, with no unnecessary explanatory text. Keep primary actions recognizable, give icon controls accessible names, and retain concise text where attendance uncertainty or a consequential decision requires it.

## Evidence on Hand

- [PROJECT_SPEC.md](PROJECT_SPEC.md): approved product rules, worked calculation examples, privacy requirements, accessibility criteria, and non-goals.
- [README.md](README.md): current workflows, build instructions, backup behavior, and release context.
- [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md): recorded host/device checks and known verification limits; historical results must not be presented as newly executed checks.
- `docs/screenshots/today.png` and `docs/screenshots/courses.png`: examples using a synthetic test semester, not personal attendance or customer evidence. These are illustrative captures; verify freshness against current code before using them as visual authority.
- `domain/src/test/` and the app's JVM/instrumentation suites: calculation, persistence, backup, and workflow regression coverage. Their existence alone does not establish a passing run.

Use synthetic attendance for screenshots and demonstrations. Do not invent customer claims, testimonials, benchmarks, pricing, or publication/licensing claims.

## Product Principles

1. Calculation correctness and honest uncertainty come before decoration.
2. Make the common daily action quick while keeping individual records easy to inspect and correct.
3. Preserve history and user ownership through every bulk action, schedule edit, restore, and upgrade.
4. Show actionable per-course information, with provisional and incomplete figures clearly qualified.
5. Keep native Android behavior and offline operation central; add complexity only when it serves an approved student workflow.

## Accessibility & Inclusion

Support scalable text, screen-reader labels, sensible focus order, keyboard-safe forms, scrolling, and at least 48 dp touch targets. Respect reduced-motion/system animation preferences. Both light and dark themes must provide accessible enabled text contrast (4.5:1, or 3:1 for large text) and essential control/focus contrast (3:1). Status must use text or icons as well as color; provisional duty leave and incomplete records remain distinct.

Use proportionate verification and report unverified behavior honestly. Physical hardware is not a development completion gate. Personal-device checks must preserve the installation and user data; never uninstall, clear storage, or reset the live attendance database for routine tests.
