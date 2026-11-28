# Autorizzazioni e sicurezza

[Guida](README.md) · [English](../en/PERMISSIONS.md)

Gli script importati sono **disabilitati**. L'utente sceglie esplicitamente le reti e abilita separatamente i permessi opzionali. Il consenso non è implicito nell'importazione.

| Capacità | Consente | Esempi |
|---|---|---|
| Nessuna aggiuntiva | Eventi, log, riepilogo canali e output locale | `tiarca.on`, `log`, `get_channels`, `show` |
| Invio messaggi | Risposta nella conversazione consentita; PM/NOTICE solo al mittente dell'evento ammesso | `send`, `pm`, `notice` |
| Membri e identità | Snapshot membri, WHO autorizzato, ricerca mask | `event.sender`, `get_user`, `get_users`, `refresh_users`, `maskfind` |
| Moderazione | Azioni strutturate solo con privilegi operatore e controlli host | `kick`, `ban`, `mute`, `tban`, `kickban`, `eject` |
| Dati persistenti | Testo salvato privatamente per script | `storage.get/set/delete` |
| Archivio JSONL Lua | Scrittura incrementale per script/rete, export esplicito; lettura ultimo messaggio storico richiede anche Identità | `journal.append/export/rotate/export_archive`, `last_message` |

Limiti fondamentali: massimo **8 script**, **40.960 byte UTF-8** per sorgente, fino a **16 reti** selezionate per script; VM da **2 MiB**, massimo **200.000 istruzioni** per caricamento/evento, **8 callback** e **12 azioni** per evento. I messaggi in uscita sono limitati e possono essere scartati; le azioni di moderazione sono richieste, **non conferme di esecuzione IRC**.

Non sono disponibili `io`, `os`, `package`, `debug`, `require`, `load`, `loadfile`, `dofile`, `string.dump`, `print` o `collectgarbage`. Niente file arbitrari, socket, Java/Android o IRC RAW. Le API controllano rete, canale, consenso e dati osservati.

Il backup conserva gli script e i dati ammessi ma **revoca le autorizzazioni** al ripristino; l'utente deve approvarle di nuovo. Per quote dettagliate e casi limite consulta [documentazione tecnica](../../LUA_SCRIPTING_MVP.md).

Opt-in v2 non concede permessi. Identità obbligatorio per sender e lookup. Allocazioni Lua aggregate 32 MiB; code 32 ingressi e 32 slot. Storage 32 chiavi, 8192 byte/valore, 128 KiB totali, JSON max 160 KiB. Effetti rilasciati dopo commit riuscito. [Migrazione](MIGRATION.md).
