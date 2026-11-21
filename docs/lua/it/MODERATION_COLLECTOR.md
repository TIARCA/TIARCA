# Moderation Collector V2 — script Lua autonomo

[Guida Lua](README.md) · [English](../en/MODERATION_COLLECTOR.md) · [Codice Lua](../../lua-examples/moderation_collector.lua)

**Disponibilità:** PR [#289](https://github.com/TIARCA/TIARCA/pull/289), non nella release stabile v0.9.10. Prova Android reale: **non verificato**.

## Che cosa registra

È `moderation_collector.lua` a scegliere gli eventi, interrogare lo storico e comporre le righe del file: **nessuna raccolta o correlazione Collector viene eseguita da TIARCA quando lo script è spento**.

- Tutte le decisioni AutoMod riconosciute, anche se lo script è stato importato con un nome diverso (per esempio `automod.lua`): **monitor**, **avviso**, **kick**, **ban**, **mute**, **tban**, **bankick**, **tbankick**. Sono classificate come *richieste*, non successi confermati.
- KICK comunicati dal server, anche di altri operatori/bot: **esecutore**, **nickname espulso**, **motivo** quando presente.
- MODE +b comunicati dal server: **esecutore**, **mask**. La mask non trasmette una motivazione IRC; se individua esattamente un nick si associa quel nick, altrimenti si salva l'evento senza inventare un destinatario.
- **Ultimo messaggio pubblico** di quel nick presente fra gli **ultimi 200 messaggi SQLite del canale**, quando recuperabile. La ricerca viene richiesta *dallo script solo dopo un evento di moderazione* e vale anche per tutti gli eventi AutoMod.

Per l'ultimo messaggio vengono preferiti ident e host presenti nel record SQLite, se disponibili. Per ogni evento e ultimo messaggio si riportano **canale, timestamp, nick, ident, host, account se osservati**, altrimenti `null`. Per KICK/BAN anche operatore, mask/motivo ove comunicati. Il messaggio è contesto, non prova del motivo del provvedimento. In presenza di mask generiche ambigue non si inventa un nickname.

## Collector V2: contesto e simulazione antiripetizione

La **V2 resta uno script Lua esterno**: non modifica AutoMod, non invia avvisi, non applica mute, kick o ban, e non registra ogni messaggio su disco. Quando è ON conserva in **RAM** gli ultimi **5 messaggi** NORMAL/ME per identità nick+canale+rete, fino a **96 identità complessive**, con scadenza a **30 minuti** e un massimo di **320 byte UTF-8 di testo per messaggio**. La memoria temporanea viene svuotata a script spento, disconnessione e riavvio; è quindi possibile che manchino messaggi precedenti. Per un evento di moderazione, i messaggi ancora disponibili vengono inseriti in \`context_messages\` (nell'ordine originale); resta anche la ricerca SQLite dell'ultimo messaggio.

La V2 esegue un **controllo sperimentale in sola osservazione** sui messaggi NORMAL. Con almeno 3 parole normalizzate e somiglianza del **75%** (sovrapposizione delle parole, senza considerarne l'ordine), confronta il nuovo messaggio con quelli precedenti dell'utente ancora in memoria, nell'arco dei 30 minuti. Registra una riga \`event: "candidate"\` soltanto se trova:

- **2 messaggi simili**: \`candidate_stage: "repeat_2"\` (prima ripetizione osservata);
- **3 messaggi simili**: \`candidate_stage: "would_warn"\` (terzo messaggio, avviso ipotetico);
- **4 messaggi simili**: \`candidate_stage: "would_mute_120s"\` (quarto messaggio, mute ipotetico di 120 secondi).

Queste sono **simulazioni**, non provvedimenti. Il test non filtra per parole specifiche e **non replica esattamente** la logica AutoMod, che ha proprie esenzioni, parametri e trattamento delle menzioni. Le etichette servono a valutare eventuali regole future, non a stabilire chi debba essere sanzionato.

Il formato rimane **JSONL append-only** ma passa a \`schema: 2\`. Tutti i campi V1 rimangono; si aggiungono \`context_messages\`, \`history_status\` (per esempio \`sqlite_found\`, \`not_found_in_last_200\`, \`target_unresolved\`, \`history_timeout\`, \`history_request_failed\`, \`not_queried_shadow\`), \`message_age_seconds\`, \`event_id\`, \`case_id\`, \`correlation\`, \`candidate_stage\`, \`candidate_occurrences\` e \`candidate_similarity_percent\`. Se SQLite non restituisce un messaggio ma esiste un contesto in RAM, \`last_message\` può provenire da \`live_buffer\`; il motivo della mancata ricerca resta in \`history_status\`.

Un KICK IRC entro **10 secondi** da una richiesta AutoMod riferita allo stesso nick e canale può ricevere lo stesso \`case_id\`, con \`correlation: "same_nick_10s"\`: indica **solo una corrispondenza temporale**, non la prova che il server abbia eseguito quella richiesta. I ban generici senza destinatario non vengono attribuiti a utenti arbitrari. Il limite di cinque messaggi e la durata di 30 minuti possono ridurre i conteggi delle sequenze; non usarli come statistiche esaustive del canale.

## Installazione e comandi

1. Importare il file [moderation_collector.lua](../../lua-examples/moderation_collector.lua) da **Script Lua** e abilitarlo sulle reti desiderate.
2. Concedere **Dati persistenti** (archivio incrementale) e **Lettura utenti/identità** (ident, host, account e ricerca storica). **Non** servono permessi di invio messaggi o moderazione.
3. Lo script **inizia a registrare quando è abilitato** sulla rete; non c'è un interruttore nascosto dentro TIARCA. I comandi locali sono:

| Comando | Risultato |
|---|---|
| `/lua collector on` | Riprende la raccolta su quella rete; persiste |
| `/lua collector off` | Sospende la raccolta; conserva l'archivio esistente |
| `/lua collector status` | Mostra ON/OFF |
| `/lua collector export` | Condivisione non distruttiva del registro corrente |
| `/lua collector exportreset` | Mostra come confermare l'azzeramento |
| `/lua collector exportreset confirm` | Archivia il registro corrente, riparte da un registro vuoto e apre la condivisione Android |
| `/lua collector archive` | Riesporta l'ultimo archivio se la condivisione era stata annullata |
| `/lua collector channels` | Elenca i canali selezionati (inizialmente tutti) |
| `/lua collector channels add #canale` | Passa alla selezione e aggiunge il canale |
| `/lua collector channels del #canale` | Rimuove il canale; se la lista è vuota non ne monitora nessuno |
| `/lua collector channels all` | Monitora di nuovo tutti i canali della rete |
| `/lua collector channels none` | Sospende la raccolta da tutti i canali, mantenendo lo script acceso |
| `/lua collector` | Stato; `/lua collector help` mostra i comandi |

La selezione dei canali è **per rete e persistente**, indipendente dalle scelte di AutoMod. Un filtro non modifica la registrazione del nome canale negli eventi. Lo script resta acceso anche se la lista canali è vuota.

L'azzeramento con conferma **non distrugge i dati precedenti**: il file corrente viene spostato in un archivio privato recuperabile, prima che Android apra la condivisione. Anche se l'utente annulla la condivisione, può richiamare `/lua collector archive`. Solo l'archivio attivo riparte da zero; l'archivio precedente non è cancellato. Nessun cambio su altre reti o script.

**Disabilitare completamente lo script dalle impostazioni** interrompe callback e interrogazioni. Con `off`, la logica Lua non chiede lo storico né scrive righe. Il consenso ai dati persistenti è obbligatorio.

## Formato e conservazione

Ogni evento produce un oggetto **JSONL su una riga** (`schema: 2`), con `schema`, `server`, `channel`, `timestamp`, `event`, `action`, `nickname`, `ident`, `host`, `account`, `operator`, `reason`, `ban_mask`, `source_script`, `similarity_percent`, `infraction_level`, `execution`, `identity_match` e `last_message` (oggetto con testo, autore, identità e orario, oppure `null`).

Il file è **incrementale, non circolare**, privato per UUID di script e rete, e può contenere centinaia/migliaia di eventi. Nessuna eliminazione automatica; cresce con l'uso. Il sistema Lua espone API generiche `tiarca.journal.append`, `tiarca.journal.export`, `tiarca.last_message`, non una funzione Collector incorporata nell'app.

Gli archivi sono esclusi dal backup automatico Android per riservatezza: esportarli prima di reinstallare o cambiare dispositivo. Contengono dati di terzi: prestare attenzione alla condivisione.

## Limiti e verifiche

Android può sospendere processi, saturare code Lua o interrompere ricerche SQLite: **completezza assoluta non garantita**. Una mask di ban generica non prova quale utente sia stato colpito. La cronologia è limitata alle ultime 200 righe di canale e il testo trasferito allo script è limitato dai vincoli IPC. La conferma IRC di un'azione AutoMod è eventualmente riportata come evento distinto.

Test/CI: vedere [PR #289](https://github.com/TIARCA/TIARCA/pull/289). Test su dispositivo Android, varie reti, esportazione e riavvii: **non verificato** finché non eseguiti.
