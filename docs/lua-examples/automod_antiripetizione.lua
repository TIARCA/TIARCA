-- TIARCA AutoMod: /lua mod help
-- Network settings persist with opt-in TIARCA storage; recent message history remains volatile.
local MAX_UTENTI, MAX_MESSAGGI = 1000, 2400
local MAX_TESTO, MAX_PAROLE = 160, 32
local LIMIT_LISTA = 32
local config, cronologie = {}, {}
local utenti, messaggi = 0, 0

local categorie={"registrati","voice","halfop","op","admin","owner"}
local azioni={monitor=true,avviso=true,kick=true,ban=true,mute=true,
tban=true,bankick=true,tbankick=true,escalation=true}
local avvisi={pubblico=true,pvt=true,notice=true}
local function validAccount(v)
return v and #v>0 and #v<=64 and v:match("^[%w_.%-]+$") and v~="*" and v~="0"
end
local function validChannel(v)
return v and #v<=64 and v:match("^[#&+!][^,%s:%c]+$")
end
local function defaults()
return {on=true,canali={},whitelist={},blacklist={},
categorie={registrati=true,voice=true,halfop=true,op=true,admin=true,owner=true},
soglia=80,ultimi=3,minuti=30,minparole=5,pausa=10,
durata="10m",azione="monitor",avviso="pubblico"}
end
local function ordinati(t)
local a={}
for k in pairs(t) do a[#a+1]=k end
table.sort(a)
return a
end
local function serializza(c)
local flags=""
for _,k in ipairs(categorie) do flags=flags..(c.categorie[k] and "1" or "0") end
local lines={table.concat({"v1",c.on and 1 or 0,c.soglia,c.ultimi,
c.minuti,c.minparole,c.pausa,c.durata,c.azione,c.avviso,flags},";")}
for _,entry in ipairs({{"C",c.canali},{"W",c.whitelist},{"B",c.blacklist}}) do
for _,v in ipairs(ordinati(entry[2])) do lines[#lines+1]=entry[1]..v end
end
return table.concat(lines,"\n")
end
local function numero(v,lo,hi)
local n=tonumber(v)
return n and n==math.floor(n) and n>=lo and n<=hi
end
local function deserializza(raw)
if type(raw)~="string" or #raw>8192 then return nil end
local rows={}
for line in (raw.."\n"):gmatch("(.-)\n") do
if line=="" then return nil end
rows[#rows+1]=line
end
local a,b,d,f,g,h,i,j,k,l=rows[1]:match(
"^v1;([01]);(%d+);(%d+);(%d+);(%d+);(%d+);([1-9]%d?[mhd]);([%a]+);([%a]+);([01]+)$")
if not a or #l~=6 or not numero(b,60,100) or not numero(d,1,10)
or not numero(f,1,120) or not numero(g,3,15)
or not numero(h,0,120) or not azioni[j] or not avvisi[k] then return nil end
local c=defaults()
c.on=a=="1";c.soglia=tonumber(b);c.ultimi=tonumber(d)
c.minuti=tonumber(f);c.minparole=tonumber(g);c.pausa=tonumber(h)
c.durata=i;c.azione=j;c.avviso=k
for index,name in ipairs(categorie) do c.categorie[name]=l:sub(index,index)=="1" end
for index=2,#rows do
local row=rows[index]
local tag,v=row:sub(1,1),row:sub(2)
local group,limit,valid
if tag=="C" then group,limit,valid=c.canali,16,validChannel
elseif tag=="W" then group,limit,valid=c.whitelist,32,validAccount
elseif tag=="B" then group,limit,valid=c.blacklist,32,validAccount
else return nil end
if not valid(v) or v~=v:lower() or group[v] then return nil end
group[v]=true
local n=0
for _ in pairs(group) do n=n+1 end
if n>limit then return nil end
end
return c
end
local function impostazioni(server)
if config[server] then return config[server] end
local ok,raw=pcall(tiarca.storage.get,"automod."..server)
if not ok then return nil end
local c=raw and deserializza(raw) or defaults()
if c then config[server]=c end
return c
end
local function copia(c)
local out={}
for k,v in pairs(c) do
if type(v)=="table" then
local t={}
for name,enabled in pairs(v) do t[name]=enabled end
out[k]=t
else out[k]=v end
end
return out
end
local function salva(server,c)
local value=serializza(c)
return #value<=8192 and pcall(tiarca.storage.set,"automod."..server,value)
end

local function chiave(e)
return e.server.."\0"..e.channel:lower().."\0"..e.nick:lower()
end
local function elimina(k)
local r=cronologie[k]
if not r then return end
utenti=utenti-1
messaggi=messaggi-#r.lista
cronologie[k]=nil
end
local function scaduti(r,now,minuti)
local sec=minuti*60
for i=#r.lista,1,-1 do
  if now-r.lista[i].t>=sec then
    table.remove(r.lista,i); messaggi=messaggi-1
  end
end
if r.infrazione>0 and now-r.ultima_infrazione>=sec then
  r.infrazione,r.ultima_infrazione=0,0
end
end
local function pulizia(server,now)
for k,r in pairs(cronologie) do
  if r.server==server then
    local c=impostazioni(server)
    if not c then return end
    scaduti(r,now,c.minuti)
    if #r.lista==0 and r.infrazione==0 then elimina(k) end
  end
end
end
local function reset(server)
for k,r in pairs(cronologie) do
  if r.server==server then elimina(k) end
end
end
local function reset_canale(server,channel)
local prefix=server.."\0"..channel:lower().."\0"
for k in pairs(cronologie) do
  if k:sub(1,#prefix)==prefix then elimina(k) end
end
end
local function posto(escludi)
local key,time
for k,r in pairs(cronologie) do
  if k~=escludi and (not time or r.visto<time) then key,time=k,r.visto end
end
if not key then return false end
elimina(key); return true
end
local function prepara(e,now,c)
local key=chiave(e)
local r=cronologie[key]
if r then scaduti(r,now,c.minuti)
else
  if utenti>=MAX_UTENTI then posto(key) end
  r={server=e.server,lista={},infrazione=0,ultima_infrazione=0,
     ultima_azione=0,visto=now}
  cronologie[key],utenti=r,utenti+1
end
r.visto=now
return key,r
end

local function impronta(text)
local parti,bytes={},0
for word in text:lower():gsub("[%c%p]"," "):gmatch("%S+") do
  if #parti>=MAX_PAROLE or bytes+#word+1>MAX_TESTO then return "",0 end
  parti[#parti+1]=word; bytes=bytes+#word+1
end
return table.concat(parti," "),#parti
end
local function simile(a,na,b,nb)
local counts,common={},0
for w in a:gmatch("%S+") do counts[w]=(counts[w] or 0)+1 end
for w in b:gmatch("%S+") do
  local n=counts[w] or 0
  if n>0 then common=common+1;counts[w]=n-1 end
end
return 200*common/(na+nb)
end

-- Returns an explicitly eligible user and whether an authenticated
-- blacklisted account overrides a category whitelist.
-- Without user-info permission or a known account state: fail closed.
local function soggetto(e,c)
local ok,list=pcall(tiarca.get_users,e.channel)
if not ok or type(list)~="table" then return false end
for _,u in ipairs(list) do
  if u.nick and u.nick:lower()==e.nick:lower() then
    local account=u.account and u.account:lower() or nil
    if not account or account=="" then return false end
    if account~="*" and c.blacklist[account] then
      return true,true,account
    end
    if account~="*" and
       (c.whitelist[account] or c.categorie.registrati) then return false end
    local p=u.prefix or ""
    if (p:find("+",1,true) and c.categorie.voice) or
       (p:find("%",1,true) and c.categorie.halfop) or
       (p:find("@",1,true) and c.categorie.op) or
       (p:find("&",1,true) and c.categorie.admin) or
       (p:find("~",1,true) and c.categorie.owner) then return false end
    return true,p~="",account
  end
end
return false
end

local function azione(e,c,livello,override,account)
local scelta=c.azione
if scelta=="escalation" then
  if livello==1 then scelta="avviso"
  elseif livello==2 then scelta="kick"
  elseif livello==3 then scelta="tbankick"
  else scelta="bankick" end
end
if scelta=="monitor" then return end
if scelta=="avviso" then
  local text="AutoMod: evita di ripetere messaggi simili."
  if c.avviso=="pvt" then tiarca.pm(e.nick,text)
  elseif c.avviso=="notice" then tiarca.notice(e.nick,text)
  else tiarca.send(e.channel,e.nick..": "..text) end
elseif override then
  tiarca.override_action(e.channel,e.nick,account,scelta,c.durata)
elseif scelta=="kick" then tiarca.kick(e.channel,e.nick,"non ripetere")
elseif scelta=="ban" then tiarca.ban(e.channel,e.nick)
elseif scelta=="mute" then tiarca.mute(e.channel,e.nick)
elseif scelta=="tban" then tiarca.tban(e.channel,e.nick,c.durata)
elseif scelta=="bankick" then tiarca.kickban(e.channel,e.nick,"non ripetere")
elseif scelta=="tbankick" then
  tiarca.kicktban(e.channel,e.nick,c.durata,"non ripetere")
end
end

tiarca.on("message",function(e)
if not e.channel:match("^[#&+!]") or e.message_type~="normal" then return end
local c=impostazioni(e.server)
if not c or not c.on or not c.canali[e.channel:lower()] then return end
local allowed,override,account=soggetto(e,c)
if not allowed then
  elimina(chiave(e))
  return
end
local now=e.timestamp
local key,r=prepara(e,now,c)
local sig,n=impronta(e.text or "")
local maximum=0
if n>=c.minparole then
  for _,before in ipairs(r.lista) do
    if before.n>=c.minparole then
      local pct=simile(sig,n,before.sig,before.n)
      if pct>maximum then maximum=pct end
    end
  end
end
while #r.lista>=c.ultimi do
  table.remove(r.lista,1);messaggi=messaggi-1
end
while messaggi>=MAX_MESSAGGI do
  if not posto(key) then break end
end
r.lista[#r.lista+1]={sig=sig,n=n,t=now}
messaggi=messaggi+1
if maximum<c.soglia or now-r.ultima_azione<c.pausa then return end
r.infrazione=r.infrazione+1
r.ultima_infrazione,r.ultima_azione=now,now
tiarca.log(string.format("AutoMod %s/%s %s %.1f%% infrazione %d (%s)",
  e.server:sub(1,8),e.channel,e.nick,maximum,r.infrazione,c.azione))
azione(e,c,r.infrazione,override,account)
end)
tiarca.on("timer",function(e) pulizia(e.server,e.timestamp) end)
tiarca.on("disconnected",function(e) reset(e.server) end)
for _,event in ipairs({"join","part","quit","nick"}) do
tiarca.on(event,function(e)
  if not e.channel:match("^[#&+!]") or e.nick=="" then return end
  local key=chiave(e)
  local r=cronologie[key]
  if not r then return end
  messaggi=messaggi-#r.lista
  r.lista={}
  if r.infrazione==0 then elimina(key)
  elseif event=="nick" and e.text and #e.text<65 then
    local nextKey=e.server.."\0"..e.channel:lower().."\0"..e.text:lower()
    if not cronologie[nextKey] then
      cronologie[nextKey],cronologie[key]=r,nil
    end
  end
end)
end

local function keys(map)
local out={}
for k in pairs(map) do out[#out+1]=k end
table.sort(out)
return out
end
local function count(map)
local n=0
for _ in pairs(map) do n=n+1 end
return n
end
local function show(title,text)
tiarca.show(title,text)
end
local function showmap(title,map)
local all=keys(map)
if #all==0 then show(title,"nessuno");return end
local line=""
for _,item in ipairs(all) do
  if #line+#item+2>380 then show(title,line);line="" end
  line=line=="" and item or line.."; "..item
end
if line~="" then show(title,line) end
end
local function editList(map,operation,value)
if not validAccount(value) then return "Account non valido" end
if operation=="add" then
  if not map[value] and count(map)>=32 then return "Massimo 32 account" end
  map[value]=true
  return "Aggiunto: "..value
elseif operation=="drop" then
  map[value]=nil
  return "Rimosso: "..value
end
return "Uso: add|drop account"
end

local function help(topic)
if topic=="" or topic=="generale" then
  show("AutoMod: comandi",
    "/lua mod help [canali|liste|azione|parametri]; /lua mod stato; /lua mod on|off; /lua mod reset")
end
if topic=="" or topic=="canali" then
  show("AutoMod: canali",
    "/lua mod canali; /lua mod canali add #canale; /lua mod canali drop #canale; default: nessun canale")
end
if topic=="" or topic=="liste" or topic=="whitelist" or topic=="blacklist" then
  show("AutoMod: whitelist/blacklist",
    "/lua mod whitelist [add|drop account]; /lua mod blacklist [add|drop account]; blacklist prevale sulla whitelist")
  show("AutoMod: categorie",
    "/lua mod whitelist categoria registrati|voice|halfop|op|admin|owner on|off; default: tutte ON")
end
if topic=="" or topic=="azione" then
  show("AutoMod: intervento",
    "/lua mod azione monitor|avviso|kick|ban|mute|tban|bankick|tbankick|escalation; /lua mod avviso pubblico|pvt|notice")
  show("AutoMod: escalation",
    "Avviso; kick; kick + ban temporaneo; kick + ban. Motivo kick: non ripetere")
end
if topic=="" or topic=="parametri" then
  show("AutoMod: parametri",
    "/lua mod soglia 80; ultimi 3; minuti 30; minparole 5; pausa 10; durata 10m")
  show("AutoMod: sicurezza",
    "Richiede permessi lettura utenti e moderazione. Identita' account sconosciuta: nessun intervento. /lua restart conserva le impostazioni.")
end
end

tiarca.on("command",function(e)
local cmd=(e.text or ""):lower():match("^%s*(.-)%s*$")
if cmd~="mod" and not cmd:match("^mod%s+") then return end
local old=impostazioni(e.server)
if not old then
  show("AutoMod","Abilita Dati persistenti o verifica lo storage: AutoMod sospeso.")
  return
end
local c=copia(old)
local changed,clear=false,nil
local key,val=cmd:match("^mod%s+(%S+)%s*(.-)%s*$")
if cmd=="mod" or key=="help" then help(val or "");return end
local msg
if key=="stato" then
  msg=string.format("%s; %s/%s; canali %d; white %d; black %d; %d%%; ultimi %d; minuti %d; parole %d; pausa %ds; durata %s; utenti %d; messaggi %d",
    c.on and "ON" or "OFF",c.azione,c.avviso,count(c.canali),
    count(c.whitelist),count(c.blacklist),c.soglia,c.ultimi,c.minuti,
    c.minparole,c.pausa,c.durata,utenti,messaggi)
elseif key=="on" or key=="off" then
  c.on=key=="on"
  changed=true
  if not c.on then clear="all" end
  msg="AutoMod "..(c.on and "ON" or "OFF")
elseif key=="reset" then
  reset(e.server);msg="Storico cancellato"
elseif key=="canali" then
  if val=="" then showmap("AutoMod: canali",c.canali);return end
  local op,ch=val:match("^(%S+)%s+(%S+)$")
  if not op or (op~="add" and op~="drop") or not validChannel(ch) then
    msg="Uso: /lua mod canali add|drop #canale"
  else
    ch=ch:lower()
    if op=="add" then
      if not c.canali[ch] and count(c.canali)>=16 then msg="Massimo 16 canali"
      else c.canali[ch]=true;changed=true;msg="Canale aggiunto: "..ch end
    else
      c.canali[ch]=nil
      changed=true;clear=ch
      msg="Canale rimosso: "..ch
    end
  end
elseif key=="whitelist" or key=="blacklist" then
  local target=key=="whitelist" and c.whitelist or c.blacklist
  if val=="" then
    showmap("AutoMod: "..key,target)
    if key=="whitelist" then
      local categories={}
      for _,name in ipairs({"registrati","voice","halfop","op","admin","owner"}) do
        categories[#categories+1]=name.."="..(c.categorie[name] and "on" or "off")
      end
      show("AutoMod: categorie",table.concat(categories,"; "))
    end
    return
  end
  local op,name,state=val:match("^(%S+)%s+(%S+)%s+(%S+)$")
  if key=="whitelist" and op=="categoria" and c.categorie[name]~=nil and
     (state=="on" or state=="off") then
    c.categorie[name]=state=="on"
    changed=true;msg=name.."="..state
  else
    local cmd2,account=val:match("^(%S+)%s+(%S+)$")
    if cmd2=="add" or cmd2=="drop" then
      msg=editList(target,cmd2,account)
      changed=not msg:match("^Account non valido") and not msg:match("^Massimo")
    else
      msg="Uso: "..key.." add|drop account"..
         (key=="whitelist" and "; categoria nome on|off" or "")
    end
  end
elseif key=="avviso" then
  if val=="privato" then val="pvt" end
  if val=="pubblico" or val=="pvt" or val=="notice" then
    c.avviso=val;changed=true;msg="Avviso: "..val
  end
elseif key=="azione" then
  if ({monitor=true,avviso=true,kick=true,ban=true,mute=true,
       tban=true,bankick=true,tbankick=true,escalation=true})[val] then
    c.azione=val;changed=true;msg="Azione: "..val
  end
elseif key=="durata" then
  if val:match("^[1-9]%d?[mhd]$") then
    c.durata=val;changed=true;msg="Durata: "..val
  end
else
  local field,lo,hi
  if key=="soglia" then field,lo,hi="soglia",60,100
  elseif key=="ultimi" then field,lo,hi="ultimi",1,10
  elseif key=="minuti" then field,lo,hi="minuti",1,120
  elseif key=="minparole" then field,lo,hi="minparole",3,15
  elseif key=="pausa" then field,lo,hi="pausa",0,120 end
  local n=tonumber(val)
  if field and n and n==math.floor(n) and n>=lo and n<=hi then
    c[field]=n;changed=true;msg=field.."="..n
  end
end
if changed then
  if not salva(e.server,c) then
    show("AutoMod","Salvataggio fallito: impostazioni precedenti conservate.")
    return
  end
  config[e.server]=c
  if clear=="all" then reset(e.server)
  elseif clear then reset_canale(e.server,clear) end
end
show("AutoMod",msg or "Comando non valido; usa /lua mod help")
end)
