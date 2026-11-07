# Phase D — Dependency supply-chain audit (experimental branch)

This document describes the initial, **non-release** hardening work. It is not a
claim that the full dependency graph is vulnerability-free.

## Verified baseline (7 November 2026)

- Base branch: `main` after phase C, commit `6ffde2d6da3eedd090495d831dc8e7ea078417c9`.
- Application: `versionName 0.9.10`, `versionCode 91000`. Do not change them in this phase.
- Repositories: Google Maven, Maven Central and JitPack for app dependencies;
  Google Maven and Maven Central for the buildscript and included Gradle plugin.
- The Gradle wrapper already specifies `distributionSha256Sum`.
- Repository tree has no committed application JAR/AAR/SO artifacts. The Gradle
  wrapper JAR is the only committed JAR.
- Phase C upgraded direct AndroidX/Material/CameraX/test libraries, but did not
  independently audit all their transitive dependencies.

## Experimental change

The app's JitPack Maven repository now accepts only the
`com.github.mcmrarm` group, corresponding to TIARCA's existing declared
JitPack dependencies. The restriction prevents JitPack being consulted as a
fallback for unrelated dependency groups. It does **not** verify artifact
contents and does not make JitPack inherently trusted.

PR CI captures the resolved **debug and release runtime dependency graphs**
for both `Standard` and `Fdroid` variants. Download the seven-day
`dependency-inventory-Standard` and `dependency-inventory-Fdroid`
artifacts from the CI run for the exact PR commit. The reports are evidence for
a follow-up transitive dependency review; they are not vulnerability scan
results. The CI still runs its existing unit tests, APK builds and
distribution checks.

## Before considering merge

1. Confirm both CI matrix jobs succeed; inspect their inventory artifacts for
   missing modules, unexpected version upgrades, snapshots and deprecated or
   unmaintained components.
2. Review the exact versions against upstream advisories and an independently
   maintained vulnerability database (for example OSV). Record findings with
   affected versions and references. Do not infer absence of vulnerabilities
   from a successful build.
3. Evaluate Gradle SHA-256 dependency verification only after trustworthy
   artifact hashes can be generated and *reviewed* for **all** resolved
   buildscript, included-build, test and application dependencies. Never add
   an incomplete verification-metadata.xml merely to say verification is on.
4. Run the existing release workflow in non-publishing mode (or an equivalent
   controlled double build) to verify byte-for-byte reproducibility before
   shipping supply-chain changes. Verify the F-Droid build path separately.
5. Check device behavior only if a dependency actually changes. Nothing in
   this first experiment changes application source or direct versions.

No release, tag, version increment or merge to `main` is authorized by this
document.
