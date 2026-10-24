#!/usr/bin/env python3
"""Check the full source identity embedded by an official A13 build."""
from __future__ import annotations

import argparse
import re
import zipfile
from pathlib import Path


def verify_apk_provenance(apk: Path, sha: str, version: str, code: int) -> None:
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("A full source SHA is required")
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        key = "assets/build-provenance.properties"
        if names.count(key) != 1 or archive.getinfo(key).file_size > 4096:
            raise ValueError("Missing or ambiguous APK provenance")
        lines = archive.read(key).decode("ascii").splitlines()
    expected = {"sourceCommit": sha, "versionName": version, "versionCode": str(code),
                "buildType": "release", "applicationId": "tv.withaibuild.customiuizer.r13"}
    if len(lines) != len(expected) or dict(line.split("=", 1) for line in lines) != expected:
        raise ValueError("APK provenance differs from the exact release source/version")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    args = parser.parse_args()
    verify_apk_provenance(args.apk, args.source_sha, args.version, args.version_code)
    print("Exact A13 APK provenance verified")
