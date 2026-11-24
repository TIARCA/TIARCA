# TIARCA — Lua redesign / piano di revisione

Decisione del 24 novembre 2026. Obiettivo: trasferire solo i dati necessari, mantenendo isolamento, permessi, limiti e controlli live prima delle moderazioni. Nessun avanzamento versione o release. PR #302 separata; Collector da riscrivere da zero come Harvester, non condiziona il redesign.

| Fase | Attività | Stato |
| --- | --- | --- |
| A | Cache identità IRC: membri condivisi, JOIN/NAMES/PRIVMSG/NICK/ACCOUNT/CHGHOST/PART/KICK/QUIT/disconnessione; limiti espliciti e identità ignote | In corso |
| B | Dispatcher: consegna solo eventi sottoscritti; evitare snapshot inutili, senza bloccare IRC/UI | Pianificata |
| C | API: event.sender acquisito al ricevimento; get_user puntuale; get_users filtrato prima della paginazione e asincrono, con indicazione completezza; get_channels su richiesta | Pianificata |
| D | Storage in memoria, aggiornamenti atomici solo al cambiamento, sincronizzazione/versioni tra processi, backup e ripristino; commit prima delle sanzioni | Pianificata |
| E | Adeguare AutoMod V4, mantenere crediti e logica; correggere integrazione voice; Mask Finder ed Espelli invariati funzionalmente | Pianificata |
| F | Diagnostica motore (eventi ricevuti/elaborati/scartati, latenza, memoria, scritture, richieste roster) e stress test 1000 membri/500 attivi, 5/10/25/50 msg/s; statistiche AutoMod separate da valutare dopo | Pianificata |
| G | Guida scripting IT/EN, contratto API e compatibilità script terzi | Pianificata |
| H | CI, reproducibilità, Candidate firmata e test reali; nessun merge/release automatico | Pianificata |

## Vincoli
- Non aumentare semplicemente lo snapshot di 128 utenti; il mittente non richiede un roster.
- Mask Finder usa WHO/WHOX dedicato; Espelli usa eject. Non sostituirli con scansioni Lua.
- Dati server osservati, distinguere account ignoto da logout; nessun WHO per messaggio.
- Cache delimitata, pulizia dopo uscita dall'ultimo canale condiviso e disconnessione; evitare account obsoleti.
- Conservare i permessi attuali, il sandbox Lua e le quote. Harvester sarà progettato separatamente.
- Le API proposte non sono ancora disponibili. Le misurazioni reali devono precedere conclusioni sulle prestazioni.
