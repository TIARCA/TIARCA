-- TIARCA moderation collector: host-backed opt-in Lua companion.
-- Import and enable on the selected IRC networks, then /lua collector on.
-- The Android host stores append-only JSONL and looks up existing SQLite
-- channel history. This script never buffers chat, sends messages, changes
-- modes or writes a second copy of the archive.
-- /lua collector off | status | export | help are local host commands.
-- Callbacks are intentionally passive: collecting and logging stop with
-- the host opt-in setting, not merely with the script's enabled flag.
tiarca.on("kick", function(_) end)
tiarca.on("ban", function(_) end)
