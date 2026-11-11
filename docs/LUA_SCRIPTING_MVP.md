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

Events: \`message\`, \`connected\`, \`disconnected\`. The callback receives
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

\`command\` events, timers, WHOIS/hostmask access, persistent storage and
plugin compatibility layers are **not yet available**; those belong to
subsequent milestones. A script error disables that script; the
normal IRC connection continues.

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

Three manually importable sources are included in the repository:
- [Count messages](lua-examples/count_messages.lua), log-only.
- [Same-conversation reply](lua-examples/auto_reply.lua), which requires the
  optional per-script `tiarca.send` permission.
- [Mask Finder Lite](lua-examples/mask_finder_lite.lua), a nickname-only
  demonstration. Edit the channel and nickname wildcard in the source before
  import. It only sees users who send messages after activation, cannot search
  ident/host masks, and has no interactive results list or /maskfind command.

Download the `.lua` file explicitly and select it in the Android document
picker. These examples are **not** downloaded or executed automatically.
