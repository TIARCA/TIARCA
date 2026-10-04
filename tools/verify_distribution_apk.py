#!/usr/bin/env python3
"""Inspect the built release APK, including the merged manifest and optimized code."""
import os
from pathlib import Path
import subprocess
import sys
import zipfile

flavor = sys.argv[1].lower()
if flavor not in ("standard", "fdroid"):
    raise SystemExit("Expected Standard or Fdroid")
apks = list(Path(f"app/build/outputs/apk/{flavor}/release").glob("*.apk"))
if len(apks) != 1:
    raise SystemExit(f"Expected one {flavor} release APK, found {len(apks)}")
sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
aapt = sorted(sdk.glob("build-tools/*/aapt"))[-1]
permissions = subprocess.check_output([str(aapt), "dump", "permissions", str(apks[0])], text=True)
can_install = "android.permission.REQUEST_INSTALL_PACKAGES" in permissions
if can_install != (flavor == "standard"):
    raise SystemExit(f"Unexpected install permission for {flavor}")
badging = subprocess.check_output([str(aapt), "dump", "badging", str(apks[0])], text=True)
if "package: name='io.tiarca.irc'" not in badging:
    raise SystemExit("Distribution changed the application ID")
with zipfile.ZipFile(apks[0]) as apk:
    dex = b"".join(apk.read(name) for name in apk.namelist() if name.endswith(".dex"))
github_api = b"https://api.github.com/repos/TIARCA/TIARCA/releases/latest"
if (github_api in dex) != (flavor == "standard"):
    raise SystemExit(f"Unexpected GitHub updater code for {flavor}")
print(f"Verified {flavor}: package, install permission and updater code")
