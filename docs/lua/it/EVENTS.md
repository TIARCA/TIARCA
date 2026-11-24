# Eventi Lua

[Guida](README.md) · [English](../en/EVENTS.md)

Registra gli eventi con `tiarca.on("nome", function(event) ... end)`. Il callback riceve una tabella; **non assumere** che ogni campo sia valorizzato.

| Evento | Quando arriva | Note |
|---|---|---|
| `connected` | Connessione IRC attiva | Nessun canale richiesto |
| `disconnected` | Disconnessione | Non è possibile moderare |
| `message` | Messaggio in entrata normalizzato | Testo NORMAL, NOTICE o ME; non i propri messaggi |
| `command` | Comando `/lua ...` digitato localmente | `event.text` è la parte dopo `/lua` |
| `join` | Utente entra in canale | Evento passivo |
| `part` | Utente lascia un canale | Evento passivo |
| `quit` | Utente esce dalla rete | Possibili callback per più canali |
| `nick` | Cambio nickname | `event.nick` vecchio nick, `event.text` nuovo |
| `kick` | KICK osservato dal server | `event.nick` esecutore, `event.message_type` nickname espulso, `event.text` motivazione |
| `history` | Risposta asincrona a `tiarca.last_message` | `event.nick` destinatario, `event.text` ultimo messaggio oppure vuoto, `event.message_type` token di richiesta, `event.timestamp` orario messaggio o 0; solo allo script richiedente; `event.ident`/`event.host` quando memorizzati nel messaggio SQLite |
| `moderation_log` | Rapporto di moderazione riconosciuto da un altro script | `event.nick` utente, `event.text` rapporto, `event.message_type` nome dello script sorgente; non prova l'esecuzione IRC |
| `ban` | MODE +b osservato | `event.nick` esecutore, `event.text` mask; non implica espulsione |
| `identity` | Risposta asincrona v2 | Solo richiedente: token, query (user/users/channels), status (ok/not_observed/unavailable), total, complete, next_offset se presente, user/users o channels |
| `timer` | Tick approssimativo di 60 secondi | Solo quando servizio IRC attivo e rete connessa; non è un allarme preciso |

Campi comuni: `type`, `server` (UUID configurazione server, **non** hostname), `channel`, `nick`, `text`, `message_type` (`normal`, `notice`, `me`), `timestamp` (secondi Unix). I campi dipendono dall'evento e possono essere vuoti.

```lua
tiarca.on("message", function(event)
  if event.channel == "#example" and event.nick then
    tiarca.log(event.nick .. ": " .. (event.text or ""))
  end
end)
```

Gli eventi possono essere persi in caso di code piene, riavvio o sospensione Android. Non usare il timer per scadenze esatte. Fonte tecnica: [LUA_SCRIPTING_MVP.md](../../LUA_SCRIPTING_MVP.md).

V2: `event.sender` può mancare; dati osservati e autorizzati, per messaggi live alla ricezione. Consegna secondo sottoscrizioni. `identity` non è un nuovo comando utente. [Migrazione](MIGRATION.md).
