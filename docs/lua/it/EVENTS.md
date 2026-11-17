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
