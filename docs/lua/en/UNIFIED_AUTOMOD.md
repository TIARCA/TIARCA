# AutoMod V4 — Watchdog + Mod

Import [automod_v4.lua](../../lua-examples/automod_v4.lua). Do **not** run it alongside `automod_unificato.lua` or the legacy A/B scripts on the same network: they can issue duplicate sanctions. Harvester/Collector remains a separate optional script.

## Setup

Enable the V4 Lua script on the selected networks and grant persistent storage and user/identity read permission. Sending and moderation permissions are needed for live actions. Add channels using `/lua mod canali add #channel`, inspect `/lua mod stato`, and test in **monitor** mode. No channels are enabled by default. Enable `/lua mod modo live` only after Candidate tests; monitor detections do not create historical Mod infractions.

## Commands

All commands start with **`/lua mod`**:
- `help`, `help watchdog`, `help azioni`, `help whitelist`, `stato`
- `on|off`; `modo monitor|live`
- `motore ripetizioni|annunci on|off` (short aliases `a`/`b`)
- `canali add|drop #channel`, `canali list`
- `parametro somiglianza N` (50–100, default **75**)
- `parametro finestra N` (1–120 minutes, default **15**)
- `parametro credito N` (0–3, default **2**, i.e. a negative credit floor of **−2**)
- `parametro minparole N` (3–12, default **3**)
- `whitelist add|drop NICK`, `whitelist list`
- `reset` (clear network Watchdog history and Mod escalation), `close` (select private tabs to close in Android UI).

## Watchdog decisions

**A / generic repetition:** a third sufficiently similar message triggers (default 75% word-multiset overlap, ignoring token order). Only this engine uses configurable similarity. The algorithm is `200 × shared word occurrences / (words in message 1 + words in message 2)`.

**B / ads and micro-ads:** Boolean classification (yes/no), **second occurrence** triggers. Wording does not need to match; all recognized ads from the same person and channel increment the same score. For example `mxm reale`, `cerco m`, `donne pvt`, `f per conoscenza` and `calabria nessuna` belong to this category. The language rules use combinations of person terms (`f`, `m`, `donna`, `ragazza`, `qualcuna`, etc.) with chat, private contact, search, encounter or geographic terms. A finite geographic vocabulary is used; terms outside the dictionary may not be recognized. Selected one-word phrases (`f?`, `donne?`, `mxm`, `qualcuna?`) and **all-numeric messages** (e.g. `08`, `07 08 09`, `07/08/09`) are micro-ads. Numeric dates may also match; this is a known possible false positive.

**Conversation credit:** each eligible ordinary message of **3 or more words** decreases the score by 1, down to the configured floor (−2 or −3, or 0 for no credit). Ordinary filler such as `ahah ahah ahah` is eligible for now: semantic filtering was deliberately omitted. Exclusions: simple greetings of any length, messages starting with `!`, non-ad messages of one or two words, advertisements, short URL-only or symbol-only messages. `ciao a tutti mi presento sono matteo` can count as conversation, unlike `ciao a tutti`. Watchdog reverses provisional credit on the first generic message if it later proves to belong to a repetition sequence, preventing self-cancellation.

The **15-minute** initial time window is configurable. Scores and reference messages are transient RAM state; the persistent Mod escalation survives script restart. The last generic reference and announcement occurrences expire by their respective timers.

## Identity and safety

Network-scoped identification uses authenticated NickServ account when observed; otherwise full host plus distinctive ident, or exact host when no trustworthy ident exists. Shared-host ambiguity is conservatively skipped. Watchdog counters are **channel-scoped**; Mod escalation is **network identity-scoped**. Halfops and higher are excluded; voiced/registered users can be moderated. Authenticated **Crys/Micaela** is excluded by default. Whitelist additions require a verified account in a monitored channel.

Only sanctioned Mod identities are persisted; normal chatter never occupies permanent identity slots. There is no artificial 360-user identity cap. Lua runtime hard limits remain (24 KiB source; 32 storage keys; 8 KiB/value; 128 KiB total). Storage failure suspends sanctions and is logged, rather than silently evicting users. The Lua roster is capped at 128 snapshots, but TIARCA attempts to include the current sender even when the full roster exceeds that limit; missing identity means no uncertain moderation.

## Existing Mod escalation: unchanged

Within 24h of the latest infraction: (1) private warning, (2) timed mute 2m, (3) timed mute 15m, (4) kick + timed mute 3h, (5) kick + timed ban 24h. The same PVT explanation strings as PR #302 are retained; every kick reason is **`Messaggi ripetuti`**. Level 5 is capped. Sending an IRC moderation command does not prove server enforcement.

`/lua mod close` opens Android's multi-select dialog across PVT tabs, preselecting only sessions recognized as script-created; it sends no IRC message. Attribution may not survive an app restart.

Local `AutoModV4` reports remain readable by the app's moderation reporter, while `WatchdogV4` logs supply the rule and score. Harvester integration is intentionally deferred.

**Still requiring manual Candidate validation:** live-channel classification and false positives, NickServ identity continuity, crowded channels, server-specific mute/ban masks, storage persistence under process death, backup/restore and PVT selection behavior.
