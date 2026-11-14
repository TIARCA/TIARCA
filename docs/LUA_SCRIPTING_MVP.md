# Lua scripting API — development on main

Scope: https://github.com/TIARCA/TIARCA/issues/216

The Lua API was integrated into `main` on 2026-11-12 through PR #260
and PR #258, with final merge commit `c7edee0e`. It is a **development
feature**, not part of the currently published stable `v0.9.10` APK.
A future official release requires separate authorization, release checks,
and remaining Android/security acceptance.

## Import and consent

Open **Settings → Lua scripts (experimental)**. Import a UTF-8 \`.lua\`
file through the Android document picker (Storage Access Framework).
It is copied into TIARCA private storage. No downloads, binary plugins,
.lua bytecode, JAR, DEX or native plugins are supported.

Maximum: 8 scripts, each 24576 source bytes. Imported scripts are
**disabled**. On enable, select **one or more configured IRC networks**
(up to 16 per script). No other network receives events from that script.
The same explicitly approved send/user-info/moderation/storage capabilities
apply to every checked network; all optional permissions are off initially.
An active script can change its network selection and permissions via
**Networks and permissions**, always with confirmation. Disable, reload,
delete and error-status controls are also available.

**Storage and backups:** scripts are included in TIARCA backup as checked
UTF-8 files but always restored disabled with read/send consent revoked.
Android Auto Backup/transfer additionally uses a non-backed-up approval
marker so a restored device never inherits an old approval.

## Core API (C1–C2)

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

The core events are \`message\`, \`connected\`, \`disconnected\`, and \`command\`; C4 also adds \`join\`, \`part\`, \`quit\`, \`nick\` and \`timer\`. The callback receives
a table with \`type\`, \`server\` (server configuration UUID), \`channel\`,
\`nick\`, \`text\`, \`message_type\` (\`normal\`, \`notice\`, \`me\`),
and \`timestamp\` (Unix seconds).
- Message events are post-storage, normalized events, **not protocol RAW**.
- Only text NORMAL, NOTICE or ME with a sender are forwarded. The
  user's own messages, batch messages and STATUSMSG are excluded.
- User identities and hostnames require separate C3 approval, and only values
  observed on the approved network are exposed. Passwords, server settings,
  filesystem access and network sockets remain inaccessible.
- \`tiarca.log(string)\`: last log value held only in main-process RAM.
- \`tiarca.send(target, string)\`: queued request subject to host-side
  consent, server scope and *same incoming conversation* target equality.
  Rate limit: at most one script send every 5 seconds per script, plus
  at least 1.5 seconds between sends on the same network. The 5-second
  script throttle is independent for each approved network. No RAW IRC,
  CTCP, multiline or arbitrary destinations.

Registered Lua closures and their variables survive individual events while
the isolated service lives. An APK update (and a Lua-service restart) kills
the process and resets ordinary local/global Lua variables. An in-place APK
replacement with the same application ID and signing certificate normally
retains the app's private files: existing script enable/disable status,
selected network scopes and C6 persistent values reload on the next start.
C6 opt-in `tiarca.storage` is the supported way to preserve counters/state
across an update. Arbitrary Lua VM closures and locals are **not**
automatically serialized.

The new \`command\` callback is available only for an explicit user-entered
\`/lua <text>\` command in TIARCA's composer. It is local and asynchronous,
never transmitted as an IRC command. \`event.text\` contains the text after
\`/lua\`, and \`event.channel\` is the active conversation (empty on the server
tab). \`tiarca.log\` updates the script's Last log. \`tiarca.send\` is still
subject to per-script send consent, rate limits and the invoking conversation.
An unavailable engine leaves the command in the input for retry.
This is not a catch-all hook for IRC, aliases or quick commands.

General-purpose WHOIS scripting and plugin compatibility layers are **not
available**. The bounded C3 roster/WHO, C4 timer, C6 storage and C7 local
results are documented below; none provides raw IRC or arbitrary network APIs.
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
2. Enable it for network A without send permission. Trigger a live
   message and check the last log counter increases.
3. Confirm that a message on unapproved network B is not exposed; explicitly
   add B and verify both networks receive their own events. Remove A and
   confirm that network no longer triggers the script.
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
on the script's **explicitly approved server(s)**, plus a boolean
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
IRC, change the target network or use arbitrary ban masks. Moderation actions are reserved in the exact Lua request order and scheduled
on a per-network pacing queue (1.5 seconds between Lua moderation commands
on the same IRC network). There is no 12-second per-script discard window:
sequences such as `tiarca.ban(channel, nick)` followed by
`tiarca.kick(channel, nick, reason)` are both scheduled. For safety the queue
holds at most 40 pending commands per network and 20 per script/network,
with no booking more than 60 seconds ahead; if Android delays execution
beyond that age, the stale action expires instead of being sent. Excess
actions are rejected with
a script log entry rather than accumulating without bound. Each command
rechecks permissions, live membership and operator status before execution.
Automatic punitive requests (kick, ban, mute, timed ban) also reject channel
members with any advertised prefix (operators, halfops, voice, etc.)
using the full live member list, not the 128-user
Lua identity snapshot. Explicit Lua voice/devoice remains permitted.
Pending actions are invalidated on disconnect, script reload and stop.
IRC servers can still reject an action; this API
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


## C6 — per-script persistent text storage (experimental)

The user must separately approve **Persistent script data** on the chosen
network(s) when enabling each source. Storage belongs to the script and is
intentionally shared across its explicitly selected IRC networks. Permission
is off on import, re-enable, backup restore and device transfer. The script
never receives filesystem access, Java objects or a database connection.

Inside an event callback, use:

\`\`\`lua
local before = tiarca.storage.get("counter")  -- string or nil
tiarca.storage.set("counter", tostring(tonumber(before or "0") + 1))
tiarca.storage.delete("counter")
\`\`\`

Keys: 1–48 ASCII letters/digits/underscore/dot/hyphen, never a path.
Values: UTF-8 strings of 1–8192 bytes. Up to 16 keys and 128 KiB
of key/value data per script (JSON file at most 160 KiB). The main process stores each script's
data in a private atomic JSON file. Lua receives only a checked,
bounded copy with each event; setting or deleting emits a main-process
action. Read-after-write in one callback is supported, while
subsequent callbacks may briefly observe an earlier snapshot if IPC
events overlap. The data survives app restarts and script reloads;
deleting a script or removing all scripts removes its state.

Manual ZIP backups include bounded state files; source scripts and state
are restored, but every script is **disabled with all consent revoked**.
Old backups without state remain compatible. Android Auto Backup
may transfer state files, but the no-backup approval marker revokes
execution permissions on a new device.

## APK update acceptance (same installed app, no data clearing)

A unit test exercises saved script metadata and C6 storage across app process
recreation; this is not a physical update. For a real update, use two
**Candidate** APKs signed with the same key and application ID:

1. On the older Candidate, import
   [upgrade_persistence_probe.lua](lua-examples/upgrade_persistence_probe.lua).
   Enable it with **Persistent script data**, run `/lua upgrade add` twice,
   then `/lua upgrade status`; note the persistent count.
2. Import another unrelated script but leave it disabled. Record both
   active/disabled states and network selections.
3. Install the newer Candidate **over the existing app** (do not uninstall,
   clear data, or restore a backup).
4. Confirm both enablement states and network selections are preserved.
   Run `/lua upgrade status`: the saved counter should still be present;
   its RAM-only session counter should start at zero. Run
   `/lua upgrade add` again to prove the persistent count increases.
5. Verify approved IRC networks still receive events and unapproved
   networks still do not.

This differs from restoring a backup or switching phones: a device transfer
**intentionally revokes all Lua consent**. Actual APK replacement on a
physical phone remains **non verificato** until tested.

## C7 — local output and selectable lists (experimental)

Within an event callback:

\`\`\`lua
tiarca.show("Result", "3 users found")
tiarca.show_list("Matching nicks", {"Alice", "Bob", "Charlie"})
\`\`\`

\`show\` accepts a nonempty title (up to 96 UTF-8 bytes) and text
(1–512 UTF-8 bytes); \`show_list\` accepts 1–16 plain-text
items, max 160 bytes each, with total serialized content capped at 512 bytes.
The app keeps **only the most recent result per script in memory**.
In **Settings → Lua scripts → View script result**, a message opens in
an Android dialog and a list opens as selectable rows. Tapping one row
copies the exact text to the clipboard. A \`/lua\` command's show/list
result also creates a **local-only notice on the server tab**; it is never
sent to IRC and must not be mistaken for a remote message. It does not
inject results into a channel or PVT transcript in this milestone.
Lua cannot open arbitrary activities, load web pages or define raw
Android widgets. Limits and isolation from earlier phases still apply.

A sample for manual import is included in
[Persistent storage and UI results](lua-examples/persistent_results.lua).
Test \`/lua c6 set\`, \`/lua c6 read\`, \`/lua c7 list\` and \`/lua c6 clear\`.
Repeat \`/lua c6 read\` after terminating/restarting TIARCA, then
test backup/restore and explicitly reapprove storage before running scripts.

**On-device verification is required** for persisted writes, quota failure,
backup and transfer consent, light/dark appearance, clipboard handling,
foreground/background transitions and absence of unwanted IRC output.


## Experimental Mask Finder (branch: feature/lua-mask-finder-20261112)

Import [mask_finder.lua](lua-examples/mask_finder.lua) through Settings → Lua
scripts; explicitly enable **Channel members and identities** for the selected
IRC network. The script needs neither send nor moderation permission.

While viewing a **joined channel**, enter:

```
/lua mask pippo
/lua mask *!*@*.example.net
```

A plain term matches anywhere within the complete `nick!ident@host` without
case sensitivity. Full IRC-style masks containing `!` or `@` use anchored
`*` (zero or more characters) and `?` (exactly one) wildcards; a short query
with wildcards searches anywhere in the full mask.

The command delegates to the host-only `tiarca.maskfind(channel, pattern)`
action. The host verifies the explicit local command, enabled script, separately
approved identity scope, same joined channel and matching server before sending
a single WHO/WHOX. The ordinary C3 128-user snapshot and the network's 512-entry
identity LRU **are not** the search limit: WHO replies are collected separately
(up to 16,384 users per request, then marked incomplete rather than silently
claiming a complete search). The search is read-only; it never sends a message
to the channel or privately to its users.

Once the server sends numeric **315 (end of WHO)**, the host shows the match
count in the **Server tab** and writes the first 50 matching
`nick!ident@host` rows. Nicknames are shown in theme-aware red and open the standard nickname context
menu, retaining the original channel as the context. When the server supports
WHOX and reports a logged-in account, the result appends `R:account` after
`nick!ident@host`; unregistered users and servers without WHOX show only the
mask. The account is taken from that search response, not a potentially stale
identity cache. For larger result sets, the
last row is followed by `Continua? [SI] [NO]`; SI adds the next 50 rows,
while NO keeps those already displayed and stops. No initial confirmation,
no paging, and no separate results window. Partial WHO rosters are explicitly
labelled **RICERCA INCOMPLETA** (including when a server returns fewer
identities than the locally known member count). Missing completion triggers
a timeout notice; it is never reported as zero matches.

The search runs only from a user-typed `/lua mask` command, not automatically
on incoming messages. Only one search per network is kept active at a time.
The host permits at least 5 seconds between WHO requests to reduce flood risk.
Result rows and continuation state are ephemeral, not backed up.

**Acceptance not yet verified manually:** real large-channel WHO/WHOX (854+
users), simultaneous external WHO operations, disconnected/reconnected IRC
sessions, case mapping of exotic nicknames, UI tap/long-press in light and dark
themes, Android backgrounding, and device memory/performance. Standard/F-Droid
CI and reproducibility need to pass on this branch before merge. There is
no version or release change.

## Phase D — integration and release-readiness checklist (2026-11-12)

Phase D is **consolidation**, not another Lua API surface or the earlier Gradle/AGP
upgrade phase. The stacked PR #260 and PR #258 were merged in order into
`main` (merge commits `41d1c586` and `c7edee0e`). No version, tag,
or release was created.

### Automated verification (including historical C6/C7 checks)

- CI (Standard and F-Droid debug build and existing unit checks):
  https://github.com/TIARCA/TIARCA/actions/runs/43173080044 — passed.
- Native Lua POC tests: https://github.com/TIARCA/TIARCA/actions/runs/43173080097 — passed.
- Standard/F-Droid APK reproducibility:
  https://github.com/TIARCA/TIARCA/actions/runs/43173080013 — passed.
- Source-based, pinned Lua 5.5.1, MIT license in `third_party/lua-5.5.1/LICENSE`,
  no new Android permissions, `isolatedProcess` not exported, no raw IRC API.
- User-reported manual tests: C6 write/read persists across TIARCA restart,
  and C7 local list is visible/selectable and copies a nickname to clipboard.
- Final pre-merge source head `24be8b67`: [Standard/F-Droid CI](https://github.com/TIARCA/TIARCA/actions/runs/43245561031),
  [native Lua](https://github.com/TIARCA/TIARCA/actions/runs/43245561174),
  and [reproducible builds](https://github.com/TIARCA/TIARCA/actions/runs/43245561015)
  all passed.
- A write-failure regression test ensures that removing/resetting Lua scripts
  does not desynchronize in-memory metadata from the persisted consent index.

### Remaining acceptance before a stable Lua release

- [x] User-confirmed manual ZIP backup/restore retained C6 data and revoked
  script approvals; re-enabling was required for access.
- [ ] Verify Android device-to-device transfer/Auto Backup with a second phone
  or clean test profile. State must survive where supported, approvals must not.
- [x] User-confirmed two-network probe: an event from the unapproved network
  did not increment the approved network's Lua event counter. Java regression
  tests also enforce separate roster/identity and action gates.
- [x] User-confirmed operator privilege regression: voice `+v` worked with
  OP and `-v` was blocked after losing OP.
- [ ] Exhaustive unsupported extban dialects, disconnection races and missing
  hostmasks still need additional on-device validation.
- [ ] Test reconnect, Android background/Doze, partial WHO/WHOX response,
  interrupted Lua process and memory/instruction limits on an actual device.
- [ ] Inspect local list/dialog contrast in light and dark theme and clipboard
  feedback with TalkBack.
- [ ] Review C6/C7 privacy expectations and Lua native code/security with an
  independent reviewer before promoting the experimental API.
- [x] Both stacked PRs were merged into `main` after explicit authorization.
  Stable version and release remained unchanged.

### Phase D implementation notes

- The live operator check uses the server's advertised PREFIX-to-mode
  mapping (no assumption that an '@' glyph always means operator), and
  nickname membership/self-protection uses ISUPPORT CASEMAPPING (RFC1459,
  strict RFC1459, or ASCII). This prevents unsafe operator actions where
  nicknames such as `Operator[` and `Operator{` denote the same user.
- Regression tests check lost operator status, disconnected sessions,
  absent targets, invalid IRC tokens, and network-specific policy scope.


- Filenames imported from Android SAF and read from ZIP metadata are now
  validated consistently; control characters, Unicode line separators and
  path separators cannot be inserted into Lua's local server-tab notices.
- Automated regression tests simulate the absent no-backup approval marker
  after an Android device transfer and assert persistent data survives while
  script execution and each privileged capability stay disabled.
- Automated green CI proves compilation and the tested paths, **not** device
  behavior, the completeness of an independent security audit or F-Droid
  publication approval.


### Phase D manual multi-network and operator tests

Use a new Candidate built from the latest phase D commit. The following tests
must be recorded separately from the automated policy tests.

**Two networks (non-destructive):**
1. Connect to two distinct configured IRC networks A and B at the same time.
   Ideally join a channel of the same name on both, with different members.
2. Import [phase D network probe](lua-examples/phase_d_network_probe.lua),
   enable it **only for A**, without any additional send/moderation permissions.
3. On A run `/lua d rete`: the local C7 result must show only A's UUID and
   channel names/counts (not B's). Run `/lua d eventi` and record the count.
4. With A quiet, have another user send a normal message on B; after processing,
   run `/lua d eventi` on A again. The count must be unchanged.
5. On B, the script must not execute or produce a local C7 result. If TIARCA
   keeps the unhandled `/lua` text in the composer, clear it rather than
   forwarding any command to IRC. Repeat after reconnecting B.
6. On A, send an ordinary message from a second nickname and confirm the
   counter changes **only for A**. Source and storage must not cross networks.

**Operator permissions (test channel, consenting target only):**
1. Grant the tester actual operator status on network A's test channel.
   Enable an existing C5 script there with **moderation consent**.
2. Request only a harmless `+v` against a consenting second nickname,
   e.g. `/lua prova voce TestNick`. Confirm the IRC server applied it.
3. Have another operator remove the tester's operator status. Attempt `-v`
   on that same nickname using `/lua prova devoice TestNick`. It **must not**
   be sent or applied. A Lua result saying a request was queued is not proof
   that the IRC server accepted an action.
4. Disconnect A, switch to another network or channel, and confirm that no
   old request executes against a different server or a new session. Repeat
   for a nickname that has left the channel and for the tester's own nickname.
5. On a separate test network, try `mute`/`tban` only when supported by
   server-advertised EXTBAN/TBAN. Unsupported syntax must not be sent.

The project owner confirmed the practical two-network and lost-OP tests on
2026-11-12. The broader test matrix above remains **non verificato** where
unchecked; Java unit tests and a green APK build cannot substitute for it.


## AutoMod — antiripetizione (script sperimentale)

Importa [automod_antiripetizione.lua](lua-examples/automod_antiripetizione.lua)
con **Impostazioni → Script Lua**. All'importazione è disabilitato: quando
lo abiliti devi autorizzare **Dati persistenti** (obbligatorio per AutoMod)
e, se vuoi verificare gli account e intervenire, i consensi separati
**Lettura dati utenti** e **Moderazione**. Senza il consenso allo storage
AutoMod non modifica la configurazione e non modera, mentre `/lua mod help`
rimane disponibile. Dopo l'attivazione su una o più reti IRC non controlla **nessun canale**:
serve `/lua mod canali add #nome` sulla rete desiderata. Anche dopo aver
scelto i canali, opera inizialmente **solo in monitoraggio**. Usa
`/lua mod stato` per leggere le impostazioni. I comandi si digitano in una
scheda della rete autorizzata e modificano le preferenze **di quella rete**:

- `/lua mod soglia 80`: similarità Dice delle parole (60–100%, ordine ignorato,
  occorrenze ripetute conteggiate); `/lua mod ultimi 3`: da 1 a 10 messaggi
  precedenti dello stesso nickname nello stesso canale;
  `/lua mod minuti 30`: scadenza da 1 a 120 minuti.
- `/lua mod minparole 3`: lunghezza minima (3–15 parole);
  `/lua mod pausa 10`: intervallo tra interventi sullo stesso utente
  (0–120 secondi); `/lua mod durata 10m`: durata ban temporaneo
  (1–99 m/h/d, solo server compatibili).
- `/lua mod azione monitor|avviso|kick|ban|mute|tban|bankick|tbankick|escalation`:
  seleziona la risposta. `bankick` e `tbankick` richiedono prima il kick
  e poi il ban, con motivazione kick `non ripetere`.
  Escalation entro la finestra temporale: avviso → kick → kick + ban
  temporaneo → kick + ban permanente. Le azioni sono richieste, non conferme di successo remoto.
- `/lua mod canali`: elenca i canali attualmente controllati sulla rete.
  `/lua mod canali add #nome` aggiunge il canale e
  `/lua mod canali drop #nome` lo rimuove, cancellandone le cronologie.
  La lista parte vuota su **ogni rete** e ammette fino a 16 canali per rete;
  il confronto dei nomi è insensibile alle maiuscole/minuscole mediante
  conversione `lower()` di Lua (non è un casemapping IRC completo).
  I canali si possono configurare anche prima di entrarvi.
- `/lua mod on`, `/lua mod off`, `/lua mod reset`: abilita/disabilita il
  monitoraggio o cancella lo storico della sola rete corrente. Anche con
  `on`, non si monitorano canali che non siano stati aggiunti.

### Whitelist e blacklist di AutoMod

**Per impostazione iniziale sono esentate tutte le categorie**:
`registrati`, `voice`, `halfop`, `op`, `admin`, `owner`.
La lista dei singoli account in whitelist e quella in blacklist iniziano vuote,
così come la lista dei canali monitorati. I nomi sono account autenticati,
**non nickname**: la configurazione resta associata all'account anche quando
un utente cambia nickname.

- `/lua mod whitelist`: elenca gli account esentati singolarmente e lo
  stato ON/OFF delle sei categorie.
- `/lua mod whitelist add nomeaccount` e `drop nomeaccount`: aggiunge o
  rimuove l'esenzione individuale.
- `/lua mod whitelist categoria registrati|voice|halfop|op|admin|owner on|off`:
  cambia l'esenzione della categoria per la sola rete corrente.
- `/lua mod blacklist`: mostra gli account sempre sottoposti al controllo;
  `/lua mod blacklist add nomeaccount` e `drop nomeaccount` li gestiscono.
  Un account in **blacklist prevale su qualsiasi whitelist**, compreso voice
  o registrati, anche se compare esplicitamente nella whitelist.
- `/lua mod help` presenta le categorie per blocchi separati;
  `/lua mod help liste`, `help canali`, `help azione`,
  `help parametri` aprono solo la relativa sezione.
- `/lua mod stato` comprende anche le quantità di account in whitelist e
  blacklist. Ogni lista supporta fino a 32 account, con validazione delle
  stringhe; i nomi dei canali fino a 16 per rete.

Il client richiede `account-tag`, `extended-join` e `account-notify`
quando disponibili e aggiorna la cache per gli utenti osservati. Per un
account ancora ignoto AutoMod richiede WHO/WHOX non più di una volta ogni
10 minuti per canale; TIARCA limita ulteriormente le richieste a una al
minuto per script/rete. Un account non verificabile rimane escluso dalle
azioni punitive. `/lua mod stato` mostra `ricevuti`, `esenti`,
`ignoti` e `monitorati` per rete; questi conteggi temporanei non
fanno parte del backup.

Il motore fornisce a Lua il roster e l'account osservato **soltanto se
l'utente autorizza anche «Lettura dati utenti»** per quello script/rete.
Per default un account confermato come registrato viene esentato.
Gli utenti con account **sconosciuto** (dato non ancora ricevuto dal
server) vengono ignorati in modo prudenziale, non interpretati come
«non registrati». Un logout esplicitamente osservato dal server viene
invece rappresentato come `*`. La lista di 128 membri fornita a Lua
include prioritariamente il mittente corrente anche nei canali più grandi.

Il motore Android rifiuta normalmente gli interventi automatici sui membri
con prefisso; un account espressamente in blacklist può superare quel filtro
**solo con permessi separati Lettura dati utenti + Moderazione**, su un
messaggio ricevuto dal medesimo nickname e dopo verifica dell'account
effettivamente osservato in quella rete. Rimangono obbligatori presenza in
canale, privilegi operatore, controllo anti-autokick, canale esatto,
dialetto e host validi. Il server può comunque negare le operazioni.

I rilevamenti si leggono dal log dello script. Gli avvisi richiedono
consenso **Invio messaggi**; kick/ban/mute richiedono consenso
**Moderazione** e privilegi operatore attuali. Liste, categorie e canali vengono conservati tramite dati persistenti
per script/rete, se autorizzati dall'utente. I permessi Lua restano
espliciti e non vengono concessi automaticamente.

La cronologia attiva (impronte dei messaggi, contatori di infrazioni e
ultima azione), insieme ai contatori diagnostici, è ora salvata per rete
tramite una seconda chiave C6, con timestamp originali. Al riavvio della
sandbox e alla riconnessione vengono mantenute solo le voci non scadute
secondo `minuti`; i contatori diagnostici restano cumulativi fino a reset.
Le **impostazioni** vengono salvate per ciascuna rete mediante
`tiarca.storage`: ON/OFF,
canali abilitati, whitelist e blacklist degli account, categorie esentate,
soglia, ultimi, minuti, minparole, pausa, durata, azione e tipo di avviso.
Sopravvivono al reload, a `/lua restart`, al riavvio dell'app e agli
aggiornamenti APK che conservano i dati dell'app. Ogni modifica viene
salvata dopo la convalida; se lo storage è indisponibile, la modifica
non viene applicata. Dati malformati non vengono sostituiti dai default:
AutoMod si astiene dall'intervenire finché la configurazione non viene
ripristinata. La pulizia dello storico scaduto avviene al messaggio
successivo e all'evento timer (~60 secondi).
I messaggi troppo brevi o con più di 32 parole/160 byte normalizzati sono
conteggiati nella finestra numerica ma non vengono confrontati (per evitare
falsi positivi da troncamento). I limiti complessivi sono **1000 nickname
attivi** e **2400 impronte** tra tutte le reti; se raggiunti vengono
eliminate le cronologie meno recenti, quindi in canali molto attivi il numero
effettivo di confronti può essere inferiore al limite scelto. Un'uscita dal canale cancella i messaggi recenti, ma non il contatore
infrazioni, finché la finestra di recidiva è valida. La disconnessione non cancella più le cronologie. Il comando esplicito
`/lua mod reset` le azzera insieme ai contatori. Non serve modificare il sorgente:
`/lua mod canali add|drop #nome` gestisce i canali della rete corrente.
L'elenco dei canali autorizzati viene conservato separatamente per rete:
il default resta **nessun canale**, ma non viene ripristinato dopo un
semplice riavvio. Lo stato persistente è incluso nel backup TIARCA,
conservando la configurazione ma **revocando sempre le autorizzazioni Lua**
durante il ripristino: l'utente deve approvare nuovamente rete, dati
persistenti e gli eventuali altri permessi. La disinstallazione con
cancellazione dati, `Elimina script` e `Elimina tutti gli script Lua`
eliminano anche le relative preferenze. Sostituire lo script importandolo
come **nuovo script** non migra automaticamente i dati del vecchio ID.

Da testare manualmente su una **Candidate**: somiglianza e falsi positivi in
canali reali, 1000 utenti, spegnimento/riavvio, privilegi e differenze IRCd,
scadenze Android e semantica ban/mute/tban. Nessun ban automatico è attivo
senza scelta esplicita dell'utente.

 
## Comandi integrati e convenzione help

`/lua list` elenca nella scheda Server solo gli script abilitati per la
rete IRC corrente. Funziona anche senza script attivi, perché è gestito
direttamente dal client, non da un callback Lua. `/lua help` mostra
i comandi generali. Nessuno dei due invia dati al server IRC.

Gli script TIARCA che espongono un comando devono implementare
`/lua <comando> help` con istruzioni ed esempi. Mask Finder:
`/lua mask help`. AutoMod: `/lua mod help` (anche `/lua mod`);
`/lua mod stato` mostra la configurazione.

## Invio privato e NOTICE controllati

`tiarca.pm(event.nick, testo)` e `tiarca.notice(event.nick, testo)`
sono permessi solo con il consenso **Invio messaggi**. Entrambi sono
limitati al **nick che ha generato il messaggio in canale** su una rete
autorizzata: confronto con casemapping IRC e verifica della presenza
in canale al momento dell'azione. Niente destinazioni arbitrarie,
messaggi da timer/comandi, invii cross-network o comandi RAW.

Si applicano i limiti di invio ordinari: 5 secondi per script/rete,
1,5 secondi tra invii Lua nella stessa rete. Invii eccedenti sono
scartati, non accodati. Testo massimo 400 byte UTF-8, senza CTCP/CRLF.

AutoMod: `/lua mod avviso pubblico|pvt|notice`; predefinito **pvt**. In pubblico: `nick: non ripetere`; in privato/NOTICE: `non ripetere su #canale`.
Le impostazioni AutoMod cambiate via comando sono persistenti con il
consenso **Dati persistenti**. Le funzioni sono disponibili nel branch
di sviluppo, non nella release 0.9.10.

 
## Console generale Lua (per rete)

I comandi di TIARCA sono disponibili anche con zero script abilitati:
- `/lua help`: guida ai comandi generali
- `/lua list`: elenco sintetico degli script abilitati sulla rete corrente
- `/lua status`: motore, script abilitati o disabilitati, errori e azioni di moderazione in attesa; la memoria effettiva è indicata come non disponibile
- `/lua log`: ultime 16 righe di log Lua per la rete, con nome dello script
- `/lua stop`: sospende eventi e azioni Lua per questa rete, senza modificare i permessi o lo stato RAM; annulla eventi già in corso e interventi di moderazione in coda
- `/lua start`: riattiva gli script sospesi, conservando il loro stato temporaneo
- `/lua restart`: sospende, ricrea le VM di questa sola rete usando i sorgenti già autorizzati e riprende solo se tutti i caricamenti riescono; le variabili solo RAM si azzerano, mentre gli stati espressamente salvati (come lo storico AutoMod non scaduto) vengono recuperati; le altre reti non vengono toccate

I comandi sono locali e non trasmessi al server IRC. Lo stop e il restart
non modificano le autorizzazioni persistenti. Dopo un errore di
restart la rete rimane sospesa e gli errori sono riportati nel log.
Una modifica nelle Impostazioni può continuare a provocare un reload
globale, come avveniva in precedenza.

Per minimizzare l'uso di RAM, le VM supplementari sono create solo al primo
restart della rete, non durante la connessione ordinaria. Ogni VM è
limitata a 2 MiB, con un limite complessivo di 32 MiB nel processo
isolato. Quando non è possibile allocare una nuova VM il restart non
viene considerato riuscito.

AutoMod: `/lua restart` ricrea la VM ma conserva configurazione e
storico ancora valido. `/lua mod reset` cancella lo storico e i
contatori per la rete corrente, senza cambiare le preferenze.

 
## Quote e sicurezza dei dati AutoMod

AutoMod usa due chiavi per rete: una per le impostazioni e una per
lo storico. Per sostenere fino a 16 reti, il numero massimo di chiavi
C6 per script sale a **32**, mantenendo 8192 byte per valore,
**128 KiB complessivi** e file JSON massimo di 160 KiB. I limiti
sono controllati nel processo Android e nel runtime nativo. Se la
cronologia di una rete supera 8192 byte, AutoMod registra
`storico persistente pieno; azioni sospese` e non emette nuove
sanzioni che non potrebbe conservare in modo affidabile: è necessario
ridurre il carico o rivedere la capacità. Il recupero è limitato alla
capacità effettiva del supporto di persistenza, e va testato su canali
molto popolati.

Il database C6 è privato all'app e compreso nei backup con le
convalide già previste; le autorizzazioni a usarlo sono separate e
revocate al ripristino. Una nuova installazione di AutoMod non
converte automaticamente impostazioni che in versioni precedenti
esistevano soltanto nella RAM. **Da verificare su una Candidate**:
riavvio app, `/lua restart`, backup/ripristino e due reti IRC attive,
oltre ai test di regressione host e CI.

## AutoMod: progressione ed espulsione prima del ban

Le sanzioni di AutoMod in modalità `escalation` sono:
1. avvertimento (pubblico, PVT o NOTICE, secondo la configurazione);
2. KICK con motivazione **`non ripetere`**;
3. KICK con motivazione **`non ripetere`**, poi ban temporaneo (default 10m);
4. KICK con motivazione **`non ripetere`**, poi ban permanente.

L'infrazione resta valida dopo JOIN/PART/QUIT e segue il cambio nick
quando la nuova chiave non è già occupata. Lo storico dei testi viene
invece eliminato quando un utente esce dal canale. Il contatore decade
alla scadenza della finestra configurata (30 minuti per default).
La disconnessione e il riavvio dello script non azzerano il contatore:
scade naturalmente, oppure viene cancellato con `/lua mod reset`.

**Nota importante sull'API:** fare prima `tiarca.kick()` e poi
`tiarca.ban()` come richieste separate non è affidabile, perché il
secondo comando potrebbe trovare il nick già uscito dal canale.
Ora sono disponibili `tiarca.kickban(channel,nick,reason)` e
`tiarca.kicktban(channel,nick,duration,reason)`: ognuna produce una
sola richiesta strutturata, verificata lato Android (rete e canale,
operatore, presenza, prefissi, host osservato, dialetto IRC).
Il codice convalida l'intero intervento **prima** di inviare il
KICK e poi il comando MODE/TBAN, conservando la maschera già rilevata.
Se manca la mask o il server non supporta il ban temporaneo,
la richiesta viene respinta senza inviare neppure il KICK.
Questa verifica non costituisce conferma di esecuzione da parte
del server IRC, che può comunque rifiutare comandi.

Queste due API sono ristrette alla moderazione, richiedono il consenso
esplicito e non consentono accesso a comandi IRC arbitrari.


## Nuovo limite sorgenti e API blacklist

Il limite degli script Lua importati, validato sia nel main process Java
sia nel processo Lua isolato in C/JNI, è **24 KiB (24576 byte UTF-8)**
invece di 16 KiB. Restano invariati il budget di istruzioni per evento,
i 2 MiB di heap per VM e il cap aggregato di 32 MiB.

La nuova API `tiarca.blacklist_action(channel,nick,account,azione,durata)`
è una richiesta strutturata e non permette di inviare comandi IRC raw.
Le azioni consentite sono `kick`, `ban`, `mute`, `tban`,
`bankick` e `tbankick`. Il client Android ricontrolla al momento
dell'invio l'account reale e l'origine dell'evento, senza affidarsi
al solo account indicato da Lua. Per un kick la motivazione è sempre
`non ripetere`; le azioni composte inviano KICK e poi MODE/TBAN solo
dopo aver preparato la mask. In assenza di un account riconosciuto non
esiste alcun override dei privilegi.
