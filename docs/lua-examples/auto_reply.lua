-- TIARCA Lua 5.5.1 example: reply to "!tiarca" on the same conversation.
-- Enable ONLY on a network you trust, and explicitly permit tiarca.send.
-- TIARCA enforces a server scope, same-conversation target and rate limits.
tiarca.on("message", function(event)
    if event.text == "!tiarca" then
        tiarca.send(event.channel, "TIARCA Lua OK")
    end
end)
