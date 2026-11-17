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
