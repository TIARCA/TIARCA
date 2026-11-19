# Collector di moderazione (sperimentale)

[Guida Lua](README.md) · [English](../en/MODERATION_COLLECTOR.md)

**Disponibilità:** codice sul branch `feat/automod-collector-20261119`, non nella release stabile v0.9.10. Funzionamento su dispositivi reali: **non verificato**.

## Che cosa raccoglie
- `KICK` ricevuti dal server, anche quando eseguiti da altri operatori o bot, con esecutore, nickname espulso e motivazione.
- `MODE +b` ricevuti dal server, con esecutore e mask; il ban non implica necessariamente l'espulsione.
- Decisioni di `automod_antiripetizione.lua` (avviso, kick, ban, mute, ecc.), conservate come **richieste non confermate** finché non si osservano eventi IRC compatibili.
- L'ultimo messaggio **pubblico** precedente al KICK, se recuperabile dalla cronologia SQLite TIARCA fra gli ultimi 200 messaggi del canale. Per `+b` si tenta soltanto se la mask identifica esplicitamente un nick senza wildcard. Mancanza di corrispondenza = `null`, non un messaggio inventato.

Un messaggio precedente è **contesto**, non dimostrazione della causa del provvedimento. Le identità `ident`, `host` e `account` vengono incluse soltanto se già osservate da TIARCA. Un operatore esterno non è automaticamente classificato come persona o bot.

## Attivazione
Importare `docs/lua-examples/moderation_collector.lua` dalle impostazioni Script Lua, abilitarlo sulla rete scelta, quindi digitare sulla rete:

| Comando | Effetto |
|---|---|
| `/lua collector on` | Abilita raccolta sulla rete corrente (stato persistente) |
| `/lua collector off` | Interrompe la raccolta; **non elimina** i dati già acquisiti |
| `/lua collector status` | Mostra stato e dimensione dell'archivio |
| `/lua collector export` | Apre la condivisione Android per esportare un file JSONL |
| `/lua collector help` | Elenca i comandi |

Non è necessario concedere agli script accesso ai file: la scrittura avviene **nel processo Android principale**, non nella sandbox Lua. Non occorre autorizzare l'invio di messaggi o la moderazione perché lo script Collector è passivo. La raccolta è **disattivata per impostazione predefinita**.

## Archivio e riservatezza

Un file JSONL incrementale per UUID della rete in una directory privata dell'app. Ogni riga è un oggetto JSON con campi `schema`, `event`, `server`, `channel`, `actor`, `target`, `detail`, `timestamp`, `origin`, `ident`, `host`, `account`, `prior_message`, `prior_timestamp`, `trigger_message`, `trigger_timestamp`. Per AutoMod sono presenti, quando analizzabili, `similarity_percent`, `infraction_level`, `requested_action`.

L'archivio **non è un registro circolare**: nessuna sovrascrittura dopo 32 eventi, nessuna eliminazione automatica. Questo comporta una crescita dello spazio occupato. Viene esportata una copia temporanea, non si espone direttamente il file privato.

**Privacy:** il JSONL può contenere messaggi e dati identificativi di terzi. Esportarlo soltanto quando necessario e condividerlo con destinatari fidati; valutare oscuramento prima di pubblicarlo.

## Limiti

Le code Lua possono perdere eventi; non è garantita la completezza in caso di crash, sospensione o disconnessione. Le decisioni AutoMod sono distinte dai KICK/BAN osservati per evitare di considerare confermato un semplice comando inviato. L'associazione esatta tra una `+b` generica (es. host condiviso) e un singolo utente **non è verificabile** senza altre evidenze.

## Verifiche

Compilazione e test automatici vanno controllati nella [PR #289](https://github.com/TIARCA/TIARCA/pull/289). Rimangono necessarie prove manuali su APK, inclusi più server, riconnessioni, esportazione e permessi Android. Non aumenta la versione e non pubblica release.
