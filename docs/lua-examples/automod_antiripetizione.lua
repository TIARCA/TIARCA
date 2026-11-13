-- /lua mod help
local MAX_UTENTI = 1000 
local MAX_MESSAGGI = 2400
local MAX_TESTO = 160
local MAX_PAROLE = 32
local config = {}
local cronologie, utenti, messaggi = {}, 0, 0
local function impostazioni(server)
if not config[server] then
config[server] = {on=true, canali={}, soglia=80, ultimi=3, minuti=30,
minparole=5, azione="monitor", avviso="pubblico",
durata="10m", pausa=10}
end
return config[server]
end
local function chiave(e)
return e.server .. "\0" .. e.channel:lower() .. "\0" .. e.nick:lower()
end
local function elimina(k)
local r = cronologie[k]
if not r then return end
utenti = utenti - 1
messaggi = messaggi - #r.lista
cronologie[k] = nil
end
local function scaduti(r, now, minuti)
local limit = minuti * 60
for i=#r.lista,1,-1 do
if now - r.lista[i].t >= limit then
table.remove(r.lista,i)
messaggi = messaggi - 1
end
end
if r.infrazione > 0 and now - r.ultima_infrazione >= limit then
r.infrazione, r.ultima_infrazione = 0, 0
end
end
local function pulizia(server, now)
for k,r in pairs(cronologie) do
if r.server == server then
scaduti(r, now, impostazioni(server).minuti)
if #r.lista == 0 and r.infrazione == 0 then elimina(k) end
end
end
end
local function reset(server)
for k,r in pairs(cronologie) do
if r.server == server then elimina(k) end
end
end
local function posto(escludi)
local k0, t0
for k,r in pairs(cronologie) do
if k ~= escludi and (not t0 or r.visto < t0) then
k0, t0 = k, r.visto
end
end
if not k0 then return false end
elimina(k0)
return true
end
local function prepara(e, now, c)
local k = chiave(e)
local r = cronologie[k]
if r then
scaduti(r, now, c.minuti)
else
if utenti >= MAX_UTENTI then posto(k) end
r = {server=e.server, lista={}, infrazione=0,
ultima_infrazione=0, ultima_azione=0, visto=now}
cronologie[k], utenti = r, utenti + 1
end
r.visto = now
return k, r
end
local function impronta(text)
local parti, bytes = {}, 0
local pulito = text:lower():gsub("[%c%p]", " ")
for parola in pulito:gmatch("%S+") do
if #parti >= MAX_PAROLE or bytes + #parola + 1 > MAX_TESTO then
return "", 0
end
parti[#parti+1] = parola
bytes = bytes + #parola + 1
end
return table.concat(parti, " "), #parti
end
local function simile(a, na, b, nb)
local conta, comuni = {}, 0
for parola in a:gmatch("%S+") do
conta[parola] = (conta[parola] or 0) + 1
end
for parola in b:gmatch("%S+") do
local n = conta[parola] or 0
if n > 0 then comuni, conta[parola] = comuni + 1, n - 1 end
end
return 200 * comuni / (na + nb)
end
local function azione(e, c, livello)
local scelta = c.azione
if scelta == "escalation" then
if livello == 1 then scelta = "avviso"
elseif livello == 2 then scelta = "kick"
elseif livello == 3 then scelta = "tbankick"
else scelta = "bankick" end
end
if scelta == "monitor" then return end
if scelta == "avviso" then
local testo = "AutoMod: evita di ripetere messaggi simili."
if c.avviso == "pvt" then tiarca.pm(e.nick, testo)
elseif c.avviso == "notice" then tiarca.notice(e.nick, testo)
else tiarca.send(e.channel, e.nick .. ": " .. testo) end
elseif scelta == "kick" then
tiarca.kick(e.channel, e.nick, "non ripetere")
elseif scelta == "ban" then tiarca.ban(e.channel, e.nick)
elseif scelta == "mute" then tiarca.mute(e.channel, e.nick)
elseif scelta == "tban" then tiarca.tban(e.channel, e.nick, c.durata)
elseif scelta == "bankick" then
tiarca.kickban(e.channel, e.nick, "non ripetere")
elseif scelta == "tbankick" then
tiarca.kicktban(e.channel, e.nick, c.durata, "non ripetere")
end
end
tiarca.on("message", function(e)
if not e.channel:match("^[#&+!]") or e.message_type ~= "normal" then return end
local c = impostazioni(e.server)
if not c.on or not c.canali[e.channel:lower()] then return end
local now = e.timestamp
local k,r = prepara(e, now, c)
local sig, n = impronta(e.text or "")
local max = 0
if n >= c.minparole then
for _,precedente in ipairs(r.lista) do
if precedente.n >= c.minparole then
  local percentuale = simile(sig, n, precedente.sig, precedente.n)
  if percentuale > max then max = percentuale end
end
end
end
while #r.lista >= c.ultimi do
table.remove(r.lista,1)
messaggi = messaggi - 1
end
while messaggi >= MAX_MESSAGGI do
if not posto(k) then break end
end
r.lista[#r.lista+1] = {sig=sig, n=n, t=now}
messaggi = messaggi + 1
if max < c.soglia then return end
if now - r.ultima_azione < c.pausa then return end
r.infrazione = r.infrazione + 1
r.ultima_infrazione, r.ultima_azione = now, now
tiarca.log(string.format("Mod %s/%s %s: %.1f%% #%d %s",
e.server:sub(1,8), e.channel, e.nick, max, r.infrazione, c.azione))
azione(e,c,r.infrazione)
end)
tiarca.on("timer", function(e) pulizia(e.server, e.timestamp) end)
tiarca.on("disconnected", function(e) reset(e.server) end)
for _,tipo in ipairs({"join","part","quit","nick"}) do
tiarca.on(tipo, function(e)
if e.channel:match("^[#&+!]") and e.nick ~= "" then
local k = chiave(e)
local r = cronologie[k]
if r then
  messaggi = messaggi - #r.lista
  r.lista = {}
  if r.infrazione == 0 then elimina(k)
  elseif tipo == "nick" and e.text and #e.text < 65 then
    local nuovo = e.server.."\0"..e.channel:lower().."\0"..e.text:lower()
    if not cronologie[nuovo] then cronologie[nuovo],cronologie[k]=r,nil end
  end
end
end
end)
end
tiarca.on("command", function(e)
local cmd = (e.text or ""):lower():match("^%s*(.-)%s*$")
if cmd ~= "mod" and not cmd:match("^mod%s+") then return end
local c = impostazioni(e.server)
local key, val = cmd:match("^mod%s+(%S+)%s*(.-)%s*$")
local msg
if cmd == "mod" or key == "help" then
msg = "Canali: /lua mod canali [add|drop #canale]; stato|on|off|reset\n" ..
"soglia 80, ultimi 3, minuti 30, minparole 5, pausa 10, durata 10m\n" ..
"avviso pubblico|pvt|notice; azione monitor|avviso|kick|ban|mute|tban|bankick|escalation\n" ..
"/lua restart resetta"
elseif key == "stato" then
local n=0;for _ in pairs(c.canali) do n=n+1 end
msg = string.format("%s | %d canali | %s | soglia %d%% | ultimi %d | minuti %d | minparole %d | pausa %ds | durata %s | u%d m%d",
c.on and "ON" or "OFF", n, c.azione .. "/" .. c.avviso, c.soglia, c.ultimi,
c.minuti, c.minparole, c.pausa, c.durata, utenti, messaggi)
elseif key == "on" or key == "off" then
c.on = key == "on"
if not c.on then reset(e.server) end
msg = "AutoMod " .. (c.on and "attivo" or "disattivato")
elseif key == "reset" then
reset(e.server)
msg = "Cronologia cancellata per questa rete"
elseif key == "canali" then
local op,ch=val:match("^(%S+)%s+(%S+)$")
if val == "" then
local a={}
for name in pairs(c.canali) do a[#a+1]=name end
table.sort(a)
msg=#a==0 and "Nessun canale" or table.concat(a,", ")
if #msg>480 then msg=msg:sub(1,477).."..." end
elseif not ch or not ch:match("^[#&+!][^,%s:%c]+$") or #ch>64 then
msg="Uso: add|drop #canale"
else
ch=ch:lower()
if op=="add" then
  local n=0;for _ in pairs(c.canali) do n=n+1 end
  if n>=16 and not c.canali[ch] then msg="Massimo 16 canali"
  else c.canali[ch]=true;msg="Aggiunto: "..ch end
elseif op=="drop" then
  c.canali[ch]=nil
  local prefix=e.server.."\0"..ch.."\0"
  for k in pairs(cronologie) do
    if k:sub(1,#prefix)==prefix then elimina(k) end
  end
  msg="Rimosso: "..ch
else msg="Usa add o drop"
end
end
elseif key == "avviso" then
if val == "privato" then val = "pvt" end
if val == "pubblico" or val == "pvt" or val == "notice" then
c.avviso = val; msg = "Avvertimento: " .. val
end
elseif key == "azione" then
local azioni = {monitor=true,avviso=true,kick=true,ban=true,
mute=true,tban=true,bankick=true,escalation=true}
if azioni[val] then c.azione = val; msg = "Azione: " .. val end
elseif key == "durata" then
local n, unit = val:match("^(%d%d?)([mhd])$")
if n and tonumber(n) >= 1 and tonumber(n) <= 99 then
c.durata = val; msg = "Ban temporaneo: " .. val
end
else
local field, low, high = nil, 0, 0
if key == "soglia" then field,low,high = "soglia",60,100
elseif key == "ultimi" then field,low,high = "ultimi",1,10
elseif key == "minuti" then field,low,high = "minuti",1,120
elseif key == "minparole" then field,low,high = "minparole",3,15
elseif key == "pausa" then field,low,high = "pausa",0,120 end
local num = tonumber(val)
if field and num and num == math.floor(num) and num >= low and num <= high then
c[field] = num
msg = field .. " = " .. tostring(num)
end
end
tiarca.show("AutoMod", msg or
"help")
end)
