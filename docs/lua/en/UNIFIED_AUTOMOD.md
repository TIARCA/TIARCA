# Unified AutoMod — engines A + B

Script: [automod_unificato.lua](../../lua-examples/automod_unificato.lua).

**Disable the legacy A and B scripts** on networks where the unified script is enabled; otherwise duplicate moderation may occur. Import the new script through **Settings → Lua Scripts**, select the networks and grant persistent storage and member/identity reading. Sending and moderation permissions are required only for live actions.

Configure channels with `/lua mod canali add #channel`, then inspect `/lua mod stato`. Both detection engines are ON by default; **monitor** mode applies no warnings or sanctions. Only enable live sanctions after a Candidate test using `/lua mod modo live`.

Commands: `/lua mod on|off`, `motore a|b on|off`, `modo monitor|live`, `canali add|drop #channel`, `parametro soglia|occorrenze|ultimi|minuti|minparole N`, `stato`, `reset`, `close`. The supported `/lua b` commands remain aliases.

**A:** 75% word-multiset similarity across three messages, at least three words, over a 30-minute window. Engine A per-network settings are adjustable with `parametro` (defaults: threshold 75, occurrences 3, last 8, minutes 30, minimum words 3). **B:** two recognized advertisements within 20 minutes, possibly in different categories, and at least 2/3 advertisement concentration in the last ten eligible messages. Initial one-word micro patterns include `F?`, `donne?`, `mxm`, and `qualcuna?`; ambiguous single expressions such as `cerco` are not sufficient. Neutral greetings (e.g. `ciao a tutti`) do not trigger A or dilute B.

Shared escalation expires after 24h without violations: (1) private warning, (2) 2-minute timed mute, (3) 15-minute timed mute, (4) kick + 3-hour timed mute, (5) kick + 24-hour timed ban. Every kick has reason `Messaggi ripetuti`. Level five is capped: further violations do not issue repeated bans. Messages explaining sanctions go to PVT. Voice (+v) and registered users are included; halfops and higher are protected. A and B cannot advance escalation twice on one message.

`/lua b whitelist add NICK` must be run from a monitored channel with the user present and an observed authenticated account; `drop` and `list` are available. Entries are network/account scoped and persisted. Collector V3 gets a read-only view when both scripts are imported using the documented filenames.

`/lua mod close` opens an Android multi-select dialog across private tabs, preselecting only tabs known to be script-created, with a Select All option.

**Not verified:** live channel detection quality, supported account extbans on all networks, actual server enforcement, Android Candidate UI, complete migration and restoration. Script source and unit/CI tests do not replace manual verification.
