# TIARCA Lua scripting — instructions for coding agents

This document is for AI coding assistants and human contributors writing **Lua scripts for TIARCA**, not Android application plugins.

1. Read [English guide](en/README.md) or [guida italiana](it/README.md), [API reference](en/API_REFERENCE.md), [events](en/EVENTS.md), and [permissions](en/PERMISSIONS.md) before generating a script.
2. Treat [LUA_SCRIPTING_MVP.md](../LUA_SCRIPTING_MVP.md) and the **current repository implementation** as authoritative for exact semantics. Never infer a function merely because it exists in standard Lua, another IRC client, or an earlier TIARCA build.
3. Use only documented `tiarca.*` functions. **No** `io`, `os`, `require`, filesystem, sockets, Android APIs, arbitrary IRC RAW or arbitrary destinations.
4. Script source must be UTF-8 and no larger than **49,152 bytes**. Bound CPU/memory, loops, callbacks, event actions and retained state.
5. Scope operations to the user's approved network and current channel. Check `nil` identity fields and incomplete roster data. Never invent account/host information.
6. For any send, moderation, roster or storage action, explicitly state the required user consent. Moderation additionally needs live operator privileges and may be rejected by the server.
7. For state surviving VM restart, use `tiarca.storage` with permission. Explain how reset, backup/restore, reimport and per-network state should work.
8. Include a `/lua <script-command> help` handler for command-driven scripts, clear examples, and a safe default (no unsolicited moderation or public output).
9. Provide a manual test plan covering reconnects, two networks, disabled permissions, missing identities, and rate limiting. Distinguish source review, compilation, automated tests and on-device tests.
10. When updating documentation, keep **Italian and English files structurally aligned**. Do not claim an API is verified unless its current implementation was checked.

This documentation covers a **development feature**; the stable v0.9.10 APK does not include it.

11. For PR #303 follow [migration guide](en/MIGRATION.md) and current implementation rather than historical MVP. Prefer event.sender for messages, point lookup for explicit identity requests, filtered paginated roster for commands. Do not blindly opt legacy scripts in; handle missing replies, unknown accounts and incomplete observations. `/lua perf` is global engine diagnostics, not AutoMod statistics.
