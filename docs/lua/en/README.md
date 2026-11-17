# Developing Lua scripts for TIARCA

[Bilingual index](../README.md) · [API](API_REFERENCE.md) · [Events](EVENTS.md) · [Permissions](PERMISSIONS.md) · [Examples](EXAMPLES.md)

> **Availability:** experimental feature on development `main`; **not included in the published v0.9.10 stable APK**.

## Your first script

Create a UTF-8 text file named `hello.lua`:

```lua
tiarca.on("connected", function(event)
  tiarca.log("Connected to network " .. event.server)
end)
```

On a Lua-enabled build: **Settings → Lua scripts (experimental)** → import the file through Android's document picker → enable it → explicitly select IRC networks. Imported scripts are disabled initially. This example needs no additional capability. The text appears in the script's local log, **not** in an IRC channel.

## Execution model

- Register callbacks using `tiarca.on(event_name, function)`. Events are normalized, not raw IRC protocol messages.
- Each script sees only user-approved networks; some operations require separate capabilities.
- `/lua <text>` triggers a local `command` event; it is not sent to IRC. Example: `/lua ping`.
- Ordinary Lua variables persist across events in the same VM, but **not** VM restarts; use consented `tiarca.storage` for durable state.
- No direct Android, filesystem, socket, or arbitrary IRC command access is provided.

## Learning path

Read [Events](EVENTS.md), then the [API reference](API_REFERENCE.md) and [Permissions](PERMISSIONS.md). Start with [importable examples](EXAMPLES.md). For historical details and limits see the [technical implementation notes](../../LUA_SCRIPTING_MVP.md).

Report missing APIs or broken examples with the commit/build, IRC network, and privacy-sanitized logs.
