-- TIARCA Moderation Collector: all recording logic lives in THIS Lua file.
-- Enable this script for each network and consent to Persistent data + Read users.
-- It immediately starts collecting; /lua collector off|on|status|export controls it.
-- The host only offers general purpose, opt-in Lua journal/history capabilities.
local state, pending, observed = {}, {}, {}
local messages, recent_actions = {}, {} -- transient and bounded
local MAX_TRACKED, CONTEXT_LIMIT = 96, 5
local CONTEXT_SECONDS, SHADOW_SIMILARITY, SHADOW_MIN_WORDS = 1800, 75, 2
local seq, unresolved_seq = 0, 0
local LIMIT = 256 -- transient identity hints
local PENDING_LIMIT = 10 -- Lua native host accepts at most 12 actions/callback
local CHANNEL_LIMIT = 32
local channels = {} -- per-network allowlists, independent of AutoMod

local function channel_key(server) return "colch." .. server end
local function channel_valid(name)
  return type(name) == "string" and #name > 1 and #name <= 64 and
         name:match("^[#&+!][^,%s:%c]+$") ~= nil
end
local function channel_config(server)
  if channels[server] then return channels[server] end
  local ok, value = pcall(tiarca.storage.get, channel_key(server))
  if not ok then return nil end
  local c = {all = true, names = {}}
  if value == "none" then c.all = false
  elseif value and value:sub(1,5) == "only:" then
    c.all = false
    local count = 0
    for name in value:sub(6):gmatch("[^,]+") do
      if not channel_valid(name) or name ~= name:lower() or c.names[name] or
          count >= CHANNEL_LIMIT then return nil end
      c.names[name] = true
      count = count + 1
    end
    if count == 0 then return nil end
  elseif value ~= nil and value ~= "all" then return nil end
  channels[server] = c
  return c
end
local function in_scope(server, channel)
  local c = channel_config(server)
  return c ~= nil and (c.all or c.names[channel:lower()] == true)
end
local function save_channels(server, c)
  local names = {}
  for name in pairs(c.names) do names[#names+1] = name end
  table.sort(names)
  local raw = c.all and "all" or (#names == 0 and "none" or
      "only:" .. table.concat(names, ","))
  local ok = pcall(tiarca.storage.set, channel_key(server), raw)
  if ok then
    channels[server] = c
    observed[server] = nil -- drop identity hints after a scope change
  end
  return ok
end
local function channels_status(server)
  local c = channel_config(server)
  if not c then return "Configurazione canali non disponibile" end
  if c.all then return "Tutti i canali della rete" end
  local names = {}
  for name in pairs(c.names) do names[#names+1] = name end
  table.sort(names)
  return #names == 0 and "Nessun canale" or table.concat(names, ", ")
end

local function key(server) return "collector." .. server end
local function active(server)
  if state[server] ~= nil then return state[server] end
  local ok, value = pcall(tiarca.storage.get, key(server))
  if not ok then return false end
  state[server] = value ~= "off"
  return state[server]
end

local function quote(value)
  if value == nil or value == "" then return "null" end
  local str = tostring(value):gsub('[%z\1-\31\\"]', function(ch)
    if ch == '"' then return '\\"' end
    if ch == "\\" then return '\\\\' end
    return string.format("\\u%04x", ch:byte())
  end)
  return '"' .. str .. '"'
end

local function number(value)
  return type(value) == "number" and value == value and
         value ~= math.huge and value ~= -math.huge and tostring(value) or "null"
end

local function identity(server, channel, nick)
  if not nick or nick == "" then return nil end
  local id = observed[server] and observed[server][channel:lower()]
  local cached = id and id[nick:lower()] or nil
  local ok, users = pcall(tiarca.get_users, channel)
  if ok and type(users) == "table" then
    for _, user in ipairs(users) do
      if user.nick and user.nick:lower() == nick:lower() then
        return {ident = user.ident or (cached and cached.ident),
                host = user.host or (cached and cached.host),
                account = user.account and user.account ~= "*" and user.account or
                          (cached and cached.account)}
      end
    end
  end
  return cached
end

local function observe(e)
  if not e.nick or e.nick == "" or not e.channel:match("^[#&+!]") then return end
  local user = identity(e.server, e.channel, e.nick)
  if not user then return end
  observed[e.server] = observed[e.server] or {}
  local channel = e.channel:lower()
  observed[e.server][channel] = observed[e.server][channel] or {}
  local users = observed[e.server][channel]
  local count = 0
  for _ in pairs(users) do count = count + 1 end
  if count >= LIMIT and not users[e.nick:lower()] then
    -- Only transient identity hints are bounded, never the on-disk journal.
    observed[e.server][channel] = {}
    users = observed[e.server][channel]
  end
  users[e.nick:lower()] = user
end

local function user_key(server, channel, nick)
  return server .. "\0" .. channel:lower() .. "\0" .. nick:lower()
end

local function message_context(server, channel, nick, now)
  if not nick or nick=="" then return {} end
  local group=messages[user_key(server,channel,nick)]
  local result={}
  if group then
    for _,m in ipairs(group.list) do
      if now>=m.timestamp and now-m.timestamp<=CONTEXT_SECONDS then
        result[#result+1]=m
      end
    end
  end
  return result
end

local function message_json(entry, item, source)
  local id=entry.identity or {}
  return "{" .. table.concat({
    '"channel":'..quote(entry.channel), '"timestamp":'..number(item.timestamp),
    '"nickname":'..quote(entry.nickname), '"ident":'..quote(item.ident or id.ident),
    '"host":'..quote(item.host or id.host),
    '"account":'..quote(item.account or id.account),
    '"text":'..quote(item.text), '"source":'..quote(source)
  },",") .. "}"
end

local function context_json(entry)
  local items={}
  for _,m in ipairs(entry.context or {}) do
    items[#items+1]=message_json(entry,m,"live_buffer")
  end
  return "["..table.concat(items,",").."]"
end

local function last_json(entry,e)
  if e and e.text and e.text~="" and e.timestamp>0 then
    return message_json(entry,e,"sqlite_history")
  end
  local ctx=entry.context or {}
  return #ctx>0 and message_json(entry,ctx[#ctx],"live_buffer") or "null"
end

local function write(entry, history, status)
  local id=entry.identity or {}
  local sql=history and history.text and history.text~="" and history.timestamp>0
  local context=entry.context or {}
  local last=sql and history or context[#context]
  local age=last and entry.timestamp>=last.timestamp and entry.timestamp-last.timestamp or nil
  status=status or (sql and "sqlite_found" or "not_found_in_last_200")
  local line = "{" .. table.concat({
    '"schema":2',
    '"server":' .. quote(entry.server),
    '"channel":' .. quote(entry.channel),
    '"timestamp":' .. number(entry.timestamp),
    '"event":' .. quote(entry.event),
    '"action":' .. quote(entry.action),
    '"nickname":' .. quote(entry.nickname),
    '"ident":' .. quote(id.ident),
    '"host":' .. quote(id.host),
    '"account":' .. quote(id.account),
    '"operator":' .. quote(entry.operator),
    '"reason":' .. quote(entry.reason),
    '"ban_mask":' .. quote(entry.ban_mask),
    '"source_script":' .. quote(entry.source_script),
    '"similarity_percent":' .. number(entry.similarity),
    '"infraction_level":' .. number(entry.infraction),
    '"execution":' .. quote(entry.execution),
    '"identity_match":' .. quote(entry.identity_match),
    '"last_message":' .. last_json(entry,history),
    '"context_messages":' .. context_json(entry),
    '"history_status":' .. quote(status),
    '"message_age_seconds":' .. number(age),
    '"event_id":' .. quote(entry.event_id),
    '"case_id":' .. quote(entry.case_id),
    '"correlation":' .. quote(entry.correlation),
    '"candidate_stage":' .. quote(entry.candidate_stage),
    '"candidate_occurrences":' .. number(entry.candidate_occurrences),
    '"candidate_similarity_percent":' .. number(entry.candidate_similarity)
  }, ",") .. "}"
  local ok, err = pcall(tiarca.journal.append, line)
  if not ok then tiarca.log("Collector: scrittura fallita: " .. tostring(err)) end
end

local function record(entry)
  if not active(entry.server) or not in_scope(entry.server,entry.channel) then return end
  if not entry.nickname or entry.nickname=="" then
    unresolved_seq=unresolved_seq%2147483646+1
    entry.event_id=tostring(entry.timestamp).."-u"..unresolved_seq
    entry.context={}
    write(entry,nil,"target_unresolved")
    return
  end
  seq=seq%2147483646+1
  local token=tostring(seq)
  entry.event_id=tostring(entry.timestamp).."-"..token
  entry.context=message_context(entry.server,entry.channel,entry.nickname,entry.timestamp)
  if (entry.event=="automod" or entry.event=="automod_b") and
      ({kick=true,bankick=true,tbankick=true,ban=true,tban=true,tmute=true})[entry.action] then
    entry.case_id=entry.event_id
    recent_actions[user_key(entry.server,entry.channel,entry.nickname)]=
      {timestamp=entry.timestamp,case_id=entry.case_id}
  end
  local network = pending[entry.server]
  if not network then network = {}; pending[entry.server] = network end
  local count, oldest, oldest_entry = 0, nil, nil
  for old, data in pairs(network) do
    count = count + 1
    if not oldest_entry or data.timestamp < oldest_entry.timestamp then
      oldest, oldest_entry = old, data
    end
  end
  if count >= PENDING_LIMIT and oldest then
    -- Preserve the oldest event even if its asynchronous history never arrived.
    -- Flush at most one line here, within the native action budget.
    write(oldest_entry,nil,"history_queue_overflow")
    network[oldest] = nil
  end
  network[token] = entry
  local ok = pcall(tiarca.last_message, entry.channel, entry.nickname, token)
  if not ok then
    network[token] = nil
    write(entry,nil,"history_request_failed")
  end
end

local function signature(text)
  local parts, bytes={},0
  for word in text:lower():gsub("[%c%p]"," "):gmatch("%S+") do
    if #parts>=32 or bytes+#word+1>160 then break end
    parts[#parts+1]=word
    bytes=bytes+#word+1
  end
  return table.concat(parts," "),#parts
end

local function similarity(a,na,b,nb)
  local counts,shared={},0
  for w in a:gmatch("%S+") do counts[w]=(counts[w] or 0)+1 end
  for w in b:gmatch("%S+") do
    if (counts[w] or 0)>0 then
      shared=shared+1
      counts[w]=counts[w]-1
    end
  end
  return 200*shared/(na+nb)
end

-- Collect text only in bounded RAM; persist it only on moderation or
-- repetition events. The shadow detector can NEVER issue moderation actions.
local function remember_message(e)
  if not e.nick or e.nick=="" or not e.text or e.text=="" then return end
  local key=user_key(e.server,e.channel,e.nick)
  local previous=message_context(e.server,e.channel,e.nick,e.timestamp)
  local sig,n=signature(e.text)
  local matches,maximum=1,0
  if n>=SHADOW_MIN_WORDS then
    for _,m in ipairs(previous) do
      if m.words and m.words>=SHADOW_MIN_WORDS then
        local pct=similarity(sig,n,m.signature,m.words)
        if pct>maximum then maximum=pct end
        if pct>=SHADOW_SIMILARITY then matches=matches+1 end
      end
    end
  end
  local group=messages[key]
  if not group then
    local count,oldest,old_time=0,nil,nil
    for k,g in pairs(messages) do
      count=count+1
      if not old_time or g.seen<old_time then oldest,old_time=k,g.seen end
    end
    if count>=MAX_TRACKED and oldest then messages[oldest]=nil end
    group={list={},seen=e.timestamp}
    messages[key]=group
  end
  group.seen=e.timestamp
  local body=e.text
  if #body>320 then
    local cut=320
    while cut>1 and body:byte(cut)>=128 and body:byte(cut)<192 do cut=cut-1 end
    body=body:sub(1,cut-1)
  end
  local id=identity(e.server,e.channel,e.nick) or {}
  group.list[#group.list+1]={timestamp=e.timestamp,text=body,
    ident=id.ident,host=id.host,account=id.account,signature=sig,words=n}
  while #group.list>CONTEXT_LIMIT do table.remove(group.list,1) end
  if e.message_type=="normal" and n>=SHADOW_MIN_WORDS and matches>=2 and matches<=4 then
    seq=seq%2147483646+1
    local stage=matches==2 and "repeat_2" or
        (matches==3 and "would_warn" or "would_mute_120s")
    write({server=e.server,channel=e.channel,timestamp=e.timestamp,
      event="candidate",action="observed_only",nickname=e.nick,identity=id,
      execution="shadow_only",candidate_stage=stage,
      candidate_occurrences=matches,candidate_similarity=maximum,
      event_id=tostring(e.timestamp).."-"..seq,
      context=message_context(e.server,e.channel,e.nick,e.timestamp)},
      nil,"not_queried_shadow")
  end
end

local function mask_matches(mask, name, id)
  if not id or not id.ident or not id.host then return false end
  if mask:find(":", 1, true) then return false end -- extban cannot infer a nick
  local text = name .. "!" .. id.ident .. "@" .. id.host
  local pattern = "^" .. mask:lower()
      :gsub("([%%%^%$%(%)%.%[%]%+%-])", "%%%1")
      :gsub("%*", ".*"):gsub("%?", ".") .. "$"
  return text:lower():match(pattern) ~= nil
end

local function ban_target(e)
  local mask = e.text or ""
  local exact = mask:match("^([^!]+)!")
  if exact and not exact:find("[%*%?]") and #exact <= 64 then
    local id = identity(e.server, e.channel, exact)
    if not id or not id.ident or not id.host or
       mask_matches(mask, exact, id) then
      return exact, id, "explicit_nickname_mask"
    end
    return nil, nil, nil -- mask contradicts observed identity
  end
  -- For a host/ident mask, use only a unique current channel member.
  local ok, users = pcall(tiarca.get_users, e.channel)
  if not ok or type(users) ~= "table" or users.truncated then return nil end
  local chosen, found, matches = nil, nil, 0
  for _, user in ipairs(users) do
    if user.nick and mask_matches(mask, user.nick, user) then
      chosen, found = user.nick, user
      matches = matches + 1
      if matches > 1 then return nil end
    end
  end
  if matches == 1 then return chosen, found, "unique_live_roster_match" end
  return nil
end

tiarca.on("message", function(e)
  if not active(e.server) or not in_scope(e.server,e.channel) then return end
  observe(e)
  if (e.message_type=="normal" or e.message_type=="me") and
      e.channel:match("^[#&+!]") then remember_message(e) end
end)

local function forget(e, nick)
  if not nick or nick == "" then return end
  local network = observed[e.server]
  local channel = network and network[(e.channel or ""):lower()]
  if channel then channel[nick:lower()] = nil end
  messages[user_key(e.server,e.channel or "",nick)]=nil
  recent_actions[user_key(e.server,e.channel or "",nick)]=nil
end

-- Nicknames are not stable identities. Do not reuse identity hints after a
-- member joins/leaves, changes nick, is kicked or the network reconnects.
for _, kind in ipairs({"join", "part", "quit", "nick"}) do
  tiarca.on(kind, function(e) forget(e, e.nick) end)
end

tiarca.on("disconnected", function(e)
  -- No further timer tick is guaranteed after a disconnect: preserve every
  -- pending event, with null history rather than silently dropping it.
  local network = pending[e.server]
  if network then
    for token, entry in pairs(network) do
      if active(e.server) then write(entry,nil,"disconnected_before_history") end
      network[token] = nil
    end
  end
  pending[e.server] = nil
  observed[e.server] = nil
  for k in pairs(messages) do if k:sub(1,#e.server+1)==e.server.."\0" then messages[k]=nil end end
  for k in pairs(recent_actions) do if k:sub(1,#e.server+1)==e.server.."\0" then recent_actions[k]=nil end end
end)


-- AutoMod can be imported under any filename (e.g. automod.lua).
-- Match the validated decision payload, not the local script filename.
local automod_actions = {monitor=true, avviso=true, kick=true, ban=true,
                       mute=true, tmute=true, tban=true, bankick=true, tbankick=true,
                       escalation=true}
tiarca.on("moderation_log", function(e)
  if not active(e.server) or not in_scope(e.server, e.channel) then return end
  local engine, server, channel, nick, percent, level, action = (e.text or ""):match(
    "^AutoMod(B?)%s+([^/%s]+)/([^%s]+)%s+([^%s]+)%s+([%d%.]+)%% infrazione (%d+) %(([^%)]+)%)$")
  if not nick or server ~= e.server:sub(1, 8) or
      channel:lower() ~= e.channel:lower() or
      nick:lower() ~= e.nick:lower() or not automod_actions[action] then return end
  record({server=e.server, channel=e.channel, timestamp=e.timestamp,
          event=engine=="B" and "automod_b" or "automod", action=action, nickname=nick,
          identity=identity(e.server, e.channel, nick),
          operator=engine=="B" and "AutoMod B" or "AutoMod",
          reason=(action ~= "monitor" and "non ripetere" or nil),
          source_script=e.message_type, similarity=tonumber(percent),
          infraction=tonumber(level), execution="requested"})
end)

tiarca.on("kick", function(e)
  if not active(e.server) or not in_scope(e.server, e.channel) then return end
  local nick = e.message_type -- IRC KICK target; e.nick is the actor
  if not nick or nick == "" then return end
  local match=recent_actions[user_key(e.server,e.channel,nick)]
  local linked=match and e.timestamp>=match.timestamp and
      e.timestamp-match.timestamp<=10
  record({server=e.server, channel=e.channel, timestamp=e.timestamp,
          event="kick", action="kick", nickname=nick,
          identity=identity(e.server, e.channel, nick), operator=e.nick,
          reason=e.text, execution="observed_irc",
          case_id=linked and match.case_id or nil,
          correlation=linked and "same_nick_10s" or nil})
  forget(e, nick)
end)

tiarca.on("ban", function(e)
  if not active(e.server) or not in_scope(e.server, e.channel) then return end
  local nick, id, matched = ban_target(e)
  record({server=e.server, channel=e.channel, timestamp=e.timestamp,
          event="ban", action="ban", nickname=nick, identity=id,
          operator=e.nick, ban_mask=e.text,
          identity_match=matched, execution="observed_irc"})
end)

tiarca.on("history", function(e)
  local network = pending[e.server]
  local entry = network and network[e.message_type]
  if not entry then return end
  network[e.message_type] = nil
  if active(e.server) then write(entry, e) end
end)

tiarca.on("timer", function(e)
  local network = pending[e.server]
  if not network then return end
  for token, entry in pairs(network) do
    if e.timestamp - entry.timestamp >= 120 then
      if active(e.server) then write(entry,nil,"history_timeout") end
      network[token] = nil
    end
  end
end)

tiarca.on("command", function(e)
  local raw = (e.text or ""):match("^collector%s*(.-)%s*$")
  if raw == nil then return end
  local op, arg = raw:match("^(%S+)%s*(.-)$")
  op = op or ""
  arg = arg or ""
  if op == "channels" then
    local c = channel_config(e.server)
    if not c then tiarca.show("Collector", "Configurazione canali non disponibile"); return end
    if arg == "" or arg == "list" then
      tiarca.show("Collector: canali", channels_status(e.server))
    elseif arg == "all" or arg == "none" then
      local next = {all = arg == "all", names = {}}
      tiarca.show("Collector", save_channels(e.server, next) and
          channels_status(e.server) or "Salvataggio non disponibile")
    else
      local action, name = arg:match("^(add)%s+(%S+)$")
      if not action then action, name = arg:match("^(del)%s+(%S+)$") end
      if not action or not channel_valid(name) then
        tiarca.show("Collector", "/lua collector channels add|del #canale; all|none|list")
      else
        name = name:lower()
        local next = {all = false, names = {}}
        for key in pairs(c.names) do next.names[key] = true end
        local count = 0
        for _ in pairs(next.names) do count = count + 1 end
        if action == "add" and not next.names[name] and count >= CHANNEL_LIMIT then
          tiarca.show("Collector", "Massimo 32 canali")
          return
        end
        if action == "add" then next.names[name] = true else next.names[name] = nil end
        tiarca.show("Collector", save_channels(e.server, next) and
            channels_status(e.server) or "Salvataggio non disponibile")
      end
    end
    return
  end
  if op == "on" or op == "off" then
    local yes = op == "on"
    if not yes and active(e.server) and pending[e.server] then
      -- OFF must not discard events accepted before the command. At most 10
      -- pending journal writes + 1 storage + 1 local show action.
      for token, entry in pairs(pending[e.server]) do
        write(entry,nil,"collector_stopped")
        pending[e.server][token] = nil
      end
    end
    local ok = pcall(tiarca.storage.set, key(e.server), yes and "on" or "off")
    if ok then
      state[e.server] = yes
      if not yes then
        pending[e.server]=nil; observed[e.server]=nil
        for k in pairs(messages) do if k:sub(1,#e.server+1)==e.server.."\0" then messages[k]=nil end end
        for k in pairs(recent_actions) do if k:sub(1,#e.server+1)==e.server.."\0" then recent_actions[k]=nil end end
      end
    end
    tiarca.show("Collector", ok and (yes and "ON" or "OFF") or "Storage non disponibile")
  elseif op == "export" then
    -- User-initiated Android share sheet, even if collecting is OFF.
    tiarca.journal.export()
    tiarca.show("Collector", "Esportazione richiesta")
  elseif op == "exportreset" then
    if arg ~= "confirm" then
      tiarca.show("Collector", "Per azzerare: /lua collector exportreset confirm")
      return
    end
    -- Flush pending accepted events before rotating the append-only journal.
    -- Rotation preserves a durable archive even if Android sharing is cancelled.
    local network = pending[e.server]
    if network then
      for token, entry in pairs(network) do
        write(entry,nil,"collector_rotated")
        network[token] = nil
      end
    end
    tiarca.journal.rotate()
    tiarca.show("Collector", "Rotazione archivio richiesta; copia precedente recuperabile con archive")
  elseif op == "archive" then
    tiarca.journal.export_archive()
    tiarca.show("Collector", "Esportazione ultimo archivio richiesta")
  elseif op == "status" or op == "" then
    tiarca.show("Collector", active(e.server) and
      "ON: Collector V2 (5 messaggi, shadow 75%); " .. channels_status(e.server) or "OFF")
  else
    tiarca.show("Collector", "/lua collector on|off|status|export|exportreset|archive|channels")
  end
end)
