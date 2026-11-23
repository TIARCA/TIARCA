# Collector V3 and Micro Discovery

Independent script: [moderation_collector_v3.lua](../../lua-examples/moderation_collector_v3.lua). Disable V2 on the same network to avoid double logging. V3 never sends IRC messages or performs moderation.

All existing V2 features remain: observed IRC KICK/BAN, AutoMod reports, historical last-message lookup, bounded in-memory context, shadow repetition candidates and incremental JSONL exports.

## Optional 24-hour micro collection

`/lua collector micro on` enables the experimental per-network recording for 24 hours. `/lua collector micro status`, `/lua collector micro off`, and `/lua collector export` inspect, stop, and export it.

Capture includes all normal messages with **one to three normalized words**, including greetings, and **numeric-only messages of any token count**. The original message is retained up to a safety limit of 2048 bytes. JSONL records use `event: "micro"` with `micro_text`, `micro_normalized`, `micro_words`, and `micro_type`.

An explicit read-only snapshot of the unified AutoMod's account whitelist excludes corresponding micro and shadow candidates. Actual observed IRC moderation remains logged.

**Privacy:** the journal can contain real private identity and public message content; collect only in appropriately authorized channels, export intentionally, and protect the shared file. Collection stops automatically after 24 hours, but already recorded entries remain until manual rotation. Live detection effectiveness is **not verified** before real Candidate testing.
