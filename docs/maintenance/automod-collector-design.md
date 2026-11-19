# Standalone Lua moderation collector — architecture

Status: [PR #289](https://github.com/TIARCA/TIARCA/pull/289). Do not claim real-device testing before it is performed.

The moderation **collector itself is an external Lua script** at `docs/lua-examples/moderation_collector.lua`. It alone chooses which KICK, MODE +b and recognized AutoMod report events to record, requests message context, serializes JSONL and implements `/lua collector` commands. Nothing in the Android host recognizes the script filename, maintains Collector-specific preferences, or records moderation evidence when the script is disabled.

## Generic host Lua interfaces

- IRC events `kick` and `ban`: already dispatched for subscribed scripts; native runtime and isolated-service event validation now support them.
- `moderation_log`: forwards an existing validated local moderation detection report (including AutoMod's requested action), with script source in `event.message_type`. A reported decision is not server confirmation.
- `tiarca.last_message(channel,nick,token)`: a consent-gated, event-scoped, asynchronous request against existing channel SQLite storage (last 200 normal/ME messages); response `history` delivered only to the requesting script, with sender `ident`/`host` from the stored message when available. Never executes with no script request.
- `tiarca.journal.append(json)`: validates a single JSON object and appends one line to private `lua_journals/<script UUID>/<network UUID>.jsonl`; no rotating buffer, quota on *individual* lines only. `tiarca.journal.export()` creates and shares a temporary snapshot only after a local command. These APIs require persistent-data consent.

Identity values come only from observed, consented channel snapshots; unknown values are null. The Lua script caches only limited transient *identity hints*, not a duplicate chat transcript. Unidentifiable generic ban masks are recorded without invented target or message context. Last message is not evidence of causal responsibility.

Archives are excluded from Android cloud/device automatic backup due to third-party identity/chat content. Explicit sharing is user initiated. User must grant Persistent data and Read identities separately, and enable the Lua script per network; script settings persist in ordinary Lua storage. No app permissions, version, tag or release are altered.

## Verification

Automated tests for append-only storage and network/script isolation; CI/Android compilation; manual IRC and export validation still required. Observe original API permission behavior, bounded IPC and event delivery under load.
