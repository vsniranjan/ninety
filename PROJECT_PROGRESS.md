# Project progress

Updated: 2026-11-11 (Asia/Kolkata).

## Current status

**Phases 2–8 implemented in one continuous build-out.** The full approved roadmap —
timetable-driven dated sessions, daily logging, labs, history/corrections, duty leave,
holidays/copies/extras, backup/restore, settings/reminders, polish, and release build —
is implemented in the working tree. All 48 app JVM tests plus the domain suite pass;
debug and release APKs build cleanly with lint (`warningsAsErrors`) green on both variants.
Connected-device execution remains optional and was not run (no device connected).

## Phase 2–8 verification in this run

| Check | Result |
|---|---|
| `:domain:test` (schedule resolver/reconciler + attendance engine) | Passed |
| `:app:testDebugUnitTest` | **48 passed**, 0 failures (repository integration, setup VM, legacy persistence) |
| `:app:lintDebug` / `:app:lintRelease` | Passed, 0 errors (`warningsAsErrors`) |
| `:app:assembleDebug` | Passed — `app/build/outputs/apk/debug/app-debug.apk` |
| `:app:assembleRelease` (minified + resource-shrunk, debug-key signed) | Passed — `app/build/outputs/apk/release/app-release.apk` |
| `:app:compileDebugAndroidTestKotlin` / debug androidTest APK | Compiles |
| Connected-device tests | Not run — no device attached; remains optional per spec §10.1 |
| Visual/large-text review | Not performed in this environment; flagged as a limitation |

## Implemented capability (Phases 1–8)

- Kotlin/JVM `domain` plus Compose Android `app`, min API 26, compile/target API 37.
- Exact per-course 90% calculations, pending-DL primary percentage/safe skips,
  recovery/no-data states, count bounds, duplicate detection, and period exclusions.
- Atomic Room semester/course/opening setup, persisted summary, done/reopen, preserving
  schema 1→2→3 migration, ownership/count guards, and no destructive migration fallback.
- Effective-dated weekly timetable versions (Mon–Fri, six institutional slots;
  Friday uses 45-minute periods with the midday break), deterministic materialization
  of dated days/sessions, and reconciliation that protects recorded, cancelled,
  pinned, copied, and extra sessions and reports conflicts instead of overwriting.
- Daily logging: per-period Present/Absent, mark-all-present, not-conducted
  cancellation with reversal, session substitution, extra classes with optional
  attendance counting, weekday-copy onto a date, and reversible holidays.
- Duty leave: multi-period events, pending provisional credit, approve/reject
  (whole event or per-period subset), opening pending-DL resolution, and per-course
  plus semester-wide DL statistics.
- Guarded undo for every mutation via change batches with post-write revision
  snapshots; stale and already-undone attempts are rejected without corrupting state.
- Versioned JSON backup: export, structural+integrity validation with a preview,
  then an atomic replace restore in foreign-key-safe order.
- Rebuilt Compose UI: Today/Day screens, Courses (stats, add/edit/retire, balance
  correction), History month view with missing-data repair, Leave events,
  Timetable editor with conflict preview, and Settings (theme, haptics,
  WorkManager daily reminders, backup/restore, semester lifecycle, new semester).
- No Internet permission; cloud backup and device transfer opted out.
- Pinned JDK 21/Gradle 9.5.0/AGP 9.3.3/Kotlin 2.4.21/KSP 2.3.13/Room 2.8.5,
  Compose BOM 2026.09.00, WorkManager 2.10.3, checksums, exported schemas,
  CI workflow, and test harness.

## Historical Phase 1 record (pre-rebuild)

## Owner-requested amendments delivered

- Spec/plan now gate existing Phase 1 design and actual personal-device retention.
  Phase 7 retains refinement of later screens; no later-phase features were added.
- Exact neutral light/dark foundations, restrained blue actions/selection/focus,
  explicit Material 3 roles including fixed colors, and no dynamic wallpaper palette.
  Launch backgrounds and launcher artwork no longer use purple/lavender.
- Compact setup/summary hierarchy; neutral dividers instead of the large tinted card;
  wrapping external field labels with accessible names, compact count rows, form help,
  error feedback, date-picker cancellation, loading/retry/saving states, and minimum
  action targets. Provisional and incomplete messages stay distinct without tinted panels.
- Explicit IME resize handling, Next/Done actions, focus dismissal on navigation/save,
  keyboard-aware scrolling, and automatic validation-feedback scrolling.
- Contrast tests cover enabled text/buttons/fields/chips/dialogs/date selection/error
  text and control boundaries in both themes. Strong control outlines are separate
  from the specified quiet divider colors. Representative ratios: light primary button
  6.16:1, dark primary button 6.60:1, light secondary dialog text 4.89:1.
- Checked-in `android.injected.androidTest.leaveApksInstalledAfterRun=true`; support
  confirmed in the installed AGP 9.3.3 option class. Retention wrapper explicitly sets
  the flag, requires a selected awake phone, and compares installation identity plus
  on-device production database/WAL fingerprints before/after connected tests.
- Fixtures remain in-memory or dedicated `upgrade-<UUID>.db` /
  `phase1-process-persistence-fixture.db`; cleanup never targets `ninety.db`.
  No routine uninstall, app-data clear, orchestrator clear-data mode, or automatic restore.
- Test activity keeps the screen awake for its lifetime and matches production
  edge-to-edge/IME behavior; it does not change phone timeout/security/theme/font settings.
  Phone screenshots now wait for Compose and system-window idleness.

Substantially changed: spec/plan/README; UI design system, setup, summary and feedback;
manifest/launch/icon/string resources; shared UI journeys and contrast tests;
Gradle retention property and `scripts/verify_device_tests.py`. No schema/math changes
were needed for these amendments. Existing personal data and unrelated work were preserved.

## Actual verification in this amendment run

| Check | Actual result and limits |
|---|---|
| JVM domain/Room/ViewModel/presentation/Compose/contrast suite | **47 passed**, zero failures at an earlier revision in this run. The final harness adds two more tests and edge-to-edge/capture changes; the JVM suite was not rerun after those changes because the owner stopped testing. |
| Debug/release lint | Passed at an earlier revision in this run. Not rerun after final harness/manifest refinements. |
| Debug and instrumentation APK compilation | Passed for the latest source during the final connected run. |
| Minified/resource-shrunk unsigned release build | Passed at an earlier revision in this run; final source was not rebuilt for release after the later refinements. |
| First completed connected suite | **15/15 passed**, Redmi Note 10S, Android 13 / API 33, before final edge-to-edge/capture/state-test changes. |
| Final connected suite | **16 passed, 1 failed, 0 skipped** out of 17. `designLightLargeText` failed in `PhaseOneJourney.courseStep`: after scrolling, touch injection could not find `setup_action`. Whether this is harness timing or a usability defect remains unresolved; do not treat it as a pass. |
| Actual installation/data retention | **Passed after both completed connected runs**, including the failed-test run: same UID/first-install time, package still installed, identical production database and WAL SHA-256 fingerprints. No reinstallation was needed after testing. |
| Phone visual review | Actual normal-text light/dark setup, keyboard/action scrolling, date picker, validation, and dark summary screenshots inspected. JVM large-text screenshots inspected. Phone large-text journeys generated evidence, but their complete final visual review is unfinished. |
| Prior interrupted device attempt | Phone slept/locked; attempt stopped. Not counted as passing. Test-only keep-awake added afterwards. |

The latest run still passed the native migration and dedicated persistence fixture checks,
as well as the other 14 UI/state checks. Earlier Phase 1 evidence (before this amendment)
included 39 JVM / 9 device tests, manifest privacy review, and separate write/force-stop/read
persistence invocations; that historical evidence is not a substitute for the new gates.

Automatic approval review rejected copying the phone's private database to this computer.
No database export occurred; the safer on-device hash comparison succeeded instead.
Only synthetic fixture screenshots were retrieved for this review.

Ignored evidence: `.toolchain/design-check.log`, `design-final-host.log`,
`design-device-final.log` (15/15), `design-device-reviewed.log` (16/17 with retention),
`.toolchain/design-device/`, `app/build/qa/`, and module test/lint reports.
Hosted CI has not run. No push, publication, signing-key change, or personal-data reset occurred.

## Historical Phase 1 exit status (before latest owner amendment)

- Core balance/math/persistence/manual semester lifecycle: previously verified and retained.
- New neutral design and safe instrumentation configuration: implemented.
- Actual package and existing production database retention: verified.
- Final large-text interaction and complete visual/host checks: **not satisfied**.
- Superseded phase boundary: the owner has now explicitly authorized Phase 2.

## Commits

Existing foundation history:
`dfc1f2e`, `f9703c5`, `0bd81dd`, `3e280ea`, `a5ac96b`.

Owner-amendment commits:

- `d63fec2` — specification and approved roadmap design/retention amendments.
- `0154aa9` — neutral Phase 1 design, accessible forms, feedback, and UI/contrast checks.
- `797cb60` — retain device installation and record paused verification.
- `9e4e059` — make device verification optional and authorize Phase 2.
- The Phase 2–8 build-out follows as the next commit.

## Outstanding work

1. Optional connected-device pass (emulator or authorized phone) for the rebuilt UI journeys; the legacy Phase 1 device suite was replaced with repository-level coverage.
2. Visual and large-text review of the rebuilt screens was not possible in this environment.
3. Hardware ergonomics, OEM behavior, TalkBack on hardware, and hardware performance remain optional deferred checks. License selection, dedicated signing key, hosted CI, and publication authorization remain later release concerns.

**All planned phases are implemented.** Remaining items are optional device/visual
verification and release-management decisions, not unimplemented features.
