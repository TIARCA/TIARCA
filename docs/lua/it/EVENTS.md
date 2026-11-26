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
| `mode` | Variazione mode del canale | `message_type` mode con segno e tipo (es. `+b:LIST`); `text` parametro, vuoto per chiavi e valori exact-unset; scegliere mode o ban legacy per evitare duplicati |
| `topic` | TOPIC osservato con mittente | `text` nuovo topic |
| `own_message` | Messaggio pubblico del proprio utente | Separato da `message`, non attiva callback legacy; non prova consegna |
| `account` | ACCOUNT IRCv3 osservato | Consenso identità; `sender.account` nuovo account o `*`; solo canali condivisi |
| `chghost` | CHGHOST IRCv3 osservato | Consenso identità; `sender.ident/host` nuovi valori; solo canali condivisi |
| `journal_status` | Statistiche archivio o errore scrittura | Consenso storage; `message_type` stats/error/sandbox_gap; sandbox_gap in `text`: durata ms, ingressi globali non disponibili, slot globali annullati (best effort dopo il recupero); stats in `text`: byte, scritture, errori, contatori globali input-full, slot-full, IPC-failed; errore `append_failed`; append vietato nel callback |
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

`source_id` identifica la stessa osservazione in `message`, `own_message` e nel report di moderazione derivato: non è un msgid IRC né conferma della sanzione. `sender.realname` è disponibile solo se osservato e con consenso identità; ignoto = nil.

Roster v2: `users[].realname` o `user.realname` quando osservato. Con `state=true`, la prima pagina può includere `channel_modes` con voci `{mode,param,kind}` e `channel_modes_partial=true`. Nessuna garanzia di lista IRC completa.
