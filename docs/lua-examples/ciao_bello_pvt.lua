-- TIARCA Lua 5.5.1 - risposta automatica SOLO nei messaggi privati.
-- Per inviare la risposta, autorizza esplicitamente l'invio dello script.

tiarca.on("message", function(event)
    if event.message_type ~= "normal" then
        return
    end

    local nick = event.nick or ""
    local target = event.channel or ""
    -- Nei PVT TIARCA usa il nickname del mittente come conversazione.
    -- Nei canali (#nome, &nome, ecc.) target e nick sono diversi.
    if nick == "" or string.lower(target) ~= string.lower(nick) then
        return
    end

    local testo = string.lower((event.text or ""):match("^%s*(.-)%s*$"))
    if testo == "ciao bello" then
        tiarca.send(target, "bello ciao")
    end
end)
