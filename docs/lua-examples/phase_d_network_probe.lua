-- TIARCA phase D: read-only two-network isolation check.
-- Import on exactly ONE network (A); no send, moderation, users or storage
-- permission needed. The other configured network (B) must NOT run this.
-- Commands from a channel or server tab:
-- /lua d rete
-- /lua d eventi
-- Try them on A. Then generate traffic on B and rerun d eventi on A.
-- No IRC messages, modes, kicks or bans are emitted.

local incoming = 0

tiarca.on("message", function(event)
  incoming = incoming + 1
end)

tiarca.on("command", function(event)
  local command = (event.text or ""):lower()
  if command == "d eventi" then
    tiarca.show("Fase D - Eventi", "Messaggi ricevuti da questa rete: " ..
      tostring(incoming) .. "\nID rete: " .. (event.server or ""))
  elseif command == "d rete" then
    local channels = tiarca.get_channels()
    local rows = {"ID rete: " .. (event.server or "")}
    local shown = math.min(#channels, 5)
    for i = 1, shown do
      local item = channels[i]
      rows[#rows + 1] = item.name .. " (" .. tostring(item.users) .. ")"
    end
    if #channels > shown or channels.truncated then
      rows[#rows + 1] = "Altri canali omessi"
    end
    local result = table.concat(rows, "\n")
    tiarca.show("Fase D - Solo rete autorizzata", result:sub(1, 500))
  end
end)
