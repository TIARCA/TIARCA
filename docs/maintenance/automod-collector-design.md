# AutoMod Collector — implementation notes

Status: implementation on feature branch / PR #289; CI and real-device validation must be checked before merge.

## Scope and reuse
The collector reuses TIARCA's existing IRC message pipeline and SQLite channel
history. It observes inbound KICK and +b events regardless of the actor, without
creating a duplicate Lua chat history. AutoMod's authenticated decision log is
captured when it is produced by the known AutoMod script, not by a second
moderation engine. Its detection rules and actions remain unchanged.

## Implementation
- `LuaScriptDispatcher` observes channel KICK and MODE +b, performs bounded,
  asynchronous lookups of the kicked user's last public message among the most
  recent 200 stored channel messages; exact `nick!ident@host` ban masks get the
  same lookup, while ambiguous bans have no inferred chat context.
- `LuaAutoModReport` validates AutoMod decisions; the dispatcher only archives
  decisions coming from `automod_antiripetizione.lua`. The original current
  message is passed through the existing Pending reply, not held in a second
  persistent history. Its similarity %, infraction level and requested action
  are stored in separate JSON fields when the input matches the defined format.
- `LuaModerationCollector` writes app-private, append-only UTF-8 JSONL, scoped
  by server UUID and versioned with schema=1. There is no circular event limit.
  Observed ident/host/account values are best-effort and null when unknown.
- `/lua collector on|off|status|export|help` is handled by the host and requires
  the opt-in companion `moderation_collector.lua` to be enabled on that network.
  Collection defaults to off and stays off through restarts until enabled.
  Turning collection off does not erase existing records.
- Explicit export copies the journal into an app cache path shared using the
  existing `FileProvider` and the Android share chooser. No new permissions or
  storage API are granted to the isolated Lua sandbox.

## Interpretation / limits
- `AUTOMOD` is a **decision/request**, never a falsely confirmed IRC action.
  When a KICK is subsequently observed by the local IRC nick within the
  correlation window, origin is `automod_correlated`; otherwise observed
  actions remain `observed_irc`. Do not classify other nicknames as bot or
  human without independent data.
- A preceding message is context, not proven cause. A ban does not itself
  expel a user. Generic host or extban masks may not identify a single target.
- Events can be missed on disconnection or if Android terminates a process.
  The archive grows with usage and is excluded from Android cloud/device\n  automatic backups for privacy; export is user-initiated and contains sensitive
  third-party chat and identity data.
- Lua event delivery may drop under load; the native archive observes KICK/+b
  independent of Lua callback delivery where the host receives the IRC event.

## Validation
Automated JSONL/Robolectric tests: `LuaModerationCollectorTest`, plus
existing `LuaAutoModReportTest` and CI (Standard and Fdroid). Also manually
verify APK multi-network isolation, reconnect, export, privacy, and backup
behavior. CI and device results are not assumed successful until checked.
No automatic version increment, tag or release.
