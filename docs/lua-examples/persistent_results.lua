-- TIARCA experimental C6/C7 test: import and enable this script.
-- Approve "persistent data" on ONE IRC network; sending/moderation not needed.
-- From a joined channel or server tab:
-- /lua c6 set
-- /lua c6 read
-- /lua c7 list
-- /lua c6 clear
-- Restart the app and repeat /lua c6 read to test persistence.

tiarca.on("command", function(e)
    local cmd = (e.text or ""):lower()
    if cmd == "c6 set" then
        local count = tonumber(tiarca.storage.get("counter") or "0") or 0
        count = math.min(count + 1, 999999)
        tiarca.storage.set("counter", tostring(count))
        tiarca.show("C6 - Dati persistenti", "Nuovo contatore: " .. count)
    elseif cmd == "c6 read" then
        local count = tiarca.storage.get("counter") or "(nessun valore)"
        tiarca.show("C6 - Lettura", "Valore salvato: " .. count)
    elseif cmd == "c6 clear" then
        tiarca.storage.delete("counter")
        tiarca.show("C6 - Cancellazione", "Contatore eliminato")
    elseif cmd == "c7 list" then
        tiarca.show_list("C7 - Lista selezionabile", {
            "NickAlice", "NickBob", "NickCharlie"
        })
    end
end)
