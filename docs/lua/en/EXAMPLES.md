# Script examples

[Guide](README.md) · [Italiano](../it/EXAMPLES.md)

These are UTF-8 files for manual import, **not** automatically installed plugins. Inspect source and requested permissions before enabling.

| Example | Purpose | Capabilities |
|---|---|---|
| [automod_antiripetizione.lua](../../lua-examples/automod_antiripetizione.lua) | Moderate repetitions with per-channel scope | Identities, persistence, moderation; send for warnings |
| [espelli.lua](../../lua-examples/espelli.lua) | Manual multi-ban and kick for compatible InspIRCd networks; enables channel +N | Identities, moderation; OP |
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
