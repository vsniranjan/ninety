# Project progress

Updated: 2026-11-12 (Asia/Kolkata).

## Ninety 1.0.2 — course names and faster navigation

Courses use their full names across setup, attendance, timetable, and duty leave.
The timetable shows one weekday's six periods at a time so names can wrap. Course
IDs, historical records, and the installed database schema stay the same. New
JSON exports omit short names; older format 1 backups still restore by course ID.
The five tabs and their detail pages share a 160 ms horizontal slide. The bottom
selection pill uses the same timing.

Domain tests (21), scoped app JVM tests (60), and the full Xiaomi M2101K7BI
Android 13 instrumented suite (11) passed. The connected run retained the
installation and its data directory. The legacy backup regression verified a
restored timetable and recorded period after renaming its course. The enlarged
text regression was excluded at the owner's request.

After refining tab feedback, reran the two normal-size Compose UI checks and
release lint; both checks passed and lint found no issues. The signed 1.0.2 APK
upgraded the existing installation in place. Inspected all five tabs in light
and dark, the course edit form and timetable, and recordings of slides in both
directions. The APK declares no Internet permission and the device reported
no app traffic records during the smoke. Restored stay-awake to 0. Local APK:
`.toolchain/ninety-1.0.2/Ninety-1.0.2.apk`.

## Ninety 1.0.1 — compact Courses cards

Courses now shows the name, percentage and one attendance takeaway by default.
The breakdown and opening leave actions live under **Details**. Provisional and
incomplete records still have visible labels. Attendance calculations did not change.

Built the signed release and compiled the affected UI test; release lint reported
no issues. Installed in place on the Xiaomi phone and verified the collapsed,
expanded and re-collapsed states at normal text size. The installation identity
and app data directory were retained. APK: `.toolchain/courses-1.0.1/Ninety-1.0.1.apk`.

## Ninety 1.0.0 — local release

Version name **1.0.0**, version code **2**, application ID `com.niranjan.ninety`.
The signed, minified APK was installed in place on the Xiaomi M2101K7BI (Android
13 / API 33). The previous UID, first installation time and app data directory
were retained. Room remains at schema 3; no destructive migration or reset.

- Today cards have inset content, a recorded-period count and a prominent **Mark all
  present** above the list. Counted extra classes are included; supplemental extras
  are skipped. Cancelled periods can be restored without losing their results.
- Weekend dates use the same full-opacity secondary text as other enabled controls.
  Sampled date action bounds are 132 × 132 pixels at 440 dpi (48 × 48 dp). Lab,
  theme, course and timetable controls adapt rather than splitting button labels.
  Onboarding has keyboard navigation, scrolling and a cancel path.
- Settings stays reachable with no active semester. Completed semesters can be
  browsed and reopened. Leave supports per-period approval, shows mixed status,
  and asks before replacing recorded attendance. Courses show physical attendance,
  pending, approved and rejected leave, and the confirmed comparison when provisional.
- Opening-balance changes refresh live statistics. Empty timetable versions stay
  effective, undo restores a replaced version, and copying a weekday removes clean
  old slots while retaining recorded conflicts. Backup preview rejects duplicates,
  invalid dates and inconsistent relationships before writing; file work is off the UI thread.

| Final verification | Evidence |
|---|---|
| Domain and app JVM checks | **21 + 60 passed**, 0 failures or skips. Includes data regressions and isolated Compose lifecycle/leave UI checks. |
| Android lint and builds | `lintDebug` and `lintRelease`: no issues. Debug, instrumentation and minified release APKs built. |
| Full connected suite | **11 passed**, 0 failures or skips. The UI journey uses direct UI actions for setup, lab/mark-all logging, holiday undo, period cancellation/restoration, leave confirmation and partial approval, and semester completion/browse/reopen. Other tests use isolated databases. |
| Visual and accessibility smoke | Inspected normal-text Today, Courses, History, Leave and Settings in light and dark on the phone. Rechecked the final release's affected screens after the last polish. Sampled touch bounds, accessible tab names and enabled weekend contrast. Further enlarged-text testing was stopped at the owner's request; full TalkBack audio traversal was not run. |
| Release upgrade and persistence | `adb install -r` succeeded with the existing local signing key. The release relaunched in a different process and retained the fixture's 1 leave request, 3 periods and mixed approval. No app storage was cleared. |
| Offline/privacy | Neither built APK declares `android.permission.INTERNET`. No app UID traffic-map rows observed during the final smoke. Android automatic backup and transfer remain disabled. |

Build command:

```sh
GRADLE_USER_HOME=$PWD/.toolchain/gradle-home ./gradlew :domain:test :app:testDebugUnitTest \
  :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
```

The device suite used `python3 scripts/verify_device_tests.py --allow-ui-fixtures
--miui-foreground` with the authorized serial. Early attempts exposed clipped-control
and timetable menu selectors; the final run passed after using direct semantics
and scoping menu choices. The runner retained both APKs and verified app data
identity and database readability. Journey writes targeted a named test semester
through the app UI. Smoke-test theme changes were restored. Normal text scale `1.0`,
theme `System`, and `stay_on_while_plugged_in=0` were read back afterward.

Local artifact: `.toolchain/v1-device/Ninety-1.0.0.apk`.
SHA-256: `177ba792391daa33af518d6f4b8eb949e1b9c6f4ff696f99acaab20c471b3940`.
Evidence: `.toolchain/v1-final-host.log`, `.toolchain/v1-device/connected-final.xml`,
`release-signature.txt`, normal-theme captures, and `final-offline-and-restart.txt`
in the device evidence folder. This release is for local sideloading, signed with the
existing development key to allow an in-place upgrade. No push or publication.

## Recorded Phase 2–8 verification

| Check | Result |
|---|---|
| `:domain:test` (schedule resolver/reconciler + attendance engine) | Passed |
| `:app:testDebugUnitTest` | **48 passed**, 0 failures (repository integration, setup VM, legacy persistence) |
| `:app:lintDebug` / `:app:lintRelease` | Passed, 0 errors (`warningsAsErrors`) |
| `:app:assembleDebug` | Passed — `app/build/outputs/apk/debug/app-debug.apk` |
| `:app:assembleRelease` (minified + resource-shrunk, debug-key signed) | Passed — `app/build/outputs/apk/release/app-release.apk` |
| `:app:compileDebugAndroidTestKotlin` / debug androidTest APK | Compiles |
| Connected-device tests | **11 passed**, 0 failures/errors/skips on 2026-11-12; see actual device record below |
| Visual/large-text review | Five tabs captured/inspected in both themes; 130%/200% dark-theme spot checks found issues below |

## Actual device verification — 2026-11-12

Device: Xiaomi M2101K7BI (Redmi Note 10S), Android 13 / API 33, USB-authorized.
Display: 1080 × 2400, density 440 dpi. No production application code changed in
this verification run. The 48 JVM tests, domain tests, lint and release build above
are prior results, not newly rerun checks.

### Instrumentation

Executed the complete suite with:

```sh
GRADLE_USER_HOME=$PWD/.toolchain/gradle-home ./gradlew :app:connectedDebugAndroidTest --console=plain
```

- First completed run: **10 passed, 1 failed**. The journey timed out searching
  for a DatePicker day number. MIUI also blocked the initial activity launch.
- After the harness fix: **11 passed, 0 failures, 0 errors, 0 skipped**;
  Gradle `BUILD SUCCESSFUL` (43 s), JUnit test execution 29.062 s.
- Breakdown: AppJourneyDeviceTest 1; RepositoryDeviceTest 8;
  ProcessPersistenceDeviceTest 1; SchemaUpgradeDeviceTest 1.
- The journey confirms the DatePicker's initially selected today via its OK
  semantics action and asserts the persisted start date. It still creates the
  semester, two courses and timetable through the actual UI, then verifies lab
  grouping, grouped Present, mark-all, holiday, snackbar Undo and all five tabs.
- Fixed the holiday assertion order (observe holiday before clicking Undo),
  removed the direct repository undo fallback, bounded repository polling, and
  handled the semester name appearing in both toolbar and Settings. **All journey
  writes now use the UI**; repository access in that test is read-only.

MIUI logged `Permission Denied Activity` for ActivityScenario's background launch.
The successful run used a host-side monitor to bring the activity forward with
the exact intent ActivityScenario expects when MIUI blocked it:

```sh
.toolchain/sdk/platform-tools/adb shell am start \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  -f 0x10008000 -n com.niranjan.ninety/.MainActivity
```

A plain component-only launch did not match ActivityScenario's intent. This is
an OEM runner limitation; no MIUI permissions or security settings were changed.
The successful runner/keep-awake helper is retained locally as
`.toolchain/device-verification-2026-11-12/run_suite.py`.

### Visual smoke and remaining findings

Launched the installed app and captured actual `adb exec-out screencap -p` images
for Today, Courses, History, Leave and Settings in **both Light and Dark**. All ten
were visually inspected. Also inspected dark Today/Courses/Settings at font scales
1.3 and 2.0 (six additional screenshots). Leave covered its empty state; populated
leave-event UI, TalkBack and a full large-text interaction journey were not tested.

- Palette: backgrounds/surfaces and restrained blue agree with spec §7.1; no
  purple/lavender or dynamic wallpaper palette was observed.
- Visible day actions, lab buttons, course actions, theme controls and calendar
  date accessibility bounds measured at least 48 dp at density 2.75 px/dp.
  Calendar circles draw at 44 dp but their exposed action bounds are 48 dp.
  This is a sampled control check, not an exhaustive accessibility audit.
- **Open: Today single-session card padding.** `SessionCard` calls `PeriodRow`
  without an inset; P1/time text touches and clips against the rounded left edge
  in both themes. Free-slot and grouped-lab cards have the expected inset.
- **Open: History weekend contrast.** Weekend dates remain clickable but use
  secondary text at 50% opacity: calculated contrast against the card is about
  2.08:1 light / 2.90:1 dark, below the 4.5:1 enabled-text requirement.
- **Open: large-text layout.** At 130%, lab Present/Absent labels break inside
  words. At 200%, navigation labels and course-type labels also break inside
  words, and the System theme segment grows taller than its neighbors. These
  screenshots do not establish compliance with the large-text design gate.

### Offline checks, retention and restoration

- `aapt dump permissions` on **both debug and release APKs**, plus the installed
  package permission list: **no `android.permission.INTERNET`**.
  `ACCESS_NETWORK_STATE` comes from WorkManager's merged manifest.
- Reviewed app/domain source and declared dependencies for URLs, HTTP/socket
  clients, WebViews, analytics and network SDKs: no app networking implementation
  or network client dependency found.
- `adb shell dumpsys netstats detail` before/after the visual smoke: app UID
  10284 had no byte/packet rows in UID traffic accounting or current traffic maps.
  No app network traffic was observed during this verification; no packet capture
  or claim about every possible future execution is implied.
- Installation retained: same UID **10284**, user-0 first-install time
  **2026-11-11 18:49:36**, and credential-encrypted data inode **43690** before/after.
  Production DB and WAL remain readable on-device. Their hashes were computed
  on-device; only fingerprints were retrieved (no DB export). Expected UI
  fixture/preferences writes mean this run does **not** claim identical before/after
  database bytes or a record-by-record
  retention comparison. No uninstall, storage clear, DB deletion or automatic
  restore occurred. Named `Device Test …` semester data remains on the phone.
- Repository tests use in-memory Room; migration/process tests use named fixture
  databases. APK retention stays enabled; no orchestrator clear-data mode.
- Enabled `stay_on_while_plugged_in=3`, dismissed keyguard, and sent periodic
  KEYCODE_WAKEUP during the long run. **Restored and read back stay-awake `0` and
  font scale `1.0`; restored Ninety's original System theme via Settings.**

Evidence stays ignored/local in `.toolchain/device-verification-2026-11-12/`:
`full-suite-first.log/.xml`, `full-suite-final.log/.xml`, `light-*.png`, `dark-*.png`,
UI XML, APK permission lists/hashes, app-only network summaries, installation
metadata and DB fingerprints. The usual latest report is
`app/build/reports/androidTests/connected/debug/index.html`. Screenshots and raw
device metadata are not committed. No push occurred.

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

## Optional follow-ups after v1

Cross-device and full TalkBack audio testing, a public distribution signing strategy,
hosted CI execution, license selection and publication are separate follow-ups. The
padding, weekend contrast and action-label issues in the earlier device record were
addressed in 1.0.0.
