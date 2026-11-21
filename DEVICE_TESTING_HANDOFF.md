# Device Testing Handoff — Ninety Attendance Tracker

**Written:** 2026-11-11 (session ended mid-verification; device unplugged during the
final journey-test run, so its outcome is *inconclusive*, not failed).

**Read this entire file before touching anything.** It contains (1) what exists,
(2) exactly what is verified vs unverified, (3) every pitfall already discovered,
(4) how to run everything, and (5) a ready-to-paste prompt for the next agent.

---

## 1. What this project is

`Ninety` — a complete, offline-only Android attendance tracker for college
students (fixed 90% rule, theory + multi-period lab courses, duty leave,
holidays, copied days, extras, undo, backup/restore). Pure Kotlin `domain`
module + Compose/Room `app` module. **Feature-complete**: all approved phases
(2–8) are implemented and committed. See `PROJECT_SPEC.md` (authoritative
behavior spec), `IMPLEMENTATION_PLAN.md`, `PROJECT_PROGRESS.md`.

Repo: `.` (git, local-only; do **not** push).

## 2. Verification status — what is already proven

| Layer | Status | Evidence |
|---|---|---|
| Domain unit tests | ✅ pass | `:domain:test` |
| App unit/Robolectric tests | ✅ 48 pass, 0 fail | `:app:testDebugUnitTest` |
| Lint (warnings-as-errors) | ✅ clean, both variants | `:app:lintDebug`/`lintRelease` |
| Debug + release APKs | ✅ build & sign | `app/build/outputs/apk/` |
| **Instrumented on real device** | ⚠️ **10 of 11 pass** | see below |
| — `SchemaUpgradeDeviceTest` | ✅ 1/1 pass on-device | Room 1→3 migration on real SQLite |
| — `ProcessPersistenceDeviceTest` | ✅ 1/1 pass | write/read stages over process restart |
| — `RepositoryDeviceTest` (new) | ✅ **8/8 pass** | materialization/lab grouping, mark-all, cancellation skip, DL pending→approved→rejected, holiday+undo, backup round-trip, corrupt-backup rejection, SQLite trigger/FK rejection — all on real device SQLite |
| — `AppJourneyDeviceTest` (new) | ❓ **inconclusive** | hung once on `performTouchInput` (fixed), re-run was interrupted when the phone was unplugged mid-test |
| Manual/visual device check | ❌ not done | screenshots, tab walk, TalkBack, large text |

## 3. Uncommitted work in this session's tree — committed as of handoff

The handoff commit adds:

- `app/src/androidTest/.../RepositoryDeviceTest.kt` — 8 repository tests over
  `Room.inMemoryDatabaseBuilder` (isolated; never touches the app DB).
- `app/src/androidTest/.../AppJourneyDeviceTest.kt` — one full end-to-end UI
  journey through the **real** `MainActivity` (real DB, real repository).
- `DayScreen.kt` — `testTag`s on the lab group buttons (`group_present`,
  `group_absent`, `group_leave`) and a corrected group label ("Lab ·"/"Class ·" by course type).

## 4. Test infrastructure already in place

**Gradle + device**
- `gradle.properties` already sets `android.injected.androidTest.leaveApksInstalledAfterRun=true` (spec §10.1.1 — never uninstalls).
- Instrumentation runner: `androidx.test.runner.AndroidJUnitRunner`.
- Test deps already declared: `compose-test`, `test-runner/junit`, `room-testing`, `coroutines-test`.
- Run everything: `./gradlew :app:connectedDebugAndroidTest`
- Run one class: `-Pandroid.testInstrumentationRunnerArguments.class=com.niranjan.ninety.AppJourneyDeviceTest`

**UI semantics already added for tests**
`semester_name`, `start_date`, `setup_action` (Continue/Start tracking),
`add_course`, `draft_name`, `draft_code`, `draft_save`, `setup_slot` (6 rows × 5 cols,
index `(slot-1)*5 + weekdayIndex`, Mon–Fri), `setup_error`, `day_title`, `day_menu`,
`day_loading`, `mark_all`, `period_<uuid>`, `action_present`, `action_absent`,
`group_present`, `group_absent`, `group_leave`, `tab_today`, `tab_courses`,
`tab_history`, `tab_leave`, `tab_settings`, `new_semester`, `overview_loading`.

**Key UI labels** (for text selectors): "Start tracking", "Mark all present · N left",
"Lab · 3 periods", "Not recorded", "Present", "Mark as holiday"/"Mark holiday",
"Copy a weekday here", "Add extra class", "Apply duty leave", "Undo" (snackbar),
"New semester". Chips: "DL pending/approved/rejected", "Not conducted", "Not counted".

## 5. Pitfalls already hit — read before debugging

1. **Material3 `DatePicker` day cells use `selectable`, not `OnClick`.**
   `hasClickAction()` never matches them → first attempt timed out.
   `performTouchInput { click(center) }` on the text node **hangs the whole test
   inside `waitForIdleSync` on a real device** — do not use touch injection here.
   Current fix: find the "N" text node, check `SemanticsActions.OnClick in
   node.config`, else `node.onParent().performClick()`.
2. **Free slots are ungrouped `SlotGroup`s too.** `view.groups.single { !it.grouped }`
   throws — filter to cells with `session != null` or by `courseId`.
3. **`waitForIdle`/`performTouchInput` can block forever on a real device.**
   Prefer semantics actions (`performClick`, `performSemanticsAction`) everywhere;
   they use the test clock, not real input injection.
4. **Undo snackbar is `SnackbarDuration.Short` (~4 s).** Click "Undo" promptly or
   fall back to `repository.observeUndoable()` + `repository.undo(batchId)` — the
   test already implements this fallback.
5. **Every unbounded `.first{}`/`collect` needs `withTimeout`** or the test hangs
   silently (already wrapped in the journey test).
6. **MIUI quirks (Redmi Note 10S, MIUI Android 13):** `powerkeeper` may background
   the app; keep screen on (see §6); MIUI shows extra USB/install prompts
   occasionally — watch for a system dialog stealing focus mid-run.
7. **In-process repo access for assertions:**
   `ApplicationProvider.getApplicationContext() as NinetyApplication` →
   `.container.repository` — instrumentation runs inside the app process.
8. **Journey test is rerun-safe:** if a semester exists it enters setup via
   Settings → "New semester" instead of the first-run gate; names are suffixed.
9. **No cleanup needed:** the app DB may contain partial fixture semesters named
   `Device Test <suffix>` — created through the app's own UI; leave them or the
   next agent may deactivate them via Settings.

## 6. Device housekeeping — MUST DO

During this session I changed a device setting and it must be restored:

```sh
adb shell settings put global stay_on_while_plugged_in 0   # restore (was 0)
```

It is currently `7` (keeps the screen on while charging — used to prevent the
10-min sleep during tests). Re-enable for test runs, restore after finishing.

Keep-alive during long runs (either `svc power stayon true` or a periodic
`adb shell input keyevent KEYCODE_WAKEUP` every ~3 min — a script existed at
`/tmp/ninety-keepawake.sh`; recreate if needed).

Dismiss keyguard before UI tests: `adb shell wm dismiss-keyguard`.

## 7. What remains for the next agent

1. Re-run `:app:connectedDebugAndroidTest` to completion — confirm 11/11.
2. If the journey test still fails, debug the remaining brittle step
   (DatePicker OK click, dropdown selection, or mark-all timing) using §5;
   worst case, split it into two tests or seed setup via `repository.createSemester`
   in-process and UI-test only the Today screen onward.
3. Manual smoke: launch app, screenshot every tab in light+dark for a real
   visual review against spec §7.1; check large-text (fontScale 1.3+).
4. Verify no `INTERNET` permission / no network calls (`manifest + apk analyze`).
5. Report honestly — never claim a pass that did not run.

## 8. Environment

- `./gradlew` with `GRADLE_USER_HOME=$PWD/.toolchain/gradle-home` (deps cached there).
- `adb` at `.toolchain/sdk/platform-tools/adb` (not on PATH).
- JDK 21; dependency verification enforced — any new dep needs
  `gradle/verification-metadata.xml` entries.
- Device seen: Xiaomi `M2101K7BI` (Redmi Note 10S, "secret"), Android 13 (API 33),
  arm64 — authorized over USB as `UKLFLZTK4TROXGYX`.
- App package `com.niranjan.ninety` (debug) + `com.niranjan.ninety.test` already
  installed; production DB `ninety.db` exists, had no real user data as of 19:11.

## 9. Personal-data safety (spec §10.1.1) — non-negotiable

- Never `pm clear`, never uninstall the app, never delete `ninety.db`.
- Fixtures: in-memory DBs (already done) or explicitly named test DBs only.
- The only writes to the app DB happen through the app's own UI inside the
  journey test — clearly named `Device Test …` fixtures.
- No orchestrator clear-data mode. Stop if a run cannot preserve app data.

---
