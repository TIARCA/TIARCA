-- TIARCA moderation collector (preview).
-- Passive IRC event observer; no duplicate message history is maintained.
-- IMPORTANT: persistent JSONL export and historical-message lookup require
-- the Android-side collector bridge and are not yet implemented.
local enabled = {}
local counts = {}
local function key(e) return e.server or "" end
local function status(e)
  local s=key(e)
  if enabled[s] == nil then
    local ok,v=pcall(tiarca.storage.get,"collector."..s)
    enabled[s]=ok and v=="1"
  end
  return enabled[s]
end
local function report(e,kind,target,reason)
  if not status(e) then return end
  local s=key(e)
  counts[s]=(counts[s] or 0)+1
  local line=table.concat({
    "Collector",kind,
    "server="..s,
    "channel="..(e.channel or ""),
    "actor="..(e.nick or ""),
    "target="..(target or ""),
    "detail="..(reason or ""),
    "time="..tostring(e.timestamp or "")
  }," | ")
  tiarca.log(line)
end
tiarca.on("kick",function(e)
  report(e,"KICK",e.message_type,e.text)
end)
tiarca.on("ban",function(e)
  report(e,"BAN",e.text,"MODE +b")
end)
tiarca.on("command",function(e)
  local arg=(e.text or ""):match("^%s*collector%s+(.+)%s*$")
  if not arg then return end
  local s=key(e)
  if arg=="on" or arg=="off" then
    local new=arg=="on"
    local ok=pcall(tiarca.storage.set,"collector."..s,new and "1" or "0")
    if not ok then
      tiarca.show("Collector","Storage permission required")
      return
    end
    enabled[s]=new
    tiarca.show("Collector",new and "Enabled" or "Disabled")
  elseif arg=="status" then
    tiarca.show("Collector",(status(e) and "ON" or "OFF")..
      "; observed in this runtime: "..tostring(counts[s] or 0))
  elseif arg=="help" then
    tiarca.show("Collector","/lua collector on | off | status | help")
  end
end)
