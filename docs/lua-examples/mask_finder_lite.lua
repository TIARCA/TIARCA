-- TIARCA Lua 5.5.1 - Mask Finder Lite (MVP dimostrativo)
-- Cerca solo i NICKNAME osservati nei messaggi ricevuti.
-- Non puo' trovare ident/hostname, utenti silenziosi o mostrare link cliccabili.
-- Modifica i due parametri prima di importare il file in TIARCA.
--
-- Esempi NICK_MASK: "*", "Mar*", "*bot", "A?ice"
-- Non usare "*!*@host": gli hostname non sono esposti dall'API Lua v1.
-- Lo script non invia messaggi IRC, ma scrive in "Ultimo log".

local CHANNEL = "#amicizia"
local NICK_MASK = "Mar*"
local MAX_USERS = 64

local seen = {}
local found = {}
local warned = false

-- Wildcard * (zero o piu' caratteri) e ? (un carattere).
-- Confronto senza distinzione maiuscole/minuscole per caratteri ASCII.
local function wildcard_match(mask, nick)
    mask = string.lower(mask)
    nick = string.lower(nick)
    local i, j, star, retry = 1, 1, nil, nil
    while j <= #nick do
        local c = string.sub(mask, i, i)
        if c == "?" or (c ~= "" and c == string.sub(nick, j, j)) then
            i, j = i + 1, j + 1
        elseif c == "*" then
            star, retry = i, j
            i = i + 1
        elseif star then
            retry = retry + 1
            j, i = retry, star + 1
        else
            return false
        end
    end
    while string.sub(mask, i, i) == "*" do
        i = i + 1
    end
    return i > #mask
end

local function show_results()
    local sorted = {}
    for i = 1, #found do sorted[i] = found[i] end
    table.sort(sorted, function(a, b)
        return string.lower(a) < string.lower(b)
    end)

    local line = "Mask Finder Lite " .. CHANNEL
        .. " [" .. NICK_MASK .. "] (" .. #sorted .. "): "
    for i = 1, #sorted do
        local piece = (i == 1 and "" or ", ") .. sorted[i]
        if #line + #piece > 450 then
            line = line .. " ..."
            break
        end
        line = line .. piece
    end
    tiarca.log(line)
end

local function reset()
    seen = {}
    found = {}
    warned = false
end

tiarca.on("connected", reset)
tiarca.on("disconnected", reset)

tiarca.on("message", function(event)
    if string.lower(event.channel or "") ~= string.lower(CHANNEL) then
        return
    end

    if string.find(NICK_MASK, "!", 1, true)
        or string.find(NICK_MASK, "@", 1, true) then
        if not warned then
            tiarca.log("Mask Finder Lite: ident e host non disponibili nell'API Lua v1.")
            warned = true
        end
        return
    end

    local nick = event.nick or ""
    local key = string.lower(nick)
    if nick == "" or seen[key] or not wildcard_match(NICK_MASK, nick) then
        return
    end

    if #found >= MAX_USERS then
        if not warned then
            tiarca.log("Mask Finder Lite: limite di 64 nickname raggiunto.")
            warned = true
        end
        return
    end

    seen[key] = true
    found[#found + 1] = nick
    show_results()
end)
