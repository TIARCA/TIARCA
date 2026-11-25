# Harvester V1 — prototipo / prototype

**Draft, based on PR #303; not the complete agreed V1.** Do not run the old Collector alongside Harvester for a measurement campaign. Harvester is independent from AutoMod, does not issue moderation or send requests, and starts OFF with no selected channels.

## Utilizzo / usage

Import `docs/lua-examples/harvester.lua` on an identity-v2 Candidate. Approve only the desired networks, identities and persistent data/journal. Never grant send or moderation for Harvester. Use a channel or server tab of the network:

- `/lua harvester add #channel`, `drop #channel` — explicit inclusion/exclusion, max 32 channels per network.
- `/lua harvester on`, `off`, `status`, `stats` — persistent on/off and current-VM counts of queued records and local rejections (not verified disk byte counts).
- `/lua harvester channels [page]` — six channel names per page.
- `/lua harvester snapshot [#channel]` — cached roster only, paginated; single-channel start/add attempts a snapshot automatically when the command can query that channel. Other channels require a command. No WHO/WHOIS request; no immutable snapshot guarantee.
- `/lua harvester export` — current network journal; `clear confirm` or `exportreset confirm` safely rotates and shares, preserving the latest archive; `archive` re-shares it. No irreversible deletion command exists in this API.
- `/lua harvester help`.

Raccogliere solo canali autorizzati, per pochi giorni, per lo scopo dichiarato. Non condividere gli archivi pubblicamente. Messaggi privati e credenziali non sono oggetto di raccolta; contenuti sensibili eventualmente scritti nei canali pubblici non vengono filtrati automaticamente. Le impostazioni ripristinate richiedono nuova autorizzazione del motore.

## Format v1

One JSON object per line. Journal is per script/network. Numeric IDs are scoped to the network journal, `epoch` (durable monotonic VM incarnation) and `segment` (RAM-bound dictionary generation). Never join independent journals by ID alone. The first use of a value emits its definition inside the same line's `defs` array, before references are interpreted. Definitions use `[id, category, value]`. Categories: server (configuration UUID), channel, nick, ident, host, account, realname, identity. Identity value is an array of nick/ident/host/account/realname dictionary IDs. 0 means unknown; empty strings are distinct from unknown; observed logout is `*`. A new combination creates a new identity; no historical overwrite.

Records have `v,epoch,segment,id,ts,s,ch,type`; timestamp is the host Unix-second timestamp. Message body is stored once per observed occurrence. Identical utterances are not collapsed. Prefix is per-event/per-channel, not part of identity. Raw report text from `moderation_log` is retained, distinct from observed KICK/BAN. No inferred causal attribution or matching by mask. Snapshot members are linked by token, not treated as observed joins. Snapshot completeness is reported, not assumed.

The script discards dictionary caches after 2,048 entries or 192 KiB of key strings (shared across networks; a callback may add a few more). It then starts a new segment and defines values again; this prevents unbounded RAM growth. Rotation also starts a new segment. A VM restart reserves a new epoch through persistent storage. The epoch reservation is committed before effects by redesign D. The limits are conservative and have not been benchmarked on Android.

One journal append per ordinary event. A snapshot page requests eight members, then continues asynchronously, so it stays within the 12-action limit. No text classification, per-message roster query, WHO, or duplicated message context. Local append rejections are counted and the next surviving record carries `local_gap`; the sequence may also contain gaps. Host ingress/IPC drops and downstream disk-write failures are not acknowledged to the script; do not interpret these statistics as proof of complete collection. A decoder must reject or mark unresolved references instead of inventing identities.

## Remaining blockers before final V1

The verified #303 contract exposes incoming normal/notice/me messages (not own outgoing messages), JOIN/PART/QUIT/NICK, KICK, MODE +b, moderation_log, connection events and paginated cached rosters. The script records these when in scope. Missing or incomplete: realname source (dictionary reserved but normally 0), ACCOUNT/CHGHOST as standalone events, all MODE incl. -b, mute/unmute classification, TOPIC, full initial channel modes/ban lists, complete automatic snapshot of multiple channels/reconnect, stable message IDs shared across scripts and structured AutoMod V4 decision reports. Do not manufacture unavailable data. Current AutoMod text reports do not prove a particular message caused a decision. Actual journal byte count and host-drop accounting require generic host acknowledgement/telemetry. Clear is rotation, not erasure.

No runtime changes, dependency changes or release/version changes in this prototype. Native acceptance tests are added for opt-in, repeat identity, independent utterances, private exclusion and epoch restart; CI and Android validation must be checked. Full V1 needs generic, consented host additions, not a hardcoded Harvester engine.

Sources: [#303](https://github.com/TIARCA/TIARCA/pull/303), [events](../lua/en/EVENTS.md), [API](../lua/en/API_REFERENCE.md), [migration](../lua/en/MIGRATION.md).
