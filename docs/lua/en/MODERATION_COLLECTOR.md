# Moderation Collector (experimental)

[Lua guide](README.md) · [Italiano](../it/MODERATION_COLLECTOR.md)

**Availability:** source branch `feat/automod-collector-20261119`; not included in the v0.9.10 stable release. Real-device operation: **not verified**.

## What it collects
- Server-observed channel `KICK`, including actions by other operators/bots: actor, target nickname and reason.
- Server-observed `MODE +b`: setter and ban mask. A ban does not necessarily kick anyone.
- Decisions produced by `automod_antiripetizione.lua`, explicitly marked **unconfirmed requests** until a compatible IRC event is observed.
- The kicked user's latest earlier **public** message where available from TIARCA's existing SQLite channel history (up to the 200 most recent channel messages). For `+b`, lookup is attempted if a nickname is exact or if the mask matches a single author with an already-observed identity among recent channel messages; ambiguous matches produce no context.

The JSONL indicates `context_match` (`nick_exact` or `unique_observed_mask_identity`) and `context_nick` where available. The preceding message is contextual evidence, **not a proven reason for moderation**. Only already-observed `ident`, `host` and `account` data are saved. Other IRC actors are not assumed to be humans or bots without evidence.

## Setup

Import `docs/lua-examples/moderation_collector.lua` in TIARCA's Lua scripting settings, enable it for the network, then issue these local commands:

| Command | Effect |
|---|---|
| `/lua collector on` | Start collecting on this network; setting persists across restarts |
| `/lua collector off` | Stop collecting without deleting past records |
| `/lua collector status` | Show enabled state and archive size |
| `/lua collector export` | Invoke Android's sharing chooser for a JSONL export |
| `/lua collector help` | Show available commands |

Collector starts **OFF**. No Lua storage, send or moderation permission is required for the passive companion script: the Android host performs the archive writes.

## Format, retention and privacy

The app writes an append-only JSONL file for each network UUID in app-private storage. Each line contains a versioned JSON object with event, server, channel, actor, target, detail, timestamps, origin, observed identity fields, previous public message where available, and AutoMod trigger/decision fields.

This is **not** a 32-event circular log; no automatic overwrite or deletion is performed. The archive grows with usage. Export shares a temporary snapshot, not the private source file. Android automatic backup excludes collector archives; export explicitly before moving devices or uninstalling.

**Privacy:** exported archives can include third-party chat messages, hostnames and accounts. Only share with trusted recipients; redact sensitive information before publication.

## Limits and verification

Lua event queues may drop callbacks; a crash, disconnection or Android suspension may cause missing events. An AutoMod request is not proof of delivery or enforcement. A generic ban mask cannot always be associated with one particular nick.

See [PR #289](https://github.com/TIARCA/TIARCA/pull/289) for compilation/tests. Manual device verification remains necessary for multiple networks, reconnections, export and Android permissions. No version bump or release is performed.
