# Script examples

[Guide](README.md) · [Italiano](../it/EXAMPLES.md)

These are UTF-8 files for manual import, **not** automatically installed plugins. Inspect source and requested permissions before enabling.

| Example | Purpose | Capabilities |
|---|---|---|
| [automod_antiripetizione.lua](../../lua-examples/automod_antiripetizione.lua) | Moderate repetitions with per-channel scope | Identities, persistence, moderation; send for warnings |
| [automod_annunci.lua](../../lua-examples/automod_annunci.lua) | [AutoMod B](AUTOMOD_B.md): announcement patterns, lookback concentration and escalation | Identities, persistence, send and moderation in live mode |
| [automod_v4.lua](../../lua-examples/automod_v4.lua) | Watchdog + Mod v2; default monitor, zero channels | Identities, persistence; send and moderation for live |
| [espelli.lua](../../lua-examples/espelli.lua) | Manual kick: host-scoped `N:` and `m:U:` TBAN for 6h, nick/account bans; no global +N | Identities, moderation; OP |
| [mask_finder.lua](../../lua-examples/mask_finder.lua) | Search nicknames, idents and hosts | Channel members and identities |
| [moderation_collector.lua](../../lua-examples/moderation_collector.lua) | Record moderation events to JSONL | Identities and persistence |

### Minimal copyable example

```lua
tiarca.on("command", function(event)
  if event.text == "my-help" then
    tiarca.log("Commands: /lua my-help")
  end
end)
```

Import, enable on the intended network, and type `/lua my-help`. Output is local.

**Before distributing a script:** check network scope, `nil` values, restarts, backups, rate limits, no RAW, behavior without OP privileges, and manual Candidate tests. See [permissions](PERMISSIONS.md).

### AutoMod V4

Import on redesign build. `/lua mod canali add #channel`, `/lua mod stato`, `/lua mod help`; verify monitor before `/lua mod modo live`. Whitelist: `/lua mod whitelist add NICK` from monitored channel; add `#channel` from server tab with multiple channels. Verified account required. Live escalation: private warning, 2m mute, 15m mute, kick+3h mute, kick+24h ban. `/lua mod close` closes script-owned PVTs; #302 multi-selection separate. Do not run multiple AutoMods together; no automatic migration from older scripts. [V2 examples](MIGRATION.md).
