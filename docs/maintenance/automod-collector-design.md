# AutoMod Collector — implementation contract (draft)

Status: design only; implementation and device verification pending.

## Scope
Optional collection of AutoMod warnings, kicks and bans, **plus observed KICK and MODE +b events from other operators and bots**. Keep the initiator, reason/mask, target, and last preceding public message of the kicked user. The detector's decisions and thresholds must not change.

## Verified baseline
- `docs/lua-examples/automod_antiripetizione.lua` currently stores normalized signatures `{sig,n,t}`, not original message text.
- `tiarca.on("message")` provides server UUID, channel, nick, text and Unix timestamp.
- `tiarca.get_users(channel)` may expose ident, host, account, mask when observed; realname is not documented.
- `tiarca.storage` has a maximum of 32 keys, 8192 bytes per value and 128 KiB total per script. It is unsuitable for an unbounded event archive.
- Script requests for moderation are not proof that the IRC server performed the action.
- Lua scripts run isolated without arbitrary Android file access.

## Proposed integration
1. Reuse TIARCA's existing channel message history rather than duplicating it in a Lua message buffer. Add a narrowly scoped, read-only app-side lookup for the latest public message by target nickname strictly before the KICK timestamp, scoped to the same network and channel. Handle nick changes, missing/unloaded history, identical nicknames on different networks and ordering ties; return null when uncertain. Determine the existing history storage interface before coding.
2. Observe inbound server KICK and MODE +b in the main IRC event pipeline, including events initiated by other clients and bots; Lua's currently documented event list does not expose kick/mode. Do not infer bot vs human without reliable metadata. Add a narrowly scoped collector event action to the Lua bridge for AutoMod-generated warnings and action requests; correlate without double-counting observed KICK/MODE. Validate origin, permissions and scope. Never interpret arbitrary log lines as trusted collection events.
3. Append a versioned JSONL record using Android private app storage; isolate records by server UUID and channel. Bound record size and total archive size; reject malformed UTF-8 and unexpected fields.
4. Add user-initiated export via Android document picker (SAF), with explicit privacy warning, and explicit confirmed clear. Do not automatically include collected identities in normal backups.
5. Add `/lua mod collect on|off|status|export|clear`; default OFF, configuration persistent per network. Collector failures must not affect moderation.
6. Record `requested` vs `observed` actions separately, with a stable correlation ID when possible; if no reliable server acknowledgement exists, report only `requested`. Do not infer success from sending an IRC command. For +b store the mask and setter; a ban need not cause a kick. For KICK store the last preceding public message as context, **not as a proven cause**.
7. Preserve original text and normalized text (for AutoMod triggers), match percentage, threshold, occurrence count, time window, action, actor nickname, target nickname, ident, host, account, realname only if actually observed, server, channel, timestamp, reason/ban mask, and last preceding public message with its own timestamp when available. Use null for missing identity fields. Label origin as AutoMod only with proven correlation; otherwise external/unknown, with bot vs human left unknown unless independently verified.

## Validation requirements
- Unit tests for JSON escaping, limits, append/restart, duplicate IDs, export, clear, partial writes, history lookup across channels/networks, and kick/ban correlation.
- Lua host regression tests for trigger-at-third-message, escalation, combined actions, disabled collection, and collector failure.
- Android manual tests: two networks, reconnect, restart, dark/light themes, export permissions, device rotation and backup/restore.
- CI green for Standard and F-Droid before merge.
- Do not bump version or publish release.
