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
