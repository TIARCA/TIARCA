# Collector V3 e Micro Discovery

Script indipendente: [moderation_collector_v3.lua](../../lua-examples/moderation_collector_v3.lua). Sostituisce la precedente V2, non va eseguito contemporaneamente a essa sulla stessa rete. **Non** invia messaggi e **non** modera.

Conserva le funzioni V2: eventi IRC KICK/BAN, report AutoMod, storico ultimo messaggio, contesto RAM, ripetizioni candidate da due parole, journal JSONL incrementale ed export su comando.

## Micro Discovery (opt-in)

- `/lua collector micro on`: attiva la registrazione sperimentale **per 24 ore** sulla rete corrente.
- `/lua collector micro status`: mostra se la modalità è attiva.
- `/lua collector micro off`: disattiva prima della scadenza.
- `/lua collector export`: condivide un'istantanea del JSONL.

Vengono registrati i messaggi **NORMAL** con 1–3 parole dopo normalizzazione, anche `ciao`, `ciao a tutti`, `F?`, `mxm`. I messaggi composti **solo da numeri e separatori** vengono raccolti indipendentemente dal numero di parole (`07/08/09/010`, o 50 numeri). Il testo originale è conservato, con un limite di sicurezza di 2048 byte per messaggio.

Ogni riga micro contiene `event: "micro"`, `micro_text`, `micro_normalized`, `micro_words`, `micro_type`, oltre alle informazioni standard su data/rete/canale/identità disponibili. È un archivio di messaggi reali: **trattare l'export come sensibile**, raccogliere solo su canali ove è consentito e limitarne la condivisione. Le trascrizioni micro sono persistenti nel journal fino a rotazione manuale; l'opzione micro scade, non cancella automaticamente dati già salvati.

Il Collector usa una copia di sola lettura della whitelist del nuovo AutoMod per escludere i relativi account da micro-candidati e ripetizioni sperimentali; continua a registrare eventi IRC di moderazione realmente osservati.

L'analisi a fine giornata serve a individuare nuovi micro-annunci e misurare i falsi positivi. **I risultati non sono ancora verificati.**
