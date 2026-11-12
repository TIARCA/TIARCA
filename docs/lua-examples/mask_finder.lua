-- TIARCA Mask Finder. Import and enable with "Channel members and identities".
-- Usage inside a joined channel:
--   /lua mask pippo
--   /lua mask *!*@*.example.net
-- Matches nick!ident@host (not messages); prints only to the local Server tab.
-- The host provides a WHO/WHOX-backed snapshot and interactive 50-row chunks.
tiarca.on("command", function(event)
  local query = event.text:match("^mask%s+(.+)$")
  if not query then
    if event.text == "mask" then
      tiarca.show("Mask Finder", "Uso: /lua mask <testo o mask IRC>")
    end
    return
  end
  -- This host action requires separate permission to read channel identities.
  -- It can only act on the joined channel from which /lua was entered.
  tiarca.maskfind(event.channel, query)
end)
