# Identity v2 migration

[Guide](README.md) · [Italiano](../it/MIGRATION.md) · [API](API_REFERENCE.md) · [Events](EVENTS.md)

PR #303 redesign, not available in published v0.9.10. Check your build.

## Compatibility and sender

Legacy: synchronous snapshots up to 128 current-channel members and 32 channels. Opt-in `tiarca.identity_v2()` at initialization changes both snapshot APIs to asynchronous requests per VM. Do not add it without adapting calls. No new permission, but Identities remains required for sender and members. V2 receives no automatic roster. Use observed sender, not a query per message.

```lua
tiarca.identity_v2()
tiarca.on("message", function(e)
  local u = e.sender
  if not u then return end
  if u.account_known and u.account ~= "*" then
    tiarca.log("Account: " .. u.account)
  end
end)
```

Sender may be absent; `account_known=false` and nil account mean unknown, not logout; `"*"` means observed logout. Never guess identity. Live messages preserve observations at receipt.

## Point lookup

```lua
tiarca.identity_v2()
local seq, pending = 0, {}
tiarca.on("command", function(e)
  local nick = e.text:match("^lookup (%S+)$")
  if not nick or e.channel == "" then return end
  seq = seq + 1
  local token = "lookup-" .. seq
  tiarca.get_user(e.channel, nick, token)
  pending[e.server] = token
end)
tiarca.on("identity", function(e)
  if e.query ~= "user" or pending[e.server] ~= e.token then return end
  pending[e.server] = nil
  if e.status ~= "ok" or not e.user then
    tiarca.show("Lookup", e.status)
    return
  end
  tiarca.show("Lookup", e.user.nick .. " " .. (e.user.host or "?"))
end)
```

Type `/lua lookup NICK` in a channel. Server-tab commands can target a shared channel; channel tabs cannot query another. Point lookup also from same-channel message/join/part/quit/nick/kick/ban events. Separate reply does not retain original moderation authority. Rejections or full queues may yield no reply: unique tokens, bounded pending state, timeout/cancellation in real programs.

## Filtered paginated roster

```lua
tiarca.identity_v2()
local seq, pending = 0, {}
tiarca.on("command", function(e)
  if e.text ~= "voice list" or e.channel == "" then return end
  seq = seq + 1
  local token = "voice-" .. seq
  tiarca.get_users(e.channel, {token=token, prefix="+", limit=100})
  pending[e.server] = token
end)
tiarca.on("identity", function(e)
  if e.query ~= "users" or pending[e.server] ~= e.token then return end
  if e.status ~= "ok" or not e.complete then
    pending[e.server] = nil
    tiarca.show("Voice", "Incomplete / unavailable")
    return
  end
  tiarca.log("Observed matches: " .. e.total)
  -- Process e.users locally.
  if e.next_offset then
    tiarca.get_users(e.channel, {token=e.token, prefix="+", limit=100,
                                 offset=e.next_offset})
  else
    pending[e.server] = nil
  end
end)
```

Combined filters across locally observable roster (max 65,536 scanned members): `nick` uses IRC CASEMAPPING, exact `ident`, case-insensitive exact `host` and `account`, `prefix` contains string, `auth` = registered/logged_out/unknown. No wildcards. Token 1–64 bytes, initial offset 0, limit 1–128 (default 100). One query per event, identity callback required; bulk starts from local command, continues with same token, channel, filters, limit and next_offset, max 256 pages. Live data, not immutable snapshot; `complete` does not prove complete WHO and may be true with next_offset. `tiarca.get_channels(token)` from local command: identity reply with channels, total, complete, max 32 channels without pagination. V2 queries do not issue WHO; Mask Finder retains dedicated WHO/WHOX.

## Storage and safety

Private per script, shared across networks; namespace keys per network if needed. Storage-enabled events serialized per script across networks; speculative writes readable in callback and committed together before effects, no write for unchanged state. Error or stale revision blocks effects and disables script. Commit does not guarantee IRC delivery. Backups retain eligible data but revoke consents; RAM does not survive restart.

## Diagnostics and tests

`/lua perf`: global counters since start, not AutoMod statistics. Ingress and deliveries use different units; cancellations/rejections are not an exclusive sum of losses. Ingress queue and slots 32 each. Mean/max queue, IPC (includes sandbox wait), JNI, storage and effect-release latency (not IRC confirmation). Memory: latest and sampled maximum Lua allocator, not RSS. No reset or disk persistence.

Test legacy and v2, unknown/logout, rosters beyond 128, missing replies and incomplete pages, two networks, restart, consents, quotas, backup. Do not run multiple AutoMods together. Host tests do not replace Android. [Plan and benchmark](../REDESIGN_PLAN.md).
