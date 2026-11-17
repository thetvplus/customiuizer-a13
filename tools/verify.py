#!/usr/bin/env python3
"""Unified local verification for CustoMIUIzer A13.

Only the Python standard library is used.  No ADB, no APK build, no release
signing, no artifact upload.

Usage:
    python tools/verify.py fast
    python tools/verify.py fast --tests PreferenceBootstrapTest
    python tools/verify.py fast --changed --base origin/main
    python tools/verify.py full
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
BUILD_LOG_DIR = REPO_ROOT / "build" / "verify-logs"
GRADLEW = REPO_ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")

FORBIDDEN_TASKS = {
    "assemble",
    "package",
    "bundle",
    "install",
    "sign",
    "publish",
    "officialRelease",
    "lintVitalRelease",
}


def log_file(task: str) -> Path:
    BUILD_LOG_DIR.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    return BUILD_LOG_DIR / f"{stamp}-{task}.log"


def _safe_print(msg: str) -> None:
    if hasattr(sys.stdout, "buffer"):
        encoded = msg.encode(sys.stdout.encoding, errors="replace")
        sys.stdout.buffer.write(encoded + b"\n")
    else:
        print(msg)


def print_tail(path: Path, lines: int = 40) -> None:
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        _safe_print(f"  could not read log: {exc}")
        return
    for line in text.splitlines()[-lines:]:
        _safe_print(f"  {line}")


def guard_task(task: str) -> None:
    if any(forbidden in task for forbidden in FORBIDDEN_TASKS):
        print(f"[verify] refuse to run forbidden task: {task}")
        sys.exit(2)


def run(cmd: list[str], task: str) -> int:
    guard_task(task)
    log = log_file(task)
    cmd = [str(c) for c in cmd]
    print(f"[verify] {task} -> {' '.join(cmd)}")
    with open(log, "w", encoding="utf-8") as f:
        result = subprocess.run(cmd, cwd=REPO_ROOT, stdout=f, stderr=subprocess.STDOUT)
    if result.returncode == 0:
        print(f"[verify] {task} OK")
    else:
        print(f"[verify] {task} FAILED (exit {result.returncode}); log: {log}")
        print_tail(log)
    return result.returncode


def git_output(*args: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=REPO_ROOT, capture_output=True, check=True,
    )
    return result.stdout.decode("utf-8", errors="surrogateescape")


def changed_files(base: str) -> set[str]:
    base_sha = git_output("rev-parse", "--verify", "--end-of-options", f"{base}^{{commit}}").strip()
    fork = git_output("merge-base", base_sha, "HEAD").strip()
    # Report both sides of a rename, including moves out of build inputs.
    diff = ("diff", "--no-renames", "--name-only", "-z")
    outputs = (
        git_output(*diff, fork, "HEAD", "--"),
        git_output(*diff, "--cached", "--"),
        git_output(*diff, "--"),
        git_output("ls-files", "--others", "--exclude-standard", "-z", "--"),
    )
    return {name for output in outputs for name in output.split("\0") if name}


def docs_only(base: str) -> bool:
    try:
        names = changed_files(base)
    except (subprocess.CalledProcessError, OSError):
        print(f"[verify] cannot compare against {base}; keeping Android checks", file=sys.stderr)
        return False
    return bool(names) and all(
        name.endswith(".md")
        and ("/" not in name or name.startswith(("docs/", ".agents/skills/", "tools/dexsql/")))
        for name in names
    )


def gradle_cmd() -> str:
    if GRADLEW.is_file():
        return str(GRADLEW)
    if os.name == "nt":
        return "gradlew"
    return "./gradlew"


def check_invariants() -> int:
    return run([sys.executable, str(REPO_ROOT / "tools" / "check-invariants.py")], "check-invariants")


def check_compat_contracts() -> int:
    return run(
        [sys.executable, str(REPO_ROOT / "tools" / "check-compat-contracts.py")],
        "check-compat-contracts",
    )


def check_text_encoding() -> int:
    return run(
        [sys.executable, str(REPO_ROOT / "tools" / "check_text_encoding.py")],
        "check-text-encoding",
    )


def check_hook_contract_parity() -> int:
    return run(
        [sys.executable, str(REPO_ROOT / "tools" / "check_hook_contract_parity.py")],
        "check-hook-contract-parity",
    )


def check_settings_coverage() -> int:
    return run([sys.executable, str(REPO_ROOT / "tools" / "check_settings_coverage.py")], "check-settings-coverage")


def check_android(mode: str, pattern: str | None) -> int:
    if mode == "full":
        return run([gradle_cmd(), ":app:testDebugUnitTest", ":app:lintDebug"], "gradle-full")
    if pattern:
        return run([gradle_cmd(), ":app:testDebugUnitTest", f"--tests=*{pattern}*"], "gradle-tests")
    return run([gradle_cmd(), ":app:compileDebugJavaWithJavac"], "gradle-compile")


def verify(mode: str, pattern: str | None, skip_android: bool) -> int:
    for check in (check_invariants, check_compat_contracts, check_hook_contract_parity,
                  check_settings_coverage, check_text_encoding):
        if check() != 0:
            return 1
    if skip_android and pattern is None:
        print("[verify] documentation-only changes; skipping Android build tasks")
        return 0
    return check_android(mode, pattern)


def main() -> int:
    parser = argparse.ArgumentParser(description="A13 local verification")
    parser.add_argument("mode", choices=["fast", "full", "impact"], help="verification mode or print docs/build impact")
    parser.add_argument("--tests", help="targeted test class name substring (fast only)")
    parser.add_argument("--changed", action="store_true", help="skip Android work for a documentation-only diff")
    parser.add_argument("--base", help="comparison ref for --changed or impact (default: origin/main)")
    args = parser.parse_args()

    if args.tests is not None and (args.mode != "fast" or not args.tests):
        parser.error("--tests requires fast mode and a non-empty pattern")
    compare = args.changed or args.mode == "impact"
    if args.base is not None and not compare:
        parser.error("--base requires --changed or impact")
    skip_android = compare and docs_only(args.base or "origin/main")
    if args.mode == "impact":
        print("docs" if skip_android else "build")
        return 0
    return verify(args.mode, args.tests, skip_android)


if __name__ == "__main__":
    sys.exit(main())
