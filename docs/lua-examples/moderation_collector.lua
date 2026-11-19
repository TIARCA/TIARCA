-- TIARCA moderation collector: opt-in, bounded persistent event journal.
-- No message cache: kick_context is read from TIARCA's existing SQLite history.
-- Journal is stored in Lua storage (max 32 recent events per network), not a file.
local enabled,counts,pending={}, {}, {}
local CAP=32
local function network(e) return e.server or "" end
local function storage_get(k)
  local ok,v=pcall(tiarca.storage.get,k)
  if ok then return v end
  return nil
end
local function storage_set(k,v)
  return pcall(tiarca.storage.set,k,v)
end
local function safe(s,n)
  s=tostring(s or "")
  s=s:gsub("[\r\n\t]"," "):gsub("|","/")
  return s:sub(1,n or 200)
end
local function root(e) return "collector:"..network(e)..":" end
local function active(e)
  local s=network(e)
  if enabled[s]==nil then enabled[s]=storage_get(root(e).."on")=="1" end
  return enabled[s]
end
local function journal(e,kind,target,detail)
  if not active(e) then return end
  local s=network(e)
  local base=root(e)
  local count=tonumber(storage_get(base.."count") or "0") or 0
  local nextid=count+1
  local record=table.concat({
    safe(e.timestamp,20),safe(kind,20),safe(e.channel,64),
    safe(e.nick,64),safe(target,100),safe(detail,350)
  },"|")
  if not storage_set(base.."entry:"..tostring((nextid-1)%CAP+1),record) then
    tiarca.log("Collector: storage write failed")
    return
  end
  storage_set(base.."count",tostring(nextid))
  counts[s]=nextid
  tiarca.log("Collector | "..record)
end
tiarca.on("kick",function(e)
  if not active(e) then return end
  local target=e.message_type or ""
  pending[network(e).."|"..(e.channel or "").."|"..target]=true
  journal(e,"KICK",target,e.text)
end)
tiarca.on("kick_context",function(e)
  local k=network(e).."|"..(e.channel or "").."|"..(e.nick or "")
  if not pending[k] then return end
  pending[k]=nil
  journal(e,"PRE_KICK_MESSAGE",e.nick,e.text)
end)
tiarca.on("ban",function(e)
  journal(e,"BAN",e.text,"MODE +b")
end)
tiarca.on("command",function(e)
  local arg=(e.text or ""):match("^%s*collector%s+(.+)%s*$")
  if not arg then return end
  local base=root(e)
  if arg=="on" or arg=="off" then
    local on=arg=="on"
    if not storage_set(base.."on",on and "1" or "0") then
      tiarca.show("Collector","Enable script storage permission first")
      return
    end
    enabled[network(e)]=on
    tiarca.show("Collector",on and "ON" or "OFF")
  elseif arg=="status" then
    local count=tonumber(storage_get(base.."count") or "0") or 0
    tiarca.show("Collector",(active(e) and "ON" or "OFF")..
      "; total saved: "..count.."; retained: "..math.min(CAP,count))
  elseif arg=="last" then
    local count=tonumber(storage_get(base.."count") or "0") or 0
    local items={}
    for i=math.max(1,count-7),count do
      local v=storage_get(base.."entry:"..tostring((i-1)%CAP+1))
      if v then items[#items+1]=safe(v,160) end
    end
    if #items==0 then tiarca.show("Collector","No saved events")
    else tiarca.show_list("Collector",items) end
  elseif arg=="help" then
    tiarca.show("Collector","/lua collector on | off | status | last | help")
  end
end)
