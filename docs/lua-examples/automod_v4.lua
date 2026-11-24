-- TIARCA AutoMod V4: Watchdog (detection) + Mod (existing escalation).
-- Separate from Harvester; disable previous AutoMod scripts on these networks.
local TTL,SHARDS=86400,20
local configs,allow,history,watch,hostSeen={},{},{},{},{}
local loaded,failed=false,false
local function words(s)
 local t={} for v in s:gmatch("%S+") do t[#t+1]=v end return t
end
local function dict(s)
 local d={} for v in s:gmatch("%S+") do d[v]=true end return d
end
local people=dict("f m mxm ragazza ragazze ragazzo ragazzi donna donne uomo uomini qualcuna qualcuno nessuna nessuno coetanea coetanee coetaneo coetanei")
local single=dict("f m mxm ragazza ragazze donna donne qualcuna qualcuno nessuna nessuno")
local search=dict("cerco cerca cercasi cerchiamo chi qualche qualke")
local chatting=dict("chat chatta chattare chatto chiacchiera conoscenza conoscere compagnia amicizia amicizie parlare")
local contact=dict("pvt priv privato privata scrivimi scrivetemi contattami contattatemi")
local encounter=dict("reale incontro incontri ospito ospitare uscire")
local media=dict("cam call videochat whatsapp whatap whatsap telegram tg")
local places=dict("parma caserta milano roma napoli biella veneto puglia lecce sicilia friuli piemonte marche toscana bari salerno genova torino calabria calabira siciliana verona enna viareggio firenze bologna palermo catania abruzzo molise umbria lazio sardegna lombardia campania")
local greet=dict("ciao salve buongiorno buonasera buonanotte sera")
local greetings={
 ["ciao"]=true,["salve"]=true,["sera"]=true,["buongiorno"]=true,
 ["buonasera"]=true,["buonanotte"]=true,["buona sera"]=true,
 ["buon pomeriggio"]=true,["buona serata"]=true,["buona notte"]=true,
 ["ciao tutti"]=true,["ciao a tutti"]=true,["salve a tutti"]=true,
 ["sera a tutti"]=true,["buonasera a tutti"]=true,
 ["buongiorno a tutti"]=true,["buonanotte a tutti"]=true,
 ["un saluto a tutti"]=true,["ciao chat"]=true}
local function normalize(s)
 s=(s or ""):lower():gsub("à","a"):gsub("è","e"):gsub("é","e")
 s=s:gsub("ì","i"):gsub("ò","o"):gsub("ù","u")
 s=s:gsub("[%p%c]"," "):gsub("%s+"," ")
 s=s:match("^%s*(.-)%s*$") or ""
 return s:gsub("(%f[%a][mf])%s+(%d+)","%1%2")
end
local function has(w,d)
 for _,v in ipairs(w) do if d[v] then return true end end
 return false
end
local function near(w,d1,d2,n)
 for i,v in ipairs(w) do if d1[v] then
  for j=math.max(1,i-n),math.min(#w,i+n) do
   if j~=i and d2[w[j]] then return true end
  end
 end end
 return false
end
local function code(w)
 for _,v in ipairs(w) do if v:match("^[mf]%d%d?%d?%d?$") then return true end end
 return false
end
local function category(raw)
 if raw:match("^%s*!") then return "ignore","BOT" end
 local s=normalize(raw)
 if s=="" then return "ignore","EMPTY" end
 if greetings[s] or s:match("^ciao da [%w_]+$") then return "ignore","GREETING" end
 local w=words(s)
 local numeric=true
 for _,v in ipairs(w) do if not v:match("^%d+$") then numeric=false;break end end
 if numeric then return "ad","NUMERIC" end
 if #w==1 and (single[w[1]] or code(w)) then return "ad","SINGLE" end
 if #w<2 then return "short","SHORT" end
 if s=="mxm reale" or near(w,{mxm=true},encounter,3) then return "ad","MXM" end
 if near(w,people,contact,7) then return "ad","CONTACT" end
 if near(w,people,chatting,5) then return "ad","CHAT" end
 if near(w,people,places,6) then return "ad","GEO" end
 if near(w,people,encounter,7) then return "ad","MEET" end
 if near(w,people,media,6) then return "ad","MEDIA" end
 if near(w,search,people,5) then return "ad","SEARCH" end
 if code(w) and (w[1]=="io" or has(w,search) or
  has(w,contact) or has(w,people)) then return "ad","MCODE" end
 if w[1]=="io" and places[w[2]] then return "ad","INTRO_GEO" end
 if w[1]=="chi" and has(w,places) then return "ad","WHO_GEO" end
 if has(w,{ospito=true,ospitare=true}) and has(w,places) then return "ad","HOST" end
 if has(w,people) and (s:find("qualsiasi eta",1,true) or
  s:find("qualunque eta",1,true)) then return "ad","AGE" end
 if #w<=2 then return "short","SHORT" end
 if not s:find("[a-z]") then return "ignore","SYMBOLS" end
 if raw:match("^%s*https?://") and #w<=4 then return "ignore","URL" end
 return "normal","NORMAL"
end
local function similar(a,b)
 local wa,wb=words(a),words(b)
 if #wa==0 or #wb==0 then return 0 end
 local counts,shared={},0
 for _,v in ipairs(wa) do counts[v]=(counts[v] or 0)+1 end
 for _,v in ipairs(wb) do if (counts[v] or 0)>0 then
  counts[v]=counts[v]-1;shared=shared+1 end end
 return 200*shared/(#wa+#wb)
end
local function config(server)
 local c=configs[server]
 if not c then c={on=true,a=true,b=true,mode="monitor",channels={},
  similarity=75,minutes=15,credit=2,minwords=3,epoch=1}
  configs[server]=c end
 return c
end
local function valid(s)
 if type(s)~="string" or #s<1 or #s>256 or s:find("[%s%c,;|]") then
  return "" end
 return s:lower()
end
local function partition(key)
 local n=0 for i=1,#key do n=(n*33+key:byte(i))%SHARDS end
 return n
end
local function shardKey(n) return string.format("v4.s%02d",n) end
local function eachline(s)
 local out={} for line in (s or ""):gmatch("[^\n]+") do out[#out+1]=line end
 return out
end
local function savePart(n)
 local rows={}
 for key,r in pairs(history) do
  if partition(key)==n and r.level>0 then
   rows[#rows+1]=key.."\t"..r.level.."\t"..r.at.."\t"..r.epoch
  end
 end
 table.sort(rows)
 local raw=table.concat(rows,"\n")
 if #raw>8192 then return false,"shard pieno (8192 byte)" end
 if raw=="" then return pcall(tiarca.storage.delete,shardKey(n)) end
 return pcall(tiarca.storage.set,shardKey(n),raw)
end
local function saveConfig()
 local rows={}
 for server,c in pairs(configs) do
  local channels={} for ch in pairs(c.channels) do channels[#channels+1]=ch end
  table.sort(channels)
  rows[#rows+1]=table.concat({server,c.on and 1 or 0,c.mode,
   c.a and 1 or 0,c.b and 1 or 0,c.similarity,c.minutes,
   c.credit,c.minwords,c.epoch,table.concat(channels,",")},"\t")
 end
 table.sort(rows)
 local raw=table.concat(rows,"\n")
 if #raw>8192 or raw=="" then return false end
 return pcall(tiarca.storage.set,"v4.cfg",raw)
end
local function saveAllow()
 local rows={} for key,nick in pairs(allow) do rows[#rows+1]=key.."\t"..nick end
 table.sort(rows)
 local raw=table.concat(rows,"\n")
 if #raw>8192 then return false end
 if raw=="" then return pcall(tiarca.storage.delete,"v4.allow") end
 return pcall(tiarca.storage.set,"v4.allow",raw)
end
local function load()
 if loaded then return not failed end
 local ok,cfg=pcall(tiarca.storage.get,"v4.cfg")
 if not ok then failed=true;return false end
 for _,line in ipairs(eachline(cfg)) do
  local f={} for v in (line.."\t"):gmatch("(.-)\t") do f[#f+1]=v end
  if #f~=11 or (f[3]~="monitor" and f[3]~="live") then
   failed=true;return false end
  local c=config(f[1])
  c.on=f[2]=="1";c.mode=f[3];c.a=f[4]=="1";c.b=f[5]=="1"
  c.similarity=tonumber(f[6]);c.minutes=tonumber(f[7])
  c.credit=tonumber(f[8]);c.minwords=tonumber(f[9])
  c.epoch=tonumber(f[10])
  if not c.similarity or c.similarity<50 or c.similarity>100 or
   not c.minutes or c.minutes<1 or c.minutes>120 or
   not c.credit or c.credit<0 or c.credit>3 or
   not c.minwords or c.minwords<3 or c.minwords>12 or
   not c.epoch then failed=true;return false end
  for ch in f[11]:gmatch("[^,]+") do c.channels[ch]=true end
 end
 local good,raw=pcall(tiarca.storage.get,"v4.allow")
 if not good then failed=true;return false end
 for _,line in ipairs(eachline(raw)) do
  local key,nick=line:match("^([^\t]+)\t([^\t]+)$")
  if not key then failed=true;return false end
  allow[key]=nick
 end
 for i=0,SHARDS-1 do
  local yes,part=pcall(tiarca.storage.get,shardKey(i))
  if not yes then failed=true;return false end
  for _,line in ipairs(eachline(part)) do
   local key,lev,at,epoch=line:match("^([^\t]+)\t(%d+)\t(%d+)\t(%d+)$")
   if not key or history[key] or partition(key)~=i then
    failed=true;return false end
   history[key]={level=tonumber(lev),at=tonumber(at),epoch=tonumber(epoch)}
   if history[key].level<1 or history[key].level>5 then
    failed=true;return false end
  end
 end
 loaded=true;return true
end
local genericIdent=dict("guest webirc android user ident unknown anonymous irccloud mibbit")
local function identify(e,now,window)
 local ok,roster=pcall(tiarca.get_users,e.channel)
 if not ok or type(roster)~="table" then return nil,"ROSTER" end
 -- TIARCA includes the event sender in a truncated snapshot whenever observed.
 for _,u in ipairs(roster) do
  if u.nick and u.nick:lower()==e.nick:lower() then
   local prefix=u.prefix or ""
   if prefix~="" and prefix~="+" then return nil,"PROTECTED" end
   local account=valid(u.account)
   if account=="*" or account=="0" then account="" end
   if account~="" then
    -- Protect the authenticated account despite a nickname change.
    if account=="micaela" or allow[e.server.."|"..account] then
     return nil,"WHITELIST" end
    return e.server.."/a:"..account
   end
   local host=valid(u.host)
   if host=="" then return nil,"NO_HOST" end
   local ident=valid(u.ident)
   if #ident>=5 and not genericIdent[ident] then
    return e.server.."/h:"..host.."/i:"..ident
   end
   local key=e.server.."/h:"..host
   local seen=hostSeen[key]
   if seen and seen.nick~=e.nick:lower() and now-seen.at<=window then
    seen.ambiguous=true end
   if seen and seen.ambiguous and now-seen.at<=window then
    seen.at=now;return nil,"HOST_SHARED" end
   hostSeen[key]={nick=e.nick:lower(),at=now}
   return key
  end
 end
 return nil,roster.truncated and "ROSTER_TRUNCATED" or "UNKNOWN"
end
local function getWatch(id,channel,now,window)
 local key=id.."\0"..channel:lower()
 local h=watch[key]
 if not h or now<h.last or now-h.last>window then
  h={score=0,ads=0,adAt=0,ref=nil,rep=0,refAt=0,
   credited=false,last=now}
  watch[key]=h
 end
 return h
end
local function action(level)
 if level==1 then return "avviso" end
 if level==2 or level==3 then return "tmute" end
 if level==4 then return "kick" end
 return "ban"
end
local function sanction(e,level,engine)
 local problem=engine=="A" and "messaggi ripetuti" or "annunci ripetuti"
 if level==1 then
  tiarca.pm(e.nick,"Ciao "..e.nick..", su "..e.channel.." sono stati rilevati "..
   problem..". Ti invitiamo a partecipare alla conversazione evitando altre ripetizioni.")
 elseif level==2 then
  tiarca.pm(e.nick,"Su "..e.channel.." continui a inviare "..problem..
   ". E' stato richiesto un mute di 2 minuti.")
  tiarca.tmute(e.channel,e.nick,"2m")
 elseif level==3 then
  tiarca.pm(e.nick,"Su "..e.channel.." le ripetizioni continuano. E' stato richiesto un mute di 15 minuti.")
  tiarca.tmute(e.channel,e.nick,"15m")
 elseif level==4 then
  tiarca.pm(e.nick,"Su "..e.channel.." continuano "..problem..
   ". Sono stati richiesti un mute di 3 ore e l'espulsione. Un'altra infrazione comportera' un ban di 24 ore.")
  tiarca.tmute(e.channel,e.nick,"3h")
  tiarca.kick(e.channel,e.nick,"Messaggi ripetuti")
 else
  tiarca.pm(e.nick,"Su "..e.channel.." hai continuato a inviare "..problem..
   ". Sono stati richiesti un ban di 24 ore e l'espulsione. Contatta un operatore in caso di errore.")
  tiarca.tban(e.channel,e.nick,"24h")
  tiarca.kick(e.channel,e.nick,"Messaggi ripetuti")
 end
end
local function detected(e,id,engine,rule,pct,h,now,c)
 local old=history[id]
 local level=0
 if old and old.epoch==c.epoch and now>=old.at and
  now-old.at<TTL then level=old.level end
 local nextLevel=math.min(5,level+1)
 local label=c.mode=="live" and (level<5 and action(nextLevel) or "escalation") or "monitor"
 tiarca.log(string.format("AutoModV4 %s/%s %s %.1f%% infrazione %d (%s) motore %s",
  e.server:sub(1,8),e.channel,e.nick,pct,nextLevel,label,engine))
 tiarca.log(string.format("WatchdogV4 %s %s/%s %s %s score=%d",
  engine,e.server:sub(1,8),e.channel,e.nick,rule,h.score))
 if c.mode~="live" then return end
 -- At level five no new ban/kick, but each new detection renews the 24h TTL.
 history[id]={level=nextLevel,at=now,epoch=c.epoch}
 local ok,err=savePart(partition(id))
 if not ok then
  history[id]=old;failed=true
  tiarca.log("AutoMod V4: archivio non scrivibile; moderazione sospesa: "..tostring(err))
  return
 end
 if level<5 then sanction(e,nextLevel,engine) end
end
tiarca.on("message",function(e)
 if e.message_type~="normal" or not e.channel or
  not e.channel:match("^[#&+!]") or not e.nick or not load() then return end
 local c=config(e.server)
 if not c.on or not c.channels[e.channel:lower()] or (not c.a and not c.b) then return end
 local now=tonumber(e.timestamp) or 0
 if now<=0 then return end
 local window=c.minutes*60
 local id,why=identify(e,now,window)
 if not id then
  if why=="HOST_SHARED" or why=="ROSTER" or why=="ROSTER_TRUNCATED" then
   tiarca.log("AutoMod V4: identita non affidabile "..why.."; controllo sospeso")
  end
  return
 end
 local kind,rule=category(e.text or "")
 if kind=="ignore" or kind=="short" then return end
 local h=getWatch(id,e.channel,now,window)
 h.last=now
 if h.adAt>0 and now-h.adAt>window then h.ads=0 end
 if h.refAt>0 and now-h.refAt>window then h.rep=0;h.ref=nil end
 if kind=="ad" and c.b then
  h.score=h.score+1;h.ads=h.ads+1;h.adAt=now
  if h.ads>=2 and h.score>=2 then
   detected(e,id,"B",rule,100,h,now,c) end
 elseif kind=="normal" and c.a then
  local s=normalize(e.text or "")
  if #words(s)<c.minwords then
   h.score=math.max(-c.credit,h.score-1);return
  end
  local pct=h.ref and similar(h.ref,s) or 0
  if h.ref and pct>=c.similarity then
   if h.rep==1 then h.score=h.score+(h.credited and 1 or 0)+2
   else h.score=h.score+1 end
   h.rep=h.rep+1;h.ref=s;h.refAt=now;h.credited=false
   if h.rep>=3 and h.score>=3 then
    detected(e,id,"A","SIMILAR",pct,h,now,c) end
  else
   h.ref=s;h.refAt=now;h.rep=1
   h.credited=h.score>-c.credit
   h.score=math.max(-c.credit,h.score-1)
  end
 elseif kind=="normal" then
  h.score=math.max(-c.credit,h.score-1)
 end
end)
tiarca.on("nick",function(e)
 if not e.nick or not e.text then return end
 for key,s in pairs(hostSeen) do
  if key:sub(1,#e.server+1)==e.server.."/" and
   s.nick==e.nick:lower() then s.nick=e.text:lower() end
 end
end)
tiarca.on("timer",function(e)
 if not load() then return end
 local now=tonumber(e.timestamp) or 0
 for key,h in pairs(watch) do
  local server=key:match("^(.-)/")
  local c=configs[server]
  if now-h.last>60*(c and c.minutes or 15) then watch[key]=nil end
 end
 for key,s in pairs(hostSeen) do
  if now-s.at>900 then hostSeen[key]=nil end
 end
 local touched,parts={},0
 for id,r in pairs(history) do
  if id:sub(1,#e.server+1)==e.server.."/" and
   (now-r.at>=TTL or (configs[e.server] and r.epoch~=configs[e.server].epoch)) then
   local n=partition(id)
   if touched[n] or parts<3 then
    touched[n]=true;history[id]=nil;parts=parts+1
   end
  end
 end
 for n in pairs(touched) do
  if not savePart(n) then
   failed=true;tiarca.log("AutoMod V4: impossibile ripulire archivio")
  end
 end
end)
local function show(s) tiarca.show("AutoMod V4",s) end
local function help(topic)
 if topic=="" or topic=="generale" then
  show("/lua mod stato | on | off | modo monitor|live | canali add|drop #canale | canali list | close | reset")
  show("Guide: /lua mod help watchdog | help whitelist | help azioni")
 elseif topic=="watchdog" or topic=="parametri" then
  show("/lua mod motore ripetizioni|annunci on|off; parametro somiglianza 50..100 | finestra 1..120 | credito 0..3 | minparole 3..12")
  show("Default 75%, 15 minuti, credito -2. Trigger: 3 ripetizioni o 2 annunci; i messaggi conversazionali sottraggono 1.")
 elseif topic=="whitelist" then
  show("/lua mod whitelist add NICK | drop NICK | list. Serve account NickServ autenticato.")
 elseif topic=="azioni" then
  show("Escalation 24h: avviso PVT; mute 2m; mute 15m; kick+mute 3h; kick+ban 24h.")
 else show("Argomenti: watchdog, parametri, whitelist, azioni") end
end
tiarca.on("command",function(e)
 local raw=e.text or ""
 local op,rest=raw:match("^%s*mod%s+(%S+)%s*(.-)%s*$")
 if not op then if raw:match("^%s*mod%s*$") then help("") end;return end
 if op=="help" then help(rest);return end
 if op=="close" and rest=="" then tiarca.close_pms();return end
 if not load() then show("Archivio non disponibile: moderazione sospesa");return end
 local c=config(e.server)
 if op=="stato" then
  local n=0 for _ in pairs(c.channels) do n=n+1 end
  show(string.format("%s; modo %s; ripetizioni %s; annunci %s; canali %d; somiglianza %d%%; finestra %dm; credito -%d; minparole %d",
   c.on and "ON" or "OFF",c.mode,c.a and "ON" or "OFF",
   c.b and "ON" or "OFF",n,c.similarity,c.minutes,c.credit,c.minwords))
  return
 end
 local old={on=c.on,mode=c.mode,a=c.a,b=c.b,channels={},
  similarity=c.similarity,minutes=c.minutes,credit=c.credit,
  minwords=c.minwords,epoch=c.epoch}
 for ch in pairs(c.channels) do old.channels[ch]=true end
 local function finish()
  if saveConfig() then show("Configurazione aggiornata") else
   configs[e.server]=old
   show("Salvataggio fallito: modifica annullata") end
 end
 if (op=="on" or op=="off") and rest=="" then
  c.on=op=="on";finish()
 elseif op=="modo" and (rest=="monitor" or rest=="live") then
  c.mode=rest;finish()
 elseif op=="motore" then
  local group,value=rest:match("^(%S+)%s+(on|off)$")
  if not group then
   local p={} for word in rest:gmatch("%S+") do p[#p+1]=word end
   group,value=p[1],p[2]
  end
  if value~="on" and value~="off" then
   show("Uso: /lua mod motore ripetizioni|annunci on|off");return end
  if group=="a" or group=="ripetizioni" then c.a=value=="on"
  elseif group=="b" or group=="annunci" then c.b=value=="on"
  else show("Motori: ripetizioni, annunci");return end
  finish()
 elseif op=="parametro" then
  local name,value=rest:match("^(%S+)%s+(%d+)$")
  local n=tonumber(value)
  if not n then help("parametri");return end
  if name=="somiglianza" or name=="soglia" then
   if n<50 or n>100 then show("Somiglianza: 50..100");return end
   c.similarity=n
  elseif name=="finestra" or name=="minuti" then
   if n<1 or n>120 then show("Finestra: 1..120 minuti");return end
   c.minutes=n
  elseif name=="credito" then
   if n<0 or n>3 then show("Credito: 0..3");return end
   c.credit=n
  elseif name=="minparole" then
   if n<3 or n>12 then show("Minparole: 3..12");return end
   c.minwords=n
  else help("parametri");return end
  finish()
 elseif op=="canali" then
  if rest=="list" then
   local list={} for ch in pairs(c.channels) do list[#list+1]=ch end
   table.sort(list);show(#list>0 and table.concat(list,", ") or "Nessun canale")
   return
  end
  local action,ch=rest:match("^(%S+)%s+(%S+)$")
  if (action~="add" and action~="drop") or not ch or
   not ch:match("^[#&+!][^,%s:%c]+$") or #ch>64 then
   show("Uso: /lua mod canali add|drop #canale");return end
  c.channels[ch:lower()]=action=="add" and true or nil;finish()
 elseif op=="whitelist" then
  if rest=="list" then
   local names={} for key,nick in pairs(allow) do
    names[#names+1]=nick.." ("..key..")" end
   table.sort(names)
   show(#names>0 and table.concat(names,", "):sub(1,480) or "Whitelist vuota")
   return
  end
  local task,nick=rest:match("^(%S+)%s+(%S+)$")
  if (task~="add" and task~="drop") or not nick or #nick>64 then
   show("Uso: /lua mod whitelist add|drop NICK; whitelist list");return
  end
  if task=="drop" then
   local previous={}
   for key,v in pairs(allow) do if v==nick:lower() then
    previous[key]=v;allow[key]=nil end end
   if not next(previous) then show("Nickname non presente");return end
   if not saveAllow() then for key,v in pairs(previous) do allow[key]=v end
    show("Salvataggio fallito")
   else show("Esclusione rimossa") end
   return
  end
  local account
  for ch in pairs(c.channels) do
   local ok,roster=pcall(tiarca.get_users,ch)
   if ok and type(roster)=="table" then for _,u in ipairs(roster) do
    if u.nick and u.nick:lower()==nick:lower() then
     local value=valid(u.account)
     if value~="" and value~="*" and value~="0" then account=value end
    end
   end end
  end
  if not account then show("Account NickServ non verificato");return end
  local key=e.server.."|"..account
  local previous=allow[key];allow[key]=nick:lower()
  if not saveAllow() then allow[key]=previous;show("Salvataggio fallito")
  else show("Whitelist aggiornata: "..nick.." ("..account..")") end
 elseif op=="reset" and rest=="" then
  c.epoch=c.epoch+1
  if saveConfig() then
   for key in pairs(watch) do
    if key:sub(1,#e.server+1)==e.server.."/" then watch[key]=nil end
   end
   show("Cronologia Watchdog ed escalation azzerata")
  else c.epoch=old.epoch;show("Reset non riuscito") end
 else show("Comando sconosciuto: /lua mod help") end
end)
