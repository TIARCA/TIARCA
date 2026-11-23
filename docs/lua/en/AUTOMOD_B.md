# AutoMod B — repetitive announcement detection

Independent Lua script: [automod_annunci.lua](../../lua-examples/automod_annunci.lua). It does not replace the general AutoMod repetition detector. Import it in **Settings → Lua scripts**, granting persistent storage and channel-member identity access, plus send/moderation consent when real actions are intended.

## Commands

From the selected network's server tab:

- `/lua b help`, `/lua b stato` (status), `/lua b libreria` (rules)
- `/lua b canali add #channel`, `/lua b canali drop #channel`
- `/lua b on`, `/lua b off`
- `/lua b modo monitor` (shadow) and **`/lua b modo live`** (real actions)
- `/lua b reset` (start a new detection epoch, keeping configuration)

**Defaults:** OFF, no channels, monitor-only. Changing modes resets the effective infraction history on the next observation, so a test history does not instantly cause a permanent ban.

## Detection and escalation

Two recognized announcements within **20 minutes** trigger evaluation, **even when they match different B rules**, including messages as short as **two words**. The script then uses the user's **last 10 observed normal messages**, including the triggering one, to calculate announcement concentration. At least **2/3** must be recognized, with no floating-point rounding. After the first infraction each further recognized message advances escalation if the concentration still qualifies, without requiring another pair.

Escalation state is shared across B rule categories and enabled channels on the same network for **24 hours from the latest infraction**.

| Level | Action |
|---|---|
| 1 | Private warning: "non ripetere su #canale" |
| 2 | Server-managed timed mute, 2 minutes, plus private explanation |
| 3 | Kick: "non ripetere su #canale, rischi il ban" |
| 4 | Server-managed 3-hour timed mute with `m:*!*@host` on InspIRCd |
| 5 | Atomic kick+permanent host ban; kick reason "non ripetere" |

The app does **not** schedule unmute timers. New structured `tiarca.tmute(channel,nick,duration)` requests are validated against the live IRCd dialect, privileges, current membership, host and consent; unsupported networks reject the action. The observed/requested action does not prove IRC server confirmation.

Rules B01–B10 and B14–B16 are live-eligible; B11–B13 remain observation-only. Each message counts once even if it matches several rules. Account, exact host and distinctive ident are used as identity evidence, with account-conflict and concurrent-session guards; nickname similarity alone never joins identities. Prefixed channel members are exempt.

Only bounded identity aliases, ten classification markers and timestamps are stored, not a full text archive. The script is limited to 360 current identities and 12 persistent shards of up to 8 KiB each; moderation fails closed when reliable identity or persistence is unavailable. Detection quality and false positives remain **unverified** until a Candidate is tested on a channel with consenting participants.

Before enabling live actions, check nickname changes, reconnections, shared-host collisions, the 2/3 gate, expiry, app restarts, denied permissions, each escalation stage, and the server's actual `TBAN` response. If AutoMod A also runs, plan for possible overlapping moderation.
