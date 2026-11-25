-- SPDX-License-Identifier: GPL-3.0-only
-- Harvester V1: passive, explicitly scoped, normalized JSONL.
-- Requires identity_v2 and persistent data; no send/moderation permission.
-- One journal append per event, containing any new dictionary definitions.
-- IDs are local to (network journal, epoch, segment). Bounded dictionaries.
tiarca.identity_v2()
local cfg, stats, dicts, pending = {}, {}, {}, {}
local epoch, segment, entries, bytes, querySeq = nil, 0, 0, 0, 0
local MAX_ENTRIES, MAX_BYTES, MAX_CHANNELS = 2048, 196608, 32

local function q(v)
 if v==nil then return "null" end
 return '"'..tostring(v):gsub('[%z\1-\31\\"]',function(c)
  if c=='"' then return '\\"' end
  if c=='\\' then return '\\\\' end
  return string.format("\\u%04x",c:byte())
 end)..'"'
end
local function valid(ch)
 return type(ch)=="string" and #ch>1 and #ch<=64 and ch:match("^[#&+!][^,%s:%c]+$")~=nil
end
local function config(server)
 if cfg[server] then return cfg[server] end
 local raw=tiarca.storage.get("h.cfg."..server)
 local c={on=false,channels={}}
 if raw then
  local flag,names=raw:match("^([01]);(.*)$")
  if not flag then error("Harvester: invalid config") end
  c.on=flag=="1"
  local n=0
  for ch in names:gmatch("[^,]+") do
   n=n+1
   if n>MAX_CHANNELS or not valid(ch) then error("Harvester: invalid channel config") end
   c.channels[ch:lower()]=true
  end
 end
 cfg[server]=c
 return c
end
local function channelList(c)
 local list={}
 for ch in pairs(c.channels) do list[#list+1]=ch end
 table.sort(list)
 return list
end
local function save(server,c)
 tiarca.storage.set("h.cfg."..server,(c.on and "1;" or "0;")..table.concat(channelList(c),","))
 cfg[server]=c
end
local function counter(server)
 if not stats[server] then stats[server]={messages=0,events=0,bytes=0,rejected=0} end
 return stats[server]
end
local function reset()
 dicts={}; entries=0; bytes=0; segment=segment+1
end
local function ensureEpoch()
 if epoch then return end
 local previous=tiarca.storage.get("h.epoch")
 local n=previous and tonumber(previous) or 0
 if not n or n<0 or n>=9007199254740990 or n%1~=0 then error("Harvester: invalid epoch") end
 epoch=n+1
 -- Storage transaction must commit before journal effects (redesign D).
 tiarca.storage.set("h.epoch",tostring(epoch))
 reset()
end
local function scope(e)
 local c=config(e.server)
 return c.on and valid(e.channel) and c.channels[e.channel:lower()]==true
end
local function state(server)
 ensureEpoch()
 if entries>=MAX_ENTRIES or bytes>=MAX_BYTES then reset() end
 if not dicts[server] then dicts[server]={next=0,seq=0,map={}} end
 return dicts[server]
end

-- Stage new dictionary values until append has been accepted by the native API.
-- A failed host write is not acknowledged by the current API: decoder must
-- report missing definitions, not guess. Epoch/segment changes limit damage.
local function write(e,kind,build)
 local d=state(e.server)
 local defs,staged,keys={}, {}, {}
 local nextId=d.next
 local function intern(category,value)
  if value==nil then return 0 end
  local key=category.."\0"..value
  if d.map[key] then return d.map[key] end
  if staged[key] then return staged[key] end
  nextId=nextId+1
  staged[key]=nextId;keys[#keys+1]=key
  defs[#defs+1]="["..nextId..","..q(category)..","..q(value).."]"
  return nextId
 end
 local function identity(nick,u)
  if not nick or nick=="" then return 0 end
  -- Never attribute one person's cached identity to a different nickname.
  if type(u)~="table" or u.nick~=nick then u={} end
  local fields={intern("nick",nick),intern("ident",u.ident),intern("host",u.host),
   intern("account",u.account_known~=false and u.account or nil),
   intern("realname",u.realname)}
  local value=table.concat(fields,",")
  local key="identity\0"..value
  if d.map[key] then return d.map[key] end
  if staged[key] then return staged[key] end
  nextId=nextId+1;staged[key]=nextId;keys[#keys+1]=key
  defs[#defs+1]="["..nextId..', "identity",['..value.."]]"
  return nextId
 end
 local server=intern("server",e.server)
 local channel=valid(e.channel) and intern("channel",e.channel:lower()) or 0
 local data=build(intern,identity) or ""
 local sequence=d.seq+1
 local st=counter(e.server)
 local line='{"v":1,"epoch":'..epoch..',"segment":'..segment..
  ',"id":'..sequence..',"ts":'..(tonumber(e.timestamp) or 0)..
  ',"s":'..server..',"ch":'..channel..',"type":'..q(kind)..
  (#defs>0 and ',"defs":['..table.concat(defs,",")..']' or "")..
  (st.lost and ',"local_gap":'..st.lost or "")..(e.source_id and ',"source_id":'..q(e.source_id) or "")..data.."}"
 if #line>8192 then
  st.rejected=st.rejected+1;st.lost=(st.lost or 0)+1;d.seq=sequence
  tiarca.log("Harvester: record over journal limit; collection incomplete")
  return false
 end
 local ok,err=pcall(tiarca.journal.append,line)
 if not ok then
  st.rejected=st.rejected+1;st.lost=(st.lost or 0)+1;d.seq=sequence
  tiarca.log("Harvester: append rejected; "..tostring(err))
  return false
 end
 for _,key in ipairs(keys) do
  d.map[key]=staged[key]; entries=entries+1; bytes=bytes+#key
 end
 d.next=nextId;d.seq=sequence;st.lost=nil
 if kind=="message" then st.messages=st.messages+1 else st.events=st.events+1 end
 st.bytes=st.bytes+#line+1
 return true
end
local function field(k,v)
 return v~=nil and ","..q(k)..":"..q(v) or ""
end
local function num(k,v) return ","..q(k)..":"..v end
local function userData(e,intern,identity)
 local u=e.sender
 return num("user",identity(e.nick,u))..
  field("prefix",type(u)=="table" and u.nick==e.nick and u.prefix or nil)
end

local function collectMessage(e)
 if not scope(e) then return end
 if e.message_type~="normal" and e.message_type~="notice" and e.message_type~="me" then return end
 write(e,"message",function(i,u)
  return userData(e,i,u)..field("kind",e.message_type)..field("text",e.text)..
   (e.type=="own_message" and ',"own":true' or "")
 end)
end
tiarca.on("message",collectMessage)
tiarca.on("own_message",collectMessage)
for _,name in ipairs({"join","part","quit","nick","kick","mode","topic","moderation_log"}) do
 local eventName=name
 tiarca.on(eventName,function(e)
  if not scope(e) then return end
  write(e,eventName,function(i,u)
   if eventName=="kick" then
    return num("actor",u(e.nick,e.sender))..num("target",u(e.message_type,nil))..field("reason",e.text)
   elseif eventName=="mode" then
    -- Keep protocol mode and argument; classify only known acting mute masks.
    local mode,kind=(e.message_type or ""):match("^([+-].):(.+)$")
    local action
    if mode=="+b" or mode=="-b" then
     local mask=e.text or ""
     local mute=mask:match("^m:") or mask:match("^~quiet:")
     action=mode=="+b" and (mute and "mute" or "ban") or (mute and "unmute" or "unban")
    end
    return num("actor",u(e.nick,e.sender))..field("mode",mode)..field("mode_kind",kind)..
     field("action",action)..(kind~="redacted" and field("param",e.text) or "")
   elseif eventName=="topic" then
    return num("actor",u(e.nick,e.sender))..field("text",e.text)
   elseif eventName=="moderation_log" then
    -- Report, not confirmed IRC execution; no fabricated message links.
    return num("target",u(e.nick,e.sender))..field("script",e.message_type)..field("report",e.text)
   elseif eventName=="nick" then
    return userData(e,i,u)..num("new_nick",i("nick",e.text))
   else
    return userData(e,i,u)..field("reason",e.text)
   end
  end)
 end)
end
local function lifecycle(e,name)
 if not config(e.server).on then return end
 write(e,name,function() return "" end)
end
tiarca.on("connected",function(e) lifecycle(e,"reconnect") end)
tiarca.on("disconnected",function(e)
 pending[e.server]=nil
 lifecycle(e,"disconnect")
end)

-- Explicit paginated snapshot. Eight users per callback; at most eight
-- journal appends and one next-page request, under the native action budget.
local function requestSnapshot(e,ch)
 if pending[e.server] then tiarca.show("Harvester","Snapshot already running");return end
 querySeq=querySeq+1
 ensureEpoch()
 local token="harv-"..epoch.."-"..querySeq
 pending[e.server]={token=token,ch=ch,started=e.timestamp}
 if not write({server=e.server,channel=ch,timestamp=e.timestamp},"snapshot_start",function()
  return field("token",token)
 end) then pending[e.server]=nil;return end
 tiarca.get_users(ch,{token=token,limit=8})
end
tiarca.on("identity",function(e)
 local p=pending[e.server]
 if not p or e.query~="users" or e.token~=p.token then return end
 local ev={server=e.server,channel=p.ch,timestamp=e.timestamp}
 if not scope(ev) then pending[e.server]=nil;return end
 if e.status~="ok" then
  pending[e.server]=nil
  write(ev,"snapshot_end",function() return field("token",p.token)..',"complete":false'..field("status",e.status) end)
  return
 end
 if not e.complete then p.failed=true end
 for _,u in ipairs(e.users or {}) do
  if not write(ev,"snapshot_member",function(i,identity)
   return field("token",p.token)..num("user",identity(u.nick,u))..field("prefix",u.prefix)
  end) then p.failed=true end
 end
 if e.next_offset then
  tiarca.get_users(p.ch,{token=p.token,limit=8,offset=e.next_offset})
 else
  pending[e.server]=nil
  write(ev,"snapshot_end",function()
   return field("token",p.token)..',"complete":'..(e.complete and not p.failed and "true" or "false")
  end)
 end
end)
tiarca.on("timer",function(e)
 local p=pending[e.server]
 if p and tonumber(e.timestamp) and tonumber(p.started) and e.timestamp-p.started>=180 then
  pending[e.server]=nil
  if config(e.server).on then
   write({server=e.server,channel=p.ch,timestamp=e.timestamp},"snapshot_end",function()
    return field("token",p.token)..',"complete":false,"status":"timeout"'
   end)
  end
 end
end)

local function show(s) tiarca.show("Harvester V1",s) end
local function change(e,c,what,ch)
 save(e.server,c)
 if c.on or what=="off" then
  write({server=e.server,channel=ch,timestamp=e.timestamp},"config",function()
   return field("change",what)
  end)
 end
end
tiarca.on("command",function(e)
 local raw=(e.text or ""):match("^harvester%s+(.+)$")
 if not raw and e.text~="harvester" then return end
 local op,arg=(raw or "help"):match("^(%S+)%s*(.-)%s*$")
 local c=config(e.server)
 if op=="on" then
  if #channelList(c)==0 then show("Add a channel first: /lua harvester add #channel");return end
  local started=not c.on
  if started then c.on=true;change(e,c,"on") end
  show("ON (selected channels only)")
  -- Automatic first snapshot when the command can query the sole channel.
  local list=channelList(c)
  if started and #list==1 and (e.channel=="" or (valid(e.channel) and e.channel:lower()==list[1])) then
   requestSnapshot(e,list[1])
  end
 elseif op=="off" then
  if c.on then c.on=false;pending[e.server]=nil;change(e,c,"off") end
  show("OFF")
 elseif op=="add" or op=="drop" then
  local ch=arg~="" and arg or e.channel
  if not valid(ch) then show("Specify a public channel");return end
  ch=ch:lower()
  if op=="add" then
   if not c.channels[ch] and #channelList(c)>=MAX_CHANNELS then show("Maximum 32 channels");return end
   c.channels[ch]=true
  else
   c.channels[ch]=nil
   if pending[e.server] and pending[e.server].ch==ch then pending[e.server]=nil end
  end
  change(e,c,op,ch)
  show(op.." "..ch)
  if op=="add" and c.on and (e.channel=="" or (valid(e.channel) and e.channel:lower()==ch)) and not pending[e.server] then
   requestSnapshot(e,ch)
  end
 elseif op=="channels" then
  local list=channelList(c)
  if #list==0 then show("No selected channels");return end
  -- Each response is limited to 512 bytes by the host.
  local page=tonumber(arg) or 1
  if page<1 or page%1~=0 or page>MAX_CHANNELS then show("Usage: channels [page]");return end
  local from=(page-1)*6+1
  local lines={}
  for n=from,math.min(from+5,#list) do lines[#lines+1]=list[n] end
  show("Page "..page.."/"..math.ceil(#list/6)..": "..table.concat(lines,", "))
 elseif op=="status" or op=="stats" then
  local st=counter(e.server)
  show((c.on and "ON" or "OFF").."; channels: "..#channelList(c)..
   "; messages: "..st.messages.."; events: "..st.events..
   "; bytes queued: "..st.bytes.."; local rejections: "..st.rejected..
   " (this VM; disk writes and host drops not acknowledged)")
 elseif op=="snapshot" then
  local ch=arg~="" and arg or e.channel
  if not scope({server=e.server,channel=ch}) then show("Enable collection for this channel first");return end
  requestSnapshot(e,ch:lower())
 elseif op=="export" then
  tiarca.journal.export()
 elseif op=="clear" or op=="exportreset" then
  if arg~="confirm" then show("Rotate and export, preserving archive: /lua harvester clear confirm");return end
  pending[e.server]=nil
  tiarca.journal.rotate()
  -- New segment always contains its own definitions, even if rotate fails.
  reset()
  stats[e.server]=nil
  show("Rotation requested; previous archive: /lua harvester archive")
 elseif op=="archive" then
  tiarca.journal.export_archive()
 else
  show("/lua harvester add|drop #channel; on|off; status|stats; channels [page]; snapshot [#channel]; export; clear confirm; archive; help")
 end
end)
