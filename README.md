# Ninety

Ninety is an offline Android attendance tracker with a fixed **90% minimum per course**. Every academic period counts as one unit, including each period in a lab and each shorter Friday period. Pending duty leave contributes provisional credit.

## Using Ninety

1. Create a semester, add theory and lab courses by name, and set up the timetable one weekday at a time. If you're starting mid-semester, enter each course's opening balance and first logging date.
2. On **Today**, record a single period, log a lab as a group, or mark remaining eligible periods present. Use the day menu for holidays, copied timetables, extra classes, and duty leave. An extra class doesn't count unless you turn counting on.
3. In **Courses**, see each course's percentage and how many periods you can miss or need to attend. Open **Details** for physical attendance, duty leave, and confirmed credit. Provisional and incomplete records stay flagged on the card.
4. Use **History** to open a date and correct records. In **Leave**, change an entire request or just one period, then see the updated credit in Courses.
5. In **Settings**, browse, complete, or reopen semesters, choose the theme, and optionally set a local reminder. Export a backup before moving phones. Restore previews the selected backup and replaces the current data only after you confirm.

[PROJECT_SPEC.md](PROJECT_SPEC.md) defines the attendance rules. [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) records verification and its limits.

## Screenshots

These examples use a synthetic test semester.

<p>
  <img src="docs/screenshots/today.png" width="235" alt="Today in light mode showing theory and grouped lab periods" />
  <img src="docs/screenshots/courses.png" width="235" alt="Courses in dark mode showing per-course attendance and provisional duty leave" />
</p>

## Build and install

Ninety 1.0.2 runs on Android 8.0 / API 26 or newer. Building uses JDK 21, Android API 37.0, SDK Build Tools 36.0.0, and the checked-in Gradle wrapper. The version catalog pins the build and library versions.

Set `ANDROID_HOME` to the SDK directory, or put `sdk.dir=/absolute/path/to/sdk` in an untracked `local.properties`.

```sh
./gradlew :app:assembleDebug :app:assembleRelease
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The minified release is `app/build/outputs/apk/release/app-release.apk` when the local debug keystore is available. Ninety's local release uses the existing development signing key so it can upgrade the existing installation. The key isn't committed. This is a local sideload release; it hasn't been published.

For an existing installation signed with the same key, use `adb install -r <apk>` to retain data. If Android rejects the signature, stop and check the APK. Uninstalling or clearing app storage removes local attendance unless you have an exported backup.

## Verify and contribute

```sh
./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:lintRelease \
  :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
```

The domain suite covers the attendance rules. JVM tests cover Room workflows, backup validation and restore, Compose lifecycle and leave interactions, and action labels at 200% text. Test and lint reports live under each module's `build/reports/`, and synthetic UI captures are generated in `app/build/qa/v1/`. GitHub Actions is configured for the host checks. Its existence does not mean a hosted run has occurred.

Read the spec before changing attendance policy. Keep changes focused and describe the actual test results. Use synthetic data for screenshots. The owner has not selected an open-source license.

### Safe tests on a personal phone

Keep `android.injected.androidTest.leaveApksInstalledAfterRun=true`. Never uninstall, clear data, delete the live database, or enable orchestrator clear-data mode. Use an authorized, awake phone in Android user 0. Install the matching debug APK in place and open it once before the test run.

```sh
export ANDROID_SERIAL=your_authorized_device_serial
export ADB=/absolute/path/to/platform-tools/adb  # only needed if adb is not on PATH
python3 scripts/verify_device_tests.py
# Include the real UI journey only when authorized to create a named test semester:
python3 scripts/verify_device_tests.py --allow-ui-fixtures --miui-foreground
```

By default the wrapper excludes the journey and checks unchanged database and WAL hashes. Other tests use in-memory Room or named migration and persistence fixtures. The full suite's journey writes only through the real app UI and leaves a `Device Test …` semester. For that mode, the wrapper verifies the same UID, first installation time and app data directory, and checks that the database is still readable; it allows the expected database changes. Only hashes and package metadata are read out of the phone. `--miui-foreground` is optional and responds to MIUI blocking the test activity, without changing security settings.

## Architecture and privacy

`:domain` owns the exact calculation and scheduling rules. `:app` uses Compose, Room and WorkManager for the UI, local storage and optional reminders. Dated sessions retain their identities; opening balances cover periods before the first logging date. Database migrations preserve data, and backup validation precedes transactional replacement.

Ninety has no account, server, analytics or Internet permission. Automatic cloud backup and device transfer are disabled. A user-initiated JSON backup uses Android's file picker; choose a location you control. Current exports use backup format 2 and omit short course names. Ninety can still restore older format 1 backups.
