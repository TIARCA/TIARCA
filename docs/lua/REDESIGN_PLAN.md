# TIARCA — Lua redesign / piano di revisione

Decisione del 24 novembre 2026. Obiettivo: trasferire solo i dati necessari, mantenendo isolamento, permessi, limiti e controlli live prima delle moderazioni. Nessun avanzamento versione o release. PR #302 separata; Collector da riscrivere da zero come Harvester, non condiziona il redesign.

| Fase | Attività | Stato |
| --- | --- | --- |
| A | Cache identità IRC: membri condivisi, JOIN/NAMES/PRIVMSG/NICK/ACCOUNT/CHGHOST/PART/KICK/QUIT/disconnessione; limiti espliciti e identità ignote | CI #1181 e reproducibilità #390 superate; test reali da verificare |
| B | Dispatcher: consegna solo eventi sottoscritti; evitare snapshot inutili, senza bloccare IRC/UI | CI #1188 e reproducibilità #397 superate; test reali da verificare |
| C | API: event.sender acquisito al ricevimento; get_user puntuale; get_users filtrato prima della paginazione e asincrono, con indicazione completezza; get_channels su richiesta | CI #1206 e reproducibilità #415 superate; test reali da verificare |
| D | Storage in memoria, aggiornamenti atomici solo al cambiamento, sincronizzazione/versioni tra processi, backup e ripristino; commit prima delle sanzioni | CI #1218 superata; reproducibilità #427 in corso al momento della migrazione |
| E | Adeguare AutoMod V4, mantenere crediti e logica; correggere integrazione voice; Mask Finder ed Espelli invariati funzionalmente | CI #1219 superata; reproducibilità #428 in corso; test reali da verificare |
| F | Diagnostica motore (eventi ricevuti/elaborati/scartati, latenza, memoria, scritture, richieste roster) e stress test 1000 membri/500 attivi, 5/10/25/50 msg/s; statistiche AutoMod separate da valutare dopo | Implementata; benchmark CI e prova Android da verificare |
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


## Fase D — storage persistente

- Stato autorevole nel processo principale, cache in RAM caricata al primo uso e limitata a 8 script. Lo storage resta globale per script (con chiavi eventualmente separate per rete), come prima. Formato JSON e quote di 32 chiavi/128 KiB invariati.
- Revisione monotona locale al processo; snapshot completo via IPC solo al primo invio, dopo reset o quando cambia la revisione. Il servizio isolato conserva solo copie convalidate di revisioni già commesse; i VM nativi riutilizzano lo stato invariato e invalidano le copie in caso di scritture speculative. Non viene ampliato l'accesso del sandbox.
- Eventi degli script con consenso storage serializzati per script, anche tra reti; gli altri script non aspettano la transazione di quello script. Le richieste in attesa condividono il limite esistente di 32 eventi preparati; la coda di ingresso resta limitata a 32. A saturazione si possono ancora perdere eventi: misurare nella fase F.
- Tutte le mutazioni di un evento sono validate e applicate insieme. Stato finale identico = nessuna scrittura. In caso di modifica, un solo commit AtomicFile, sync e verifica dei byte; gli effetti dell'evento vengono rilasciati soltanto dopo successo. Una revisione obsoleta o un errore di persistenza blocca gli effetti e disabilita lo script. Non è una transazione distribuita con il server IRC: un crash dopo il commit e prima dell'invio può lasciare lo stato aggiornato senza sanzione effettivamente inviata.
- Backup esporta soltanto lo stato commesso, restore e cancellazione invalidano la cache; le approvazioni restano revocate al ripristino. Nessuna migrazione di file o API Lua.
- Test aggiunti: 1000 accessi alla stessa revisione, batch/no-op, errore e revisione obsoleta, restore/reset, mirror IPC, coda FIFO multirete, invalidazione VM dopo mutazione non commessa e callback fallita. Test Android reali, throughput e latenza del commit ancora da misurare.

## Fase E — migrazione AutoMod V4

- Portato V4 dalla PR #302 (commit 0554a56), inclusa la revisione dei saluti. Classificazione, crediti, soglie, escalation e formato persistente invariati. Nessun roster per messaggio: usa il mittente osservato, senza WHO; se manca sospende la decisione.
- Whitelist: lookup puntuale asincrono, senza scansione. Dal canale monitorato: `/lua mod whitelist add NICK`; dalla scheda server con più canali: `/lua mod whitelist add NICK #canale`. Con un unico canale monitorato il parametro resta facoltativo. Solo account osservato e risposta correlata alla richiesta.
- Il gate live permette il voice soltanto per V4, sul mittente dell'evento e con i consensi richiesti. Halfop e superiori restano protetti. Mute/ban temporanei V4 privilegiano l'account osservato con extban R: compatibile, altrimenti host esatto. Log V4 riconosciuti nella scheda server. Mask Finder, Espelli e Collector non modificati.
- Test host aggiunti per sender senza roster, voice, prefissi superiori, identità mancante e whitelist asincrona; test Java del gate. PR #302 separata: selezione multipla PVT e altre modifiche non pertinenti non portate qui; riconciliare i file comuni prima del merge. CI, reproducibilità, prestazioni e prova reale da verificare.

## Fase F — diagnostica e carico

- `/lua perf`: contatori globali del processo principale (tutte le reti, non per utente o script), in RAM dall'avvio. Nessun dato IRC salvato. Disponibile anche con motore sospeso, dalla connessione attiva. Le statistiche AutoMod restano separate. Ingressi e consegne sono unità diverse: un evento può avere più destinatari; una saturazione slot può interrompere il fan-out. Gli annullamenti e gli errori sono contatori di stadio, non una somma esclusiva di perdite. I messaggi esclusi prima di enqueue (ad esempio echo e batch) non sono conteggiati.
- Latenza media/max: da enqueue a invio IPC, invio-risposta (include coda sandbox e Binder), durata della chiamata JNI, enqueue fino a rilascio degli effetti (non conferma IRC), durata transazione storage. Snapshot roster legacy, righe effettivamente inviate (incluse query), query eseguite, invii snapshot storage, commit riusciti e no-op. Gli errori storage includono anche caricamenti/permessi falliti.
- Memoria: ultimo campione e massimo campionato dell'allocatore Lua nel processo isolato, aggregato su VM; non RSS, heap Java, strutture C o memoria IPC. Dopo invalidazione il valore corrente torna non disponibile. Nessun reset dei contatori in F, nessuna scrittura diagnostica su disco.
- `HostLuaStressTest`: CI Standard, runtime JNI reale, 1000 identità sintetiche (500 mittenti attivi oltre i primi 128), pre-seed di un annuncio ciascuno e 500 successivi arrivi reali a 5/10/25/50 msg/s, coda host 32. V4 in monitor, nessun roster o query, verifica riconoscimenti e contabilità, stampa throughput, drop, p95/p99, campioni memoria. Nessun limite prestazionale arbitrario: errori funzionali falliscono, i drop vengono riportati. Separatamente il test Java identity query di C esercita il roster effettivo di 1000 membri/130 voice. Non è uno stress test dell'intero Android: Binder, cache IRC, disco reale, UI e code host Android vanno misurati con Candidate e server/test traffic nella fase H. Nessuna conclusione prestazionale prima dei risultati.
