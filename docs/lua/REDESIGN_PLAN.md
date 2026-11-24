# TIARCA — Lua redesign / piano di revisione

Decisione del 24 novembre 2026. Obiettivo: trasferire solo i dati necessari, mantenendo isolamento, permessi, limiti e controlli live prima delle moderazioni. Nessun avanzamento versione o release. PR #302 separata; Collector da riscrivere da zero come Harvester, non condiziona il redesign.

| Fase | Attività | Stato |
| --- | --- | --- |
| A | Cache identità IRC: membri condivisi, JOIN/NAMES/PRIVMSG/NICK/ACCOUNT/CHGHOST/PART/KICK/QUIT/disconnessione; limiti espliciti e identità ignote | CI #1181 e reproducibilità #390 superate; test reali da verificare |
| B | Dispatcher: consegna solo eventi sottoscritti; evitare snapshot inutili, senza bloccare IRC/UI | CI #1188 e reproducibilità #397 superate; test reali da verificare |
| C | API: event.sender acquisito al ricevimento; get_user puntuale; get_users filtrato prima della paginazione e asincrono, con indicazione completezza; get_channels su richiesta | Implementata sullo stesso branch; CI da verificare |
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

## Fase A — implementazione e limiti

- Base: main `b7ae71300620e21e023fdd2af48384585beae433`, 0.9.10 / 91000.
- La cache di base ha 4.096 voci LRU (il precedente limite di 512 non è più attuale).
- Separazione tra membri condivisi e identità recenti. Limiti per connessione: 16.384 nickname condivisi, 65.536 relazioni nickname-canale, 4.096 identità recenti. Quote conservative iniziali, non risultati di benchmark. Superando una quota non si espellono le identità condivise già note; l'indice può essere incompleto, con flag interno fino alla disconnessione.
- NAMES a completamento, JOIN, PART, KICK, NICK, QUIT e uscita dal canale mantengono i riferimenti. Dopo l'ultima presenza condivisa si elimina l'identità; la disconnessione azzera tutto. PRIVMSG/account-tag, extended-join, ACCOUNT, WHO/WHOIS/WHOX e CHGHOST aggiornano le informazioni osservate.
- Un cambio di maschera non esplicitamente correlato invalida il vecchio account; CHGHOST esplicito conserva l'account. Un JOIN da una presenza non condivisa scarta il vecchio record recente prima di osservare il mittente.
- Nessun nuovo WHO automatico, nessun dato persistente, nessuna API Lua nuova in A. Snapshot 128 e storage invariati: fasi B–D.
- Test: conservazione oltre il limite LRU, presenze multicanale, NAMES/NICK, JOIN/PART/KICK/QUIT, riuso nickname, CHGHOST, account ignoto/logout, quota e reset. Verifica Android/traffico reale e prestazioni da eseguire.

## Fase B — distribuzione degli eventi

- Un solo branch/PR per il redesign A–H: continuare sulla PR #303, senza creare un branch per ciascuna fase.
- Dopo il caricamento, il runtime nativo comunica la maschera dei 13 tipi di evento registrati mediante `tiarca.on`. Niente analisi testuale degli script o regole basate sul loro nome.
- Il dispatcher controlla le sottoscrizioni prima di mettere l'evento in coda e nuovamente sul worker prima di preparare i dati; se nessun destinatario è interessato non crea snapshot, non legge lo storage e non invia IPC per quell'evento.
- Per ciascun evento con destinatari, uno snapshot canali e al massimo uno snapshot utenti vengono costruiti e riutilizzati; i dati utenti sono trasmessi solo agli script autorizzati. La quota degli eventi è verificata prima della preparazione.
- Le sottoscrizioni sono associate al VM globale o al VM della rete dopo un restart mirato. Reset e ricariche invalidano i metadati; risposte obsolete non sovrascrivono la nuova sottoscrizione. In assenza di metadati confermati si usa la consegna precedente (conservativa), per non perdere callback.
- Compatibilità: `get_users` e `get_channels` restano disponibili con la semantica precedente per gli eventi sottoscritti; il roster fino a 128 utenti resta necessario per gli script legacy, sino alla fase C. Storage per evento invariato, fase D.
- Test aggiunti: filtro command-only, sottoscrizioni AutoMod, restart per rete, reset/metadati obsoleti, tutte le 13 registrazioni JNI e VM senza callback. Prestazioni Android effettive non ancora misurate.

## Fase C — API identità su richiesta

Gli script esistenti continuano a usare le API sincrone e gli snapshot legacy. Uno script nuovo opta esplicitamente per la modalità v2 chiamando `tiarca.identity_v2()` durante l'inizializzazione, prima delle callback. Dopo conferma dei metadati del VM, il dispatcher non costruisce né trasferisce roster o lista canali per gli eventi ordinari di quello script. Nessun nuovo permesso.

### Mittente

```lua
tiarca.identity_v2()
tiarca.on("message", function(e)
    local u = e.sender
    if not u then return end
    tiarca.log(u.nick .. " " .. (u.host or "?"))
end)
```

Con consenso identità e mittente osservato nel canale condiviso, il messaggio contiene `e.sender.nick`, `ident`, `host`, `prefix`, `account`, `account_known`, `mask` (se ident e host noti). Account nil = ignoto, "*" = logout confermato. Per i messaggi IRC live il DTO conserva account e presenza/prefissi osservati quando viene costruito; non si consulta un roster né si persiste l'account nel DB. Eventi non associabili a un membro osservato possono non avere sender. Le azioni di moderazione continuano a richiedere le verifiche live del client.

### Richieste asincrone

```lua
tiarca.identity_v2()
tiarca.on("command", function(e)
    if e.text == "voice list" then
        tiarca.get_users(e.channel, {token="voice", prefix="+", limit=100})
    end
end)
tiarca.on("identity", function(e)
    if e.token ~= "voice" or e.status ~= "ok" then return end
    tiarca.log("Corrispondenze osservate: " .. e.total)
    for _, u in ipairs(e.users) do
        -- elaborare u.nick, u.prefix, u.ident, u.host, u.account
    end
    if e.next_offset then
        tiarca.get_users(e.channel, {token=e.token, prefix="+", limit=100,
                                      offset=e.next_offset})
    end
end)
```

- `get_user(channel, nick, token)`: ricerca puntuale di un membro conosciuto, nessun roster completo. Risposta `e.query="user"`, `e.user` se trovato, `e.users`, `e.status`. Da comando locale o evento IRC del medesimo canale.
- `get_users(channel, options)`: `token` obbligatorio, `offset` default 0, `limit` default 100, max 128. Filtri combinabili: nickname/ident/host/account esatti (nick secondo CASEMAPPING), `prefix` contiene il prefisso richiesto, `auth` = registered/logged_out/unknown. Filtro sull'intero roster locale osservato prima della paginazione, fino a 65.536 membri; oltre questo limite si segnala incompleto. Richiesta iniziale solo da comando locale; continuazione con stesso canale/token/filtri/limite e next_offset, massimo 256 pagine per comando. Ogni pagina legge il roster live, quindi JOIN/PART/NICK tra pagine possono cambiare i risultati; non è una fotografia immutabile.
- `get_channels(token)`: da comando locale, solo canali condivisi, fino a 32, `e.query="channels"`, `e.channels`; eventuale troncamento segnalato.
- Risposte: callback `identity` solo allo script richiedente, con `e.token`, `e.query`, `e.status` (ok/not_observed/unavailable), `e.total`, `e.next_offset` (nil a fine pagina), `e.complete`. Completezza relativa ai dati locali disponibili e ai filtri; non prova una WHO completa. Account ignoti possono rendere un filtro incompleto. Una richiesta per evento; rifiuti di scope o saturazione della coda vengono registrati nel log, non autorizzano ulteriori azioni. Nessuna richiesta WHO automatica. 
- Compatibilità: script legacy invariati; migrazione AutoMod V4 e altri script nella fase E. La documentazione bilingue completa è fase G.
- Test aggiunti: 1000 membri/130 voice (100+30 risultati, tutti oltre il vecchio limite), lookup puntuale, account ignoto, logout successivo al messaggio, scope, continuazione, opt-in per rete, callback JNI dirette e asincrone. Compilazione/CI/benchmark Android ancora da verificare per C.

