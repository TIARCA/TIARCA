# Lua API reference

[Guide](README.md) · [Italiano](../it/API_REFERENCE.md) · [Events](EVENTS.md) · [Permissions](PERMISSIONS.md)

**Only documented, implemented APIs.** Calls are scoped by event, consent, and current network. Asynchronous results are not immediately available after requesting them.

| API | Arguments and result / effect | Requirements |
|---|---|---|
| `tiarca.on(event, callback)` | Registers callback for a [supported event](EVENTS.md) | None |
| `tiarca.log(text)` | Writes to the script's local log | None |
| `tiarca.send(target, text)` | Requests a send to the same conversation as the event | Send; rate limits |
| `tiarca.pm(nick, text)` | Requests a private message to the allowed channel-message sender | Send; compatible event |
| `tiarca.notice(nick, text)` | Like `pm`, using NOTICE | Send; compatible event |
| `tiarca.get_channels()` | Array of `{name, users}`; `.truncated` beyond 32 channels | Within callback |
| `tiarca.get_users(channel)` | Observed members array; `.truncated` beyond 128; missing fields are `nil` | Identities; current channel |
| `tiarca.refresh_users(channel)` | Requests asynchronous WHO/WHOX; one request/minute/script | Identities; local command |
| `tiarca.maskfind(channel, pattern)` | Structured WHO/WHOX search with local results; does not immediately return matches | Identities; explicit command |
| `tiarca.show(title, text)` | Local result: 1–96 byte title, 1–512 byte text | Within callback |
| `tiarca.show_list(title, items)` | Local list of 1–16 strings, up to 160 bytes each and 512 total | Within callback |
| `tiarca.storage.get(key)` | Persistent string or `nil` | Persistent data |
| `tiarca.storage.set(key, value)` | Requests UTF-8 string storage | Persistent data |
| `tiarca.storage.delete(key)` | Requests deletion | Persistent data |
| `tiarca.journal.append(json)` | Append one private JSONL object line on the current network, no circular event limit | Persistent data; callback |
| `tiarca.journal.export()` | Open Android share chooser for the current network journal | Persistent data; local command |
| `tiarca.journal.rotate()` | Safely archives the active JSONL and requests Android sharing; future entries start a fresh journal | Persistent data; local command |
| `tiarca.journal.export_archive()` | Re-share the latest durable archive if sharing was dismissed | Persistent data; local command |
| `tiarca.last_message(channel, nick, token)` | Search last 200 public SQLite channel messages; return asynchronous `history` event only to requester | Persistent data + Identities; from `kick`, `ban`, `moderation_log`; same channel |
| `tiarca.kick(channel, nick, reason)` | Requests KICK | Moderation; OP |
| `tiarca.eject(channel, nick, reason)` | Manual compound removal: `+N`, optional `-v`, KICK, plus `m:U:reduced-host`, `R:account` when known, `nick!*@*`, and `account!*@*` when distinct. Requires advertised InspIRCd extbans and a known host | Identities and Moderation; OP; local command only |
| `tiarca.ban(channel, nick)` | Requests observed-host ban | Moderation; OP |
| `tiarca.mode(channel, nick, mode)` | Only `+o`, `-o`, `+v`, `-v` | Moderation; OP |
| `tiarca.mute(channel, nick)` | Requests mute with supported IRC dialect | Moderation; OP |
| `tiarca.tban(channel, nick, duration)` | Requests timed ban (e.g. `"3h"`) | Moderation; OP; supported dialect |
| `tiarca.tmute(channel, nick, duration)` | Requests server-managed timed mute of current observed host (`TBAN` + `m:` on InspIRCd); no app-side timer | Moderation; OP; supported dialect |
| `tiarca.kickban(channel, nick, reason)` | KICK + ban as one validated request | Moderation; OP |
| `tiarca.kicktban(channel, nick, duration, reason)` | KICK + timed ban validated together | Moderation; OP |
| `tiarca.blacklist_action(channel, nick, account, action, duration)` | `kick`, `ban`, `mute`, `tban`, `bankick`, `tbankick`; client verifies account | Moderation and identities; additional constraints |

### Identity fields

`get_users` exposes `nick`, `prefix`, and **only when observed** `ident`, `host`, `account`, `mask`. Missing data is `nil`; a host may be cloaked. Never construct bans from unknown hosts.

### Persistence

Keys: 1–48 ASCII characters (letters, digits, `_`, `.`, `-`); values: 1–8192 UTF-8 bytes. State is shared between approved networks of **the same script**. Reads and writes are callback-scoped; overlapping events may briefly see older snapshots. See [limits and security](PERMISSIONS.md).

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
