#!/usr/bin/env python3
"""Run connected tests without uninstall/clear; verify a pre-existing debug installation.

Requires ANDROID_SERIAL and adb (or ADB=/path/to/adb). No attendance leaves the phone:
only package metadata and on-device database hashes are inspected. For schema-changing
upgrades use dedicated migration fixtures; byte hashes are intentionally strict here.
"""
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "com.niranjan.ninety"
ADB = os.environ.get("ADB", "adb")


def adb(*args, check=True):
    result = subprocess.run([ADB, "-s", os.environ["ANDROID_SERIAL"], *args],
                            text=True, capture_output=True)
    if check and result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    return result


def fingerprint():
    if not adb("shell", "pm", "path", PACKAGE).stdout.startswith("package:"):
        raise RuntimeError("App missing. Install debug with adb install -r first; never uninstall it.")
    metadata = adb("shell", "dumpsys", "package", PACKAGE).stdout
    identity = tuple(re.search(pattern, metadata).group(1) for pattern in
                     (r"userId=(\d+)", r"firstInstallTime=([^\r\n]+)"))
    # Only the application's authoritative database/WAL, never screenshots or test fixtures.
    hashes = adb("shell", "run-as", PACKAGE, "sha256sum",
                 "databases/ninety.db", "databases/ninety.db-wal", check=False).stdout
    records = dict((name, digest) for digest, name in
                   re.findall(r"(?m)^([0-9a-f]{64})\s+(databases/\S+)\s*$", hashes))
    if "databases/ninety.db" not in records:
        raise RuntimeError("No readable production database. Open the debug app once before this check.")
    return identity, records


def main():
    if not os.environ.get("ANDROID_SERIAL"):
        sys.exit("Select the authorized device with ANDROID_SERIAL; do not run on an arbitrary phone.")
    power = adb("shell", "dumpsys", "power").stdout
    window = adb("shell", "dumpsys", "window").stdout
    if "mWakefulness=Awake" not in power or "mDreamingLockscreen=true" in window:
        sys.exit("Unlock the selected phone and keep it awake before starting UI tests.")
    adb("shell", "am", "force-stop", PACKAGE)
    before = fingerprint()
    print("Recorded installation identity and on-device database fingerprints; no attendance exported.", flush=True)
    result = subprocess.run([str(ROOT / "gradlew"), ":app:connectedDebugAndroidTest",
                             "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true",
                             "--console=plain"], cwd=ROOT)
    # Also verify retention after a failed test run. Never restore/reset data automatically.
    adb("shell", "am", "force-stop", PACKAGE)
    after = fingerprint()
    if before != after:
        sys.exit("RETENTION CHECK FAILED: installation/database changed. Stop; investigate without clearing or restoring data.")
    print("RETENTION PASSED: same installation identity and unchanged production database/WAL hashes.", flush=True)
    sys.exit(result.returncode)


if __name__ == "__main__":
    main()
