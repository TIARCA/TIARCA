-- TIARCA Moderation Collector: all recording logic lives in THIS Lua file.
-- Enable this script for each network and consent to Persistent data + Read users.
-- It immediately starts collecting; /lua collector off|on|status|export controls it.
-- The host only offers general purpose, opt-in Lua journal/history capabilities.
local state, pending, observed = {}, {}, {}
local seq = 0
local LIMIT = 256

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
    '"ident":' .. quote(id and id.ident),
    '"host":' .. quote(id and id.host),
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
  if not active(entry.server) then return end
  if not entry.nickname or entry.nickname == "" then
    write(entry)
    return
  end
  seq = seq % 2147483646 + 1
  local token = tostring(seq)
  local network = pending[entry.server]
  if not network then network = {}; pending[entry.server] = network end
  local count = 0
  for _ in pairs(network) do count = count + 1 end
  if count >= LIMIT then
    -- Keep evidence even if the host could not deliver old history replies.
    for old, data in pairs(network) do write(data); network[old] = nil end
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
  if active(e.server) then observe(e) end
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


tiarca.on("moderation_log", function(e)
  if not active(e.server) or e.message_type ~= "automod_antiripetizione.lua" then return end
  local channel, nick, percent, level, action = (e.text or ""):match(
    "^AutoMod%s+[%w%-]+/([^%s]+)%s+([^%s]+)%s+([%d%.]+)%% infrazione (%d+) %(([^%)]+)%)$")
  if not nick or channel ~= e.channel or nick ~= e.nick then return end
  record({server=e.server, channel=e.channel, timestamp=e.timestamp,
          event="automod", action=action, nickname=nick,
          identity=identity(e.server, e.channel, nick),
          operator="AutoMod", reason=(action ~= "monitor" and "non ripetere" or nil),
          source_script=e.message_type, similarity=tonumber(percent),
          infraction=tonumber(level), execution="requested"})
end)

tiarca.on("kick", function(e)
  if not active(e.server) then return end
  local nick = e.message_type -- IRC KICK target; e.nick is the actor
  if not nick or nick == "" then return end
  record({server=e.server, channel=e.channel, timestamp=e.timestamp,
          event="kick", action="kick", nickname=nick,
          identity=identity(e.server, e.channel, nick), operator=e.nick,
          reason=e.text, execution="observed_irc"})
  forget(e, nick)
end)

tiarca.on("ban", function(e)
  if not active(e.server) then return end
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
  local op = (e.text or ""):match("^collector%s*(%S*)$")
  if op == nil then return end
  if op == "on" or op == "off" then
    local yes = op == "on"
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
  elseif op == "status" or op == "" then
    tiarca.show("Collector", active(e.server) and
      "ON: script attivo; archivio incrementale per questa rete" or "OFF")
  else
    tiarca.show("Collector", "/lua collector on|off|status|export")
  end
end)
