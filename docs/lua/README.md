# TIARCA Lua scripting — Developer documentation

TIARCA's embedded Lua scripting API is an **experimental development feature** on development branches (this guide includes unmerged PR #303 redesign); it is **not available in the published v0.9.10 APK**. This documentation describes the app's restricted API, not unrestricted Lua or IRC protocol access.

Choose your language / Scegli la lingua:

- 🇮🇹 [Guida per sviluppatori](it/README.md) · [API](it/API_REFERENCE.md) · [Eventi](it/EVENTS.md) · [Permessi](it/PERMISSIONS.md) · [Esempi](it/EXAMPLES.md)
- 🇬🇧 [Developer guide](en/README.md) · [API](en/API_REFERENCE.md) · [Events](en/EVENTS.md) · [Permissions](en/PERMISSIONS.md) · [Examples](en/EXAMPLES.md)

For coding agents / Per gli agenti AI: [AGENTS.md](AGENTS.md).

**Implementation and change history:** [Lua scripting MVP](../LUA_SCRIPTING_MVP.md). Source examples: [lua-examples](../lua-examples/). If an API is not listed, **do not assume it exists**. Scripts run in a constrained, isolated process and require explicit network and capability consent.

**Redesign / Migrazione:** [Italiano](it/MIGRATION.md) · [English](en/MIGRATION.md) · [piano e benchmark](REDESIGN_PLAN.md). MVP notes are historical, not the current v2 contract.
