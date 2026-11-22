# Android instrumentation

Run the standard suite on a disposable emulator with no personal data.

```sh
./gradlew :app:connectedDebugAndroidTest
```

The visual walkthrough uses a separate runner backed by an in-memory database.

```sh
./gradlew :app:connectedDebugAndroidTest -Pninety.visualReview=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.niranjan.ninety.RedesignDeviceTest
```

## Testing a personal installation

Use the wrapper on an awake device in the primary Android user. Open the matching debug build once before the run. Install upgrades in place with `adb install -r` and keep `android.injected.androidTest.leaveApksInstalledAfterRun=true`. Uninstalling, clearing app data, or enabling orchestrator clear-data mode can erase attendance records.

```sh
export ANDROID_SERIAL=your_device_serial
python3 scripts/verify_device_tests.py
```

Set `ADB` to your platform-tools `adb` path if it is missing from `PATH`. The wrapper checks installation identity and compares database and WAL hashes before and after fixture-only tests. The database stays on the device.

With `--allow-ui-fixtures`, the app journey creates a named `Device Test …` semester in the live app. The wrapper still checks installation identity and database readability. Add `--miui-foreground` if MIUI blocks the journey from launching the app.

## Updating README screenshots

Run `./gradlew :app:testDebugUnitTest` from a clean checkout. [V1UiTest](../app/src/test/kotlin/com/niranjan/ninety/ui/V1UiTest.kt) saves `today-ready.png` and `timetable-editor.png` in `app/build/qa/v1/`. Review the captures before replacing the README images. Use synthetic data for public screenshots and issue attachments.
