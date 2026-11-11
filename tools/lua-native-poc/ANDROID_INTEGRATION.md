# Lua 5.5.1 isolated Android diagnostic

Issue: https://github.com/TIARCA/TIARCA/issues/216

## Status and scope

This is a **branch-only feasibility integration** of official Lua 5.5.1.
The native code is compiled from `third_party/lua-5.5.1` for both Standard
and F-Droid. No external sources are downloaded during Gradle builds.

The `Settings → Lua diagnostics (experimental)` page can bind to an Android
service with `android:isolatedProcess="true"`. The app process and IRC sessions
are never exposed to the Lua VM. The service accepts only the following
five predefined probes: arithmetic, denied library globals, CPU/infinite loop
budget, Lua memory allocation budget, and explicit isolated-process death.

**No arbitrary user scripts, IRC event hooks, files, network access, persisted
settings, user imports or plugin API are implemented.** Never enable arbitrary
scripts in the app process. The isolated-service API must be redesigned,
capability-checked and threat-modeled before that.

## UI test instructions

1. Run the official **Build TIARCA Candidate** workflow against the
   `feature/lua-scripting-sandbox-poc` branch. It builds signed Standard and
   F-Droid APKs without modifying the published version.
2. Install one Candidate on an Android test device.
3. Open Settings → Lua diagnostics (experimental).
4. Run the arithmetic test: expected `OK: 42` and `Isolated: true`.
5. Run restricted-libraries test: expected `OK: nil:nil:nil:nil`.
6. Run infinite loop: expected `ERROR: ... instruction budget exceeded`.
7. Run memory budget: expected an ERROR reporting Lua memory exhaustion.
8. Run isolated-process death: the service should disappear but TIARCA should
   remain usable, including IRC connections. Run arithmetic again to confirm
   service restart.
9. Re-test foreground/background, theme light/dark, and battery/RAM.

## Build/F-Droid evidence and boundaries

- Official Lua version 5.5.1, MIT, source provenance and SHA-256:
  `third_party/lua-5.5.1/README.md`.
- Android NDK pinned to `28.2.13676358`; CMake `3.22.1`.
- `libtiarca_lua_probe.so` contains the engine and JNI diagnostics; it is
  loaded only in the isolated service.
- Do not add extra Android permissions, dynamic library downloads, APK
  updaters, automatic script importers, or binary artifacts.
- The earlier standalone ARM64 stripped `.so` was 284,168 bytes and
  byte-for-byte reproducible in two CMake build directories. That was a
  *native library* test, **not an APK reproducibility or device test**.
- Validate full Standard/F-Droid CI and the independent APK reproducibility
  workflow before treating these binaries as eligible for release.
- Android execution, actual isolated IPC, RAM, on-device crash resilience
  and F-Droid acceptance are **not verified** by source or host tests.

No version bump, tag, release or main-branch merge.
