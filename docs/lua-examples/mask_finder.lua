-- TIARCA Mask Finder. Enable "Channel members and identities" for this script.
-- From a joined channel: /lua mask nick!ident@host
-- From any tab of the same IRC network: /lua mask #channel nick!ident@host
-- Results are local to the network's Server tab; never sent into the chat.
tiarca.on("command", function(event)
  local keyword, query = event.text:match("^%s*(%S+)%s*(.-)%s*$")
  if not keyword or keyword:lower() ~= "mask" then return end

  if not query or query == "" or query:lower() == "help" then
    tiarca.show("Mask Finder",
      "Uso: /lua mask <testo o mask IRC> (da un canale)\n" ..
      "Oppure: /lua mask #canale <testo o mask IRC> (da qualsiasi scheda della stessa rete).\n" ..
      "Esempi: /lua mask pippo; /lua mask #chat *!*@*.example.net\n" ..
      "Cerca nel nick!ident@host; risultati nella scheda Server.\n" ..
      "Permesso: Membri e identita'.")
    return
  end

  local target = event.channel or ""
  local channel, pattern = query:match("^([#&+!][^%s]+)%s+(%S+)$")
  if channel then
    target = channel
    query = pattern
  elseif query:find("%s") or query:match("^[#&+!]") then
    tiarca.show("Mask Finder", "Sintassi: /lua mask [#canale] <testo o mask IRC>.")
    return
  end

  if not target:match("^[#&+!]") then
    tiarca.show("Mask Finder",
      "Da questa scheda specifica il canale: /lua mask #canale <testo o mask IRC>.")
    return
  end
  if #query > 192 or query:find("[%c\\]") then
    tiarca.show("Mask Finder", "Maschera troppo lunga o non valida.")
    return
  end

  -- The host checks the exact typed command, network permission, and
  -- live membership of the named channel. No IRC chat or operator privileges.
  tiarca.maskfind(target, query)
end)
