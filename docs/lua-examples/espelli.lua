-- TIARCA: espulsione e multi-ban per operatori SimosNap (InspIRCd).
-- Importa come script autonomo; autorizza "Membri e identità" e "Moderazione".
-- Da un canale: /lua espelli nickname
-- Dalla scheda server: /lua espelli #canale nickname
-- N.B. +N resta attivo sul canale. Il ban sul suffisso host può coinvolgere
-- altre persone dello stesso provider: usare solo quando intenzionale.
-- La funzionalità tiarca.eject richiede una build TIARCA che la includa.

tiarca.on("command", function(event)
  local verb, args = (event.text or ""):match("^%s*(%S+)%s*(.-)%s*$")
  if not verb or verb:lower() ~= "espelli" then return end

  if args == "" or args:lower() == "help" then
    tiarca.show("Espelli / multi-ban",
      "Nel canale: /lua espelli nick\n" ..
      "Scheda server: /lua espelli #canale nick\n" ..
      "Esegue +N, eventuale -v, KICK e 3-4 ban. " ..
      "Il suffisso host coinvolge potenzialmente altri utenti.")
    return
  end

  local channel, nick = args:match("^([#&+!][^%s]+)%s+(%S+)$")
  if not channel then
    channel = event.channel or ""
    nick = args
  end
  local active = event.channel or ""
  if not channel:match("^[#&+!][^%s,%c:]+$") or
      (active ~= "" and active:lower() ~= channel:lower()) then
    tiarca.show("Espelli", "Apri il canale oppure usa la scheda server e indica #canale.")
    return
  end
  if not nick or #nick > 64 or
      nick:find("[%s%c!@*,?:]") or nick:match("^[#&+!]") then
    tiarca.show("Espelli", "Nickname non valido. Uso: /lua espelli [#canale] nickname.")
    return
  end
  -- Un'unica azione: il client verifica OP, membro, host e account
  -- prima di inviare, nell'ordine, tutti i comandi IRC.
  tiarca.eject(channel, nick, "utente non gradito")
end)
