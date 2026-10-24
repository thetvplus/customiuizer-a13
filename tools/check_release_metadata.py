#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent


def validate_metadata(gradle: str, changelogs: dict[str, str], tag: str | None = None) -> tuple[str, int]:
    m_code = re.search(r"val\s+lastVersion\s*=\s*(\d+)", gradle)
    m_name = re.search(r'val\s+lastVersionName\s*=\s*"(r13\.\d+\.\d+)"', gradle)
    if not m_code or not m_name or int(m_code[1]) <= 0:
        raise ValueError("missing or invalid A13 lastVersion/lastVersionName")
    version_name, version_code = m_name[1], int(m_code[1])
    for filename, text in changelogs.items():
        first = re.search(r"(?m)^## (r\d+\.\d+\.\d+)\b", text)
        if not first or first[1] != version_name:
            raise ValueError(f"{filename} top version differs from {version_name}")
        section = re.split(r"(?m)^## ", text[first.end():], maxsplit=1)[0]
        code = re.search(r"versionCode\s+(\d+)\b", section)
        if not code or int(code[1]) != version_code:
            raise ValueError(f"{filename} versionCode differs from {version_code}")
    if tag is not None and tag != version_name:
        raise ValueError("tag/version mismatch or missing tag")
    return version_name, version_code


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--require-tag", action="store_true")
    args = p.parse_args()

    gradle = (REPO / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    changelogs = {name: (REPO / name).read_text(encoding="utf-8") for name in ("CHANGELOG.md", "CHANGELOG_EN.md")}
    try:
        version_name, version_code = validate_metadata(gradle, changelogs,
            os.environ.get("GITHUB_REF_NAME", "") if args.require_tag else None)
    except ValueError as error:
        print(error)
        return 1

    print(f"release metadata OK: {version_name} ({version_code})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
