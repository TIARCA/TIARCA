# Lua scripting API — experimental branch

Scope: https://github.com/TIARCA/TIARCA/issues/216

This API is **experimental** and confined to
\`feature/lua-scripting-sandbox-poc\`. Do not merge or publish as an
official TIARCA release without independent Android and security testing.

## Import and consent

Open **Settings → Lua scripts (experimental)**. Import a UTF-8 \`.lua\`
file through the Android document picker (Storage Access Framework).
It is copied into TIARCA private storage. No downloads, binary plugins,
.lua bytecode, JAR, DEX or native plugins are supported.

Maximum: 8 scripts, each 8192 source bytes. Imported scripts are
**disabled**. On enable, select exactly **one configured IRC network**,
confirm read access to message text and nicknames there, and optionally
approve automatic replies on that same network. By default, send is off.
Disable, reload, delete and error-status controls are available.

**Storage and backups:** scripts are included in TIARCA backup as checked
UTF-8 files but always restored disabled with read/send consent revoked.
Android Auto Backup/transfer additionally uses a non-backed-up approval
marker so a restored device never inherits an old approval.

## API v1 implemented

\`\`\`lua
local count = 0
tiarca.on("connected", function(event)
  tiarca.log("Connected on " .. event.server)
end)

tiarca.on("message", function(event)
  if event.channel == "#example" then
    count = count + 1
    tiarca.log("Messages seen in #example: " .. count)
    -- Only if separately approved by the user:
    -- if event.text == "!ciao" then tiarca.send(event.channel, "Ciao!") end
  end
end)

tiarca.on("disconnected", function(event)
  tiarca.log("Disconnected from " .. event.server)
end)
\`\`\`

Events: \`message\`, \`connected\`, \`disconnected\`, \`command\`. The callback receives
a table with \`type\`, \`server\` (server configuration UUID), \`channel\`,
\`nick\`, \`text\`, \`message_type\` (\`normal\`, \`notice\`, \`me\`),
and \`timestamp\` (Unix seconds).
- Message events are post-storage, normalized events, **not protocol RAW**.
- Only text NORMAL, NOTICE or ME with a sender are forwarded. The
  user's own messages, batch messages and STATUSMSG are excluded.
- No user/ident/hostname, passwords, server configuration or network socket
  is accessible from Lua.
- \`tiarca.log(string)\`: last log value held only in main-process RAM.
- \`tiarca.send(target, string)\`: queued request subject to host-side
  consent, server scope and *same incoming conversation* target equality.
  Rate limit: at most one script send every 5 seconds per script, plus
  at least 1.5 seconds between sends on the same network. No RAW IRC,
  CTCP, multiline or arbitrary destinations.

Registered Lua closures and their variables survive individual events while
the isolated service lives. Reload/reconnect of the Lua service resets that
state. There is no persistent \`tiarca.storage\` API in this milestone.

The new \`command\` callback is available only for an explicit user-entered
\`/lua <text>\` command in TIARCA's composer. It is local and asynchronous,
never transmitted as an IRC command. \`event.text\` contains the text after
\`/lua\`, and \`event.channel\` is the active conversation (empty on the server
tab). \`tiarca.log\` updates the script's Last log. \`tiarca.send\` is still
subject to per-script send consent, rate limits and the invoking conversation.
An unavailable engine leaves the command in the input for retry.
This is not a catch-all hook for IRC, aliases or quick commands.

Timers, WHOIS/hostmask access, persistent storage and plugin compatibility
layers are **not yet available**; those belong to subsequent milestones.
A script error disables that script; normal IRC continues.

## Limits and security requirements

- Each VM: 2 MiB Lua-managed memory, 200,000 Lua instructions per load
  or dispatch, maximum 8 callbacks per event, 12 actions per event.
- At most 32 in-flight events and bounded main-process IPC queue; additional
  messages are dropped instead of blocking IRC.
- No \`io\`, \`os\`, \`package\`, \`debug\`, \`require\`, \`load\`, \`loadfile\`,
  \`dofile\`, \`string.dump\`, \`print\`, \`collectgarbage\`.
- The VM is in a non-exported Android \`isolatedProcess\` and has no direct
  Android app permissions. It receives only minimal structured values.
- An app/device build and the Android isolated-process design **do not
  replace an independent audit** of hostile scripts and native code.
- Code is compiled from the MIT Lua sources in \`third_party/lua-5.5.1\`,
  with NDK pinned; full Standard/F-Droid reproducibility is mandatory.

## Manual checks required on a new Candidate

1. Import the example script; confirm it is disabled at import.
2. Enable it for one server without send permission. Trigger a live
   message and check the last log counter increases.
3. Confirm that a message on another configured server is not exposed.
4. Attempt \`tiarca.send\` with permission off: no IRC output.
5. Re-enable with explicit send permission: a reply is delivered only
   in the source conversation, and rate limiting prevents flooding.
6. Trigger an infinite loop, bad Lua code, or an OOM attempt:
   that script becomes disabled without affecting the IRC connection.
7. Terminate the Lua process from Diagnostics, then reload/reconnect:
   the process is restarted and state is reset; IRC stays active.
8. Backup, restore and Android device-to-device transfer:
   sources return but permission must be reapproved.
9. Test all supported ABIs, light/dark UI, foreground/background,
   APK size, RAM, battery and reproducible full APKs.

## Example scripts

Four manually importable sources are included in the repository:
- [Count messages](lua-examples/count_messages.lua), log-only.
- [Same-conversation reply](lua-examples/auto_reply.lua), which requires the
  optional per-script `tiarca.send` permission.
- [Local Lua commands](lua-examples/local_command.lua), `/lua ping` updates the
  script log; `/lua saluta` sends to the current conversation only if approved.
- [Private "ciao bello" reply](lua-examples/ciao_bello_pvt.lua), which responds
  "bello ciao" only to incoming private messages matching "ciao bello"
  (case-insensitive, ignoring outer whitespace). Requires send consent.
  It checks that the event's conversation target equals its sender nick.

Download the `.lua` file explicitly and select it in the Android document
picker. These examples are **not** downloaded or executed automatically.

## Additional read-only API: channel snapshots (phase C2)

\`tiarca.get_channels()\` is available **inside** Lua event callbacks. It returns
an array of \`{ name = "#channel", users = 12 }\` entries for joined IRC channels
on the script's **one explicitly approved server**, plus a boolean
\`result.truncated\` (true if more than 32 channels were joined).
Private conversations are excluded. The entries are sorted by channel name.

\`users\` is the number of members **currently cached locally**, not a fresh
NAMES/WHO result: zero can mean the initial member list has not yet arrived.
The snapshot is empty after disconnection or when the connection is unavailable.
No IRC request is made, and no hostmask, ident, account or user roster is
exposed. There is no send capability implicit in this API. It is suitable for
scripts that summarize channels and their cached member counts, but **not**
for mask matching; that requires a separate consented API with explicit
unknown-data handling in a later milestone.


## Phases C3–C5 (experimental; requires new per-script consent)

**C3 — current-channel roster and identities.** After separately approving
"channel members and identities" when enabling a script,
\`tiarca.get_users(event.channel)\` provides a **bounded snapshot** of the current
joined channel. Each entry contains \`nick\`, \`prefix\`, and optionally \`ident\`,
\`host\`, \`account\`, \`mask\` when *observed from the server*. Missing properties
are **nil**, not guesses. \`result.truncated\` indicates more than 128 cached
members; no promise of complete results for very large channels. Only the
**event's own joined channel** can be queried, not another network or PVT.
The host may be a cloaked hostname, not the user's IP address.

\`tiarca.refresh_users(event.channel)\` queues **one explicit WHO request**
from a locally typed \`/lua\` command when that extra consent is enabled.
It is rate-limited to one request per script per minute. When the server
advertises WHOX in ISUPPORT 005, TIARCA uses a fixed-token WHOX request
to populate nick, ident, host and account. Otherwise it uses standard WHO
and the account may remain unknown. Results update a 512-entry per-network
LRU cache in the IRC core, and arrive asynchronously: call \`get_users\`
in a **later** Lua event, not immediately after requesting WHO.
No script receives the raw protocol reply, password or Android object.
The cache resets on connection reset; the list may be incomplete before
NAMES finishes. The separate consent defaults to **off** and is never
restored from backup.

**C4 — passive events and bounded timers.** The available event names are
\`message\`, \`connected\`, \`disconnected\`, \`command\`, \`join\`, \`part\`,
\`quit\`, \`nick\` and \`timer\`. JOIN/PART/QUIT/NICK are post-storage,
normalized IRC events (not raw protocol hooks); \`event.channel\` identifies
the source channel, \`event.nick\` the prior nickname and for \`nick\`,
\`event.text\` the new nickname. A QUIT or NICK affecting more than one
joined channel may generate multiple per-channel callbacks.
The common \`timer\` is sent about every 60 seconds **while the Android
IRC service is running and the network is connected**. It may be delayed
or skipped by Android power management, process restarts, queue overflow
or disconnections. It cannot be used as an accurate alarm. Timer callbacks
have no channel target and cannot send IRC messages.

**C5 — restricted operator actions.** When enabling a script the user must
explicitly opt in to a **separate moderation capability**, disabled by
default. The sandbox offers:
- \`tiarca.kick(event.channel, nick, reason)\`
- \`tiarca.ban(event.channel, nick)\` (requires a known, safe host;
  generates a host-based \`+b *!*@host\` mask)
- \`tiarca.mode(event.channel, nick, "+o" | "-o" | "+v" | "-v")\`

The main process verifies the script still has consent, the approved
server is connected, the channel is joined and equals the incoming/current
channel, the **local user currently has channel operator privileges**, and
the target is a present member other than the local user. Only validated
structured KICK and MODE commands can be sent: scripts cannot issue raw
IRC, change the target network or use arbitrary ban masks. Moderation
is rate-limited (at most once every 12 seconds per script and a separate
per-server interval). IRC servers can still reject an action; this API
does not promise that a request succeeded, and moderation is intentionally
*not* executed for disconnected or timer callbacks. No unrestricted
extbans, arbitrary timed-ban commands, cross-channel actions or blanket mass bans.

### Example of the new API

\`\`\`lua
tiarca.on("command", function(event)
  if event.text ~= "roster" or event.channel == "" then return end
  local users = tiarca.get_users(event.channel)
  local unknown = 0
  for _, user in ipairs(users) do
    if not user.host then unknown = unknown + 1 end
  end
  tiarca.log("Cached users: " .. #users .. "; unknown hosts: " .. unknown)
  -- Optional, separately approved and rate-limited:
  -- tiarca.refresh_users(event.channel)
end)
\`\`\`

**Manual acceptance still required:** simultaneous networks, WHO/WHOX
variations across IRCds, JOIN/PART/NICK/QUIT event replay/ordering,
Android Doze and reconnect timing, operator status changes, removal of
consent while actions are pending, attempt to moderate another network
or PVT, empty/unknown hostmask, backup/restore revocation and device
crash isolation. Never enable moderation for untrusted scripts.

Additional C5 actions (only if separately approved, while operator and with an observed host): `tiarca.mute(channel, nick)` uses the existing network dialect's advertised acting-quiet extban (`m:` on known InspIRCd syntax, `~quiet:` on known UnrealIRCd syntax), and `tiarca.tban(channel, nick, "3h")` uses only the dialect's verified timed ban construction, restricted to 1–99 units of `m`, `h`, or `d`. Unsupported dialects reject the request without emitting any IRC command. These requests have the same rate limits and current-channel checks as `ban`.
