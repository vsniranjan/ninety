# Ninety

A native, offline Android attendance tracker for a fixed **90% minimum per course**.
One academic period is one attendance unit, including shorter Friday periods.
Pending duty leave contributes provisionally to the primary attendance and safe-skip figures.

## Current capability

Ninety is feature-complete for the approved roadmap:

- **Setup** — name a semester, add courses (theory/lab/other) with optional opening
  balances and per-course first logging dates, and build the weekly timetable.
- **Timetable** — Monday–Friday templates across six institutional slots (Friday
  uses 45-minute periods with the midday break). Effective-dated revisions reconcile
  future days while preserving recorded, cancelled, pinned, copied, and extra periods.
- **Today & any day** — tap a period for Present/Absent, mark all present, cancel or
  restore a period, substitute the course for one date, add extra classes (optionally
  attendance-counting), copy another weekday's timetable onto the date, or mark a
  reversible holiday. Future dates show the projected timetable read-only.
- **Courses** — live per-course percentage, safe skips, recovery, provisional and
  confirmed attendance; add, edit, or retire courses; correct opening balances and
  resolve pending opening duty leave.
- **History** — month calendar showing recorded/unrecorded days, holidays, and gaps;
  tap any past date to repair missing records.
- **Duty leave** — apply pending DL to one or many periods as a named event, then
  approve or reject per event or per period. Pending DL credits provisionally and is
  clearly labelled in every percentage.
- **Undo** — a snackbar offers undo after every mutation; stale or already-undone
  changes are rejected safely instead of corrupting records.
- **Settings** — theme (system/light/dark), haptics, an optional approximate daily
  reminder (WorkManager, fully offline), semester switching/done/reopen, starting a
  new semester, and versioned JSON backup export with validated, previewed restore.

Attendance lives only in the on-device database: no accounts, no servers, no
telemetry, no Internet permission, and Android cloud backup/transfer opted out.
[PROJECT_SPEC.md](PROJECT_SPEC.md) is the authoritative attendance policy;
[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) records the phased roadmap.

## Build and run

- JDK 21, Android Studio with Android API 37.0, SDK Build Tools 36.0.0, and platform tools.
- Android 8.0 / API 26 or newer to run the app.
- The checked-in Gradle 9.5.0 wrapper downloads and verifies its distribution checksum.
- Gradle uses AGP 9.3.3 with built-in Kotlin, Kotlin/Compose compiler 2.4.21, KSP 2.3.13,
  Room 2.8.5, and the stable Compose BOM 2026.09.00. Exact pins live in the version catalog.

Set `ANDROID_HOME` to your SDK directory, or create an untracked `local.properties`
containing `sdk.dir=/absolute/path/to/your/sdk`. Then run:

```sh
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.
`./gradlew :app:assembleRelease` produces a minified, resource-shrunk release APK at
`app/build/outputs/apk/release/app-release.apk`, signed with the local debug key when
`~/.android/debug.keystore` exists (sideloaded app — no release keystore is committed).
No machine-specific SDK paths or dedicated signing keys belong in Git.

## Verify

```sh
./gradlew :domain:test :app:testDebugUnitTest
./gradlew :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
# Optional, only with an available emulator or explicitly authorized device:
./gradlew :app:connectedDebugAndroidTest
```

The JVM suite includes native-SQLite Robolectric integration tests and the same Compose
journeys compiled for instrumentation. JVM screenshots are generated under
`app/build/qa/`; test/lint reports are under each module's `build/reports/` directory.
These are generated evidence and are not committed. See [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md)
for actual execution results and hardware coverage.

CI is configured to run unit/integration/UI JVM tests, lint, debug and instrumentation APK
compilation, and Room schema drift checks. Configuring a workflow does not establish that
it has run on GitHub; remote publication requires the owner's authorization.

For a real process-restart check, install both debug APKs and run the fixture in separate
invocations. It never uses the production database:

```sh
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.niranjan.ninety.ProcessPersistenceDeviceTest -e persistenceStage write com.niranjan.ninety.test/androidx.test.runner.AndroidJUnitRunner
adb shell am force-stop com.niranjan.ninety
adb shell am instrument -w -e class com.niranjan.ninety.ProcessPersistenceDeviceTest -e persistenceStage read com.niranjan.ninety.test/androidx.test.runner.AndroidJUnitRunner
```

Both stages must report `OK (1 test)`. The read stage verifies N=40/A=38, identity, and
two safe absent periods, then removes its fixture. Device journeys foreground only their
debug test activity to accommodate OEM restrictions without changing security settings.

## Architecture and integrity

- `domain`: plain Kotlin/JVM types, validation, and independently testable integer attendance rules.
- `app/data`: Room entities/DAO and atomic setup repository. Foreign keys, uniqueness,
  and SQLite triggers protect ownership and opening-balance invariants.
- `app/ui`: immutable ViewModel state, typed Compose navigation, and Material 3 screens.
- Opening balances cover dates **before** each course's first logging date. They are not
  fabricated dated classes. Lab and theory courses have separate identities.
- No destructive migration fallback is enabled. Schema versions 1–3 are exported; future
  schema changes must add explicit migrations and preservation tests, including trigger recreation.

## Privacy and backups

Attendance lives in the application-private SQLite database. There are no accounts,
servers, telemetry, or Internet permission. Android automatic cloud backup and device
transfer are opted out using manifest and extraction rules. **Settings → Backup**
exports a versioned JSON file via the system file picker; restore validates the file,
shows a preview, then replaces all data atomically in foreign-key-safe order.
Uninstalling or clearing app storage without an exported backup loses local data.

## Contributing

Read the spec, approved plan, and progress record first. Work on one phase at a time,
keep changes and commits focused, and run the checks relevant to the change. Preserve
existing data and never change the fixed institutional rules silently. Include test
commands and actual outcomes with changes. Use synthetic attendance data in screenshots.

An open-source license has not yet been selected by the repository owner.

## Safe tests on a personal phone

The supported connected runner uses
`android.injected.androidTest.leaveApksInstalledAfterRun=true` in `gradle.properties`.
AGP 9.3.3 supports this option. Do not override it to false, use an uninstall task,
`pm clear`, or an orchestrator clear-data option on a personal installation. Upgrade
with `adb install -r` only (same application ID/signing key). If installation fails,
stop and investigate; never “fix” it by uninstalling. No routine test resets the app.

Select the authorized phone explicitly, unlock it, and run the retention-checking
wrapper (Python 3, adb and the normal Android build prerequisites):

```sh
export ANDROID_SERIAL=your_authorized_device_serial
# Optional if adb is not on PATH:
export ADB=/absolute/path/to/platform-tools/adb
python3 scripts/verify_device_tests.py
```

Open the installed debug app once before the first check so its database exists.
The wrapper force-stops the process (without clearing storage), records only on-device
SHA-256 database/WAL fingerprints and installation identity, runs connected tests with
the retention flag explicitly true, then verifies the package is still installed with
the same UID/first-install time and unchanged production database/WAL. It never copies
attendance records to the computer or restores/resets anything. Byte fingerprints are
intentionally strict for runs without schema changes. Schema upgrades use separate
migration fixtures and require their own preservation review. A mismatch or an
unsupported runner is a stop condition; use a disposable emulator for destructive tests.
The wrapper requires a debuggable app signed with the project's existing debug key.

Compose journeys use **in-memory Room**. Migration and restart checks use only named
test databases (`upgrade-<UUID>.db` and `phase1-process-persistence-fixture.db`), never `ninety.db`; cleanup removes only their fixtures.
Synthetic screenshots are written to app `cache/qa` on hardware and `app/build/qa` on
the JVM. The foreground debug activity sets `FLAG_KEEP_SCREEN_ON` only for its lifetime;
phone timeout, lock, font and theme settings are not changed. Tests are not designed to
run through a lock screen. After testing, open the normal launcher activity; its personal
data remains independent of the synthetic UI journeys.

## Phase 1 design verification

The owner-approved palette is specified in `PROJECT_SPEC.md` §7.1. Explicit Material 3
roles keep backgrounds/surfaces neutral; blue is limited to action, selection and focus.
No dynamic wallpaper colors are used. Quiet dividers use the specified border colors;
interactive outlines use stronger contrasting colors. Enabled text and control contrast
is checked by `ThemeContrastTest`, including buttons, fields, chips, date selection,
dialog text, error text and focus boundaries.

For local design review, inspect available previews/screenshots; phone review is optional. Cover interactions
in **light/dark × normal/200% text**: both setup steps, software keyboard and Next/Done,
date-picker cancel/selection, error recovery, pending-DL summary/confirmed comparison,
scrolling to the bottom, 48 dp targets, no-attendance, and done/reopen. Verify loading,
retry and incomplete-data messaging too. The shared UI suite can capture these synthetic
states on the JVM and optionally on a phone. Inspect those images; functional test success alone is not
visual approval. Use the system theme for normal app use; the test harness supplies
explicit theme/font settings without changing device preferences. Full TalkBack,
minimum/current-API emulators and broader later-screen polish remain phases 7–8.

## Current verification policy

The owner has authorized Phase 2 and will not connect a phone. Use focused local
calculation/domain tests, repository/migration checks for persistence changes, targeted
UI regressions for interaction changes, and relevant build/lint checks. Physical-device
review and instrumentation are optional, not phase completion blockers. Preserve data
safety and report unavailable checks honestly. See specification v1.3 §10.1 and the
latest progress entry; historical device results are not new verification evidence.
