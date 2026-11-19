-- TIARCA moderation collector (native-backed Lua companion).
-- Enable this script on the intended network, then type /lua collector on.
-- The main application (LuaScriptDispatcher + LuaModerationCollector) observes
-- KICK, +b and AutoMod decisions and appends one event per JSONL line.
-- This Lua companion deliberately does not maintain a parallel message buffer,
-- duplicate event archive or bounded ring. Chat context comes from existing SQLite.
-- /lua collector off | status | export | help are handled by the host.
-- No IRC commands are sent by this script.
local function safe(value)
  local s = tostring(value or "")
  return s:gsub("[\r\n]"," "):sub(1,200)
end
tiarca.on("kick",function(e)
  tiarca.log("Collector observed KICK on "..safe(e.channel)..
    ": "..safe(e.message_type).." by "..safe(e.nick))
end)
tiarca.on("ban",function(e)
  tiarca.log("Collector observed +b on "..safe(e.channel)..
    ": "..safe(e.text).." by "..safe(e.nick))
end)
