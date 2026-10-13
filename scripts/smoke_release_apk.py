#!/usr/bin/env python3
"""Install the actual release APK and verify Android TV cold startup.

Use an already booted emulator, or pass --avd NAME to start an installed AVD.
Pass --no-accel when local KVM is unavailable. SDK downloads belong to the CI
emulator setup, not to this test.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time


class SmokeFailure(RuntimeError):
    pass


def has_app_fatal(logcat, package):
    process = re.escape(package) + r"(?:\b|:)"
    java_crash = re.search(r"Process:\s*" + process, logcat)
    native_crash = re.search(r">>>\s*" + process + r"[^\n]*<<<", logcat)
    return bool(java_crash or native_crash)


def is_foreground(activities, component):
    return any(
        re.search(r"mResumedActivity|mFocusedActivity|topResumedActivity", line)
        and component in line
        for line in activities.splitlines()
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--serial", default=os.getenv("ANDROID_SERIAL", "emulator-5554"))
    parser.add_argument("--output-dir", type=Path, default=Path("app/build/release-startup-smoke"))
    parser.add_argument("--observe-seconds", type=int, default=30)
    parser.add_argument("--startup-timeout", type=int, default=120)
    parser.add_argument("--boot-timeout", type=int, default=600)
    parser.add_argument("--avd", help="Start this installed AVD before testing")
    parser.add_argument("--no-accel", action="store_true")
    args = parser.parse_args()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    sdk = os.getenv("ANDROID_SDK_ROOT") or os.getenv("ANDROID_HOME")
    sdk = Path(sdk) if sdk else None
    result = {"status": "failed", "serial": args.serial}
    emulator = None
    adb = None

    def tool(name):
        executable = shutil.which(name)
        if executable:
            return executable
        if sdk:
            candidates = [sdk / "platform-tools" / name, sdk / "emulator" / name]
            candidates += sorted(
                sdk.glob("build-tools/*/" + name),
                key=lambda path: tuple(map(int, re.findall(r"\d+", path.parent.name))),
                reverse=True,
            )
            for candidate in candidates:
                if candidate.is_file():
                    return str(candidate)
        raise SmokeFailure(f"Cannot find {name}; set ANDROID_SDK_ROOT or PATH")

    def run(command, timeout=30, check=True):
        completed = subprocess.run(
            command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, timeout=timeout,
        )
        if check and completed.returncode:
            raise SmokeFailure(f"Command failed: {Path(command[0]).name} {command[1]}")
        return completed.stdout

    def device(*command, timeout=30, check=True):
        return run([adb, "-s", args.serial, *command], timeout, check)

    def capture():
        if not adb:
            return
        for filename, command in [
            ("logcat.txt", ("logcat", "-d", "-v", "threadtime")),
            ("crash-logcat.txt", ("logcat", "-b", "crash", "-d", "-v", "threadtime")),
            ("activities.txt", ("shell", "dumpsys", "activity", "activities")),
            ("processes.txt", ("shell", "ps", "-A")),
        ]:
            try:
                (output / filename).write_text(device(*command, check=False))
            except (SmokeFailure, OSError, subprocess.TimeoutExpired):
                pass
        try:
            with (output / "screen.png").open("wb") as screen:
                subprocess.run(
                    [adb, "-s", args.serial, "exec-out", "screencap", "-p"],
                    stdout=screen, stderr=subprocess.DEVNULL, timeout=30, check=False,
                )
        except (OSError, subprocess.TimeoutExpired):
            pass

    try:
        if not args.apk.is_file():
            raise SmokeFailure("Release APK does not exist")
        badging = run([tool("aapt"), "dump", "badging", str(args.apk.resolve())])
        (output / "apk-badging.txt").write_text(badging)
        if "application-debuggable" in badging:
            raise SmokeFailure("Startup gate requires the release APK, not a debug APK")
        package = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
        activity = re.search(r"^launchable-activity: name='([^']+)'", badging, re.MULTILINE)
        if not package or package.group(1) != "com.codex.ciyuanbox.tv":
            raise SmokeFailure("Unexpected release APK application ID")
        if not activity or activity.group(1) != "com.jing.sakura.home.MainActivity":
            raise SmokeFailure("Release APK does not launch MainActivity")
        package = package.group(1)
        component = package + "/" + activity.group(1)
        digest = hashlib.sha256()
        with args.apk.open("rb") as apk_file:
            for chunk in iter(lambda: apk_file.read(1024 * 1024), b""):
                digest.update(chunk)
        result.update(apk=args.apk.name, sha256=digest.hexdigest(), component=component)
        adb = tool("adb")

        if args.avd:
            port = re.fullmatch(r"emulator-(\d+)", args.serial)
            if not port:
                raise SmokeFailure("--avd requires an emulator-PORT serial")
            command = [
                tool("emulator"), "-avd", args.avd, "-port", port.group(1),
                "-no-window", "-no-audio", "-no-boot-anim", "-no-snapshot",
                "-gpu", "swiftshader_indirect", "-memory", "2048", "-cores", "2",
            ]
            if args.no_accel:
                command += ["-accel", "off"]
            if os.getenv("HTTP_PROXY"):
                command += ["-http-proxy", os.environ["HTTP_PROXY"]]
            with (output / "emulator.log").open("wb") as emulator_log:
                emulator = subprocess.Popen(command, stdout=emulator_log, stderr=subprocess.STDOUT)

        boot_deadline = time.monotonic() + args.boot_timeout
        while True:
            if emulator and emulator.poll() is not None:
                raise SmokeFailure("Android TV emulator exited during startup")
            try:
                if device("shell", "getprop", "sys.boot_completed", timeout=15, check=False).strip() == "1":
                    break
            except subprocess.TimeoutExpired:
                pass
            if time.monotonic() >= boot_deadline:
                raise SmokeFailure("Android TV emulator did not finish booting")
            time.sleep(2)
        if "android.software.leanback" not in device("shell", "pm", "list", "features"):
            raise SmokeFailure("Startup gate requires an Android TV device")

        (output / "install.txt").write_text(device("install", "-r", str(args.apk.resolve()), timeout=600))
        device("shell", "am", "force-stop", package)
        device("logcat", "-c")
        launch = device(
            "shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LEANBACK_LAUNCHER", "-n", component,
            timeout=args.startup_timeout,
        )
        (output / "launch.txt").write_text(launch)

        startup_deadline = time.monotonic() + args.startup_timeout
        while True:
            crash = device("logcat", "-b", "crash", "-d", "-v", "threadtime")
            if has_app_fatal(crash, package):
                raise SmokeFailure("App-specific fatal exception during MainActivity startup")
            pid = device("shell", "pidof", package, check=False).strip()
            activities = device("shell", "dumpsys", "activity", "activities")
            if pid and is_foreground(activities, component):
                break
            if time.monotonic() >= startup_deadline:
                raise SmokeFailure("MainActivity did not remain alive in the foreground")
            time.sleep(2)

        result["pid"] = pid
        observe_deadline = time.monotonic() + args.observe_seconds
        while time.monotonic() < observe_deadline:
            if has_app_fatal(device("logcat", "-b", "crash", "-d", "-v", "threadtime"), package):
                raise SmokeFailure("App-specific fatal exception after MainActivity startup")
            if device("shell", "pidof", package, check=False).strip() != pid:
                raise SmokeFailure("App process stopped or restarted after MainActivity startup")
            time.sleep(2)
        if not is_foreground(device("shell", "dumpsys", "activity", "activities"), component):
            raise SmokeFailure("MainActivity left the foreground after startup")
        result.update(status="passed", observe_seconds=args.observe_seconds)
        print(f"Release startup passed: {component}; process alive for {args.observe_seconds}s")
        return 0
    except (SmokeFailure, OSError, subprocess.TimeoutExpired) as error:
        result["error"] = str(error)
        print(f"Release startup failed: {error}", file=sys.stderr)
        return 1
    finally:
        capture()
        (output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        if emulator:
            emulator.terminate()
            try:
                emulator.wait(timeout=30)
            except subprocess.TimeoutExpired:
                emulator.kill()
                emulator.wait()


if __name__ == "__main__":
    sys.exit(main())
