# Script examples

[Guide](README.md) · [Italiano](../it/EXAMPLES.md)

These are UTF-8 files for manual import, **not** automatically installed plugins. Inspect source and requested permissions before enabling.

| Example | Purpose | Capabilities |
|---|---|---|
| [count_messages.lua](../../lua-examples/count_messages.lua) | Count messages in local log | None additional |
| [auto_reply.lua](../../lua-examples/auto_reply.lua) | Reply in the same conversation | Send messages |
| [local_command.lua](../../lua-examples/local_command.lua) | Local `/lua ping` and `/lua saluta` commands | Send only for remote greeting |
| [persistent_results.lua](../../lua-examples/persistent_results.lua) | Persistent state and local results | Persistent data |
| [espelli.lua](../../lua-examples/espelli.lua) | Manual multi-ban and kick for compatible InspIRCd networks; enables channel +N | Identities, moderation; OP |
| [mask_finder.lua](../../lua-examples/mask_finder.lua) | Search channel nick/ident/host | Channel members and identities |
| [automod_antiripetizione.lua](../../lua-examples/automod_antiripetizione.lua) | Detect repetition and optionally moderate | Identities, persistence, moderation; send for warnings |

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
