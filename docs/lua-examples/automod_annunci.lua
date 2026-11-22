-- TIARCA AutoMod B: annunci ripetuti, separato dall'AutoMod generale.
-- Importa disabilitato; consensi: storage, membri/identita, invio, moderazione.
-- Nessun canale attivo di default; /lua b help.
-- Le sanzioni sono abilitate SOLO da /lua b modo live.
local STORAGE_PREFIX="b2.s"
local SHARDS,MAX_USERS,WINDOW,TTL,MIN_WORDS=12,360,1200,86400,2
local state,configs,sessions,index={}, {}, {}, {}
local loaded,broken,serial,users=false,false,0,0
local function norm(v)
  v=(v or ""):lower():gsub("à","a"):gsub("è","e"):gsub("é","e")
  v=v:gsub("ì","i"):gsub("ò","o"):gsub("ù","u")
  v=v:gsub("[%p%c]"," "):gsub("%s+"," "):match("^%s*(.-)%s*$") or ""
  -- Compatibilita tra "m 08" e "m08", idem f 09.
  return v:gsub("(%f[%a][mf])%s+(%d+)","%1%2")
end
local function words(s)
  local r={}
  for w in s:gmatch("%S+") do
    if #r==48 then break end
    r[#r+1]=w
  end
  return r
end
local function has(w,alternatives)
  for _,t in ipairs(w) do if alternatives[t] then return true end end
  return false
end
local function nearby(w,left,right,span)
  for i,a in ipairs(w) do
    if left[a] then
      for j=math.max(1,i-span),math.min(#w,i+span) do
        if i~=j and right[w[j]] then return true end
      end
    end
  end
  return false
end
local target={f=true,ragazza=true,ragazze=true,donna=true,donne=true,
              qualcuna=true,nessuna=true,chattatemi=true}
local invite={qualcuna=true,ragazza=true,ragazze=true,coetanei=true,
              coetanee=true,scrivetemi=true,f=true}
local private={pvt=true,privato=true,privata=true}
local chat={chat=true,chatta=true,chattare=true,conoscenza=true,conoscere=true}
local encounter={incontro=true,incontri=true,reale=true}
local call={cam=true,call=true,videochat=true}
local places={parma=true,caserta=true,milano=true,roma=true,napoli=true,
 biella=true,veneto=true,puglia=true,lecce=true,sicilia=true,friuli=true,
 piemonte=true,marche=true,toscana=true,bari=true,salerno=true,
 genova=true,torino=true,calabria=true,siciliana=true}
local regional={siciliana=true,napoletana=true,romana=true,milanese=true,
 pugliese=true,toscana=true,leccese=true}
local function mcode(w)
  for _,x in ipairs(w) do if x:match("^m%d%d?%d?%d?$") then return true end end
  return false
end
-- Una sola categoria per messaggio, con priorita alle regole piu specifiche.
-- B11-B13 sono osservazione, NON contribuiscono alla concentrazione.
local rules={
 {"A","B01",function(s) return s:find("%f[%a]f%s+[pxe][er]?%s+m%d+")~=nil or
                                     s:find("%f[%a]f%s+x%s+m%d+")~=nil end},
 {"B","B02",function(s,w) return mcode(w) and (s:find("cerco f",1,true) or
 s:find("cerca f",1,true) or s:find("per f",1,true) or
 s:find("x f",1,true)) end},
 {"C","B03",function(s,w) return mcode(w) and nearby(w,{},{},1)==true or
 (mcode(w) and nearby(w,(function()
   local t={} for _,x in ipairs(w) do if x:match("^m%d+$") then t[x]=true end end
   return t end)(),invite,4)) end},
 {"D","B04",function(s,w) return nearby(w,target,private,7) end},
 {"E","B05",function(s,w) return nearby(w,target,chat,4) or
 (has(w,{maturo=true,maturi=true}) and has(w,chat)) end},
 {"F","B06",function(s,w) return nearby(w,target,places,5) end},
 {"G","B07",function(s,w) return nearby(w,target,encounter,9) end},
 {"H","B08",function(s,w) return
 s:find("qualsiasi eta",1,true)~=nil and has(w,target) or
 s:find("qualunque eta",1,true)~=nil and has(w,target) end},
 {"I","B09",function(s) return s=="mxm reale" end},
 {"J","B10",function(s,w) return nearby(w,target,call,6) end},
 {"N","B14",function(s) return s:find("esiste una ragazza di %d+")~=nil or
 s:find("esiste una ragazza della mia eta",1,true)~=nil end},
 {"O","B15",function(s,w) return (#w<=8) and
 (w[1]=="qualche" or w[1]=="qualke") and regional[w[2]]==true end},
 {"P","B16",function(s) return s:find("f per me io m%d+")~=nil end}
}
local observations={
 {"K","B11",function(s) return s:find("%f[%a]per%s+role%f[%A]")~=nil or
 s:find("%f[%a]x%s+role%f[%A]")~=nil end},
 {"L","B12",function(s,w) return nearby(w,target,
 {whatsapp=true,whatap=true,whatsap=true,telegram=true,tg=true},6) or
 nearby(w,{chi=true},{whatsapp=true,whatap=true,whatsap=true,telegram=true,tg=true},3) end},
 {"M","B13",function(s,w) return nearby(w,target,
 {liceo=true,superiori=true,liceali=true},6) end}
}
local function classify(text)
  local s=norm(text)
  local w=words(s)
  if #w<MIN_WORDS then return "0",nil end
  for _,rule in ipairs(rules) do
    if rule[3](s,w) then return rule[1],rule[2] end
  end
  for _,rule in ipairs(observations) do
    if rule[3](s,w) then return "0",rule[2] end
  end
  return "0",nil
end
local function safe(v)
  if type(v)~="string" or #v>192 or v=="" then return "" end
  if v:find("[%s%c\t,;|]") then return "" end
  return v:lower()
end
local function distinctIdent(v)
  return #v>=5 and not ({guest=true,webirc=true,android=true,
        user=true,ident=true,unknown=true,anonymous=true,
        irccloud=true,mibbit=true})[v]
end
local function shardKey(n) return STORAGE_PREFIX..string.format("%02d",n) end
local function shardNum(n) return (n%SHARDS)+1 end
local function split(s,pat)
  local r={}
  for part in (s..pat):gmatch("(.-)"..pat) do r[#r+1]=part end
  return r
end
local function cfgFor(server)
  local c=configs[server]
  if not c then c={on=false,mode="monitor",epoch=1,channels={}}; configs[server]=c end
  return c
end
local function saveConfig()
  local rows={}
  for server,c in pairs(configs) do
    local ch={}
    for v in pairs(c.channels) do ch[#ch+1]=v end
    table.sort(ch)
    rows[#rows+1]=table.concat({server,c.on and "1" or "0",c.mode,
      tostring(c.epoch),table.concat(ch,",")},"\t")
  end
  table.sort(rows)
  local value=table.concat(rows,"\n")
  if #value>8192 then return false end
  if value=="" then return pcall(tiarca.storage.delete,"b2.config") end
  return pcall(tiarca.storage.set,"b2.config",value)
end
local function encode(t)
  local marks=table.concat(t.marks)
  local last={}
  for k,v in pairs(t.last) do last[#last+1]=k..":"..v end
  table.sort(last)
  return table.concat({t.id,t.server,t.account~="" and t.account or "-",
    #t.hosts>0 and table.concat(t.hosts,",") or "-",
    #t.idents>0 and table.concat(t.idents,",") or "-",
    t.level,t.infraction,t.seen,t.epoch,
    marks~="" and marks or "-",#last>0 and table.concat(last,";") or "-"},"\t")
end
local function decode(line)
  local p=split(line,"\t")
  if #p~=11 then return nil end
  local id,lv,inf,seen,epoch=tonumber(p[1]),tonumber(p[6]),
    tonumber(p[7]),tonumber(p[8]),tonumber(p[9])
  if not id or not lv or not inf or not seen or not epoch or
      id<1 or lv<0 or lv>5 or p[2]=="" or #p[2]>64 then return nil end
  local t={id=id,server=p[2],account=p[3]=="-" and "" or p[3],
    hosts={},idents={},level=lv,infraction=inf,seen=seen,epoch=epoch,
    marks={},last={}}
  if p[4]~="-" then for x in p[4]:gmatch("[^,]+") do
    if safe(x)=="" then return nil end t.hosts[#t.hosts+1]=x end end
  if p[5]~="-" then for x in p[5]:gmatch("[^,]+") do
    if safe(x)=="" then return nil end t.idents[#t.idents+1]=x end end
  if p[10]~="-" then
    if #p[10]>10 or p[10]:find("[^0A-P]") then return nil end
    for x in p[10]:gmatch(".") do t.marks[#t.marks+1]=x end
  end
  if p[11]~="-" then for chunk in p[11]:gmatch("[^;]+") do
    local code,stamp=chunk:match("^([A-P]):(%d+)$")
    if not code or not stamp then return nil end
    t.last[code]=tonumber(stamp)
  end end
  return t
end
local function addidx(map,k,t)
  if not k or k=="" then return end
  if map[k] and map[k]~=t then map[k]=false
  elseif map[k]==nil then map[k]=t end
end
local function rebuild()
  index={account={},host={},ident={}}
  for _,t in pairs(state) do
    local s=t.server.."\0"
    addidx(index.account,s..t.account,t.account~="" and t or nil)
    for _,x in ipairs(t.hosts) do addidx(index.host,s..x,t) end
    for _,x in ipairs(t.idents) do
      if distinctIdent(x) then addidx(index.ident,s..x,t) end
    end
  end
end
local function load()
  if loaded then return not broken end
  local ok,c=pcall(tiarca.storage.get,"b2.config")
  if not ok then return false end
  if type(c)=="string" then
    for line in c:gmatch("[^\n]+") do
      local p=split(line,"\t")
      if #p==5 and (p[2]=="0" or p[2]=="1") and
          (p[3]=="monitor" or p[3]=="live") then
        local cfg=cfgFor(p[1])
        cfg.on=p[2]=="1";cfg.mode=p[3];cfg.epoch=tonumber(p[4]) or 1
        for ch in p[5]:gmatch("[^,]+") do cfg.channels[ch]=true end
      else broken=true end
    end
  end
  for n=1,SHARDS do
    local good,raw=pcall(tiarca.storage.get,shardKey(n))
    if not good then return false end
    if type(raw)=="string" then
      for line in raw:gmatch("[^\n]+") do
        local t=decode(line)
        if not t or state[t.id] or shardNum(t.id)~=n then
          broken=true
        else state[t.id]=t; serial=math.max(serial,t.id);users=users+1 end
      end
    end
  end
  if users>MAX_USERS then broken=true end
  rebuild();loaded=true
  return not broken
end
local function persist(n)
  local rows={}
  for _,t in pairs(state) do
    if shardNum(t.id)==n then rows[#rows+1]=encode(t) end
  end
  table.sort(rows)
  local v=table.concat(rows,"\n")
  if #v>8192 then broken=true;return false end
  local ok
  if v=="" then ok=pcall(tiarca.storage.delete,shardKey(n))
  else ok=pcall(tiarca.storage.set,shardKey(n),v) end
  if not ok then broken=true end
  return ok
end
local function contains(list,v)
  for _,x in ipairs(list) do if x==v then return true end end
  return false
end
local function alias(t,field,v)
  if v=="" or contains(t[field],v) then return end
  local l=t[field];l[#l+1]=v
  while #l>3 do table.remove(l,1) end
end
local function nickKey(server,nick) return server.."\0"..nick:lower() end
local function observed(e)
  local ok,roster=pcall(tiarca.get_users,e.channel)
  if not ok or type(roster)~="table" then return nil end
  for _,u in ipairs(roster) do
    if u.nick and u.nick:lower()==e.nick:lower() then
      local prefix=u.prefix or ""
      if prefix~="" then return nil end -- Ops, voice, halfop e simili: esenti.
      return safe(u.account=="*" and "" or u.account),safe(u.host),safe(u.ident)
    end
  end
  return nil
end
local function resolve(e,account,host,ident)
  local k=e.server.."\0"
  local a=account~="" and index.account[k..account] or nil
  local h=host~="" and index.host[k..host] or nil
  local i=distinctIdent(ident) and index.ident[k..ident] or nil
  local r=a or h or i
  if a==false or (not a and h==false and not i) then r=nil end
  -- Credenziali registrate differenti NON condividono una cronologia.
  if r and (r.account=="" or account=="" or r.account==account) then
    if account~="" and r.account=="" then r.account=account end
  else r=nil end
  -- Un ident identico su due connessioni simultanee discordanti non basta.
  if not a and not h and i and i~=false and host~="" and
      not contains(i.hosts,host) and e.timestamp-i.seen<900 then
    local own=sessions[nickKey(e.server,e.nick)]
    if own~=i.id then r=nil end
  end
  if not r then
    if users>=MAX_USERS then return nil end
    serial=serial+1;users=users+1
    r={id=serial,server=e.server,account=account,hosts={},idents={},
       level=0,infraction=0,seen=e.timestamp,epoch=cfgFor(e.server).epoch,
       marks={},last={}}
    state[r.id]=r
  end
  alias(r,"hosts",host);alias(r,"idents",ident)
  r.seen=e.timestamp
  sessions[nickKey(e.server,e.nick)]=r.id
  rebuild()
  return r
end
local function active(e)
  local c=cfgFor(e.server)
  return c.on and c.channels[e.channel:lower()] and c or nil
end
local function decide(e,t,c,letter,rule)
  if t.epoch~=c.epoch then
    t.level=0;t.infraction=0;t.marks={};t.last={};t.epoch=c.epoch
  end
  if t.level>0 and e.timestamp-t.infraction>=TTL then t.level=0;t.infraction=0 end
  local earlier=t.last[letter]
  t.last[letter]=e.timestamp
  -- Il controllo e' retroattivo sui 10 messaggi, incluso quello corrente.
  t.marks[#t.marks+1]=letter
  while #t.marks>10 do table.remove(t.marks,1) end
  local recognized=0
  for _,v in ipairs(t.marks) do if v~="0" then recognized=recognized+1 end end
  local qualified=recognized*3>=#t.marks*2
  local repeatNow=earlier and e.timestamp>=earlier and e.timestamp-earlier<=WINDOW
  local violation=qualified and (t.level>0 or repeatNow)
  if not violation then return nil,recognized,#t.marks end
  t.level=math.min(5,t.level+1);t.infraction=e.timestamp
  return t.level,recognized,#t.marks
end
local function recordPlain(t,c)
  if t.epoch~=c.epoch then
    t.level=0;t.infraction=0;t.marks={};t.last={};t.epoch=c.epoch
  end
  t.marks[#t.marks+1]="0"
  while #t.marks>10 do table.remove(t.marks,1) end
end
local function action(e,level)
  if level==1 then tiarca.pm(e.nick,"non ripetere su "..e.channel)
  elseif level==2 then
    tiarca.tmute(e.channel,e.nick,"2m")
    tiarca.pm(e.nick,"Sei stato silenziato per 2 minuti. La chat non e' una bacheca annunci. Cerca di interagire in pubblica.")
  elseif level==3 then tiarca.kick(e.channel,e.nick,
      "non ripetere su "..e.channel..", rischi il ban")
  elseif level==4 then tiarca.tmute(e.channel,e.nick,"3h")
  else tiarca.kickban(e.channel,e.nick,"non ripetere") end
end
local function log(e,level,found,total,rule,mode)
  local pct=100*found/total
  local label=level==1 and "avviso" or level==2 or level==4 and "tmute" or
    level==3 and "kick" or "bankick"
  if level==2 or level==4 then label="tmute" end
  local logged=mode=="live" and label or "monitor"
  -- L'evento è una richiesta, non la prova che il server abbia eseguito la sanzione.
  tiarca.log(string.format("AutoModB %s/%s %s %.1f%% infrazione %d (%s)",
    e.server:sub(1,8),e.channel,e.nick,pct,level,logged))
end
tiarca.on("message",function(e)
  if not e.channel:match("^[#&+!]") or e.message_type~="normal" or
     not load() or not active(e) then return end
  local c=cfgFor(e.server)
  local account,host,ident=observed(e)
  if account==nil or (account=="" and host=="" and ident=="") then return end
  local t=resolve(e,account,host,ident)
  if not t then return end
  local letter,rule=classify(e.text or "")
  local level,n,d
  if letter=="0" then recordPlain(t,c)
  else level,n,d=decide(e,t,c,letter,rule) end
  if not persist(shardNum(t.id)) then return end
  if level then
    log(e,level,n,d,rule,c.mode)
    if c.mode=="live" then action(e,level) end
  end
end)
-- Il server notifica un cambio NICK: la sessione continua con il nuovo nick.
tiarca.on("nick",function(e)
  if not e.nick or not e.text then return end
  local old=nickKey(e.server,e.nick)
  local sid=sessions[old]
  if sid then
    sessions[nickKey(e.server,e.text)]=sid
    sessions[old]=nil
  end
end)
tiarca.on("quit",function(e)
  if e.nick then sessions[nickKey(e.server,e.nick)]=nil end
end)
tiarca.on("disconnected",function(e)
  for k in pairs(sessions) do
    if k:sub(1,#e.server+1)==e.server.."\0" then sessions[k]=nil end
  end
end)
local function show(title,msg) tiarca.show(title,msg) end
tiarca.on("command",function(e)
  local op,rest=(e.text or ""):match("^%s*b%s*(%S*)%s*(.-)%s*$")
  if op==nil then return end
  if op=="help" or op=="" then
    show("AutoMod B","/lua b stato; b canali add|drop #canale; b on|off; b modo monitor|live; b libreria; b reset")
    show("AutoMod B - logica","2 messaggi riconosciuti/20m; ultimi 10 messaggi, 2/3; escalation 24h; 1 avviso, 2 mute 2m, 3 kick, 4 mute 3h, 5 kick+ban.")
    return
  end
  if not load() then show("AutoMod B","Storage indisponibile o stato corrotto: nessuna moderazione.");return end
  local c=cfgFor(e.server)
  if op=="stato" then
    local count=0 for _ in pairs(c.channels) do count=count+1 end
    show("AutoMod B",string.format("%s; modo %s; canali %d; utenti %d; 20m, 2 occorrenze; 10 messaggi, soglia 2/3; escalation 24h.",
      c.on and "ON" or "OFF",c.mode,count,users))
  elseif op=="libreria" then
    show("AutoMod B - regole","Attive: B01-10, B14-16. Solo osservazione: B11-13. Ogni regola conta una sola volta per messaggio.")
  elseif op=="canali" then
    local command,ch=rest:match("^(%S+)%s+(%S+)$")
    if not command or (command~="add" and command~="drop") or
        not ch:match("^[#&+!][^%s,:%c]+$") or #ch>64 then
      show("AutoMod B","Uso: /lua b canali add|drop #canale");return end
    local count=0 for _ in pairs(c.channels) do count=count+1 end
    if command=="add" and count>=16 and not c.channels[ch:lower()] then
      show("AutoMod B","Massimo 16 canali");return end
    if command=="add" then c.channels[ch:lower()]=true
    else c.channels[ch:lower()]=nil end
    show("AutoMod B",saveConfig() and "Canali aggiornati" or "Salvataggio non riuscito")
  elseif op=="on" or op=="off" then
    c.on=op=="on"
    show("AutoMod B",saveConfig() and ("Motore "..op) or "Salvataggio non riuscito")
  elseif op=="modo" then
    if rest~="monitor" and rest~="live" then
      show("AutoMod B","Uso: /lua b modo monitor|live");return end
    if c.mode~=rest then c.epoch=c.epoch+1;c.mode=rest end
    show("AutoMod B",saveConfig() and ("Modo "..rest.." (storico infrazioni resettato per il nuovo modo)") or
        "Salvataggio non riuscito")
  elseif op=="reset" then
    -- Cambia epoca: non eredita infrazioni o messaggi di un vecchio test.
    c.epoch=c.epoch+1
    show("AutoMod B",saveConfig() and "Cronologia operativa azzerata" or "Salvataggio non riuscito")
  end
end)
