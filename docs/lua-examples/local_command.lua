-- TIARCA Lua 5.5.1 - local command demonstration (experimental).
-- After enabling this script for one IRC network, type:
--   /lua ping      -> writes "pong" to this script's Last log
--   /lua saluta    -> sends "Ciao da Lua!" to the active conversation
-- Sending requires the separate, explicit automatic-send approval.
-- The /lua command itself is local and is never forwarded to IRC.

tiarca.on("command", function(event)
    local cmd = string.lower((event.text or ""):match("^%s*(.-)%s*$"))
    if cmd == "ping" then
        tiarca.log("pong")
    elseif cmd == "saluta" then
        if event.channel ~= "" then
            tiarca.send(event.channel, "Ciao da Lua!")
        else
            tiarca.log("Apri prima un canale o un PVT.")
        end
    else
        tiarca.log("Comando di esempio sconosciuto: " .. string.sub(cmd, 1, 100))
    end
end)
