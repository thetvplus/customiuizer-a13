#!/usr/bin/env python3
"""Configure the real official task graph with fake credentials; execute no tasks."""
from __future__ import annotations

import os
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PRIVATE_ENV = {"GH_TOKEN", "GITHUB_TOKEN", "CUSTOMIUIZER_A13_KEYSTORE_PROPERTIES",
               "A13_KEYSTORE_BASE64", "A13_STORE_PASSWORD", "A13_KEY_ALIAS", "A13_KEY_PASSWORD"}


def fixture(directory: Path) -> Path:
    path = directory / "graph-only.properties"
    path.write_text(f"storeFile={(directory / 'unavailable-test-only.p12').as_posix()}\n"
                    "storePassword=test-only\nkeyAlias=test-only\nkeyPassword=test-only\n", encoding="utf-8")
    return path


def build_command(config: Path, sha: str) -> list[str]:
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("A full source SHA is required")
    return [str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")), "--no-daemon",
            "--no-configuration-cache", "--no-build-cache", "--no-scan", "--dry-run",
            ":app:assembleRelease", ":app:lintVitalRelease", "-PofficialRelease=true",
            f"-PreleaseSourceSha={sha}", f"-PcustomiuizerA13KeystoreProperties={config}"]


def check_plan(text: str) -> None:
    tasks = re.findall(r"(?m)^(:app:\w+) SKIPPED\s*$", text)
    ordered = [":app:generateA13ReleaseProvenance", ":app:mergeReleaseAssets",
               ":app:packageRelease", ":app:assembleRelease"]
    if any(task not in tasks for task in [*ordered, ":app:lintVitalRelease"]):
        raise ValueError("Official graph is missing provenance, asset merge, packaging or lint")
    positions = [tasks.index(task) for task in ordered]
    if positions != sorted(positions):
        raise ValueError("Provenance must precede asset merge and APK packaging")


def main() -> int:
    sha = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True,
                         text=True, check=True).stdout.strip()
    with tempfile.TemporaryDirectory(prefix="a13-graph-") as temporary:
        config = fixture(Path(temporary))
        env = {key: value for key, value in os.environ.items() if key not in PRIVATE_ENV}
        result = subprocess.run(build_command(config, sha), cwd=ROOT, env=env,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if result.returncode:
            print(result.stdout)  # Only fake credentials exist in this configuration.
            return result.returncode
        check_plan(result.stdout)
    print("Official release graph verified with --dry-run; no real key, task execution or APK assembly")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
