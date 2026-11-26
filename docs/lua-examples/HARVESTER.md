# Harvester V1 — prototipo / prototype

**Draft, based on PR #303; implementation and acceptance testing in PR #304.** Do not run the old Collector alongside Harvester for a measurement campaign. Harvester is independent from AutoMod, does not issue moderation or send requests, and starts OFF with no selected channels.

## Utilizzo / usage

Import `docs/lua-examples/harvester.lua` on an identity-v2 Candidate. Approve only the desired networks, identities and persistent data/journal. Never grant send or moderation for Harvester. Use a channel or server tab of the network:

- `/lua harvester add #channel`, `drop #channel` — explicit inclusion/exclusion, max 32 channels per network.
- `/lua harvester on`, `off`, `status`, `stats` — persistent on/off and current-VM counts of queued records and local rejections. `stats` also requests actual current file bytes, write/error counts and global Lua engine queue/slot/IPC failure counters.
- `/lua harvester channels [page]` — six channel names per page.
- `/lua harvester snapshot [#channel]` — cached roster only, paginated; single-channel start/add attempts a snapshot automatically when the command can query that channel. Other channels require a command. No WHO/WHOIS request; no immutable snapshot guarantee.
- `/lua harvester export` — current network journal; `clear confirm` or `exportreset confirm` safely rotates and shares, preserving the latest archive; `archive` re-shares it. No irreversible deletion command exists in this API.
- `/lua harvester help`.

Raccogliere solo canali autorizzati, per pochi giorni, per lo scopo dichiarato. Non condividere gli archivi pubblicamente. Messaggi privati e credenziali non sono oggetto di raccolta; contenuti sensibili eventualmente scritti nei canali pubblici non vengono filtrati automaticamente. Le impostazioni ripristinate richiedono nuova autorizzazione del motore.

## Format v1

One JSON object per line. Journal is per script/network. Numeric IDs are scoped to the network journal, `epoch` (durable monotonic VM incarnation) and `segment` (RAM-bound dictionary generation). Never join independent journals by ID alone. The first use of a value emits its definition inside the same line's `defs` array, before references are interpreted. Definitions use `[id, category, value]`. Categories: server (configuration UUID), channel, nick, ident, host, account, realname, identity, source_session. Identity value is an array of nick/ident/host/account/realname dictionary IDs. 0 means unknown; empty strings are distinct from unknown; observed logout is `*`. A new combination creates a new identity; no historical overwrite.

Records have `v,epoch,segment,id,ts,s,ch,type`; timestamp is the host Unix-second timestamp. Message body is stored once per observed occurrence. Identical utterances are not collapsed. Prefix is per-event/per-channel, not part of identity. V4 reports store actual rule, score, threshold, occurrence count, percent, escalation level, action and up to two preceding source references; other recognized reports retain text. `source_id` and `previous_sources` contain pairs [source_session dictionary ID, source sequence], resolved within that record's epoch/segment. The triggering observation ID is shared by message and report. `previous_sources_complete` is false when earlier observations are missing or exceed the bound. Decisions are distinct from observed KICK/MODE; no inferred causal attribution or matching by mask. Snapshot members are linked by token, not treated as observed joins. Snapshot completeness is reported, not assumed.

The script discards dictionary caches after 2,048 entries or 192 KiB of key strings (shared across networks; a callback may add a few more). It then starts a new segment and defines values again; this prevents unbounded RAM growth. Rotation also starts a new segment. A VM restart reserves a new epoch through persistent storage. The epoch reservation is committed before effects by redesign D. The limits are conservative and have not been benchmarked on Android.

One journal append per ordinary event. A snapshot page requests eight members, then continues asynchronously, so it stays within the 12-action limit. No text classification, per-message roster query, WHO, or duplicated message context. Local append rejections are counted and the next surviving record carries `local_gap`; the sequence may also contain gaps. Host append errors generate a scoped best-effort `journal_status` notification; Harvester starts a new segment and puts `disk_gap` in the next surviving record. Errors may themselves be lost. File byte count and write/error counters can be requested; engine queue/slot/IPC counters are process-wide, not per channel or exact per-script loss accounting. Do not interpret these statistics as proof of complete collection. A decoder must reject or mark unresolved references instead of inventing identities.

After a sandbox interruption the host may send a best-effort recovery notification; the updated Harvester attaches a `sandbox_gap` object to its next ordinary record with `duration_ms`, `global_ingress_unavailable` and `global_slots_cancelled`. These are global engine counts, not unique messages or exact per-channel losses. A notification or the next record may itself be lost; epochs remain an independent indicator. Install the updated script to record this field.

## Extended API / remaining limitations

- Passive incoming and own public message observations, MODE entries (including -b and known mute extbans), TOPIC with sender, KICK, JOIN/PART/QUIT/NICK, IRCv3 ACCOUNT/CHGHOST and connection events. Own observations are separate from ordinary message callbacks. Legacy ban events remain available, but Harvester subscribes only to mode. Channel key and exact-unset values are redacted.
- Observed realname (extended JOIN, WHO/WHOIS replies already received) is available on sender and v2 roster rows; unknown is nil, an observed empty value is distinct. No automatic WHO/WHOIS.
- Optional roster `state=true` adds a bounded first-page snapshot of locally observed public modes; up to 64 rows and 2 KiB of estimated raw payload. Marked partial because the local cache is not proof of complete current server state or ban lists. No channel key is exported. One automatic initial snapshot on single-channel start/add when command scope permits; multiple channels and reconnect snapshots require explicit commands. Roster pages are live, not atomic.
- Source IDs correlate the triggering observation with recognized moderation reports. V4 preserves only the latest two preceding repetition IDs or one announcement ID, plus rule/score/threshold/count; no changes to sanctions. No general proof of which manual kick or ban followed a message. Config changes, protected/whitelisted non-decisions and all sanction expiry events are not comprehensively reported.
- Journal error feedback and actual size are implemented; no per-record success acknowledgement, no exact per-script/channel accounting of engine losses, no background polling. Counters reset with host/VM and bounded cache; archived bytes are not included in active file size. Clear rotates, not erases.

No new dependency, Android permission, version, tag or release. This branch changes generic engine interfaces and adds native/Java regression cases, including privacy and realname, source ID, cached modes, failure feedback and updated native V4 stress input. CI/reproducibility and real-device acceptance must be checked before merge or Candidate use.

Sources: [#303](https://github.com/TIARCA/TIARCA/pull/303), [#304](https://github.com/TIARCA/TIARCA/pull/304), [events](../lua/en/EVENTS.md), [API](../lua/en/API_REFERENCE.md), [migration](../lua/en/MIGRATION.md).
