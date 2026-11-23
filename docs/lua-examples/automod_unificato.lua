local STORAGE_PREFIX="u1.s"
local SHARDS,MAX_USERS,WINDOW,TTL,MIN_WORDS=12,360,1200,86400,2
local state,configs,sessions,index={}, {}, {}, {}
local repeats,whitelist={},{}
local loaded,broken,serial,users=false,false,0,0
local function norm(v)
v=(v or ""):lower():gsub("à","a"):gsub("è","e"):gsub("é","e")
v=v:gsub("ì","i"):gsub("ò","o"):gsub("ù","u")
v=v:gsub("[%p%c]"," "):gsub("%s+"," "):match("^%s*(.-)%s*$") or ""
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
local rules={
 {"A","B01",function(s) return s:find("%f[%a]f%s+per%s+m%d+")~=nil or
s:find("%f[%a]f%s+x%s+m%d+")~=nil end},
 {"B","B02",function(s,w) return mcode(w) and (s:find("cerco f",1,true) or
 s:find("cerca f",1,true) or s:find("per f",1,true) or
 s:find("x f",1,true)) end},
 {"C","B03",function(s,w) return mcode(w) and nearby(w,(function()
local t={} for _,x in ipairs(w) do if x:match("^m%d+$") then t[x]=true end end
return t end)(),invite,4) end},
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
local greet=s:gsub("[\128-\255]",""):gsub("%s+$","")
if greet=="ciao a tutti" or greet=="salve a tutti" or
greet=="buongiorno a tutti" or greet=="buonasera a tutti" or
greet=="buonanotte a tutti" or greet=="ciao" or
greet=="buongiorno" or greet=="buonasera" or
greet=="buonanotte" or greet=="salve" then
return "Z","NEUTRAL"
end
if s=="f" or s=="donne" or s=="mxm" or s=="qualcuna" then
return "Q","MICRO"
end
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
if not c then c={on=true,a=true,b=true,mode="monitor",epoch=1,channels={},soglia=75,occorrenze=3,ultimi=8,minuti=30,minparole=3}; configs[server]=c end
return c
end
local function saveConfig()
local rows={}
for server,c in pairs(configs) do
local ch={}
for v in pairs(c.channels) do ch[#ch+1]=v end
table.sort(ch)
rows[#rows+1]=table.concat({server,c.on and "1" or "0",c.mode,
tostring(c.epoch),table.concat(ch,","),c.a and "1" or "0",c.b and "1" or "0",c.soglia,c.occorrenze,c.ultimi,c.minuti,c.minparole},"\t")
end
table.sort(rows)
local value=table.concat(rows,"\n")
if #value>8192 then return false end
if value=="" then return pcall(tiarca.storage.delete,"u1.config") end
return pcall(tiarca.storage.set,"u1.config",value)
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
if #p[10]>10 or p[10]:find("[^0A-QZ]") then return nil end
for x in p[10]:gmatch(".") do t.marks[#t.marks+1]=x end
end
if p[11]~="-" then for chunk in p[11]:gmatch("[^;]+") do
local code,stamp=chunk:match("^([A-Q]):(%d+)$")
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
local ok,c=pcall(tiarca.storage.get,"u1.config")
if not ok then return false end
if type(c)=="string" then
for line in c:gmatch("[^\n]+") do
local p=split(line,"\t")
if #p==12 and (p[2]=="0" or p[2]=="1") and
(p[3]=="monitor" or p[3]=="live") then
local cfg=cfgFor(p[1])
cfg.on=p[2]=="1";cfg.mode=p[3];cfg.epoch=tonumber(p[4]) or 1
cfg.a=p[6]=="1";cfg.b=p[7]=="1"
cfg.soglia=tonumber(p[8]);cfg.occorrenze=tonumber(p[9]);
cfg.ultimi=tonumber(p[10]);cfg.minuti=tonumber(p[11]);cfg.minparole=tonumber(p[12])
if not cfg.soglia or cfg.soglia<60 or cfg.soglia>100 or
not cfg.ultimi or cfg.ultimi<3 or cfg.ultimi>11 or
not cfg.occorrenze or cfg.occorrenze<2 or cfg.occorrenze>cfg.ultimi or
not cfg.minuti or cfg.minuti<1 or cfg.minuti>120 or
not cfg.minparole or cfg.minparole<2 or cfg.minparole>12 then
broken=true
end
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
local allowOk,allowRaw=pcall(tiarca.storage.get,"u1.allow")
if not allowOk then broken=true;return false end
if type(allowRaw)=="string" then
for line in allowRaw:gmatch("[^\n]+") do
local server,account,nick=line:match("^([%w%-]+)\t([%w_.%-]+)\t([%w_%-%[%]{}\\^|]+)$")
if server and account and nick then
whitelist[server.."|"..account]=nick
else broken=true end
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
if prefix~="" and prefix~="+" then return nil end -- Voice included; halfop and higher protected.
local account=u.account
if account=="*" or account=="0" then account="" end
return safe(account),safe(u.host),safe(u.ident)
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
local sameSession=r and sessions[nickKey(e.server,e.nick)]==r.id
if r and account~="" and r.account~="" and r.account~=account then
r=nil
elseif r and account~="" and r.account=="" and not sameSession then
r=nil
end
if r and account~="" and r.account=="" then r.account=account end
if not a and not h and i and i~=false and host~="" and
not contains(i.hosts,host) and e.timestamp-i.seen<900 then
local concurrent=false
for nick,sid in pairs(sessions) do
if sid==i.id and nick~=nickKey(e.server,e.nick) then concurrent=true;break end
end
if concurrent then r=nil end
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
local function allowSave()
local entries={}
for k,nick in pairs(whitelist) do
local network,account=k:match("^(.-)|(.+)$")
if network and account then entries[#entries+1]=network.."\t"..account.."\t"..nick end
end
table.sort(entries)
local raw=table.concat(entries,"\n")
if #raw>8192 then return false end
if raw=="" then return pcall(tiarca.storage.delete,"u1.allow") end
return pcall(tiarca.storage.set,"u1.allow",raw)
end
local function imprint(text)
local a={}
for w in norm(text):gmatch("%S+") do
a[#a+1]=w
if #a>=32 then break end
end
return table.concat(a," "),#a
end
local function sim(a,na,b,nb)
local seen,shared={},0
for w in a:gmatch("%S+") do seen[w]=(seen[w] or 0)+1 end
for w in b:gmatch("%S+") do
if (seen[w] or 0)>0 then seen[w]=seen[w]-1;shared=shared+1 end
end
return 200*shared/(na+nb)
end
local function detectA(e,t,eligible,c)
local key=t.id.."\0"..e.channel:lower()
local h=repeats[key]
if not h or h.epoch~=t.epoch then
h={list={},triggered=false,epoch=t.epoch};repeats[key]=h
end
for i=#h.list,1,-1 do
if e.timestamp-h.list[i].t>c.minuti*60 then table.remove(h.list,i) end
end
if #h.list==0 then h.triggered=false end
local sig,n=imprint(e.text or "")
if not eligible or n<c.minparole then return false end
local matches=0
for _,v in ipairs(h.list) do
if v.n>=c.minparole and sim(sig,n,v.sig,v.n)>=c.soglia then matches=matches+1 end
end
h.list[#h.list+1]={sig=sig,n=n,t=e.timestamp}
while #h.list>c.ultimi do table.remove(h.list,1) end
local triggered=matches>=(h.triggered and 1 or c.occorrenze-1)
if triggered then h.triggered=true end
return triggered
end
local function active(e)
local c=cfgFor(e.server)
return c.on and c.channels[e.channel:lower()] and c or nil
end
local function mark(t,letter)
t.marks[#t.marks+1]=letter
while #t.marks>10 do table.remove(t.marks,1) end
end
local function detectB(e,t,letter)
local earlier
for _,v in pairs(t.last) do
if not earlier or v>earlier then earlier=v end
end
t.last[letter]=e.timestamp
mark(t,letter)
local found,total=0,0
for _,v in ipairs(t.marks) do
if v~="Z" then
total=total+1
if v~="0" then found=found+1 end
end
end
local eligible=found>=2 and total>0 and found*3>=total*2 and
((earlier and e.timestamp-earlier<=WINDOW and e.timestamp>=earlier) or
t.level>0)
return eligible,found,total
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
tiarca.on("message",function(e)
if not e.channel or not e.channel:match("^[#&+!]") or
e.message_type~="normal" or not load() then return end
local c=active(e)
if not c or (not c.a and not c.b) then return end
local account,host,ident=observed(e)
if account==nil or (account=="" and host=="" and ident=="") then return end
if account~="" and whitelist[e.server.."|"..account] then return end
local t=resolve(e,account,host,ident)
if not t then return end
if t.epoch~=c.epoch then
t.level=0;t.infraction=0;t.marks={};t.last={};t.epoch=c.epoch
end
if t.level>0 and e.timestamp-t.infraction>=TTL then
t.level=0;t.infraction=0
end
local letter,rule=classify(e.text or "")
local b,found,total=false,0,0
if c.b and letter~="0" and letter~="Z" then
b,found,total=detectB(e,t,letter)
else mark(t,letter=="Z" and "Z" or "0") end
local a=c.a and detectA(e,t,letter~="Z",c) or false
local engine=b and "B" or (a and "A" or nil)
local rising=engine and t.level<5
if engine then
t.level=math.min(5,t.level+1)
t.infraction=e.timestamp
end
if not persist(shardNum(t.id)) then return end
if engine then
local label=t.level==1 and "avviso" or (t.level==4 or t.level==5) and "kick" or "tmute"
tiarca.log(string.format("AutoModU %s/%s %s %.1f%% infrazione %d (%s) motore %s",
e.server:sub(1,8),e.channel,e.nick,total>0 and 100*found/total or 0,
t.level,c.mode=="live" and rising and label or "monitor",engine))
if c.mode=="live" and rising then sanction(e,t.level,engine) end
end
end)
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
tiarca.on("timer",function(e)
if not load() then return end
local dirty={}
for id,t in pairs(state) do
if t.server==e.server and e.timestamp-t.seen>TTL and
(t.infraction==0 or e.timestamp-t.infraction>TTL) then
dirty[shardNum(t.id)]=true
state[id]=nil
users=users-1
end
end
for n in pairs(dirty) do
if not persist(n) then return end
end
if next(dirty) then rebuild() end
end)
local function show(msg) tiarca.show("AutoMod unificato",msg) end
tiarca.on("command",function(e)
local root,op,rest
if not root then
local raw=e.text or ""
if raw:match("^%s*mod%s") or raw:match("^%s*mod$") then
root="mod";op,rest=raw:match("^%s*mod%s*(%S*)%s*(.-)%s*$")
elseif raw:match("^%s*b%s") or raw:match("^%s*b$") then
root="b";op,rest=raw:match("^%s*b%s*(%S*)%s*(.-)%s*$")
else return end
end
if op=="" or op=="help" then
show("/lua mod stato; on|off; motore a|b on|off; modo monitor|live; canali add|drop #canale; parametro soglia|occorrenze|ultimi|minuti|minparole N; whitelist add|drop|list NICK; close; reset.")
return
end
if op=="close" and rest=="" and root=="mod" then tiarca.close_pms();return end
if not load() then show("Archivio non disponibile: moderazione sospesa");return end
local c=cfgFor(e.server)
local old={on=c.on,a=c.a,b=c.b,mode=c.mode,epoch=c.epoch,channels={},soglia=c.soglia,occorrenze=c.occorrenze,ultimi=c.ultimi,minuti=c.minuti,minparole=c.minparole}
for ch in pairs(c.channels) do old.channels[ch]=true end
local function commit(msg)
if saveConfig() then show(msg);return end
c.on,c.a,c.b,c.mode,c.epoch,c.channels,c.soglia,c.occorrenze,c.ultimi,c.minuti,c.minparole=
old.on,old.a,old.b,old.mode,old.epoch,old.channels,old.soglia,old.occorrenze,old.ultimi,old.minuti,old.minparole
show("Salvataggio non riuscito")
end
if op=="stato" then
local n=0 for _ in pairs(c.channels) do n=n+1 end
show(string.format("%s; modo %s; A %s; B %s; canali %d; identita %d; escalation 24h; avvisi PVT.",
c.on and "ON" or "OFF",c.mode,c.a and "ON" or "OFF",
c.b and "ON" or "OFF",n,users))
elseif op=="on" or op=="off" then
c.on=op=="on";commit("AutoMod "..op)
elseif op=="modo" then
if rest~="monitor" and rest~="live" then show("Uso: /lua mod modo monitor|live");return end
if c.mode~=rest then c.mode=rest;c.epoch=c.epoch+1 end
commit("Modo "..rest)
elseif op=="motore" then
local which,value=rest:match("^([ab])%s+(on|off)$")
if not which then
which,value=rest:match("^([ab])%s+(%S+)$")
end
if not which or (value~="on" and value~="off") then
show("Uso: /lua mod motore a|b on|off");return
end
c[which]=value=="on";commit("Motore "..which.." "..value)
elseif op=="parametro" then
local name,val=rest:match("^(%a+)%s+(%d+)$")
local n=tonumber(val)
local bounds={soglia={60,100},occorrenze={2,5},ultimi={3,11},minuti={1,120},minparole={2,12}}
local b=bounds[name]
if not b or not n or n<b[1] or n>b[2] or n%1~=0 or
(name=="occorrenze" and n>c.ultimi) or
(name=="ultimi" and n<c.occorrenze) then
show("Uso: /lua mod parametro soglia|occorrenze|ultimi|minuti|minparole N");return
end
c[name]=n;c.epoch=c.epoch+1
commit("Parametro "..name.." = "..n)
elseif op=="canali" then
local change,ch=rest:match("^(%S+)%s+(%S+)$")
if not change or (change~="add" and change~="drop") or
not ch:match("^[#&+!][^,%s:%c]+$") or #ch>64 then
show("Uso: /lua mod canali add|drop #canale");return
end
local count=0 for _ in pairs(c.channels) do count=count+1 end
if change=="add" and count>=16 and not c.channels[ch:lower()] then
show("Massimo 16 canali");return end
c.channels[ch:lower()]=change=="add" and true or nil
commit("Canali aggiornati")
elseif op=="whitelist" then
local cmd,nick=rest:match("^(%S+)%s*(%S*)$")
if cmd=="list" then
local names={}
for k,n in pairs(whitelist) do names[#names+1]=n.." ("..k..")" end
table.sort(names)
show(#names>0 and table.concat(names,", "):sub(1,480) or "Whitelist vuota")
elseif (cmd=="add" or cmd=="drop") and nick~="" and #nick<=64 then
if cmd=="drop" then
local changed={}
for k,v in pairs(whitelist) do
if v==nick:lower() then changed[#changed+1]=k end
end
if #changed==0 then show("Nickname non presente in whitelist");return end
for _,k in ipairs(changed) do whitelist[k]=nil end
if allowSave() then show("Esclusione eliminata per "..nick)
else for _,k in ipairs(changed) do whitelist[k]=nick:lower() end
show("Impossibile salvare la modifica") end
return
end
local account
for ch in pairs(c.channels) do
local ok,roster=pcall(tiarca.get_users,ch)
if ok and type(roster)=="table" then
for _,u in ipairs(roster) do
if u.nick and u.nick:lower()==nick:lower() and u.account and
u.account~="*" and u.account~="0" then
account=u.account:lower()
end
end
end
end
if not account or not account:match("^[%w_.%-]+$") then
show("Account NickServ non verificato nei canali monitorati");return end
local k=e.server.."|"..account
local before=whitelist[k]
whitelist[k]=cmd=="add" and nick:lower() or nil
if allowSave() then show("Whitelist aggiornata: "..nick.." ("..account..")")
else whitelist[k]=before;show("Salvataggio whitelist fallito") end
else show("Uso: /lua b whitelist add|drop NICK; whitelist list") end
elseif op=="reset" then
c.epoch=c.epoch+1
commit("Storico escalation azzerato")
else show("Comando sconosciuto; /lua mod help") end
end)
