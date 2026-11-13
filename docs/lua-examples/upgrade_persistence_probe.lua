-- TIARCA: test persistent counters across an in-place APK update.
-- Import/enable on a test network and approve "Persistent script data".
-- In a channel or server tab:
--   /lua upgrade add
--   /lua upgrade status
-- Import a SECOND script but leave it disabled to check its off-state too.
--
-- Reinstall a newer TIARCA Candidate OVER the installed APK, keeping app
-- data and the same signing certificate. Then run /lua upgrade status.
-- The persistent count must survive; the RAM-only count is reset.

local session_count = 0

tiarca.on("command", function(event)
  if event.text == "upgrade add" then
    local old = tonumber(tiarca.storage.get("upgrade_counter") or "0") or 0
    local count = old + 1
    tiarca.storage.set("upgrade_counter", tostring(count))
    session_count = session_count + 1
    tiarca.show("Lua upgrade test", "Persistent count: " .. count ..
      "\nSession count: " .. session_count)
  elseif event.text == "upgrade status" then
    local saved = tiarca.storage.get("upgrade_counter") or "0"
    tiarca.show("Lua upgrade test", "Persistent count: " .. saved ..
      "\nSession count: " .. session_count)
  end
end)
