# Moderation Collector — script Lua autonomo

[Guida Lua](README.md) · [English](../en/MODERATION_COLLECTOR.md) · [Codice Lua](../../lua-examples/moderation_collector.lua)

**Disponibilità:** PR [#289](https://github.com/TIARCA/TIARCA/pull/289), non nella release stabile v0.9.10. Prova Android reale: **non verificato**.

## Che cosa registra

È `moderation_collector.lua` a scegliere gli eventi, interrogare lo storico e comporre le righe del file: **nessuna raccolta o correlazione Collector viene eseguita da TIARCA quando lo script è spento**.

- Tutte le decisioni riconosciute di `automod_antiripetizione.lua`: **monitor**, **avviso**, **kick**, **ban**, **mute**, **tban**, **bankick**, **tbankick**. Sono classificate come *richieste*, non successi confermati.
- KICK comunicati dal server, anche di altri operatori/bot: **esecutore**, **nickname espulso**, **motivo** quando presente.
- MODE +b comunicati dal server: **esecutore**, **mask**. La mask non trasmette una motivazione IRC; se individua esattamente un nick si associa quel nick, altrimenti si salva l'evento senza inventare un destinatario.
- **Ultimo messaggio pubblico** di quel nick presente fra gli **ultimi 200 messaggi SQLite del canale**, quando recuperabile. La ricerca viene richiesta *dallo script solo dopo un evento di moderazione* e vale anche per tutti gli eventi AutoMod.

Per ogni evento e ultimo messaggio si riportano **canale, timestamp, nick, ident, host, account se osservati**, altrimenti `null`. Per KICK/BAN anche operatore, mask/motivo ove comunicati. Il messaggio è contesto, non prova del motivo del provvedimento. In presenza di mask generiche ambigue non si inventa un nickname.

## Installazione e comandi

1. Importare il file [moderation_collector.lua](../../lua-examples/moderation_collector.lua) da **Script Lua** e abilitarlo sulle reti desiderate.
2. Concedere **Dati persistenti** (archivio incrementale) e **Lettura utenti/identità** (ident, host, account e ricerca storica). **Non** servono permessi di invio messaggi o moderazione.
3. Lo script **inizia a registrare quando è abilitato** sulla rete; non c'è un interruttore nascosto dentro TIARCA. I comandi locali sono:

| Comando | Risultato |
|---|---|
| `/lua collector on` | Riprende la raccolta su quella rete; persiste |
| `/lua collector off` | Sospende la raccolta; conserva l'archivio esistente |
| `/lua collector status` | Mostra ON/OFF |
| `/lua collector export` | Condivisione Android del JSONL di quello script e rete |
| `/lua collector` | Stato; `/lua collector help` mostra i comandi |

**Disabilitare completamente lo script dalle impostazioni** interrompe callback e interrogazioni. Con `off`, la logica Lua non chiede lo storico né scrive righe. Il consenso ai dati persistenti è obbligatorio.

## Formato e conservazione

Ogni evento produce un oggetto **JSONL su una riga**, con `schema`, `server`, `channel`, `timestamp`, `event`, `action`, `nickname`, `ident`, `host`, `account`, `operator`, `reason`, `ban_mask`, `source_script`, `similarity_percent`, `infraction_level`, `execution`, `identity_match` e `last_message` (oggetto con testo, autore, identità e orario, oppure `null`).

Il file è **incrementale, non circolare**, privato per UUID di script e rete, e può contenere centinaia/migliaia di eventi. Nessuna eliminazione automatica; cresce con l'uso. Il sistema Lua espone API generiche `tiarca.journal.append`, `tiarca.journal.export`, `tiarca.last_message`, non una funzione Collector incorporata nell'app.

Gli archivi sono esclusi dal backup automatico Android per riservatezza: esportarli prima di reinstallare o cambiare dispositivo. Contengono dati di terzi: prestare attenzione alla condivisione.

## Limiti e verifiche

Android può sospendere processi, saturare code Lua o interrompere ricerche SQLite: **completezza assoluta non garantita**. Una mask di ban generica non prova quale utente sia stato colpito. La cronologia è limitata alle ultime 200 righe di canale e il testo trasferito allo script è limitato dai vincoli IPC. La conferma IRC di un'azione AutoMod è eventualmente riportata come evento distinto.

Test/CI: vedere [PR #289](https://github.com/TIARCA/TIARCA/pull/289). Test su dispositivo Android, varie reti, esportazione e riavvii: **non verificato** finché non eseguiti.
