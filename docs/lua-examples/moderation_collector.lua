-- TIARCA Moderation Collector: all recording logic lives in THIS Lua file.
-- Enable this script for each network and consent to Persistent data + Read users.
-- It immediately starts collecting; /lua collector off|on|status|export controls it.
-- The host only offers general purpose, opt-in Lua journal/history capabilities.
local state, pending, observed = {}, {}, {}
local seq = 0
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
  if ok then channels[server] = c end
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

local function last_json(entry, e)
  if not e or not e.text or e.text == "" or e.timestamp <= 0 then return "null" end
  local id = identity(entry.server, entry.channel, entry.nickname) or entry.identity
  return "{" .. table.concat({
    '"channel":' .. quote(entry.channel),
    '"timestamp":' .. number(e.timestamp),
    '"nickname":' .. quote(entry.nickname),
    '"ident":' .. quote(e.ident or (id and id.ident)),
    '"host":' .. quote(e.host or (id and id.host)),
    '"account":' .. quote(id and id.account),
    '"text":' .. quote(e.text),
    '"source":"sqlite_history"'
  }, ",") .. "}"
end

local function write(entry, history)
  local id = entry.identity or {}
  local line = "{" .. table.concat({
    '"schema":1',
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
    '"last_message":' .. last_json(entry, history)
  }, ",") .. "}"
  local ok, err = pcall(tiarca.journal.append, line)
  if not ok then tiarca.log("Collector: scrittura fallita: " .. tostring(err)) end
end

local function record(entry)
  if not active(entry.server) or not in_scope(entry.server, entry.channel) then return end
  if not entry.nickname or entry.nickname == "" then
    write(entry)
    return
  end
  seq = seq % 2147483646 + 1
  local token = tostring(seq)
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
    write(oldest_entry)
    network[oldest] = nil
  end
  network[token] = entry
  local ok = pcall(tiarca.last_message, entry.channel, entry.nickname, token)
  if not ok then
    network[token] = nil
    write(entry)
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
  if active(e.server) and in_scope(e.server, e.channel) then observe(e) end
end)

local function forget(e, nick)
  if not nick or nick == "" then return end
  local network = observed[e.server]
  local channel = network and network[(e.channel or ""):lower()]
  if channel then channel[nick:lower()] = nil end
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
      if active(e.server) then write(entry) end
      network[token] = nil
    end
  end
  pending[e.server] = nil
  observed[e.server] = nil
end)


-- AutoMod can be imported under any filename (e.g. automod.lua).
-- Match the validated decision payload, not the local script filename.
local automod_actions = {monitor=true, avviso=true, kick=true, ban=true,
                       mute=true, tban=true, bankick=true, tbankick=true,
                       escalation=true}
tiarca.on("moderation_log", function(e)
  if not active(e.server) or not in_scope(e.server, e.channel) then return end
  local server, channel, nick, percent, level, action = (e.text or ""):match(
    "^AutoMod%s+([^/%s]+)/([^%s]+)%s+([^%s]+)%s+([%d%.]+)%% infrazione (%d+) %(([^%)]+)%)$")
  if not nick or server ~= e.server:sub(1, 8) or
      channel:lower() ~= e.channel:lower() or
      nick:lower() ~= e.nick:lower() or not automod_actions[action] then return end
  record({server=e.server, channel=e.channel, timestamp=e.timestamp,
          event="automod", action=action, nickname=nick,
          identity=identity(e.server, e.channel, nick),
          operator="AutoMod", reason=(action ~= "monitor" and "non ripetere" or nil),
          source_script=e.message_type, similarity=tonumber(percent),
          infraction=tonumber(level), execution="requested"})
end)

tiarca.on("kick", function(e)
  if not active(e.server) or not in_scope(e.server, e.channel) then return end
  local nick = e.message_type -- IRC KICK target; e.nick is the actor
  if not nick or nick == "" then return end
  record({server=e.server, channel=e.channel, timestamp=e.timestamp,
          event="kick", action="kick", nickname=nick,
          identity=identity(e.server, e.channel, nick), operator=e.nick,
          reason=e.text, execution="observed_irc"})
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
      if active(e.server) then write(entry) end
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
        write(entry)
        pending[e.server][token] = nil
      end
    end
    local ok = pcall(tiarca.storage.set, key(e.server), yes and "on" or "off")
    if ok then
      state[e.server] = yes
      if not yes then pending[e.server] = nil; observed[e.server] = nil end
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
        write(entry)
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
      "ON: archivio incrementale; " .. channels_status(e.server) or "OFF")
  else
    tiarca.show("Collector", "/lua collector on|off|status|export|exportreset|archive|channels")
  end
end)
