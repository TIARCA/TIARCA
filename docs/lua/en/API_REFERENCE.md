# Lua API reference

[Guide](README.md) · [Italiano](../it/API_REFERENCE.md) · [Events](EVENTS.md) · [Permissions](PERMISSIONS.md)

**Only documented, implemented APIs.** Calls are scoped by event, consent, and current network. Asynchronous results are not immediately available after requesting them.

| API | Arguments and result / effect | Requirements |
|---|---|---|
| `tiarca.on(event, callback)` | Registers callback for a [supported event](EVENTS.md) | None |
| `tiarca.log(text)` | Writes to the script's local log | None |
| `tiarca.send(target, text)` | Requests a send to the same conversation as the event | Send; rate limits |
| `tiarca.pm(nick, text)` | Requests a private message to the allowed channel-message sender | Send; compatible event |
| `tiarca.close_pms()` | Closes script-owned PVTs, not manual tabs | Local `mod close`; multi-selection not in #303 |
| `tiarca.notice(nick, text)` | Like `pm`, using NOTICE | Send; compatible event |
| `tiarca.identity_v2()` | Per-VM opt-in; initialization only | None |
| `tiarca.get_channels()` / `tiarca.get_channels(token)` | Legacy snapshot max 32; v2 identity reply | Legacy callback; v2 local command |
| `tiarca.get_user(channel, nick, token)` | V2 asynchronous point lookup | Identities; local command or same-channel IRC event |
| `tiarca.get_users(channel)` / `tiarca.get_users(channel, options)` | Legacy max 128; v2 filtered and paginated | Identities; legacy current channel, v2 local command or continuation |
| `tiarca.refresh_users(channel)` | Requests asynchronous WHO/WHOX; one request/minute/script | Identities; local command |
| `tiarca.maskfind(channel, pattern)` | Structured WHO/WHOX search with local results; does not immediately return matches | Identities; explicit command |
| `tiarca.show(title, text)` | Local result: 1–96 byte title, 1–512 byte text | Within callback |
| `tiarca.show_list(title, items)` | Local list of 1–16 strings, up to 160 bytes each and 512 total | Within callback |
| `tiarca.storage.get(key)` | Persistent string or `nil` | Persistent data |
| `tiarca.storage.set(key, value)` | Requests UTF-8 string storage | Persistent data |
| `tiarca.storage.delete(key)` | Requests deletion | Persistent data |
| `tiarca.journal.append(json)` | Append one private JSONL object line on the current network, no circular event limit | Persistent data; callback |
| `tiarca.journal.stats()` | Asynchronous file bytes and bounded write/error counters via `journal_status` | Persistent data; local command |
| `tiarca.journal.export()` | Open Android share chooser for the current network journal | Persistent data; local command |
| `tiarca.journal.rotate()` | Safely archives the active JSONL and requests Android sharing; future entries start a fresh journal | Persistent data; local command |
| `tiarca.journal.export_archive()` | Re-share the latest durable archive if sharing was dismissed | Persistent data; local command |
| `tiarca.last_message(channel, nick, token)` | Search last 200 public SQLite channel messages; return asynchronous `history` event only to requester | Persistent data + Identities; from `kick`, `ban`, `moderation_log`; same channel |
| `tiarca.kick(channel, nick, reason)` | Requests KICK | Moderation; OP |
| `tiarca.eject(channel, nick, reason)` | Manual compound removal: two host-scoped `6h` `TBAN`s (`N:*!*@reduced-host` to prevent nick changes; `m:U:*!*@reduced-host` to mute), optional `-v`, KICK, plus non-expiring `R:account` when known, `nick!*@*`, and `account!*@*` when distinct. Never sets or clears channel-wide `+N`. Requires advertised InspIRCd `N`, `m`, `U` (and `R` for accounts) and known host | Identities and Moderation; OP; local command only |
| `tiarca.ban(channel, nick)` | Requests observed-host ban | Moderation; OP |
| `tiarca.mode(channel, nick, mode)` | Only `+o`, `-o`, `+v`, `-v` | Moderation; OP |
| `tiarca.mute(channel, nick)` | Requests mute with supported IRC dialect | Moderation; OP |
| `tiarca.tban(channel, nick, duration)` | Requests timed ban (e.g. `"3h"`) | Moderation; OP; supported dialect |
| `tiarca.tmute(channel, nick, duration)` | Server-managed timed mute; V4 prefers observed R: account if supported, otherwise exact host; legacy host | Moderation; OP; supported dialect |
| `tiarca.kickban(channel, nick, reason)` | KICK + ban as one validated request | Moderation; OP |
| `tiarca.kicktban(channel, nick, duration, reason)` | KICK + timed ban validated together | Moderation; OP |
| `tiarca.blacklist_action(channel, nick, account, action, duration)` | `kick`, `ban`, `mute`, `tban`, `bankick`, `tbankick`; client verifies account | Moderation and identities; additional constraints |

| `tiarca.override_action(channel, nick, account, action, duration)` | Alias of blacklist_action | Same requirements |

### Identity fields

`get_users`, `event.sender` and identity replies expose `nick`, `prefix`, and **only when observed** `ident`, `host`, `account`, `mask`. `account_known` distinguishes unknown (`nil`) from logout (`"*"`). Missing data is `nil`; a host may be cloaked. Never construct bans from unknown hosts.

### Persistence

Keys: 1–48 ASCII characters (letters, digits, `_`, `.`, `-`); values: 1–8192 UTF-8 bytes. State is shared between approved networks of **the same script**. Max 32 keys, 128 KiB total, JSON max 160 KiB. Storage-enabled events serialized per script across networks; atomic commit before effects, no write if unchanged. See [limits and security](PERMISSIONS.md).

### Example: local command

```lua
tiarca.on("command", function(event)
  if event.text == "ping" then
    tiarca.show("TIARCA", "pong")
  end
end)
```

Type `/lua ping`. No IRC message is sent.

**Note:** APIs do not promise delivery/ban confirmation. For full semantics, quotas, and preconditions consult the [technical source](../../LUA_SCRIPTING_MVP.md); Java/C implementation remains authoritative.

V4 also prefers observed account for timed bans if supported. V4 voice gate limited to `automod_v4.lua`, sender and consents; halfops and above protected in ordinary V4 moderation. [Migration](MIGRATION.md) for scope, tokens, filters and limits.

For v2 roster requests, optional `state=true` adds `event.channel_modes` on the first result page (bounded local observations only; `channel_modes_partial=true`). Keep the option identical for continuation pages. Values that may contain channel passwords are redacted. `event.user` and `event.users` include `realname` when observed.
