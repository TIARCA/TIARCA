-- TIARCA Lua phase D: safe operator-permission check (voice only).
-- Run in a test channel, with a consenting second nickname.
-- Enable this script on ONE IRC network and allow "Moderazione".
-- Commands:
--   /lua prova voce NickTest
--   /lua prova devoice NickTest
-- A local message is NOT proof a server accepted the command.
-- Never grants operator status, kicks, bans, mutes or uses RAW IRC.

tiarca.on("command", function(event)
  local action, nick = (event.text or ""):match("^%s*prova%s+(%S+)%s+(%S+)%s*$")
  if action ~= "voce" and action ~= "devoice" then return end
  local channel = event.channel or ""
  local first = channel:sub(1, 1)
  if first ~= "#" and first ~= "&" and first ~= "+" and first ~= "!" then
    tiarca.show("Test moderazione", "Apri prima un canale IRC di prova.")
    return
  end
  local mode = action == "voce" and "+v" or "-v"
  tiarca.mode(channel, nick, mode)
  tiarca.show("Test moderazione",
    "Richiesta " .. mode .. " per " .. nick ..
    " accodata su " .. channel ..
    ". Verifica sul server se e' stata applicata. " ..
    "Senza privilegi OP non deve funzionare.")
end)
