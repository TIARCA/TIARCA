# Riferimento API Lua

[Guida](README.md) · [English](../en/API_REFERENCE.md) · [Eventi](EVENTS.md) · [Permessi](PERMISSIONS.md)

**Solo API documentate e implementate.** Le chiamate sono soggette a evento, consenso e rete corrente. I risultati asincroni non sono disponibili immediatamente dopo una richiesta.

| API | Parametri e risultato / effetto | Requisiti |
|---|---|---|
| `tiarca.on(evento, callback)` | Registra callback per [evento supportato](EVENTS.md) | Nessuno |
| `tiarca.log(testo)` | Registra testo nel log locale dello script | Nessuno |
| `tiarca.send(destinazione, testo)` | Richiede invio nella stessa conversazione dell'evento | Invio; limiti temporali |
| `tiarca.pm(nick, testo)` | Richiede PVT al mittente ammesso di un messaggio in canale | Invio; evento compatibile |
| `tiarca.notice(nick, testo)` | Come `pm`, tramite NOTICE | Invio; evento compatibile |
| `tiarca.get_channels()` | Array `{name, users}`; `.truncated` se oltre 32 canali | Dentro callback |
| `tiarca.get_users(canale)` | Array di membri osservati; `.truncated` oltre 128; campi mancanti `nil` | Identità; canale corrente |
| `tiarca.refresh_users(canale)` | Richiede WHO/WHOX asincrono; massimo una richiesta/minuto per script | Identità; comando locale |
| `tiarca.maskfind(canale, pattern)` | Ricerca WHO/WHOX strutturata, mostra risultati locali; non restituisce subito le corrispondenze | Identità; comando esplicito |
| `tiarca.show(titolo, testo)` | Risultato locale: titolo 1–96 byte, testo 1–512 byte | Dentro callback |
| `tiarca.show_list(titolo, elementi)` | Lista locale di 1–16 stringhe, massimo 160 byte ciascuna e 512 byte totali | Dentro callback |
| `tiarca.storage.get(chiave)` | Stringa persistente o `nil` | Dati persistenti |
| `tiarca.storage.set(chiave, valore)` | Richiede salvataggio stringa UTF-8 | Dati persistenti |
| `tiarca.storage.delete(chiave)` | Richiede cancellazione | Dati persistenti |
| `tiarca.kick(canale, nick, motivo)` | Richiede KICK | Moderazione; OP |
| `tiarca.ban(canale, nick)` | Richiede ban host osservato | Moderazione; OP |
| `tiarca.mode(canale, nick, modo)` | Solo `+o`, `-o`, `+v`, `-v` | Moderazione; OP |
| `tiarca.mute(canale, nick)` | Richiede mute con dialetto IRC supportato | Moderazione; OP |
| `tiarca.tban(canale, nick, durata)` | Richiede ban temporaneo (es. `"3h"`) | Moderazione; OP; dialetto supportato |
| `tiarca.kickban(canale, nick, motivo)` | KICK + ban come unica richiesta convalidata | Moderazione; OP |
| `tiarca.kicktban(canale, nick, durata, motivo)` | KICK + ban temporaneo convalidati insieme | Moderazione; OP |
| `tiarca.blacklist_action(canale, nick, account, azione, durata)` | Azioni `kick`, `ban`, `mute`, `tban`, `bankick`, `tbankick`; account verificato dal client | Moderazione e identità; vincoli aggiuntivi |

### Campi delle identità

`get_users` espone `nick`, `prefix` e, **solo se osservati**, `ident`, `host`, `account`, `mask`. I dati assenti valgono `nil`; un host può essere cloaked. Non costruire ban da host sconosciuti.

### Persistenza

Chiavi di 1–48 caratteri ASCII (lettere, numeri, `_`, `.`, `-`); valori UTF-8 da 1 a 8192 byte. La persistenza è condivisa tra le reti abilitate per **lo stesso script**. Letture e scritture sono consentite nel callback; tra eventi concorrenti possono esistere snapshot temporaneamente precedenti. Consulta [limiti e sicurezza](PERMISSIONS.md).

### Esempio: comando locale

```lua
tiarca.on("command", function(event)
  if event.text == "ping" then
    tiarca.show("TIARCA", "pong")
  end
end)
```

Digita `/lua ping`. Non viene inviato alcun messaggio IRC.

**Nota:** l'API non promette conferme di consegna/ban. Per semantica, quote e condizioni complete consulta [la fonte tecnica](../../LUA_SCRIPTING_MVP.md); l'implementazione Java/C è la fonte definitiva.
