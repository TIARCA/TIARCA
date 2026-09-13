#!/usr/bin/env python3
"""Synchronize release-specific README fields with app/build.gradle.

The script reads versionName, requires a matching CHANGELOG section, and
updates the repository home. CI and the release workflow use --check so a
release cannot proceed with a stale home page.
"""

from __future__ import annotations

import argparse
import difflib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = ROOT / "app" / "build.gradle"
CHANGELOG = ROOT / "CHANGELOG.md"
README = ROOT / "README.md"


def read_version() -> str:
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    match = re.search(r'^\s*versionName\s+"([^"]+)"', text, re.MULTILINE)
    if not match:
        raise RuntimeError("Unable to read versionName from app/build.gradle")
    return match.group(1)


def verify_changelog(version: str) -> None:
    text = CHANGELOG.read_text(encoding="utf-8")
    if not re.search(rf"^## v{re.escape(version)}\s*$", text, re.MULTILINE):
        raise RuntimeError(f"CHANGELOG.md has no section for v{version}")


def replace_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise RuntimeError(f"Unable to update {label} in README.md")
    return updated


def desired_readme(version: str, current: str) -> str:
    text = current

    text = replace_once(
        text,
        r"^> \*\*Current stable version: [^*]+\*\* · Android 9\+ · Open source · GPLv3$",
        f"> **Current stable version: {version}** · Android 9+ · Open source · GPLv3",
        "stable-version banner",
    )

    text = replace_once(
        text,
        r"^\[Download TIARCA [^]]+\]\(https://github\.com/TIARCA/TIARCA/releases/download/v[^/]+/TIARCA-v[^)]+\.apk\) · \[Release notes\]\(https://github\.com/TIARCA/TIARCA/releases/tag/v[^)]+\) · \[Changelog\]\(CHANGELOG\.md\) · \[Documentation\]\(docs/README\.md\)$",
        f"[Download TIARCA {version}](https://github.com/TIARCA/TIARCA/releases/download/v{version}/TIARCA-v{version}.apk) · [Release notes](https://github.com/TIARCA/TIARCA/releases/tag/v{version}) · [Changelog](CHANGELOG.md) · [Documentation](docs/README.md)",
        "download and release links",
    )

    whats_new = (
        f"## What's new in {version}\n\n"
        f"See the full [{version} release notes](https://github.com/TIARCA/TIARCA/releases/tag/v{version}) "
        "and [changelog](CHANGELOG.md).\n\n"
    )
    text, count = re.subn(
        r"^## What's new in .*?\n\n.*?(?=^## What TIARCA offers\s*$)",
        whats_new,
        text,
        count=1,
        flags=re.MULTILINE | re.DOTALL,
    )
    if count != 1:
        raise RuntimeError("Unable to update What's new section in README.md")

    text = replace_once(
        text,
        r"^(TIARCA is not distributed through Google Play\. Version )[^ ]+( is the current stable release\.)",
        rf"\g<1>{version}\g<2>",
        "Installation stable-version sentence",
    )

    return text


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail instead of modifying README.md when synchronization is needed.",
    )
    args = parser.parse_args()

    try:
        version = read_version()
        verify_changelog(version)
        current = README.read_text(encoding="utf-8")
        desired = desired_readme(version, current)
    except (OSError, RuntimeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    if current == desired:
        print(f"README.md is synchronized with TIARCA {version}.")
        return 0

    if args.check:
        print(
            f"ERROR: README.md is not synchronized with TIARCA {version}.\n"
            "Run: python tools/sync_release_home.py",
            file=sys.stderr,
        )
        sys.stderr.writelines(
            difflib.unified_diff(
                current.splitlines(keepends=True),
                desired.splitlines(keepends=True),
                fromfile="README.md",
                tofile="README.md (expected)",
            )
        )
        return 1

    README.write_text(desired, encoding="utf-8")
    print(f"Updated README.md for TIARCA {version}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
