# Lua events

[Guide](README.md) · [Italiano](../it/EVENTS.md)

Register events with `tiarca.on("name", function(event) ... end)`. The callback receives a table; **do not assume** every field is populated.

| Event | Trigger | Notes |
|---|---|---|
| `connected` | IRC connection established | No channel required |
| `disconnected` | Disconnection | Moderation unavailable |
| `message` | Normalized incoming message | NORMAL, NOTICE, or ME; excludes own messages |
| `command` | Locally typed `/lua ...` | `event.text` is the text following `/lua` |
| `join` | Member joins a channel | Passive event |
| `part` | Member leaves a channel | Passive event |
| `quit` | User quits network | May yield callbacks for multiple channels |
| `nick` | Nickname change | `event.nick` old nickname; `event.text` new nickname |
| `kick` | Observed channel KICK | `event.nick` actor, `event.message_type` kicked nickname, `event.text` reason; no proof of who initiated an automated action |
| `history` | Asynchronous `tiarca.last_message` result | `event.nick` target, `event.text` latest message or empty, `event.message_type` request token, `event.timestamp` message time or 0; requester only; `event.ident`/`event.host` if stored with the SQLite message |
| `moderation_log` | Recognized moderation report from another script | `event.nick` target, `event.text` report, `event.message_type` originating script name; not an IRC confirmation |
| `ban` | Observed channel MODE +b | `event.nick` setter, `event.text` ban mask; one event per added mask, not proof of an expulsion |
| `identity` | Async v2 reply | Requester only: token, query (user/users/channels), status (ok/not_observed/unavailable), total, complete, next_offset when present, user/users or channels |
| `mode` | Observed channel mode entry | `message_type` signed mode and entry type (e.g. `+b:LIST`); `text` parameter, empty for redacted key/exact-unset values; choose either mode or legacy ban to avoid duplicates |
| `topic` | Observed TOPIC with sender | `text` new topic |
| `own_message` | Local user public message observation | Separate from `message`, never triggers legacy message callbacks; not a delivery guarantee |
| `account` | Observed IRCv3 ACCOUNT | Identities consent; `sender.account` is new account or `*`; shared channels only |
| `chghost` | Observed IRCv3 CHGHOST | Identities consent; `sender.ident/host` are new values; shared channels only |
| `journal_status` | Scoped journal stats or append error | Storage consent; `message_type` stats/error/sandbox_gap; sandbox_gap text contains duration ms, global unavailable ingress, global cancelled slots (best effort after recovery); stats text contains bytes, writes, failures, global input-full, slot-full, IPC-failed counts; error text is `append_failed`; no journal append in callback |
| `timer` | Approximately 60-second tick | Only with running IRC service and connected network; not an accurate alarm |

Common fields: `type`, `server` (server configuration UUID, **not** hostname), `channel`, `nick`, `text`, `message_type` (`normal`, `notice`, `me`), `timestamp` (Unix seconds). Fields are event-dependent and may be empty.

```lua
tiarca.on("message", function(event)
  if event.channel == "#example" and event.nick then
    tiarca.log(event.nick .. ": " .. (event.text or ""))
  end
end)
```

Events may be dropped when queues fill or Android suspends/restarts processes. Do not use the timer for precise deadlines. Technical source: [LUA_SCRIPTING_MVP.md](../../LUA_SCRIPTING_MVP.md).

V2: `event.sender` may be absent; authorized observed data, captured at receipt for live messages. Delivery depends on subscriptions. `identity` is not a new user command. [Migration](MIGRATION.md).

`source_id` is an opaque observation ID on `message`, `own_message` and a moderation report derived from that message. It identifies the triggering observation across scripts, not an IRC msgid or proof of sanction. `sender.realname` is present only when observed, with identities consent; unknown is nil.

V2 roster: `users[].realname` or `user.realname` when observed. For `state=true`, first page may carry `channel_modes` entries `{mode,param,kind}` and `channel_modes_partial=true`. No full IRC list guarantee.

For AutoMod v4 (`automod_v4.lua`) with identities consent and V2 opt-in, `event.mentions` may contain a map of original tokens verified against the local channel index (key = token without trailing `,:;.!?`, value = `true`). At most 64 tokens examined and 32 matches; sender excluded. No IRC request or full roster. An absent field or key does not prove the nickname is absent from the channel; the original message is unchanged.
