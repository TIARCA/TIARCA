# Permissions and security

[Guide](README.md) · [Italiano](../it/PERMISSIONS.md)

Imported scripts start **disabled**. Users explicitly select networks and grant optional capabilities separately. Importing a file does not grant consent.

| Capability | Enables | Examples |
|---|---|---|
| No additional consent | Events, logs, channel summaries, local output | `tiarca.on`, `log`, `get_channels`, `show` |
| Send messages | Replies in the approved conversation; PM/NOTICE only to the allowed event sender | `send`, `pm`, `notice` |
| Channel members and identities | Member snapshots, consented WHO, mask search | `get_users`, `refresh_users`, `maskfind` |
| Moderation | Structured actions subject to operator privileges and host validation | `kick`, `ban`, `mute`, `tban`, `kickban`, `eject` |
| Persistent script data | Private per-script text state | `storage.get/set/delete` |
| Script JSONL journal | Incremental private storage per script/network; historic last-message lookup additionally needs Identities | `journal.append/export/rotate/export_archive`, `last_message` |

Key limits: **8 scripts** maximum, **24,576 UTF-8 bytes** per source, up to **16 approved networks** per script; **2 MiB** per VM, **200,000 instructions** per load/event, **8 callbacks** and **12 actions** per event. Sends are rate-limited and may be dropped; moderation actions are requests, **not proof that IRC executed them**.

`io`, `os`, `package`, `debug`, `require`, `load`, `loadfile`, `dofile`, `string.dump`, `print`, and `collectgarbage` are unavailable. No arbitrary filesystem, sockets, Java/Android, or raw IRC. APIs enforce network/channel scope, consent, and observed identity data.

Backups retain eligible scripts and state but **revoke all approvals** on restore; users must reauthorize them. For quotas and edge cases see the [technical notes](../../LUA_SCRIPTING_MVP.md).
