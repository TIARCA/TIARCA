# AutoMod Collector — implementation contract (draft)

Status: design only; implementation and device verification pending.

## Scope
Optional collection of AutoMod detection events for warning, kick and ban (including escalation and combined kick/ban), preserving the original offending text and the contributing historical messages. The detector's decisions and thresholds must not change.

## Verified baseline
- `docs/lua-examples/automod_antiripetizione.lua` currently stores normalized signatures `{sig,n,t}`, not original message text.
- `tiarca.on("message")` provides server UUID, channel, nick, text and Unix timestamp.
- `tiarca.get_users(channel)` may expose ident, host, account, mask when observed; realname is not documented.
- `tiarca.storage` has a maximum of 32 keys, 8192 bytes per value and 128 KiB total per script. It is unsuitable for an unbounded event archive.
- Script requests for moderation are not proof that the IRC server performed the action.
- Lua scripts run isolated without arbitrary Android file access.

## Proposed integration
1. Keep original text in a bounded in-memory companion record alongside the normalized signature, including historical messages that match the trigger. Do not persist full messages in the existing AutoMod history, which has strict quotas.
2. Add a narrowly scoped collector event action to the Lua bridge; validate origin, script permission, event context, channel and nick. Never interpret arbitrary log lines as trusted collection events.
3. Append a versioned JSONL record using Android private app storage; isolate records by server UUID and channel. Bound record size and total archive size; reject malformed UTF-8 and unexpected fields.
4. Add user-initiated export via Android document picker (SAF), with explicit privacy warning, and explicit confirmed clear. Do not automatically include collected identities in normal backups.
5. Add `/lua mod collect on|off|status|export|clear`; default OFF, configuration persistent per network. Collector failures must not affect moderation.
6. Record `requested` vs `confirmed` actions separately; if no reliable server acknowledgement exists, report only `requested`. Do not infer success from sending an IRC command.
7. Preserve original text and normalized text, match percentage, threshold, occurrence count, time window, action, nickname, ident, host, account, realname only if actually observed, server, channel, timestamp, and last preceding public message when available. Use null for missing identity fields.

## Validation requirements
- Unit tests for JSON escaping, limits, append/restart, duplicate IDs, export, clear, and partial writes.
- Lua host regression tests for trigger-at-third-message, escalation, combined actions, disabled collection, and collector failure.
- Android manual tests: two networks, reconnect, restart, dark/light themes, export permissions, device rotation and backup/restore.
- CI green for Standard and F-Droid before merge.
- Do not bump version or publish release.
