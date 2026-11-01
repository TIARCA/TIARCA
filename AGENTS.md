# TIARCA — Instructions for coding agents

This file provides repository-wide instructions to Codex and other coding agents.
Follow the live repository as the source of truth; these instructions are not a substitute for inspecting the code.

## Project
- Repository: `TIARCA/TIARCA`.
- Android IRC client evolved from Revolution IRC.
- Application ID: `io.tiarca.irc`; license: `GPL-3.0-only`.
- The application uses Java/Android and Gradle. Inspect the current build files for versions and toolchain requirements.

## Before any change
1. Check the default branch and its current HEAD, `app/build.gradle` (`versionName` and `versionCode`), latest tag/release, and relevant open issues, PRs and branches.
2. Read the current implementation, related resources/tests, and the actual workflows in `.github/workflows/`. Do not infer existing classes, methods, APIs or behavior from prior conversations.
3. Identify the likely root cause and the smallest coherent change. Avoid unrelated refactoring and preserve backward compatibility.
4. If the user requests analysis only, or says not to implement yet, **do not change files**.

## Development and pull requests
- Work in a dedicated task branch and use a pull request targeting `main`; do not push development work directly to `main`.
- Do not bypass review policies, required checks, or branch protections. Never force-push `main` or move published tags.
- Keep changes localized, readable, reversible and consistent with existing architecture.
- Do not add dependencies, permissions, network access or build tools without a concrete justification.
- Before merging a requested change, inspect the PR diff and ensure the required CI is green. If checks are pending or failing, report that rather than claiming success.

## Versions, Candidate builds, tags and releases
- **Never change `versionName` or `versionCode` automatically** for fixes, features, tests or Candidate builds.
- Do not create/push release tags, publish releases, or modify existing tags/releases without explicit user authorization.
- A Candidate is not a stable release. The manual `.github/workflows/candidate.yml` creates signed test artifacts without publishing.
- The official `.github/workflows/release.yml` handles publishing from a valid `v<versionName>` tag. Do not replace it with a manual GitHub Release.
- For an explicitly authorized release, update versions and `CHANGELOG.md`, run `python tools/sync_release_home.py` and commit the resulting README changes before tagging. Confirm CI passes on the definitive commit.
- Preserve byte-for-byte reproducibility checks and the relationship between source, tag, APK and checksum.

## CI and local validation
- Treat `.github/workflows/ci.yml` as authoritative: inspect it each time rather than assuming its steps are unchanged.
- At the time this file was added, CI checked translations, release-home synchronization, unit tests, debug/release compilation and APK distribution checks for both `Standard` and `Fdroid` flavors.
- Relevant commands (confirm task names against current Gradle files before running):
  - `python tools/translation_structure_audit.py`
  - `python tools/sync_release_home.py --check`
  - `./gradlew :app:testStandardDebugUnitTest :app:testFdroidDebugUnitTest`
  - `./gradlew :app:assembleStandardDebug :app:assembleFdroidDebug`
  - `./gradlew :app:assembleStandardRelease :app:assembleFdroidRelease`
  - `python tools/verify_distribution_apk.py Standard` and `python tools/verify_distribution_apk.py Fdroid`
- Run relevant focused tests first, then broader checks as appropriate. Report environment limitations separately from actual code failures.
- A successful build does **not** prove that a bug is fixed. Distinguish implementation, compilation, automated tests and manual device verification.

## F-Droid and Android compatibility
- Keep `standard` and `fdroid` product flavors in the same source tree unless a design change is explicitly requested.
- Respect the distribution-specific updater behavior; the F-Droid variant must not silently regain the GitHub updater or its APK installation permission.
- Review changes affecting manifests, signing, Gradle/Maven sources, prebuilt binaries, Android permissions, cleartext network traffic, WebView, external intents, file access, and APK installation.
- Never store signing material, credentials, API secrets or private certificates in source control or logs. Signing secrets belong in the approved GitHub Actions secret infrastructure.
- Preserve reproducible builds and avoid unnecessary or unreviewable binary dependencies.

## IRC behavior, UI and preferences
- Preserve correct IRC protocol semantics and network/server compatibility. For reported bugs, find the underlying cause rather than introducing cosmetic workarounds.
- For UI changes, consider light/dark themes, graphic presets, touch gestures, accessibility, and actual rendered behavior.
- In TIARCA, a **theme** (light/dark colors/resources) differs from a **graphic preset** (the full chat appearance configuration).
- When a user manually changes an appearance setting managed by the selected preset, the preset should become `Personalizzato` unless the requested design specifies otherwise.
- For persistent settings, verify defaults, storage/load, migration, reset, manual backup/restore and Android backup/device transfer as applicable.
- When changing visible user-facing functionality, update the relevant documentation or changelog as appropriate, but do not describe unreleased features as already available.

## Security and privacy
- Keep diagnostic data and examples free from credentials, personal chat content and other unnecessary sensitive information.
- Do not weaken TLS, WebView, URI or Android security boundaries as a shortcut.
- Before removing an APK-related permission or code path, inspect how it affects the built-in updater.

## Completion report
For each implementation task, report:
1. Root cause or findings (if known).
2. What changed and the principal files.
3. Branch, commit and PR (and merge status if applicable).
4. Tests actually run and their results.
5. Manual verification still needed.
6. Any uncertainty explicitly marked **non verificato**.

Do not claim work, tests, approval or deployment that did not actually happen.
