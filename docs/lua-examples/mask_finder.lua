-- TIARCA Mask Finder. Enable "Channel members and identities" for this script.
-- Usage inside a joined channel:
--   /lua mask pippo
--   /lua mask *!*@*.example.net
-- The search reads a full channel WHO/WHOX and writes only to the Server tab.
tiarca.on("command", function(event)
  local keyword, query = event.text:match("^%s*(%S+)%s*(.-)%s*$")
  if not keyword or keyword:lower() ~= "mask" then return end

  if not query or query == "" or query:lower() == "help" then
    tiarca.show("Mask Finder",
      "Uso: /lua mask <testo o mask IRC>\n" ..
      "Esegui da un canale; ricerca nickname, ident e host.\n" ..
      "Esempi: /lua mask pippo e /lua mask *!*@*.example.net\n" ..
      "Risultati nella scheda Server. Permesso: Membri e identita'.")
    return
  end
  if not event.channel:match("^[#&+!]") then
    tiarca.show("Mask Finder", "Esegui il comando da un canale IRC.")
    return
  end
  if #query > 192 or query:find("[%c\\]") then
    tiarca.show("Mask Finder", "Maschera troppo lunga o non valida.")
    return
  end

  -- The host checks permission, connected network and exact source channel.
  -- No outgoing IRC messages, operator privileges or auto-send are required.
  tiarca.maskfind(event.channel, query)
end)
