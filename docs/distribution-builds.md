# Distribution builds

Both distributions use the same `main` source tree, application ID (`io.tiarca.irc`),
version, signing configuration and preference names. No distribution branch is needed.
Installing one over the other requires compatible signatures and Android version rules.

| Distribution | Debug task | Release task | Updates |
| --- | --- | --- | --- |
| Standard | `:app:assembleStandardDebug` | `:app:assembleStandardRelease` | Existing opt-in GitHub updater |
| F-Droid | `:app:assembleFdroidDebug` | `:app:assembleFdroidRelease` | Open the F-Droid app listing; updates managed by the client |

The F-Droid manifest removes `REQUEST_INSTALL_PACKAGES`. Its startup updater is disabled,
and its optimized release APK excludes the GitHub updater endpoint. CI tests both flavors
and inspects each release APK's application ID, permission and updater endpoint.

Neither distribution includes a default TMDB key. Personal keys continue to use
`quick_command_tmdb_key` in the existing default preferences. Manual backups include that
preference, and Android cloud backup/device transfer rules continue to include preferences
under their existing encryption requirements. A key that was only supplied by the old
embedded fallback was never user data and is not automatically copied into preferences.

The manual Candidate workflow produces both signed APKs without publishing a release.
Future tagged releases verify reproducibility independently for both flavors and publish
`TIARCA-vVERSION.apk` and `TIARCA-fdroid-vVERSION.apk` with SHA-256 files. Existing releases
are unchanged.

For a future F-Droid metadata update, select `fdroid` in the recipe's `gradle` list and use
`https://github.com/TIARCA/TIARCA/releases/download/v%v/TIARCA-fdroid-v%v.apk`
as `Binaries`. The selected commit/tag must actually provide this new asset. Do not apply
this recipe to the already published v0.9.9, which predates these flavors.

The listing button does not promise that a version has already reached F-Droid; catalog
review and publication remain separate from producing an APK.
