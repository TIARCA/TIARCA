-- TIARCA Lua 5.5.1 example: count messages in each conversation.
-- Import manually in Settings > Lua scripts.
-- No send permission is needed.
local counters = {}
tiarca.on("message", function(event)
    local key = event.channel
    counters[key] = (counters[key] or 0) + 1
    tiarca.log(key .. ": " .. counters[key] .. " messages")
end)
