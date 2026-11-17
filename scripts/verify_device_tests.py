#!/usr/bin/env python3
"""Run connected tests with APK retention. No private database is copied or reset."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
import threading

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "com.niranjan.ninety"
JOURNEY = f"{PACKAGE}.AppJourneyDeviceTest"
ADB = os.environ.get("ADB", "adb")


def adb_command(*args):
    return [ADB, "-s", os.environ["ANDROID_SERIAL"], *args]


def adb(*args, check=True):
    result = subprocess.run(adb_command(*args), text=True, capture_output=True, timeout=15)
    if check and result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    return result.stdout


def fingerprint():
    if not adb("shell", "pm", "path", PACKAGE).startswith("package:"):
        raise RuntimeError("App missing. Upgrade with adb install -r; never uninstall it.")
    metadata = adb("shell", "dumpsys", "package", PACKAGE)
    user_zero = re.search(r"(?ms)^\s*User 0:.*?(?=^\s*User \d+:|\Z)", metadata)
    if user_zero is None or "installed=true" not in user_zero.group():
        raise RuntimeError("Expected an installed app for Android user 0.")
    identity = tuple(re.search(pattern, source).group(1) for pattern, source in (
        (r"\buserId=(\d+)", metadata),
        (r"\bfirstInstallTime=([^\r\n]+)", user_zero.group()),
        (r"\bceDataInode=(\d+)", user_zero.group()),
    ))
    hashes = adb("shell", "run-as", PACKAGE, "sha256sum",
                 "databases/ninety.db", "databases/ninety.db-wal", check=False)
    records = {name: digest for digest, name in
               re.findall(r"(?m)^([0-9a-f]{64})\s+(databases/\S+)\s*$", hashes)}
    if "databases/ninety.db" not in records:
        raise RuntimeError("No readable production database. Install the matching debug APK in place and open it once.")
    return identity, records


def foreground_miui_activity(logcat):
    """Respond only to a launch denial observed during this runner's journey."""
    running = False
    for line in logcat.stdout:
        if JOURNEY in line and "TestRunner" in line:
            if "started:" in line:
                running = True
            elif "finished:" in line:
                running = False
        if running and "Permission Denied Activity" in line and f"pkg : {PACKAGE} " in line:
            print("MIUI blocked the UI test launch; foregrounding its matching intent.", flush=True)
            adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c",
                "android.intent.category.LAUNCHER", "-f", "0x10008000", "-n", f"{PACKAGE}/.MainActivity")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-ui-fixtures", action="store_true",
                        help="Include the journey that creates a named Device Test semester through the app UI.")
    parser.add_argument("--miui-foreground", action="store_true",
                        help="Foreground only a blocked Ninety activity during the journey on MIUI.")
    options = parser.parse_args()
    if not os.environ.get("ANDROID_SERIAL"):
        sys.exit("Select the authorized device with ANDROID_SERIAL.")
    if adb("shell", "am", "get-current-user").strip() != "0":
        sys.exit("Use the primary Android user; the runner verifies that installation.")
    power = adb("shell", "dumpsys", "power")
    window = adb("shell", "dumpsys", "window")
    if "mWakefulness=Awake" not in power or "mDreamingLockscreen=true" in window:
        sys.exit("Unlock the selected phone and keep it awake before starting UI tests.")

    adb("shell", "am", "force-stop", PACKAGE)
    before = fingerprint()
    print("Recorded installation identity and on-device database fingerprints; no attendance exported.", flush=True)
    args = [str(ROOT / "gradlew"), ":app:connectedDebugAndroidTest",
            "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true", "--console=plain"]
    if not options.allow_ui_fixtures:
        args.append(f"-Pandroid.testInstrumentationRunnerArguments.notClass={JOURNEY}")
    logcat = None
    try:
        if options.miui_foreground and options.allow_ui_fixtures:
            logcat = subprocess.Popen(adb_command("logcat", "-T", "1", "-s", "TestRunner:I",
                                                   "ActivityStarterImpl:D"), stdout=subprocess.PIPE, text=True)
            threading.Thread(target=foreground_miui_activity, args=(logcat,), daemon=True).start()
        result = subprocess.run(args, cwd=ROOT)
    finally:
        if logcat is not None:
            logcat.terminate()
            logcat.wait(timeout=5)
        adb("shell", "am", "force-stop", PACKAGE)
        after = fingerprint()
        if before[0] != after[0]:
            sys.exit("RETENTION CHECK FAILED: installation or app data directory changed. Investigate without resetting anything.")
        if not options.allow_ui_fixtures and before[1] != after[1]:
            sys.exit("RETENTION CHECK FAILED: database fingerprints changed in a fixture-only run.")
        print("Installation and app data directory retained. " + (
            "Production database remains readable; byte changes are expected with authorized UI fixtures."
            if options.allow_ui_fixtures else "Production database/WAL fingerprints unchanged."), flush=True)
    sys.exit(result.returncode)


if __name__ == "__main__":
    main()
