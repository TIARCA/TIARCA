# Migrazione identity v2

[Guida](README.md) · [English](../en/MIGRATION.md) · [API](API_REFERENCE.md) · [Eventi](EVENTS.md)

Redesign PR #303, non disponibile nell'APK stabile v0.9.10. Verificare la build.

## Compatibilità e mittente

Legacy: snapshot sincroni fino a 128 membri del canale corrente e 32 canali. L'opt-in `tiarca.identity_v2()` al caricamento cambia entrambe le API snapshot in asincrone, per VM. Non aggiungerlo senza adattare le chiamate. Nessun nuovo permesso, ma Identità resta necessario per sender e membri. V2 non riceve roster automatici. Usare sender osservato, non una query a ogni messaggio.

```lua
tiarca.identity_v2()
tiarca.on("message", function(e)
  local u = e.sender
  if not u then return end
  if u.account_known and u.account ~= "*" then
    tiarca.log("Account: " .. u.account)
  end
end)
```

Sender può mancare; `account_known=false` e account nil indicano ignoto, non logout; `"*"` indica logout osservato. Non indovinare identità. I messaggi live conservano i dati osservati alla ricezione.

## Lookup puntuale

```lua
tiarca.identity_v2()
local seq, pending = 0, {}
tiarca.on("command", function(e)
  local nick = e.text:match("^lookup (%S+)$")
  if not nick or e.channel == "" then return end
  seq = seq + 1
  local token = "lookup-" .. seq
  tiarca.get_user(e.channel, nick, token)
  pending[e.server] = token
end)
tiarca.on("identity", function(e)
  if e.query ~= "user" or pending[e.server] ~= e.token then return end
  pending[e.server] = nil
  if e.status ~= "ok" or not e.user then
    tiarca.show("Lookup", e.status)
    return
  end
  tiarca.show("Lookup", e.user.nick .. " " .. (e.user.host or "?"))
end)
```

Digita `/lua lookup NICK` nel canale. Dalla scheda server si può indicare un canale condiviso; da una scheda canale non si può interrogare un altro. Lookup anche da eventi message/join/part/quit/nick/kick/ban dello stesso canale. Risposta separata, non conserva autorità di moderazione originaria. Rifiuti o code piene possono non dare risposta: token univoci, stato pendente delimitato, timeout/annullamento nei programmi reali.

## Roster filtrato e paginato

```lua
tiarca.identity_v2()
local seq, pending = 0, {}
tiarca.on("command", function(e)
  if e.text ~= "voice list" or e.channel == "" then return end
  seq = seq + 1
  local token = "voice-" .. seq
  tiarca.get_users(e.channel, {token=token, prefix="+", limit=100})
  pending[e.server] = token
end)
tiarca.on("identity", function(e)
  if e.query ~= "users" or pending[e.server] ~= e.token then return end
  if e.status ~= "ok" or not e.complete then
    pending[e.server] = nil
    tiarca.show("Voice", "Incomplete / unavailable")
    return
  end
  tiarca.log("Observed matches: " .. e.total)
  -- Process e.users locally.
  if e.next_offset then
    tiarca.get_users(e.channel, {token=e.token, prefix="+", limit=100,
                                 offset=e.next_offset})
  else
    pending[e.server] = nil
  end
end)
```

Filtri combinabili sull'intero roster locale osservabile (max 65.536 membri scanditi): `nick` secondo CASEMAPPING IRC, `ident` esatto, `host` e `account` esatti senza distinzione maiuscole, `prefix` contiene stringa, `auth` = registered/logged_out/unknown. Nessun wildcard. Token 1–64 byte, offset iniziale 0, limit 1–128 (default 100). Una query per evento, callback identity obbligatoria; bulk inizia da comando locale e continua con stessi token, canale, filtri, limite e next_offset, max 256 pagine. Dati live, non fotografia immutabile; `complete` non prova WHO completa e può essere true con next_offset. `tiarca.get_channels(token)` da comando locale: risposta identity con channels, total, complete, max 32 canali senza paginazione. Nessuna query v2 avvia WHO; Mask Finder mantiene WHO/WHOX dedicato.

## Storage e sicurezza

Privato per script, condiviso tra reti; separare le chiavi per rete se necessario. Eventi con storage serializzati per script anche multirete; scritture speculative leggibili nella callback e commesse insieme prima degli effetti, nessuna scrittura se invariato. Errore o revisione obsoleta blocca effetti e disabilita script. Commit non garantisce consegna IRC. Backup conserva dati ammessi ma revoca consensi; RAM non sopravvive al restart.

## Diagnostica e test

`/lua perf`: contatori globali dall'avvio, non statistiche AutoMod. Ingressi e consegne sono unità diverse; annullamenti e rifiuti non sono una somma esclusiva di perdite. Coda ingresso e slot 32 ciascuno. Latenze media/max: coda, IPC (include attesa sandbox), JNI, storage e rilascio effetti (non conferma IRC). Memoria: ultimo campione e massimo campionato allocatore Lua, non RSS. Nessun reset o salvataggio su disco.

Verificare legacy e v2, identità ignote/logout, roster oltre 128, risposte mancanti e pagine incomplete, due reti, restart, consensi, quote, backup. Non attivare più AutoMod insieme. Test host non sostituiscono Android. [Piano e benchmark](../REDESIGN_PLAN.md).
