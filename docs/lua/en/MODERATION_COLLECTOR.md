# Moderation Collector V2 — standalone Lua script

[Lua guide](README.md) · [Italiano](../it/MODERATION_COLLECTOR.md) · [Lua source](../../lua-examples/moderation_collector.lua)

**Availability:** [PR #289](https://github.com/TIARCA/TIARCA/pull/289); not part of stable v0.9.10. Real-device testing: **not verified**.

## Events captured

All collection decisions, event selection, SQLite lookup requests and JSONL serialization happen in **moderation_collector.lua**. The Android host only exposes generic Lua facilities; it performs no Collector-specific work when the script is disabled.

- AutoMod **monitor, warning, kick, ban, mute, timed ban and combined actions**, recognized even when the AutoMod script was imported under a different filename (for example `automod.lua`), explicitly marked as **requested, unconfirmed** decisions.
- Server-observed KICK from any operator/bot, with **actor**, **target nickname** and any **reason**.
- Server-observed MODE +b, with actor and mask; no reason is transmitted in that protocol command. A nickname is attached only when identifiable without ambiguity.
- The target's **latest earlier public message**, if found in the last **200 messages of the channel's existing SQLite history**. The Lua script requests this on demand after any qualifying event, including AutoMod.

The stored message's original ident/host take precedence when available. Each event and last-message object has channel, timestamp, nickname, observed ident/host/account (otherwise `null`). Previous chat is context, **not proof of a moderation reason**. Ambiguous ban masks do not receive guessed nicknames.

## Collector V2: context and repetition shadow analysis

V2 remains an **external Lua script**. It does not modify AutoMod or send moderation actions. While ON, it keeps up to **5 NORMAL/ME messages** per network/channel/nickname in **RAM**, for up to **96 identities globally**, for a **30-minute window** (maximum **320 UTF-8 bytes of text** per message). It saves this context to disk only when a moderation event or a shadow candidate occurs. The temporary buffer is cleared on script shutdown, disconnection and restart.

An experimental **shadow-only detector** compares NORMAL messages containing at least **3 normalized words** with the author's five most recent buffered messages. It uses **75% word overlap**, without taking order into account. It appends `event: "candidate"` with one of these stages:

- **2 similar messages:** `candidate_stage: "repeat_2"` (first repetition observed).
- **3 similar messages:** `candidate_stage: "would_warn"` (hypothetical warning on message 3).
- **4 similar messages:** `candidate_stage: "would_mute_120s"` (hypothetical 120-second mute on message 4).

These events **never send IRC commands**. No vocabulary filters are active yet; this detector is only an exploratory approximation, not AutoMod's exact algorithm (which also handles mentions, exemptions and custom settings). The buffer may undercount earlier repetitions.

The JSONL schema is now `schema: 2`; all V1 fields are kept. New fields: `context_messages` (the messages available at event time), `history_status` (e.g. `sqlite_found`, `not_found_in_last_200`, `target_unresolved`, `history_timeout`, `history_request_failed`, `not_queried_shadow`), `message_age_seconds`, `event_id`, `case_id`, `correlation`, `candidate_stage`, `candidate_occurrences`, `candidate_similarity_percent`. If SQLite has no match, `last_message` falls back to buffered context when available, with `source: "live_buffer"`. The status remains explicit.

An observed KICK within **10 seconds** of a request from AutoMod for the same nickname/channel may share its `case_id`, with `correlation: "same_nick_10s"`. This means **temporal association, not confirmed server execution**. Ambiguous ban masks are never linked to an invented nickname.
## Setup and commands

Import [moderation_collector.lua](../../lua-examples/moderation_collector.lua) into TIARCA's Lua scripts. Enable it on the selected network(s) and grant **Persistent data** and **Read users/identities**. Neither sending messages nor moderation authorization is necessary.

**Collection starts as soon as the script is enabled.** There is no host-side Collector switch. The optional script-owned settings are:

| Command | Behavior |
|---|---|
| `/lua collector on` | Resume collection on this network, persisted |
| `/lua collector off` | Pause collection without deleting archived records |
| `/lua collector status` | Show ON/OFF |
| `/lua collector export` | Share the current journal without clearing it |
| `/lua collector exportreset` | Show the required explicit confirmation syntax |
| `/lua collector exportreset confirm` | Archive the active journal, start a new one and open Android sharing |
| `/lua collector archive` | Re-share the previous archive after a cancelled share |
| `/lua collector channels` | Display monitored channels (all by default) |
| `/lua collector channels add #channel` | Add a channel and switch to an allowlist |
| `/lua collector channels del #channel` | Remove a channel; empty selection means no channels |
| `/lua collector channels all` | Restore all-channel monitoring |
| `/lua collector channels none` | Monitor no channels while script remains enabled |
| `/lua collector` | Show state; `/lua collector help` lists commands |

Channel selection is **persistent per network** and independent of AutoMod. Every recorded event retains the actual IRC channel, including with a filter.

Confirmed journal rotation **does not destroy prior records**: the active JSONL is moved into a durable private archive before Android sharing begins. If the chooser is cancelled, `/lua collector archive` can share the latest archive again. The active journal alone starts fresh, without affecting other networks.

Disabling the script in TIARCA stops all callbacks and history requests. With `off`, the script does not request history or append records.

## Format, retention and privacy

One append-only JSONL record per event (`schema: 2`). Fields: `schema`, `server`, `channel`, `timestamp`, `event`, `action`, `nickname`, `ident`, `host`, `account`, `operator`, `reason`, `ban_mask`, `source_script`, `similarity_percent`, `infraction_level`, `execution`, `identity_match`, `last_message` (nested message, identity, timestamp, or `null`).

The private journal is scoped by **script UUID and network UUID**, grows incrementally and has no circular truncation at 32, 128 or any other event count. Generic APIs `tiarca.journal.append/export` and `tiarca.last_message` are the only Android assistance.

Android automatic backup excludes journal archives for privacy; export before uninstall or migration. Exports may contain third-party messages and identifying details; only share carefully.

## Limitations

Events may be dropped if Android interrupts Lua, a queue fills or SQLite lookup fails. IRC MODE +b does not prove a user was expelled. An AutoMod decision does not prove the server applied it; observed server actions are distinct records. The history window is limited to 200 channel messages, and message text is bounded by IPC.

See [PR #289](https://github.com/TIARCA/TIARCA/pull/289) for CI and automated tests. Device validation remains **not verified** until performed.
