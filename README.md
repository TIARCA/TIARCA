# TIARCA

**TIARCA Is Another Relay Chat App** — a modern, actively maintained IRC client for Android, based on Revolution IRC and evolved as an independent GPLv3 project.

TIARCA keeps the speed and directness of classic IRC while adding a modern Android interface, current Android compatibility, richer moderation and user tools, configurable appearance, MONITOR support, structured IRC information, search, mentions, quick commands and an integrated update flow.

> **Current stable version: 0.9.8** · Android 9+ · Open source · GPLv3

[Download TIARCA 0.9.8](https://github.com/TIARCA/TIARCA/releases/download/v0.9.8/TIARCA-v0.9.8.apk) · [Release notes](https://github.com/TIARCA/TIARCA/releases/tag/v0.9.8) · [Changelog](CHANGELOG.md) · [Documentation](docs/README.md)

---

## What's new in 0.9.8

See the full [0.9.8 release notes](https://github.com/TIARCA/TIARCA/releases/tag/v0.9.8) and [changelog](CHANGELOG.md).

## What TIARCA offers

- Multiple IRC networks and servers, TLS/SSL and SASL authentication.
- Channels and private conversations with persistent history and unread counters.
- Channel topic editing directly from the right drawer, with explicit confirmation before sending the native IRC `TOPIC` command.
- WHOIS and structured WHOWAS information with direct user actions.
- IRC user modes and channel modes with descriptions and server-aware handling.
- Ban lists and channel exceptions in a unified interface.
- IRC `MONITOR` support with a dedicated monitored-users screen.
- Caller-ID (`+g`) support, numeric 718 handling and automatic `ACCEPT` for private conversations initiated by the user while `+g` is active.
- Message search with chronological/context navigation and a separate mentions counter.
- Nick, channel and command autocomplete; mIRC formatting and colors.
- Ignore management and fast actions directly from private conversations.
- Sharing of images, files, audio and video through temporary links.
- Configurable quick commands such as `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` and `!dizionario`.
- Extensive appearance customization: light/dark theme, app colors, chat font, simplified/advanced message formatting, optional channel-event monochrome rendering and optional chat avatars.
- Manual server/network ordering and configurable connection behavior.
- Integrated update checker for official GitHub releases.
- Android encrypted backup/device transfer support, separate from TIARCA manual backup.
- Direct native IRC commands including `/whowas`, `/accept`, `/invite`, `/ison`, `/userhost`, `/motd`, `/version`, `/time`, `/admin`, `/info`, `/lusers`, `/links`, `/stats`, `/knock`, `/list`, `/names` and `/who`.

## Screenshots

The following screens use privacy-safe demonstration data.

![Channel conversation](docs/images/02_chat_canale.jpg)
![Server, channel and conversation drawer](docs/images/01_drawer_menu.jpg)
![WHOIS](docs/images/04_whois.jpg)
![Interface settings](docs/images/10_impostazioni_interfaccia.jpg)

## Documentation / Wiki

The detailed guide explains configuration and connection, channels, private chats, WHOIS/WHOWAS, user and channel modes, caller-ID `+g`, MONITOR, moderation, search, mentions, quick commands, appearance, updates and troubleshooting.

Documentation is available in every language currently supported by TIARCA:

**[Italiano](docs/it/README.md)** · **[English](docs/en/README.md)** · **[Deutsch](docs/de/README.md)** · **[Español](docs/es/README.md)** · **[Français](docs/fr/README.md)** · **[Polski](docs/pl/README.md)** · **[Português (Brasil)](docs/pt-BR/README.md)** · **[Suomi](docs/fi/README.md)** · **[Română](docs/ro/README.md)**

## Installation

Download the signed APK from the latest GitHub Release. Android may ask you to allow installation from the browser or file manager used to open the APK. Official TIARCA APKs are distributed through this repository's **Releases** section.

TIARCA is not distributed through Google Play. Version 0.9.8 is the current stable release. TIARCA is under review for inclusion in the official F-Droid repository; recent release-engineering work targets F-Droid policy compliance and reproducible verification. Availability on F-Droid still depends on completion of the F-Droid review and build process.

## Building from source

Open the project in Android Studio. The project uses Android SDK 36; use JDK 21 for Gradle. A development build can be produced with:

```text
gradlew :app:assembleDebug
```

Public releases are signed with a private signing key that is never stored in the repository. See `keystore.properties.example`.

## Project origin and license

TIARCA is a fork of [Revolution IRC](https://github.com/MCMrARM/revolution-irc), originally developed by MrARM/MCMrARM. TIARCA continues development independently while preserving the original copyright notices and the **GNU GPLv3** license.

The `!movie` quick command uses TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.
