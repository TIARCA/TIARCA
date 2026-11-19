# Moderation Collector — standalone Lua script

[Lua guide](README.md) · [Italiano](../it/MODERATION_COLLECTOR.md) · [Lua source](../../lua-examples/moderation_collector.lua)

**Availability:** [PR #289](https://github.com/TIARCA/TIARCA/pull/289); not part of stable v0.9.10. Real-device testing: **not verified**.

## Events captured

All collection decisions, event selection, SQLite lookup requests and JSONL serialization happen in **moderation_collector.lua**. The Android host only exposes generic Lua facilities; it performs no Collector-specific work when the script is disabled.

- AutoMod **monitor, warning, kick, ban, mute, timed ban and combined actions**, recognized even when the AutoMod script was imported under a different filename (for example `automod.lua`), explicitly marked as **requested, unconfirmed** decisions.
- Server-observed KICK from any operator/bot, with **actor**, **target nickname** and any **reason**.
- Server-observed MODE +b, with actor and mask; no reason is transmitted in that protocol command. A nickname is attached only when identifiable without ambiguity.
- The target's **latest earlier public message**, if found in the last **200 messages of the channel's existing SQLite history**. The Lua script requests this on demand after any qualifying event, including AutoMod.

The stored message's original ident/host take precedence when available. Each event and last-message object has channel, timestamp, nickname, observed ident/host/account (otherwise `null`). Previous chat is context, **not proof of a moderation reason**. Ambiguous ban masks do not receive guessed nicknames.

## Setup and commands

Import [moderation_collector.lua](../../lua-examples/moderation_collector.lua) into TIARCA's Lua scripts. Enable it on the selected network(s) and grant **Persistent data** and **Read users/identities**. Neither sending messages nor moderation authorization is necessary.

**Collection starts as soon as the script is enabled.** There is no host-side Collector switch. The optional script-owned settings are:

| Command | Behavior |
|---|---|
| `/lua collector on` | Resume collection on this network, persisted |
| `/lua collector off` | Pause collection without deleting archived records |
| `/lua collector status` | Show ON/OFF |
| `/lua collector export` | Share this script's network JSONL via Android |
| `/lua collector` | Show state; `/lua collector help` lists commands |

Disabling the script in TIARCA stops all callbacks and history requests. With `off`, the script does not request history or append records.

## Format, retention and privacy

One event per append-only JSONL line. Fields: `schema`, `server`, `channel`, `timestamp`, `event`, `action`, `nickname`, `ident`, `host`, `account`, `operator`, `reason`, `ban_mask`, `source_script`, `similarity_percent`, `infraction_level`, `execution`, `identity_match`, `last_message` (nested message, identity, timestamp, or `null`).

The private journal is scoped by **script UUID and network UUID**, grows incrementally and has no circular truncation at 32, 128 or any other event count. Generic APIs `tiarca.journal.append/export` and `tiarca.last_message` are the only Android assistance.

Android automatic backup excludes journal archives for privacy; export before uninstall or migration. Exports may contain third-party messages and identifying details; only share carefully.

## Limitations

Events may be dropped if Android interrupts Lua, a queue fills or SQLite lookup fails. IRC MODE +b does not prove a user was expelled. An AutoMod decision does not prove the server applied it; observed server actions are distinct records. The history window is limited to 200 channel messages, and message text is bounded by IPC.

See [PR #289](https://github.com/TIARCA/TIARCA/pull/289) for CI and automated tests. Device validation remains **not verified** until performed.
