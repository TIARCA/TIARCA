local api=tiarca
local TTL,SHARDS=86400,20
local DEFAULT_RAPID,MAX_AD_GROUPS=30,6
local ranges={similarity={50,100},minutes={1,120},credit={0,3},minwords={3,12},rapid={0,120}}
local parameters={somiglianza="similarity",soglia="similarity",finestra="minutes",
minuti="minutes",credito="credit",minparole="minwords",raffica="rapid"}
local configs,allow,watch,hostSeen,unresolved,cursor={},{},{},{},{},{}
local loaded,failed=false,false
local pending,seq={},0
api.identity_v2()
local function words(s)
local t={} for v in s:gmatch("%S+") do t[#t+1]=v end return t
end
local function dict(s)
local d={} for v in s:gmatch("%S+") do d[v]=true end return d
end
local people=dict("f m mxm ragazza ragazze ragazzo ragazzi donna donne uomo uomini qualcuna qualcuno nessuna nessuno coetanea coetanee coetaneo coetanei")
local single=dict("mxm ragazza ragazze donna donne")
local chatTarget=dict("f m mxm donna donne uomo uomini")
local search=dict("cerco cerca cercasi cerchiamo chi qualche qualke")
local chatting=dict("chat chatta chattare chatto chiacchiera conoscenza conoscere compagnia amicizia amicizie parlare")
local contact=dict("pvt priv privato privata prvt scrivimi scrivetemi contattami contattatemi")
local encounter=dict("reale incontro incontri ospito ospitare uscire")
local media=dict("cam call videochat whatsapp whatap whatsap telegram tg")
local places=dict("parma caserta brescia bergamo foggia avellino milano roma napoli biella veneto puglia lecce sicilia friuli piemonte marche toscana bari salerno genova torino calabria calabira siciliana verona enna viareggio firenze bologna palermo catania abruzzo molise umbria lazio sardegna lombardia campania")
local greetings={}
for s in ("ciao|salve|sera|buongiorno|buonasera|buonanotte|buona sera|buon pomeriggio|buona serata|"..
"buona notte|ciao tutti|ciao a tutti|salve a tutti|sera a tutti|buonasera a tutti|buongiorno a tutti|buonanotte a tutti|un saluto a tutti|ciao chat"):gmatch("[^|]+") do greetings[s]=true end
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
local wish=dict("buon buona felice sereno serena splendido splendida ottimo ottima dolce bellissimo bellissima")
local periods=dict("giorno giornata mattina mattinata pomeriggio sera serata notte nottata weekend")
local greetPrefix={"ti auguro","vi auguro","auguro a tutti","auguro a tutte","auguro a te","auguro a voi","auguro"}
local greetSuffix={"a te","a voi","a tutti","a tutte","tutti","tutte","un abbraccio","tutto bene"}
local notNick=dict("a te voi tutti tutte io tu mi ti vi un una il la che di da per con si no anche e o bene sesso sexy sex info numero numeri sono chiamo qualsiasi qualunque eta")
local function greetingCore(s)
if greetings[s] then return true end
for _,p in ipairs(greetPrefix) do
if s:sub(1,#p+1)==p.." " then
s=s:sub(#p+2):gsub("^un ",""):gsub("^una ","");break
end
end
for i=1,2 do
for _,p in ipairs(greetSuffix) do
if s:sub(-#p-1)==" "..p then s=s:sub(1,-#p-2);break end
end
end
if greetings[s] then return true end
local a,b=s:match("^(%S+) (.+)$")
return wish[a] and (periods[b] or b=="fine settimana")
end
local function protected(n)
return notNick[n] or people[n] or search[n] or chatting[n] or contact[n]
or encounter[n] or media[n] or places[n] or greetings[n] or wish[n] or periods[n]
end
local function greetingNick(n)
return n and n:match("^[a-z][%w]*$") and not protected(n) and not code({n})
end
local function isGreeting(raw)
local s=normalize(raw:gsub("\240\159[\128-\191][\128-\191]"," ")
:gsub("\226[\152\153\157][\128-\191]"," ")
:gsub("\239\184\143",""):gsub("\226\128\141","")
:gsub("([%w])[_%-]+%f[%w]","%1"))
if greetingCore(s) then return true end
local n,tail=s:match("^(%S+) (.+)$")
if greetingNick(n) and greetingCore(tail) then return true end
local head,last=s:match("^(.+) (%S+)$")
if greetingNick(last) and greetingCore(head) then return true end
local first,middle,rest=s:match("^(%S+) (%S+) (.+)$")
if greetings[first] and greetingNick(middle) and greetingCore(first.." "..rest) then
return true end
return false
end
local function category(raw)
if raw:match("^%s*!") then return "ignore","BOT" end
local s=normalize(raw)
if s=="" then return "ignore","EMPTY" end
if greetings[s] then return "ignore","GREETING" end
local w=words(s)
if not s:find("[^%d ]") then
if raw:match("^%s*0%d%d?%s*[/%-]%s*0%d%d?%s*%??%s*$") then return "ad","NUMERIC" end
return "short","NUMERIC"
end
if #w==1 and (single[w[1]] or code(w) or
((w[1]=="f" or w[1]=="m") and raw:find("?",1,true))) then return "ad","SINGLE" end
if #w<2 then return "short","SHORT" end
if s=="mxm reale" or near(w,{mxm=true},encounter,3) then return "ad","MXM" end
if near(w,people,contact,7) then return "ad","CONTACT" end
if near(w,people,places,6) then return "ad","GEO" end
if near(w,people,encounter,7) then return "ad","MEET" end
if near(w,people,media,6) then return "ad","MEDIA" end
if w[1]=="chi" and has(w,places) then return "ad","WHO_GEO" end
if has(w,{ospito=true,ospitare=true}) and has(w,places) then return "ad","HOST" end
if has(w,people) and (s:find("qualsiasi eta",1,true) or
s:find("qualunque eta",1,true)) then return "ad","AGE" end
if near(w,people,chatting,5) then
if #w<=8 and (near(w,chatTarget,chatting,5) or
has(w,{conoscenza=true,conoscere=true})) and
not (#w==2 and chatting[w[1]] and not raw:find("?",1,true)) then
return "ad","CHAT" end
return "normal","NORMAL" end
if near(w,search,people,5) then return "ad","SEARCH" end
if code(w) and (w[1]=="io" or has(w,search) or
has(w,contact) or has(w,people)) then return "ad","MCODE" end
if isGreeting(raw) or s:match("^ciao da [%w_]+$") then return "ignore","GREETING" end
if #w<=2 then return "short","SHORT" end
if not s:find("[a-z]") then return "ignore","SYMBOLS" end
if raw:match("^%s*https?://") and #w<=4 then return "ignore","URL" end
return "normal","NORMAL"
end
local function contextual(e,raw,kind,rule)
if not e.mentions or not next(e.mentions) or kind=="ignore" then return kind,rule end
local w=words(normalize(raw))
local ad=has(w,search) or has(w,contact) or has(w,media) or has(w,encounter)
or has(w,{io=true,per=true})
local prev=""
local clean=raw:gsub("%S+",function(t)
local n=t:gsub("[,:;.!?]+$","")
local v=words(normalize(n))
local keep=false
for _,x in ipairs(v) do if protected(x) or x:match("^%d+$") then keep=true end end
if code(v) and (ad or prev=="sono" or prev=="chiamo" or not code({n:lower()})) then keep=true end
prev=v[#v] or ""
if e.mentions[n] and not keep then return " " end
end)
if clean==raw then return kind,rule end
local k,r=category(clean)
if kind=="ad" or k=="ignore" then return k,r end
return kind,rule
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
local function adThreshold(rule,tokens)
if rule=="WHO_GEO" or (rule=="SINGLE" and not code(tokens)) or
   (rule=="CHAT" and not has(tokens,chatTarget)) then return 3 end
return 2
end

local function config(server)
local c=configs[server]
if not c then c={on=true,a=true,b=true,mode="monitor",channels={},
similarity=75,minutes=15,credit=2,minwords=3,epoch=1,rapid=DEFAULT_RAPID}
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
local function savePart(n,history)
local rows={}
for key,r in pairs(history) do
rows[#rows+1]=key.."\t"..r.level.."\t"..r.at.."\t"..r.epoch
end
table.sort(rows)
local raw=table.concat(rows,"\n")
if #raw>8192 then return false,"shard pieno (8192 byte)" end
if raw=="" then return pcall(api.storage.delete,shardKey(n)) end
return pcall(api.storage.set,shardKey(n),raw)
end
local function saveConfig()
local rows={}
for server,c in pairs(configs) do
local channels={} for ch in pairs(c.channels) do channels[#channels+1]=ch end
table.sort(channels)
local row=table.concat({server,c.on and 1 or 0,c.mode,
c.a and 1 or 0,c.b and 1 or 0,c.similarity,c.minutes,
c.credit,c.minwords,c.epoch,table.concat(channels,",")},"\t")
rows[#rows+1]=c.rapid==DEFAULT_RAPID and row or row.."\t"..c.rapid
end
table.sort(rows)
local raw=table.concat(rows,"\n")
if #raw>8192 or raw=="" then return false end
return pcall(api.storage.set,"v4.cfg",raw)
end
local function saveAllow()
local rows={} for key,nick in pairs(allow) do rows[#rows+1]=key.."\t"..nick end
table.sort(rows)
local raw=table.concat(rows,"\n")
if #raw>8192 then return false end
if raw=="" then return pcall(api.storage.delete,"v4.allow") end
return pcall(api.storage.set,"v4.allow",raw)
end
local function load()
if loaded then return true end
if failed then return false end
configs,allow={},{}
local ok,cfg=pcall(api.storage.get,"v4.cfg")
if not ok then failed=true;return false end
for _,line in ipairs(eachline(cfg)) do
local f={} for v in (line.."\t"):gmatch("(.-)\t") do f[#f+1]=v end
if (#f~=11 and #f~=12) or (f[3]~="monitor" and f[3]~="live") then
failed=true;return false end
local c=config(f[1])
c.on=f[2]=="1";c.mode=f[3];c.a=f[4]=="1";c.b=f[5]=="1"
c.similarity=tonumber(f[6]);c.minutes=tonumber(f[7])
c.credit=tonumber(f[8]);c.minwords=tonumber(f[9])
c.epoch=tonumber(f[10])
if f[12] then c.rapid=tonumber(f[12]) end
for field,limits in pairs(ranges) do
local n=c[field]
if not n or n<limits[1] or n>limits[2] then failed=true;return false end
end
if not c.epoch then failed=true;return false end
for ch in f[11]:gmatch("[^,]+") do c.channels[ch]=true end
end
local good,raw=pcall(api.storage.get,"v4.allow")
if not good then failed=true;return false end
for _,line in ipairs(eachline(raw)) do
local key,nick=line:match("^([^\t]+)\t([^\t]+)$")
if not key then failed=true;return false end
allow[key]=nick
end
loaded=true;return true
end
local function readPart(i)
local history={}
local yes,part=pcall(api.storage.get,shardKey(i))
if yes then
for _,line in ipairs(eachline(part)) do
local key,lev,at,epoch=line:match("^([^\t]+)\t(%d+)\t(%d+)\t(%d+)$")
lev=tonumber(lev)
if not key or history[key] or partition(key)~=i or lev<1 or lev>5 then
yes=false;break end
history[key]={level=lev,at=tonumber(at),epoch=tonumber(epoch)}
end
end
if yes then return history end
api.log("V4: archivio non leggibile; moderazione sospesa per blocco "..i)
end
local genericIdent=dict("guest webirc android user ident unknown anonymous irccloud mibbit")
local function identify(e,now,window)
local u=e.sender
if not u or u.nick~=e.nick then return nil,"UNKNOWN" end
local prefix=u.prefix or ""
if prefix~="" and prefix~="+" then return nil,"PROTECTED" end
local account=valid(u.account)
if account=="*" or account=="0" then account="" end
if account~="" then
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
local function getWatch(id,channel,now,window)
local key=id.."\0"..channel:lower()
local h=watch[key]
if not h or now<h.last or now-h.last>window then
h={score=0,ref=nil,rep=0,refAt=0,
credited=false,last=now}
watch[key]=h
end
return h
end
local function hasVoice(e)
return e.sender and e.sender.prefix=="+" or false
end
local function needsBan(e)
local account=valid(e.sender and e.sender.account)
return hasVoice(e) or (account~="" and account~="*" and account~="0")
end
local function action(e,level)
if level==1 then return "avviso" end
if level==5 or (level==4 and needsBan(e)) then return "tbankick" end
return "tmute"
end
local function sanction(e,level,engine)
local problem=engine=="A" and "messaggi ripetuti" or "annunci ripetuti"
local ch,nick=e.channel,e.nick
if level==1 then
 api.pm(nick,"Ciao "..nick..", su "..ch.." sono stati rilevati "..
  problem..". Evita altre ripetizioni.")
 return
end
local ban=action(e,level)=="tbankick"
local duration=({"","2m","15m","3h","24h"})[level]
local time=({"","2 minuti","15 minuti","3 ore","24 ore"})[level]
local msg="Su "..ch.." continuano "..problem..". E' stato richiesto un "..
 (ban and "ban" or "mute").." di "..time.."."
if ban then msg=msg.." E' stata richiesta anche l'espulsione."
elseif hasVoice(e) then msg=msg.." Verra' rimosso anche il voice." end
if level==4 then msg=msg.." Alla prossima infrazione: ban di 24 ore."
elseif level==5 then msg=msg.." Per errori contatta un operatore." end
api.pm(nick,msg)
if ban then
 api.tban(ch,nick,duration)
 api.kick(ch,nick,"Messaggi ripetuti")
else
 api.tmute(ch,nick,duration)
 if hasVoice(e) then api.mode(ch,nick,"-v") end
end
end
local function detected(e,id,engine,rule,pct,h,now,c,ad,threshold)
local n=partition(id)
local history=readPart(n)
if not history then return end
local old=history[id]
local level=0
if old and old.epoch==c.epoch and now>=old.at and
now-old.at<TTL then level=old.level end
local nextLevel=math.min(5,level+1)
if c.mode=="live" then
history[id]={level=nextLevel,at=now,epoch=c.epoch}
local ok,err=savePart(n,history)
if not ok then
api.log("V4: archivio non scrivibile; moderazione sospesa: "..tostring(err));return end
end
local label=c.mode=="live" and (level<5 and action(e,nextLevel) or "escalation") or "monitor"
api.log(string.format("AutoModV4 %s/%s %s %.1f%% infrazione %d (%s) motore %s",
e.server:sub(1,8),e.channel,e.nick,pct,nextLevel,label,engine)..
string.format(" rule %s score %d threshold %d count %d refs %s",rule,h.score,
threshold or c.similarity,ad and ad.count or h.rep,
ad and ((threshold==3 and ((ad.previous or "-")..",") or "")..(ad.source or "-")) or
((h.r2 or "-")..","..(h.r1 or "-"))))
api.log(string.format("WatchdogV4 %s %s/%s %s %s score=%d",
engine,e.server:sub(1,8),e.channel,e.nick,rule,h.score))
if c.mode=="live" and level<5 then sanction(e,nextLevel,engine) end
end
api.on("message",function(e)
if e.message_type~="normal" or not e.channel or
not e.channel:match("^[#&+!]") or not e.nick or not load() then return end
local c=config(e.server)
if not c.on or not c.channels[e.channel:lower()] or (not c.a and not c.b) then return end
local now=tonumber(e.timestamp) or 0
if now<=0 then return end
local window=c.minutes*60
local raw=e.text or ""
local kind,rule=category(raw)
kind,rule=contextual(e,raw,kind,rule)
if kind=="ignore" or kind=="short" then return end
local id,why=identify(e,now,window)
if not id then
if why~="WHITELIST" and why~="PROTECTED" then
local k=e.server.."/"..e.channel:lower().."/"..e.nick:lower()
if not unresolved[k] or now-unresolved[k]>window then
api.log("V4: identita incerta per "..e.nick..
" in "..e.channel.." ("..tostring(why).."); controllo sospeso")
unresolved[k]=now
end
end
return
end
local h=getWatch(id,e.channel,now,window)
h.last=now
if h.refAt>0 and now-h.refAt>window then h.rep=0;h.ref=nil;h.r1=nil;h.r2=nil end
if kind=="ad" and c.b then
local text=normalize(raw):gsub("%f[%a]prvt%f[%A]","pvt")
local threshold=adThreshold(rule,words(text))
local ads=h.adGroups or {}
h.adGroups=ads
local ad=ads[text]
if ad and e.source_id and e.source_id==ad.source then return end
if not ad then
if (h.groups or 0)>=MAX_AD_GROUPS then
local oldest
for key,r in pairs(ads) do
if not oldest or r.at<ads[oldest].at then oldest=key end end
ads[oldest]=nil
else h.groups=(h.groups or 0)+1 end
end
h.score=h.score+1
if not ad or now-ad.first>window then
ads[text]={first=now,at=now,count=1,source=e.source_id}
return
end
ad.count=ad.count+1
local rapid=c.rapid>0 and now-ad.at<=c.rapid
if rapid or (ad.count>=threshold and h.score>=threshold) then
detected(e,id,"B",rapid and "RAPID_"..rule or rule,100,h,now,c,ad,rapid and 2 or threshold)
end
ad.previous,ad.source=ad.source,e.source_id
ad.at=now
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
h.r2=h.r1;h.r1=e.source_id
else
h.ref=s;h.refAt=now;h.rep=1;h.r1=e.source_id;h.r2=nil
h.credited=h.score>-c.credit
h.score=math.max(-c.credit,h.score-1)
end
elseif kind=="normal" then
h.score=math.max(-c.credit,h.score-1)
end
end)
api.on("nick",function(e)
if not e.nick or not e.text then return end
for key,s in pairs(hostSeen) do
if key:sub(1,#e.server+1)==e.server.."/" and
s.nick==e.nick:lower() then s.nick=e.text:lower() end
end
end)
local function lifetime(server)
local c=configs[server]
return 60*(c and c.minutes or 15)
end
api.on("timer",function(e)
failed=false
if not load() then api.log("V4: config non leggibile");return end
local now=tonumber(e.timestamp) or 0
for key,h in pairs(watch) do
local server=key:match("^(.-)/")
if now-h.last>lifetime(server) then watch[key]=nil end
end
for key,s in pairs(hostSeen) do
local server=key:match("^(.-)/")
if now-s.at>lifetime(server) then hostSeen[key]=nil end
end
for key,at in pairs(unresolved) do
local server=key:match("^(.-)/")
if now-at>lifetime(server) then unresolved[key]=nil end
end
local p=pending[e.server]
if p and now-(tonumber(p.at) or 0)>60 then pending[e.server]=nil end
local n=cursor[e.server] or 0
cursor[e.server]=(n+1)%SHARDS
local history=readPart(n)
if not history then return end
local touched=false
for id,r in pairs(history) do
if id:sub(1,#e.server+1)==e.server.."/" and
(now-r.at>=TTL or (configs[e.server] and r.epoch~=configs[e.server].epoch)) then
history[id]=nil;touched=true
end
end
if touched and not savePart(n,history) then
api.log("V4: pulizia fallita: "..n)
end
end)
local function show(s) api.show("AutoMod V4",s) end
local function help(topic)
if topic=="" or topic=="generale" then
show("/lua mod stato | on | off | modo monitor|live | canali add|drop #canale | canali list | close | reset")
show("Guide: watchdog, whitelist, azioni")
elseif topic=="watchdog" or topic=="parametri" then
show("motore ripetizioni|annunci on|off; parametro somiglianza|finestra|credito|minparole|raffica N")
show("Annunci: 2 espliciti o 3 generici. Raffica 30s; 0 disattiva.")
elseif topic=="whitelist" then
show("/lua mod whitelist add NICK [#canale] | drop NICK | list")
elseif topic=="azioni" then
show("Escalation 24h: avviso; mute 2m e 15m; 3h ban+kick per account o +v, altrimenti mute; ban+kick 24h.")
else show("Argomenti: watchdog, parametri, whitelist, azioni") end
end
api.on("command",function(e)
local raw=e.text or ""
local op,rest=raw:match("^%s*mod%s+(%S+)%s*(.-)%s*$")
if not op then if raw:match("^%s*mod%s*$") then help("") end;return end
if op=="help" then help(rest);return end
if op=="close" and rest=="" then api.close_pms();return end
failed=false
if not load() then show("Archivio indisponibile; moderazione sospesa");return end
local c=config(e.server)
if op=="stato" then
local n=0 for _ in pairs(c.channels) do n=n+1 end
show(string.format("%s; modo %s; ripetizioni %s; annunci %s; canali %d; somiglianza %d%%; finestra %dm; credito -%d; minparole %d; raffica %ds",
c.on and "ON" or "OFF",c.mode,c.a and "ON" or "OFF",
c.b and "ON" or "OFF",n,c.similarity,c.minutes,c.credit,c.minwords,c.rapid))
return
end
local old={}
for field,value in pairs(c) do old[field]=value end
old.channels={}
for ch in pairs(c.channels) do old.channels[ch]=true end
local function finish()
if saveConfig() then show("Configurazione aggiornata") else
configs[e.server]=old
show("Modifica annullata: salvataggio fallito") end
end
if (op=="on" or op=="off") and rest=="" then
c.on=op=="on";finish()
elseif op=="modo" and (rest=="monitor" or rest=="live") then
c.mode=rest;finish()
elseif op=="motore" then
local group,value=rest:match("^(%S+)%s+(%S+)$")
if value~="on" and value~="off" then
show("Uso: /lua mod motore ripetizioni|annunci on|off");return end
if group=="a" or group=="ripetizioni" then c.a=value=="on"
elseif group=="b" or group=="annunci" then c.b=value=="on"
else show("Motori: ripetizioni, annunci");return end
finish()
elseif op=="parametro" then
local name,value=rest:match("^(%S+)%s+(%d+)$")
local n=tonumber(value)
local field=parameters[name]
if not n or not field then help("parametri");return end
local limits=ranges[field]
if n<limits[1] or n>limits[2] then
show(name..": "..limits[1]..".."..limits[2]);return end
c[field]=n
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
local task,nick,ch=rest:match("^(%S+)%s+(%S+)%s*(.-)$")
if (task~="add" and task~="drop") or not nick or #nick>64 or
(task=="drop" and ch~="") then
show("Uso: /lua mod whitelist add NICK [#canale] | drop NICK | list");return
end
pending[e.server]=nil
if task=="drop" then
local previous={}
for key,v in pairs(allow) do if v==nick:lower() then
previous[key]=v;allow[key]=nil end end
if not next(previous) then show("Nickname non presente");return end
if not saveAllow() then for key,v in pairs(previous) do allow[key]=v end
show("Errore salvataggio")
else show("Esclusione rimossa") end
return
end
local source=e.channel or ""
if ch=="" then
if source~="" then ch=source
else
for name in pairs(c.channels) do
if ch~="" then show("Indicare #canale dopo il nick");return end
ch=name
end
end
end
if not c.channels[ch:lower()] or (source~="" and source:lower()~=ch:lower()) then
show("Indicare un canale monitorato");return end
seq=seq+1
local token="v4w"..seq
local ok=pcall(api.get_user,ch,nick,token)
if not ok then show("Ricerca identita indisponibile");return end
pending[e.server]={token=token,nick=nick,channel=ch:lower(),at=e.timestamp}
show("Ricerca account in corso")
elseif op=="reset" and rest=="" then
pending[e.server]=nil
c.epoch=c.epoch+1
if saveConfig() then
for key in pairs(watch) do
if key:sub(1,#e.server+1)==e.server.."/" then watch[key]=nil end
end
show("Cronologia ed escalation azzerate")
else c.epoch=old.epoch;show("Reset non riuscito") end
else show("Comando ignoto: /lua mod help") end
end)

api.on("identity",function(e)
local p=pending[e.server]
if not p or e.token~=p.token or e.query~="user" then return end
pending[e.server]=nil
if not load() or not config(e.server).channels[p.channel] then return end
local u=e.user
local account=u and u.account_known and valid(u.account) or ""
if e.status~="ok" or not u or u.nick:lower()~=p.nick:lower() or
e.channel:lower()~=p.channel or account=="" or account=="*" or account=="0" then
show("Account NickServ non verificato");return end
local key=e.server.."|"..account
local previous=allow[key];allow[key]=p.nick:lower()
if not saveAllow() then allow[key]=previous;show("Errore salvataggio")
else show("Whitelist aggiornata: "..p.nick.." ("..account..")") end
end)
