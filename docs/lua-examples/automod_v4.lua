local api=tiarca
local TTL,SHARDS=86400,20
local RAPID,MAX_B,MAX_A=30,6,5
local ranges={similarity={50,100},minutes={1,120},credit={0,3},minwords={3,12},rapid={0,120}}
local parameters={somiglianza="similarity",soglia="similarity",finestra="minutes",
minuti="minutes",credito="credit",minparole="minwords",raffica="rapid"}
local configs,allow,unresolved,cursor={},{},{},{}
local loaded,failed=false,false
local pending,seq={},0
api.identity_v2()
local function words(s)
local t={} for v in s:gmatch("%S+") do t[#t+1]=v end return t
end
local function dict(s)
local d={} for v in s:gmatch("%S+") do d[v]=true end return d
end
local people=dict("f m mxm ragazza ragazze ragazzo ragazzi donna donne uomo uomini qualcuna qualcuno nessuna nessuno coetanea coetanee coetaneo coetanei tipa tipo tipe tipi femmina femmine maschio maschi")
local single=dict("mxm ragazza ragazze donna donne")
local chatTarget=dict("f m mxm donna donne uomo uomini")
local search=dict("cerco cerca cercasi cerchiamo chi qualche qualke")
local chatting=dict("chat chatta chattare chatto chiacchiera conoscenza conoscere compagnia amicizia amicizie parlare chattiamo chiacchiere messaggiare")
local contact=dict("pvt priv privato privata prvt scrivimi scrivetemi contattami contattatemi")
local encounter=dict("reale incontro incontri ospito ospitare uscire")
local media=dict("cam call videochat whatsapp whatap whatsap telegram tg")
local places=dict("parma caserta brescia bergamo foggia avellino milano roma napoli biella veneto puglia lecce sicilia friuli piemonte marche toscana bari salerno genova torino calabria calabira siciliana verona enna viareggio firenze bologna palermo catania abruzzo molise umbria lazio sardegna lombardia campania liguria trentino basilicata aosta bolzano trento potenza matera aquila pescara chieti teramo isernia campobasso ancona ascoli pesaro macerata fermo perugia terni rimini ravenna cesena forli modena reggio piacenza ferrara padova vicenza treviso venezia rovigo belluno udine trieste gorizia pordenone como lecco varese lodi cremona mantova sondrio monza pavia asti cuneo novara vercelli alessandria imperia savona spezia lucca pisa livorno arezzo siena grosseto prato pistoia massa carrara latina frosinone viterbo rieti benevento brindisi taranto barletta andria trani catanzaro cosenza crotone vibo messina siracusa ragusa trapani agrigento caltanissetta cagliari sassari nuoro oristano")
local geo={}
for line in ("puglia pugliese pugliesi|roma romano romana romani romane|napoli napoletano napoletana napoletani napoletane|"..
"abruzzo abruzzese abruzzesi|sicilia siciliano siciliana siciliani siciliane|milano milanese milanesi|"..
"calabria calabrese calabresi|sardegna sardo sarda sardi sarde|toscana toscano toscana toscani toscane|"..
"veneto veneta veneti venete|piemonte piemontese piemontesi|lombardia lombardo lombarda lombardi lombarde|"..
"campania campano campana campani campane|liguria ligure liguri|marche marchigiano marchigiana marchigiani marchigiane|"..
"umbria umbro umbra umbri umbre|lazio laziale laziali|molise molisano molisana molisani molisane|"..
"friuli friulano friulana friulani friulane|trentino trentina trentini trentine|basilicata lucano lucana lucani lucane|"..
"aosta valdostano valdostana valdostani valdostane|bari barese baresi|palermo palermitano palermitana palermitani palermitane|"..
"torino torinese torinesi|genova genovese genovesi|bologna bolognese bolognesi|firenze fiorentino fiorentina fiorentini fiorentine|"..
"salerno salernitano salernitana salernitani salernitane|catania catanese catanesi|messina messinese messinesi|"..
"verona veronese veronesi|venezia veneziano veneziana veneziani veneziane|pescara pescarese pescaresi|"..
"caserta casertano casertana casertani casertane|lecce leccese leccesi|foggia foggiano foggiana foggiani foggiane"):gmatch("[^|]+") do
local city=line:match("^%S+")
for v in line:gmatch("%S+") do geo[v]=city;places[v]=true end
end
local greetings={}
for s in ("ciao|salve|sera|buongiorno|buonasera|buonanotte|buona sera|buon pomeriggio|buona serata|"..
"buona notte|ciao tutti|ciao a tutti|salve a tutti|sera a tutti|buonasera a tutti|buongiorno a tutti|buonanotte a tutti|un saluto a tutti|ciao chat"):gmatch("[^|]+") do greetings[s]=true end
local function norm(s)
s=(s or ""):lower():gsub("à","a"):gsub("è","e"):gsub("é","e")
s=s:gsub("ì","i"):gsub("ò","o"):gsub("ù","u")
s=s:gsub("\194\160"," "):gsub("\225\154\128"," "):gsub("\226\128[\128-\138\168\169\175]"," ")
s=s:gsub("\226\129\159"," "):gsub("\227\128\128"," ")
s=s:gsub("[%p%c]"," "):gsub("%s+"," ")
s=s:match("^%s*(.-)%s*$") or ""
return s:gsub("(%f[%a][mf])%s+(%d+)","%1%2")
end
local lex={}
for _,d in ipairs({people,single,chatTarget,search,chatting,contact,encounter,media,places}) do
for v in pairs(d) do lex[v]=true end
end
local equivalents={pvt="privato",prvt="privato",priv="privato",privata="privato",x="per"}
local function normB(raw)
local w=words(norm(raw))
for i,v in ipairs(w) do
local t=v
while not lex[t] and #t>3 and t:byte(-1)==t:byte(-2) do t=t:sub(1,-2) end
if lex[t] then v=t end
w[i]=equivalents[v] or v
end
return table.concat(w," ")
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
local function greetCore(s)
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
local function greetNick(n)
return n and n:match("^[a-z][%w]*$") and not protected(n) and not code({n})
end
local function greet(raw)
local s=norm(raw:gsub("\240\159[\128-\191][\128-\191]"," ")
:gsub("\226[\152\153\157][\128-\191]"," ")
:gsub("\239\184\143",""):gsub("\226\128\141","")
:gsub("([%w])[_%-]+%f[%w]","%1"))
if greetCore(s) then return true end
local n,tail=s:match("^(%S+) (.+)$")
if greetNick(n) and greetCore(tail) then return true end
local head,last=s:match("^(.+) (%S+)$")
if greetNick(last) and greetCore(head) then return true end
local first,middle,rest=s:match("^(%S+) (%S+) (.+)$")
if greetings[first] and greetNick(middle) and greetCore(first.." "..rest) then
return true end
return false
end
local function category(raw,s,w)
if raw:match("^%s*!") then return "ignore","BOT" end
s=s or normB(raw)
if s=="" then return "ignore","EMPTY" end
if greetings[s] then return "ignore","GREETING" end
w=w or words(s)
if not s:find("[^%d ]") then
if raw:match("^%s*0%d%d?%s*[/%-]%s*0%d%d?%s*%??%s*$") then return "ad","NUMERIC" end
return "short","NUMERIC"
end
if #w==1 and (single[w[1]] or code(w) or
((w[1]=="f" or w[1]=="m") and raw:find("?",1,true))) then return "ad","SINGLE" end
if #w<2 then return "short","SHORT" end
if (w[1]=="ciao" or w[1]=="salve" or w[1]=="buongiorno" or w[1]=="buonasera") and
has(w,places) and not has(w,search) and not has(w,contact) and not has(w,chatting)
and not has(w,{per=true,ci=true,siete=true}) then return "ignore","GREETING" end
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
local explicit=w[1]=="cerco" or w[1]=="cercasi" or w[1]=="cerchiamo"
if code(w) and (has(w,{io=true}) or explicit or
(not code({w[1]}) and has(w,{per=true}) and has(w,people))) then return "ad","MCODE" end
if #w<=8 and (near(w,chatTarget,chatting,5) or
has(w,{conoscenza=true,conoscere=true})) and
not (#w==2 and chatting[w[1]] and not raw:find("?",1,true)) then
return "ad","CHAT" end
if explicit and near(w,search,people,5) then return "ad","SEARCH" end
return "normal","NORMAL" end
if near(w,search,people,5) then return "ad","SEARCH" end
if code(w) and (w[1]=="io" or has(w,search) or
has(w,contact) or has(w,people)) then return "ad","MCODE" end
if greet(raw) or s:match("^ciao da [%w_]+$") then return "ignore","GREETING" end
if #w<=2 then return "short","SHORT" end
if not s:find("[a-z]") then return "ignore","SYMBOLS" end
if raw:match("^%s*https?://") and #w<=4 then return "ignore","URL" end
return "normal","NORMAL"
end
local function contextual(e,raw,kind,rule)
if not e.mentions or not next(e.mentions) or kind=="ignore" then return kind,rule end
local w=words(norm(raw))
local ad=has(w,search) or has(w,contact) or has(w,media) or has(w,encounter)
or has(w,{io=true,per=true})
local prev=""
local clean=raw:gsub("%S+",function(t)
local n=t:gsub("[,:;.!?]+$","")
local v=words(normB(n))
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
local feminine=dict("f ragazza ragazze donna donne tipa tipe femmina femmine nessuna qualcuna coetanea coetanee")
local masculine=dict("m ragazzo ragazzi uomo uomini tipo tipi maschio maschi nessuno qualcuno coetaneo coetanei")
local function adKey(rule,s,raw,w)
w=w or words(s)
local f,m=has(w,feminine),has(w,masculine)
local target=f and (m and "both" or "f") or (m and "m" or "")
local first=w[1] or ""
local explicit=search[first] or people[first] or
((first=="una" or first=="un") and people[w[2]]) or
(raw:find("?",1,true) and (has(w,{per=true,["in"]=true,zona=true}) or #w<=4))
if not explicit then return s end
if rule=="CONTACT" and target~="" then return "CONTACT:"..target end
if rule=="GEO" or rule=="WHO_GEO" then
if first=="chi" and w[2] and not places[w[2]] and
not ({di=true,da=true,dal=true,dalla=true,a=true,["in"]=true,zona=true})[w[2]] then return s end
local loc
for _,v in ipairs(w) do if places[v] then
local city=geo[v] or v
if loc and loc~=city then return s end
loc=city
end end
if loc then return "GEO:"..target..":"..loc end
end
return s
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
similarity=75,minutes=15,credit=2,minwords=3,epoch=1,rapid=RAPID}
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
rows[#rows+1]=c.rapid==RAPID and row or row.."\t"..c.rapid
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
local function readPart(i,filter)
local history={}
local seen=filter and {} or history
local yes,part=pcall(api.storage.get,shardKey(i))
if yes then
for line in (part or ""):gmatch("[^\n]+") do
local key,lev,at,epoch=line:match("^([^\t]+)\t(%d+)\t(%d+)\t(%d+)$")
lev=tonumber(lev)
if not key or seen[key] or partition(key)~=i or lev<1 or lev>5 then
yes=false;break end
seen[key]=true
if not filter or filter[key] then history[key]={level=lev,at=tonumber(at),epoch=tonumber(epoch)} end
end
end
if yes then return history end
api.log("V4: archivio non leggibile; moderazione sospesa per blocco "..i)
end
local genericIdent=dict("guest webirc android user ident unknown anonymous irccloud mibbit")
local genericHost=dict("localhost services.simosnap.com bot.simosnap.com * 0 unknown 0.0.0.0 127.0.0.1 ::1")
local MAX_GRAPH,MAX_KEYS,MAX_NAMES,LINK_SHARDS=768,24,8,6
local graph,nets={},{}
local graphCount=0
local GI,GN,GL,GK,GA,GW=1,2,3,4,5,6
local HS,HL,HR,HA=1,2,3,4
local function distinct(i)
return #i>=5 and not genericIdent[i]
end
local function provider(h)
if not h:match("^simosnap%-") or h:match("%.ip$") then return nil end
local suffix=h:match("^[^.]+%.(.+)$")
if not suffix or not suffix:find("%.") then return nil end
return suffix:match("([^.]+%.co%.uk)$") or suffix:match("([^.]+%.com%.au)$") or
suffix:match("([^.]+%.[^.]+)$")
end
local function usableNick(n)
return n~="" and not n:match("^guest[%d_%-]*$") and
not n:match("^utente[%d_%-]*$") and not n:match("^user[%d_%-]*$") and
not n:match("^ospite[%d_%-]*$")
end
local function network(server)
local n=nets[server]
if not n then n={server=server,index={},active={}};nets[server]=n end
return n
end
local function ni(g,k)
local a=g[GA]
for i=1,#a,3 do if a[i]:lower()==k then return i end end
end
local function forget(g)
if not graph[g] then return end
local n=g[GN]
for k in pairs(g[GK]) do if n.index[k]==g then n.index[k]=nil end end
for i=1,#g[GA],3 do
local k=g[GA][i]:lower()
if n.active[k]==g then n.active[k]=nil end
end
graph[g]=nil;graphCount=graphCount-1
g[GK]=nil;g[GA]=nil;g[GW]=nil
end
local function addName(g,n,at)
local k=valid(n)
if k=="" or #n>64 or n:find("[/:]") then return end
local a=g[GA]
local i=ni(g,k)
if i then
if at>a[i+1] then a[i],a[i+1]=n,at end
return i
end
i=#a+1
if #a>=MAX_NAMES*3 then
i=1
for j=4,#a,3 do if a[j+1]<a[i+1] then i=j end end
if a[i+1]>at then return end
local old=a[i]:lower()
if g[GN].active[old]==g then g[GN].active[old]=nil end
end
a[i],a[i+1],a[i+2]=n,at,false
return i
end
local function attach(g,k,at)
local index=g[GN].index
if not g[GK][k] then
local count,oldest=0
for key,t in pairs(g[GK]) do
count=count+1
if key~=g[GI] and (not oldest or t<g[GK][oldest]) then oldest=key end
end
if count>=MAX_KEYS and oldest then
if g[GK][oldest]>at then return end
if index[oldest]==g then index[oldest]=nil end
g[GK][oldest]=nil
end
end
g[GK][k]=math.max(g[GK][k] or 0,at);index[k]=g
end
local K,T,N,S,P,F=1,2,3,4,5,6
local sessions,sessionId={},{}
local function ref(s)
if not s or s=="" then return false end
local p,n=s:match("^([%w%-]+):(%d+)$")
if p then
local i=sessionId[p]
if not i and #sessions<32 then i=#sessions+1;sessions[i]=p;sessionId[p]=i end
if i then return string.char(i)..n end
end
return "\0"..s
end
local function unref(s)
if not s then return "-" end
local i=s:byte(1)
if i==0 then return s:sub(2) end
return sessions[i]..":"..s:sub(2)
end
local fmt="<I8I8I8s2s2s2"
local function pack(g,ad)
return string.pack(fmt,g[T],g[N],ad and g[F] or (g[F] and 1 or 0),g[K],g[S] or "",g[P] or "")
end
local function unpackRef(v,ad)
local t,n,f,k,r,p=string.unpack(fmt,v)
return {k,t,n,r~="" and r or false,p~="" and p or false,ad and f or f==1}
end
local function mergeRefs(a,b,now,window,cap,ads)
local all,by={},{}
for _,list in ipairs({a or {},b or {}}) do
for _,v in ipairs(list) do
local g=unpackRef(v,ads)
if now>=g[T] and now-(ads and g[F] or g[T])<=window then
local p=by[g[K]]
if not p then by[g[K]]=g;all[#all+1]=g
else
local latest,older=g,p
if p[T]>g[T] then latest,older=p,g end
local same=latest[S] and latest[S]==older[S]
local count=same and math.max(p[N],g[N]) or p[N]+g[N]
local prev=latest[P] or (not same and older[S]) or false
local first=ads and math.min(p[F],g[F]) or latest[F]
p[N],p[P],p[S],p[T],p[F]=count,prev,latest[S],latest[T],first
end
end
end
end
table.sort(all,function(x,y) return x[T]>y[T] end)
while #all>cap do table.remove(all) end
for i,g in ipairs(all) do all[i]=pack(g,ads) end
return all
end
local function merge(a,b,now,window)
if a==b then return a end
if a[GL]<b[GL] then a,b=b,a end
if b[GW] then
for ch,h in pairs(b[GW]) do
if now>=h[HL] and now-h[HL]<=window then
if not a[GW] then a[GW]={} end
local x=a[GW][ch]
if not x or now<x[HL] or now-x[HL]>window then a[GW][ch]=h
else
x[HS]=math.max(x[HS],h[HS])
x[HL]=math.max(x[HL],h[HL])
x[HR]=mergeRefs(x[HR],h[HR],now,window,MAX_A,false)
x[HA]=mergeRefs(x[HA],h[HA],now,window,MAX_B,true)
end
end
end
end
local index,active=a[GN].index,a[GN].active
for k,t in pairs(b[GK]) do
if index[k]==b then index[k]=nil end
attach(a,k,t)
end
for i=1,#b[GA],3 do
local k=b[GA][i]:lower()
local live=active[k]==b
if live then active[k]=nil end
local dst=addName(a,b[GA][i],b[GA][i+1])
if live and dst then a[GA][dst+2]=b[GA][i+2];active[k]=a end
end
forget(b)
a[GL]=math.max(a[GL],b[GL])
return a
end
local roots
local function observe(e,now,window,event)
local u=e.sender
local oldNick=valid(e.nick)
local newNick=event=="nick" and valid(e.text) or oldNick
local net=network(e.server)
local index,active=net.index,net.active
local session=active[oldNick]
local pos=session and ni(session,oldNick)
local oldDomain=pos and session[GA][pos+2]
local g
if event=="nick" or event=="account" or event=="chghost" then
g=session
if g and (not graph[g] or now<g[GL] or now-g[GL]>TTL) then g=nil end
end
local keys,primary,domain={}
local function key(k) keys[#keys+1]=k;return k end
if u and (u.nick==e.nick or (event=="nick" and valid(u.nick)==newNick)) then
local account,host,ident=valid(u.account),valid(u.host),valid(u.ident)
if account=="*" or account=="0" then account="" end
if account~="" then primary=key("a:"..account) end
if host~="" and not genericHost[host] then
local exact=key("h:"..host)
if distinct(ident) then exact=key("h:"..host.."/i:"..ident) end
primary=primary or exact
domain=provider(host)
if domain then
if usableNick(oldNick) then key("n:"..oldNick.."@"..domain) end
if newNick~=oldNick and usableNick(newNick) then key("n:"..newNick.."@"..domain) end
if distinct(ident) then key("i:"..ident.."@"..domain) end
end
end
elseif g and event=="nick" and oldDomain then
domain=oldDomain
if usableNick(newNick) then key("n:"..newNick.."@"..domain) end
end
if primary then
roots(keys,now,config(e.server),net)
end
for _,k in ipairs(keys) do
local p=index[k]
if p and graph[p] and now>=p[GL] and now-p[GL]<=TTL then
g=g and merge(g,p,now,window) or p
end
end
if not g and primary then
if graphCount>=MAX_GRAPH then
local oldest,quiet
for p in pairs(graph) do
if not oldest or p[GL]<oldest[GL] then oldest=p end
if not p[GW] and (not quiet or p[GL]<quiet[GL]) then quiet=p end
end
if oldest then forget(quiet or oldest) end
end
g={primary,net,now,{},{},false}
graph[g]=true;graphCount=graphCount+1
end
if not g then return nil end
for _,k in ipairs(keys) do attach(g,k,now) end
g[GL]=math.max(g[GL],now)
local row=addName(g,e.nick,now)
if event=="nick" and newNick~="" then
row=addName(g,e.text,now);active[oldNick]=nil
end
if row and newNick~="" then
g[GA][row+2]=domain or (event=="nick" and oldDomain) or false
active[newNick]=g
end
return g
end
local function identify(e,g)
local u=e.sender
if not u or u.nick~=e.nick then return nil,"UNKNOWN" end
local prefix=u.prefix or ""
if prefix~="" and prefix~="+" then return nil,"PROTECTED" end
local account=valid(u.account)
if account~="" and account~="*" and account~="0" and
(account=="micaela" or allow[e.server.."|"..account]) then return nil,"WHITELIST" end
if not g then return nil,"NO_HOST" end
return g
end
local function getWatch(g,channel,now,window)
if not g[GW] then g[GW]={} end
local key=channel:lower()
local h=g[GW][key]
if not h or now<h[HL] or now-h[HL]>window then
h={0,now,false,false};g[GW][key]=h
end
return h
end
local function linkStorage(n) return "v4.g"..n end
-- Version 3: one record per root/time, shared by all its exact signatures.
-- Old key-sharded rows remain readable. No change to legacy escalation shards.
local function linkRows(raw,fn)
local compact=raw=="!3" or raw:sub(1,3)=="!3\n"
for line in (compact and raw:sub(4) or raw):gmatch("[^\n]+") do
local root,at,epoch,names,keys
if compact then
root,at,epoch,names,keys=line:match("^([^\t]+)\t(%d+)\t(%d+)\t([^\t]*)\t(.+\t)$")
else
local k,r,t,e,ns=line:match("^([^\t]+)\t([^\t]+)\t(%d+)\t(%d+)\t(.*)$")
local server,key
if k then server,key=k:match("^([^/]+)/(.*)$") end
if server then root,at,epoch,names,keys=server.."/"..r,t,e,ns,key.."\t" end
end
if not root or not root:match("^[^/]+/[ah]:") then return false end
if fn(root,tonumber(at),tonumber(epoch),names,keys)==false then return false end
end
return true
end
local function linkRaw(parts,n)
if parts[n]==nil then
local ok,v=pcall(api.storage.get,linkStorage(n))
if not ok then return nil end
parts[n]=v or ""
end
return parts[n]
end
local function lookupLink(parts,k,now)
local server,key=k:match("^([^/]+)/(.*)$")
local row
for n=0,LINK_SHARDS-1 do
local raw=linkRaw(parts,n)
if not raw then return nil,false end
local compact=raw=="!3" or raw:sub(1,3)=="!3\n"
if raw:find(compact and "\t"..key.."\t" or k.."\t",1,true) then
local ok=linkRows(raw,function(r,t,e,names,ks)
if r:sub(1,#server+1)==server.."/" and ("\t"..ks):find("\t"..key.."\t",1,true) and (now<t or now-t<TTL) then
if row then return false end
row={r,t,e,k==r and names or ""}
end
end)
if not ok then return nil,false end
end
end
return row,true
end
local function fresh(row,now,c)
return row and row.epoch==c.epoch and now>=row.at and now-row.at<TTL
end
local function lfresh(row,now,c)
return row and row[3]==c.epoch and now>=row[2] and now-row[2]<TTL
end
roots=function(keys,now,c,net)
local parts={}
local prefix=net.server.."/"
for j=1,#keys do
local key=keys[j]
if not net.index[key] then
local k,seen=prefix..key,{}
for i=1,MAX_KEYS do
if seen[k] then break end
seen[k]=true
local row,ok=lookupLink(parts,k,now)
if not ok then break end
if not lfresh(row,now,c) then
if k~=prefix..key then keys[#keys+1]=k:sub(#prefix+1) end
break
end
if row[1]==k then keys[#keys+1]=k:sub(#prefix+1);break end
k=row[1]
end
end
end
end
local function namesText(g,now)
local out,a={},g[GA]
for i=1,#a,3 do
if now>=a[i+1] and now-a[i+1]<TTL then out[#out+1]=a[i]..":"..a[i+1] end
end
table.sort(out)
return table.concat(out,",")
end
local function restoreNames(g,row,now)
for n,t in (row[4] or ""):gmatch("([^,:]+):(%d+)") do
t=tonumber(t)
if now>=t and now-t<TTL then addName(g,n,t) end
end
end
local function historyFor(g,now,c)
local links,keys,candidates,done={},{},{},{}
local prefix=g[GN].server.."/"
for k in pairs(g[GK]) do keys[prefix..k]=true end
local id=prefix..g[GI]
keys[id]=true
local function getLink(k)
return lookupLink(links,k,now)
end
local function follow(k)
local seen={}
for i=1,MAX_KEYS do
if seen[k] then return nil end
seen[k]=true
local row,ok=getLink(k)
if not ok then return nil end
if not lfresh(row,now,c) then return k end
if row[4]~="" and not done[k] then restoreNames(g,row,now);done[k]=true end
if row[1]==k then return k end
k=row[1]
end
end
local primary=follow(id)
if not primary then return nil end
for k in pairs(keys) do
local r=follow(k)
if not r then return nil end
if r:match("^[^/]+/[ah]:") then candidates[r]=true end
end
candidates[primary]=true
local chosen,level,bestAt=primary,0,0
local shards={}
for r in pairs(candidates) do shards[partition(r)]=true;keys[r]=true end
for n in pairs(shards) do
local rows=readPart(n,candidates)
if not rows then return nil end
for r,row in pairs(rows) do
if fresh(row,now,c) and (row.level>level or
(row.level==level and (row.at>bestAt or (row.at==bestAt and r<chosen)))) then
chosen,level,bestAt=r,row.level,row.at
end
end
end
local history
if c.mode=="live" then
history=readPart(partition(chosen))
if not history then return nil end
end
return chosen,level,history,links,keys
end
local function saveLinks(chosen,links,keys,g,now,c)
local groups={}
local function add(root,at,epoch,names,key)
local h=root.."\t"..at.."\t"..epoch
local v=groups[h]
if not v then v={""};groups[h]=v end
if root==root:match("^[^/]+/")..key then v[1]=names end
v[#v+1]=key
end
local seen={}
for n=0,LINK_SHARDS-1 do
local raw=linkRaw(links,n)
if not raw then return nil,"archivio identita non leggibile" end
local ok=linkRows(raw,function(r,t,e,names,ks)
local prefix=r:match("^[^/]+/")
if now<t or now-t<TTL then
if prefix~=g[GN].server.."/" or e==c.epoch then
for k in ks:gmatch("[^\t]+") do
local full=prefix..k
if seen[full] then return false end
seen[full]=true
if not keys[full] then add(r,t,e,names,k) end
end
end
end
end)
if not ok then return nil,"archivio identita non leggibile" end
end
local names=namesText(g,now)
for k in pairs(keys) do add(chosen,now,c.epoch,names,k:match("^[^/]+/(.*)$")) end
local records={}
for h,v in pairs(groups) do
local names=table.remove(v,1);table.sort(v)
records[#records+1]=h.."\t"..names.."\t"..table.concat(v,"\t").."\t"
end
table.sort(records,function(a,b) if #a==#b then return a<b end return #a>#b end)
local pages,sizes={},{}
for n=0,LINK_SHARDS-1 do pages[n]={"!3"};sizes[n]=2 end
for _,r in ipairs(records) do
local n=0
for i=1,LINK_SHARDS-1 do if sizes[i]<sizes[n] then n=i end end
if sizes[n]+#r+1>8192 then return nil,"archivio identita pieno" end
pages[n][#pages[n]+1]=r;sizes[n]=sizes[n]+#r+1
end
local writes={}
for n=0,LINK_SHARDS-1 do
local raw=table.concat(pages[n],"\n")
if raw~=links[n] then writes[n]=raw end
end
return writes
end
local function aliases(g,e,now,room)
local out={}
local a=g[GA]
for i=1,#a,3 do
if a[i]:lower()~=e.nick:lower() and now>=a[i+1] and now-a[i+1]<TTL then out[#out+1]={a[i],a[i+1]} end
end
table.sort(out,function(a,b)
if a[2]==b[2] then return a[1]<b[1] end
return a[2]>b[2]
end)
local s=""
for _,v in ipairs(out) do
local part=(s=="" and " aliases " or "/")..v[1]
if #s+#part<=room then s=s..part end
end
return s
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
local function detected(e,id,engine,rule,pct,h,now,c,g,threshold)
local chosen,level,history,links,keys=historyFor(id,now,c)
if not chosen then api.log("V4: archivio identita/storico non leggibile; moderazione sospesa");return end
local nextLevel=math.min(5,level+1)
if c.mode=="live" then
local writes,why=saveLinks(chosen,links,keys,id,now,c)
if not writes then api.log("V4: "..why);return end
history[chosen]={level=nextLevel,at=now,epoch=c.epoch}
local ok,err=savePart(partition(chosen),history)
if not ok then
api.log("V4: archivio non scrivibile; moderazione sospesa: "..tostring(err));return end
for n,raw in pairs(writes) do api.storage.set(linkStorage(n),raw) end
end
local label=c.mode=="live" and (level<5 and action(e,nextLevel) or "escalation") or "monitor"
local line=string.format("AutoModV4 %s/%s %s %.1f%% infrazione %d (%s) motore %s",
e.server:sub(1,8),e.channel,e.nick,pct,nextLevel,label,engine)..
string.format(" rule %s score %d threshold %d count %d refs %s",rule,h[HS],
threshold or c.similarity,g[N],
((engine=="A" or threshold==3) and (unref(g[P])..",") or "")..unref(g[S]))
api.log(line..aliases(id,e,now,512-#line))
api.log(string.format("WatchdogV4 %s %s/%s %s %s score=%d",
engine,e.server:sub(1,8),e.channel,e.nick,rule,h[HS]))
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
local observed=observe(e,now,window,"message")
local raw=e.text or ""
local text=normB(raw)
local tokens=words(text)
local kind,rule=category(raw,text,tokens)
kind,rule=contextual(e,raw,kind,rule)
if kind=="ignore" or kind=="short" then return end
local id,why=identify(e,observed)
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
h[HL]=now
local source=ref(e.source_id)
if kind=="ad" and c.b then
local threshold=adThreshold(rule,tokens)
local key=adKey(rule,text,raw,tokens)
local ads=h[HA] or {}
h[HA]=ads
local g
for i=#ads,1,-1 do
local a=unpackRef(ads[i],true)
if now<a[F] or now-a[F]>window then table.remove(ads,i)
elseif a[K]==key then g=a;table.remove(ads,i) end
end
if g and source and source==g[S] then table.insert(ads,1,pack(g,true));return end
h[HS]=h[HS]+1
if not g then
g={key,now,1,source,false,now}
table.insert(ads,1,pack(g,true))
if #ads>MAX_B then table.remove(ads) end
return
end
g[N]=g[N]+1
local rapid=c.rapid>0 and now-g[T]<=c.rapid
if rapid or (g[N]>=threshold and h[HS]>=threshold) then
detected(e,id,"B",rapid and "RAPID_"..rule or rule,100,h,now,c,g,rapid and 2 or threshold)
end
g[P],g[S]=g[S],source
g[T]=now
table.insert(ads,1,pack(g,true))
elseif kind=="normal" and c.a then
local s=norm(raw)
if #words(s)<c.minwords then h[HS]=math.max(-c.credit,h[HS]-1);return end
local reps=h[HR] or {}
h[HR]=reps
for i=#reps,1,-1 do
local at=string.unpack("<I8",reps[i])
if now<at or now-at>window then table.remove(reps,i) end
end
local index,pct,g
if reps[1] then
local a=unpackRef(reps[1],false)
pct=a[K]==s and 100 or similar(a[K],s)
if pct>=c.similarity then index=1;g=a end
end
if not index then
for i=2,#reps do
local a=unpackRef(reps[i],false)
if a[K]==s then index=i;pct=100;g=a;break end
end
end
if index then
table.remove(reps,index)
if source and source==g[S] then table.insert(reps,1,pack(g,false));return end
if g[N]==1 then h[HS]=h[HS]+(g[F] and 1 or 0)+2
else h[HS]=h[HS]+1 end
local rapid=c.rapid>0 and now-g[T]<=c.rapid and pct>=90
if rapid then h[HS]=h[HS]+1 end
g[N]=g[N]+1;g[K]=s;g[T]=now;g[F]=false
if g[N]>=3 and h[HS]>=3 then
detected(e,id,"A",rapid and "RAPID_SIMILAR" or "SIMILAR",pct,h,now,c,g)
end
g[P],g[S]=g[S],source
table.insert(reps,1,pack(g,false))
else
table.insert(reps,1,pack({s,now,1,source,false,h[HS]>-c.credit},false))
if #reps>MAX_A then table.remove(reps) end
h[HS]=math.max(-c.credit,h[HS]-1)
end
elseif kind=="normal" then
h[HS]=math.max(-c.credit,h[HS]-1)
end
end)
for _,name in ipairs({"join","nick","account","chghost"}) do
local event=name
api.on(event,function(e)
if not e.nick or not load() then return end
local c=config(e.server)
if not c.on or not next(c.channels) or (e.channel and e.channel~="" and
not c.channels[e.channel:lower()]) then return end
local now=tonumber(e.timestamp) or 0
if now>0 then observe(e,now,c.minutes*60,event) end
end)
end
for _,name in ipairs({"quit","part"}) do
api.on(name,function(e)
local n=nets[e.server]
if n and e.nick then n.active[e.nick:lower()]=nil end
end)
end
api.on("disconnected",function(e)
local n=nets[e.server]
if n then n.active={} end
end)
local function lifetime(server)
local c=configs[server]
return 60*(c and c.minutes or 15)
end
api.on("timer",function(e)
failed=false
if not load() then api.log("V4: config non leggibile");return end
local now=tonumber(e.timestamp) or 0
for g in pairs(graph) do
if now>=g[GL] and now-g[GL]>=TTL then forget(g)
elseif g[GW] then
for ch,h in pairs(g[GW]) do
if now-h[HL]>lifetime(g[GN].server) then g[GW][ch]=nil end
end
if not next(g[GW]) then g[GW]=nil end
end
end
for _,n in pairs(nets) do
for k,g in pairs(n.active) do
local i=ni(g,k)
if not i or now-g[GA][i+1]>=TTL then n.active[k]=nil end
end
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
for g in pairs(graph) do if g[GN].server==e.server then forget(g) end end
nets[e.server]=nil
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
