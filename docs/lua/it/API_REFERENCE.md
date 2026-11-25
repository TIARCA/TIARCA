# Riferimento API Lua

[Guida](README.md) · [English](../en/API_REFERENCE.md) · [Eventi](EVENTS.md) · [Permessi](PERMISSIONS.md)

**Solo API documentate e implementate.** Le chiamate sono soggette a evento, consenso e rete corrente. I risultati asincroni non sono disponibili immediatamente dopo una richiesta.

| API | Parametri e risultato / effetto | Requisiti |
|---|---|---|
| `tiarca.on(evento, callback)` | Registra callback per [evento supportato](EVENTS.md) | Nessuno |
| `tiarca.log(testo)` | Registra testo nel log locale dello script | Nessuno |
| `tiarca.send(destinazione, testo)` | Richiede invio nella stessa conversazione dell'evento | Invio; limiti temporali |
| `tiarca.pm(nick, testo)` | Richiede PVT al mittente ammesso di un messaggio in canale | Invio; evento compatibile |
| `tiarca.close_pms()` | Chiude PVT dello script, non manuali | Comando locale `mod close`; selezione multipla non inclusa #303 |
| `tiarca.notice(nick, testo)` | Come `pm`, tramite NOTICE | Invio; evento compatibile |
| `tiarca.identity_v2()` | Opt-in per VM; solo inizializzazione | Nessuno |
| `tiarca.get_channels()` / `tiarca.get_channels(token)` | Legacy snapshot max 32; v2 risposta identity | Legacy callback; v2 comando locale |
| `tiarca.get_user(canale, nick, token)` | V2 lookup puntuale asincrono | Identità; comando locale o evento IRC stesso canale |
| `tiarca.get_users(canale)` / `tiarca.get_users(canale, opzioni)` | Legacy max 128; v2 filtrato e paginato | Identità; legacy canale corrente, v2 comando locale o continuazione |
| `tiarca.refresh_users(canale)` | Richiede WHO/WHOX asincrono; massimo una richiesta/minuto per script | Identità; comando locale |
| `tiarca.maskfind(canale, pattern)` | Ricerca WHO/WHOX strutturata, mostra risultati locali; non restituisce subito le corrispondenze | Identità; comando esplicito |
| `tiarca.show(titolo, testo)` | Risultato locale: titolo 1–96 byte, testo 1–512 byte | Dentro callback |
| `tiarca.show_list(titolo, elementi)` | Lista locale di 1–16 stringhe, massimo 160 byte ciascuna e 512 byte totali | Dentro callback |
| `tiarca.storage.get(chiave)` | Stringa persistente o `nil` | Dati persistenti |
| `tiarca.storage.set(chiave, valore)` | Richiede salvataggio stringa UTF-8 | Dati persistenti |
| `tiarca.storage.delete(chiave)` | Richiede cancellazione | Dati persistenti |
| `tiarca.journal.append(json)` | Aggiunge una riga JSONL privata dello script alla rete dell'evento, senza limite circolare | Dati persistenti; callback |
| `tiarca.journal.stats()` | Byte del file e contatori limitati di scritture/errori via `journal_status` | Dati persistenti; comando locale |
| `tiarca.journal.export()` | Richiede selettore Android per il JSONL della rete corrente | Dati persistenti; comando locale |
| `tiarca.journal.rotate()` | Archivia senza distruggere il JSONL attivo e richiede la condivisione Android; le nuove righe partono da un registro vuoto | Dati persistenti; comando locale |
| `tiarca.journal.export_archive()` | Riesporta l'ultimo archivio privato conservato dopo la rotazione | Dati persistenti; comando locale |
| `tiarca.last_message(canale, nick, token)` | Cerca fino a 200 messaggi pubblici SQLite e invia evento `history` solo allo script richiedente | Dati persistenti + Identità; dopo evento `kick`, `ban`, `moderation_log`; stesso canale |
| `tiarca.kick(canale, nick, motivo)` | Richiede KICK | Moderazione; OP |
| `tiarca.eject(canale, nick, motivo)` | Espulsione manuale: due `TBAN` da `6h` limitati all'host ridotto (`N:*!*@host` per bloccare i cambi nick, `m:U:*!*@host` per il mute), eventuale `-v`, KICK e ban senza scadenza `R:account` se osservato, `nick!*@*` e `account!*@*` se diverso. Non imposta né rimuove il `+N` globale del canale. Richiede extban InspIRCd `N`, `m`, `U` (e `R` per account) e un host osservato | Identità e Moderazione; OP; solo comando locale |
| `tiarca.ban(canale, nick)` | Richiede ban host osservato | Moderazione; OP |
| `tiarca.mode(canale, nick, modo)` | Solo `+o`, `-o`, `+v`, `-v` | Moderazione; OP |
| `tiarca.mute(canale, nick)` | Richiede mute con dialetto IRC supportato | Moderazione; OP |
| `tiarca.tban(canale, nick, durata)` | Richiede ban temporaneo (es. `"3h"`) | Moderazione; OP; dialetto supportato |
| `tiarca.tmute(canale, nick, durata)` | Mute temporaneo lato server; V4 usa account R: osservato se supportato, altrimenti host esatto; legacy host | Moderazione; OP; dialetto supportato |
| `tiarca.kickban(canale, nick, motivo)` | KICK + ban come unica richiesta convalidata | Moderazione; OP |
| `tiarca.kicktban(canale, nick, durata, motivo)` | KICK + ban temporaneo convalidati insieme | Moderazione; OP |
| `tiarca.blacklist_action(canale, nick, account, azione, durata)` | Azioni `kick`, `ban`, `mute`, `tban`, `bankick`, `tbankick`; account verificato dal client | Moderazione e identità; vincoli aggiuntivi |

| `tiarca.override_action(canale, nick, account, azione, durata)` | Alias di blacklist_action | Stessi requisiti |

### Campi delle identità

`get_users`, `event.sender` e risposte identity espongono `nick`, `prefix` e, **solo se osservati**, `ident`, `host`, `account`, `mask`. `account_known` distingue ignoto (`nil`) da logout (`"*"`). I dati assenti valgono `nil`; un host può essere cloaked. Non costruire ban da host sconosciuti.

### Persistenza

Chiavi di 1–48 caratteri ASCII (lettere, numeri, `_`, `.`, `-`); valori UTF-8 da 1 a 8192 byte. La persistenza è condivisa tra le reti abilitate per **lo stesso script**. Max 32 chiavi, 128 KiB totali, JSON max 160 KiB. Eventi serializzati per script con storage anche multirete; commit atomico prima degli effetti, nessuna scrittura se invariato. Consulta [limiti e sicurezza](PERMISSIONS.md).

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

V4 privilegia account osservato anche per ban temporanei se supportato. Gate voice V4 limitato a `automod_v4.lua`, mittente e consensi; halfop e superiori protetti nella moderazione ordinaria V4. [Migrazione](MIGRATION.md) per scope, token, filtri e limiti.
