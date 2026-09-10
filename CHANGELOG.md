# TIARCA — Changelog

## v0.9.4

- Preparata TIARCA per la distribuzione tramite F-Droid con metadata upstream, icona, screenshot e changelog dedicati.
- Migliorata la riproducibilità della build rimuovendo la dipendenza da repository Maven locali (`mavenLocal`).
- L'updater GitHub chiarisce ora che, nelle installazioni provenienti da F-Droid, il suo utilizzo bypassa i controlli di F-Droid.
- Nessuna modifica sostanziale alle funzionalità IRC rispetto alla 0.9.3.

## v0.9.3

- Il topic del canale è ora modificabile direttamente dal drawer destro tramite il pulsante di modifica accanto al topic.
- Il topic corrente viene precompilato nell'editor e la modifica richiede una conferma esplicita prima dell'invio del comando IRC `TOPIC`.
- I permessi restano demandati al server IRC, così TIARCA non blocca modifiche legittime in base a supposizioni locali sui mode del canale.

## v0.9.2

- Nei PVT aggiunti in barra superiore i pulsanti rapidi **Invia** e **Ignora**, riutilizzando le stesse azioni già disponibili nel pannello WHOIS.
- La voce **Impostazioni** è ora sempre presente in fondo al menu `...` anche nelle schermate chat.
- L'apertura volontaria di un PVT aggiunge automaticamente il nickname alla lista `ACCEPT` solo quando la modalità utente `+g` è realmente attiva.
- **Utenti monitorati** è stato portato nella posizione superiore del drawer principale al posto della vecchia voce Cerca.

## v0.9.1.8c

- Corretti i chip dell'editor **Formato del messaggio** nel tema scuro, rendendoli nuovamente distinguibili sullo sfondo nero.
- Corretta la gestione della tastiera Android nella schermata **Formato del messaggio**: su Android recenti l'area di editing e la barra di formattazione rispettano ora gli inset della tastiera e delle barre di sistema, così tutte le sezioni restano raggiungibili e scorribili.

## v0.9.1.8b

- Reso cliccabile il nickname nei messaggi server che confermano l'aggiunta alla ACCEPT list: un tap sul nick apre direttamente il relativo PVT.

## v0.9.1.8a

- Corretto il parsing del numeric IRC `718` su SimosNap: il nickname reale del mittente viene distinto dalla maschera `ident@host`.
- Il pulsante **ACCETTA** invia ora `ACCEPT +<nickname>` usando il nickname corretto, evitando l'errore IRC `401 No such nick`.
- Aggiunto un test di regressione basato sul formato `718` osservato dal vivo su SimosNap.

## v0.9.1.8

- Aggiunti comandi IRC nativi utilizzabili direttamente senza `/raw`: `/whowas`, `/accept`, `/invite`, `/ison`, `/userhost`, `/motd`, `/version`, `/time`, `/admin`, `/info`, `/lusers`, `/links`, `/stats`, `/knock`, `/list`, `/names` e `/who`; argomenti e supporto restano demandati al server IRC.
- Gestito strutturalmente il numeric `718` dei server con caller-id/modalità `+g`: TIARCA ricava il nickname dai parametri IRC e mostra nella scheda Server una richiesta leggibile con azione **ACCETTA**.
- Premendo **ACCETTA** TIARCA invia direttamente `ACCEPT +<nickname>`, senza richiedere `/raw` e senza dipendere dal testo descrittivo inglese inviato dal server.
- Aggiunti test di regressione per l'inventario dei comandi IRC nativi e per il parsing del numeric `718`.

## v0.9.1.7

- Corretto il routing delle conversazioni private quando un utente cambia nickname e poi torna a un nickname precedente: la cronologia degli alias viene normalizzata senza cicli, evitando messaggi ricevuti invisibili nella query aperta.
- Aggiunti test di regressione per sequenze `A → B → A` e per catene più lunghe di cambi nickname.

## v0.9.1.6

- Ampliate le descrizioni dei mode IRC di canale e utente con profili più specifici per SimosNap, IRCnet, Undernet e Libera.Chat.
- Interpretazione migliorata dei mode server-specifici senza attribuire significati arbitrari ai mode sconosciuti.
- Raffinata la gestione condivisa delle modalità IRC mantenendo separati supporto annunciato dal server, significato del mode ed editabilità.

## v0.9.1.5

- Aggiunta **Modalità utente** nel menu della scheda Server: mostra i mode annunciati dal server, quelli attivi e consente di modificarli quando sono impostabili dall’utente.
- Unificata l’interpretazione dei mode canale e utente in un registro condiviso sensibile al profilo IRCd.
- Estese le descrizioni dinamiche oltre InspIRCd con profili IRCnet, UnrealIRCd 6, Solanum ed Ergo; i mode non riconosciuti restano visibili come specifici del server senza interpretazioni arbitrarie.
- Le modalità gestite dal server o dai services vengono mostrate ma non rese modificabili quando il profilo le identifica come tali.

## v0.9.1.4

- Migliorata la resa dei record WHOWAS: l'icona Copia usa ora una tinta grigia visibile sia con tema scuro sia con tema chiaro.
- Ridotta l'altezza dei pulsanti Copia da 40 a 32 dp, eliminando lo spazio verticale eccessivo tra le righe Nick, Ident, Host e Server senza ridurre la leggibilità dei dati.

## v0.9.1.3

- Corretto WHOWAS: il collector viene ora installato alla creazione di ogni connessione IRC, prima dell'avvio del thread di rete, così le risposte non ricadono più come numeric `314`/`369` grezzi nella scheda Server.
- Le risposte WHOWAS vengono raccolte come record strutturati anche quando il comando parte da `/WHOWAS` o da altri ingressi oltre al dialog dedicato; restano supportati record multipli e l'associazione del `312` quando il server lo fornisce.
- Aggiornata la home del repository, rimasta erroneamente ferma alla 0.9.1.1.

## v0.9.1.2

- WHOWAS ora raccoglie e mostra record storici strutturati nella scheda Server: nickname, ident, host, real name, server, orario di disconnessione quando fornito dal server e info server aggiuntive.
- Le risposte WHOWAS multiple per lo stesso nickname vengono mantenute come record distinti; il numeric `312` condiviso con WHOIS viene associato al record WHOWAS senza comparire come protocollo grezzo.
- Nei record WHOWAS nickname, ident, host e server hanno un pulsante di copia; long press sul nickname apre il menu contestuale, su ident/host apre il Kickban con la mask corrispondente e scelta del canale operatore quando necessaria.
- Aggiunto WHOWAS al menu della scheda Server con richiesta del nickname.
- Corretta la voce italiana “Ignora l'elenco” in “Utenti ignorati”.

## v0.9.1.1

- Corretto il fallback **WHOWAS**: il numeric IRC `312`, condiviso con WHOIS, non viene più interpretato come una risposta WHOIS fuori sequenza e non compare più come riga protocollo grezza nella scheda **Server** durante la consultazione dei dati storici di un nickname.
- Aggiunto un test di regressione per le risposte `312` ricevute durante WHOWAS.

## v0.9.1

### Aggiornamenti integrati

- Aggiunto in **Informazioni su TIARCA** il controllo aggiornamenti da GitHub Releases, con comando manuale **Cerca aggiornamenti** sempre disponibile.
- Al primo avvio della 0.9.1 TIARCA chiede una sola volta se abilitare il controllo automatico; se accettato verifica la disponibilità di nuove release al massimo una volta ogni 7 giorni.
- Il controllo automatico è silenzioso quando l'app è aggiornata o la rete non è disponibile; il controllo manuale mostra invece un esito esplicito.
- Quando è disponibile una nuova versione vengono mostrati versione installata, nuova versione e note della release; l'APK viene scaricato soltanto dopo la conferma dell'utente.
- Prima di aprire l'installer Android, TIARCA verifica che l'APK appartenga allo stesso package e sia firmato con la stessa chiave dell'app installata. L'installazione finale resta sempre sotto il controllo dell'installer Android.

### Server, autenticazione e canali

- Ridisegnata la configurazione iniziale dei nuovi server con il flusso semplificato **Ho una password**: username SASL precompilato dal nickname ma modificabile, password dedicata e SASL PLAIN impostato automaticamente, mantenendo le modalità avanzate disponibili.
- Rinominato il campo IRC **Utente** in **Ident** per distinguerlo chiaramente dallo username dell'account SASL.
- Aggiunto il campo **Collegati ai canali** nella configurazione semplice; per Simosnap vengono proposti inizialmente `#amicizia` e `#chatitaly`, sempre modificabili o eliminabili dall'utente.
- Aggiunta **Lista canali** durante la creazione del server: TIARCA effettua una connessione IRC temporanea isolata, recupera `LIST`, si disconnette e apre il selettore multiplo senza registrare sessioni, reconnect, notifiche o cronologia.
- La Lista canali parte ora ordinata per numero utenti decrescente; in **Entra nel canale** il campo viene precompilato con `#`, senza impedirne la modifica o la cancellazione.
- Corretta la schermata **Aggiungi un nuovo server** affinché tutti i campi restino raggiungibili e scorribili sopra la tastiera Android.

### Chat, nickname e sessione

- Corretto lo stato delle query private dopo i cambi nickname, evitando conversazioni residue o riaperte con il vecchio nick e migliorando il riconoscimento dei messaggi locali.
- Aggiunto il long press sui nickname nei suggerimenti dell'autocompletamento `@`: il tap breve continua a completare il nick, mentre il long press apre il normale menu utente con PVT, menzione, WHOIS, MONITOR, Ignore e azioni operatore consentite.
- Eliminati alcuni aggiornamenti completi della lista messaggi che potevano produrre un lampeggio nero dell'intero canale durante il redraw.
- Il tasto Indietro propone ora **Disconnetti ed esci**, **Rimani in background** e **Annulla**; l'uscita volontaria disconnette ordinatamente i server, ferma i servizi e non ripristina automaticamente la sessione al successivo avvio.

### Backup e rilascio

- Abilitato il backup/ripristino Android cifrato delle configurazioni e preferenze compatibili; il trasferimento diretto tra dispositivi può includere anche i log chat, mentre il backup manuale TIARCA rimane separato e invariato.
- Il workflow Release non parte più a ogni merge su `main`: la CI resta automatica, Candidate resta manuale e Release viene eseguita manualmente o tramite tag `v*`.
- Le release ufficiali pubblicano ora, oltre all'APK firmato, anche il relativo file SHA-256.

## v0.8.2

- Corretta **Cerca nella conversazione**: una nuova ricerca seleziona ora per prima l’occorrenza più recente invece della più vecchia.
- **Precedente** risale cronologicamente verso i risultati più vecchi, mentre **Successiva** torna verso quelli più recenti, in modo coerente con la navigazione delle menzioni.
- Il contatore dei risultati segue lo stesso ordine: con 8 corrispondenze la ricerca parte da `8/8`, poi **Precedente** passa a `7/8`, `6/8` e così via.

## v0.8.1

### Navigazione messaggi, ricerca e menzioni

- Ridisegnata la navigazione delle menzioni: il contatore superiore mostra le menzioni non ancora visitate e apre per prima la menzione non letta più recente.
- Visitando le menzioni il contatore viene decrementato progressivamente senza interferire con il conteggio dei normali messaggi non letti.
- La barra inferiore delle menzioni usa ora tre comandi estesi e interamente cliccabili — **Precedente**, **Chiudi** e **Successiva** — con etichette compatte sugli schermi più stretti.
- Chiudere la barra delle menzioni non ripristina più la vecchia posizione della chat: la conversazione rimane esattamente sul punto raggiunto.
- I salti a un messaggio, sia dalle menzioni sia dalla ricerca, centrano il messaggio nella chat quando possibile per mostrare meglio il contesto precedente e successivo.
- I risultati di **Cerca nella conversazione** sono ora apribili direttamente: un tocco sull’anteprima chiude il dialog e porta al messaggio nella chat.
- Aggiunto un pulsante traslucido con freccia verso il basso che compare quando si naviga lontano dal fondo della conversazione e riporta immediatamente all’ultimo messaggio.

### Ban, eccezioni e moderazione

- Unificata la gestione di **Ban** ed **Eccezioni** (`+b` / `+e`) nella stessa schermata **Ban | Eccezioni**, con ricerca, selezione multipla e aggiornamenti MODE in tempo reale.
- Aggiunto il supporto ai numerici InspIRCd `348/349` e al parametro `EXCEPTS` di ISUPPORT per la lista eccezioni.
- La funzione di pulizia è ora indicata semplicemente come **Pulizia** e può selezionare anche i ban ident-only `*!ident@*` più vecchi di 7 giorni quando la data è affidabile; la pulizia continua ad applicarsi esclusivamente ai ban.
- Corretto il crash **Handler registration name collision** nella schermata Ban | Eccezioni riutilizzando il gestore MODE esistente senza registrare un secondo handler principale.
- L’azione Voice nel menu utente è ora un vero toggle: propone **Dai voice** o **Togli voice** in base allo stato corrente dell’utente.
- Corretta la formattazione dei messaggi IRC MODE relativi a voice, op, half-op, admin e owner, inclusa la resa italiana.

### Utenti monitorati

- La lista MONITOR conserva e mostra ora lo **stato noto più recente** dell’utente, il nickname/alias attivo e i relativi timestamp anche dopo disconnessioni del client e riavvii dell’app.
- Sistemata la semantica di `onlineSince` e `lastSeen`: gli eventi duplicati non alterano i timestamp e uno stato sconosciuto non inventa una falsa data di ultima presenza.
- Corrette la navigazione contestuale e l’apertura dei PVT dalla schermata **Utenti monitorati**.
- Corretto il backup/ripristino delle liste MONITOR anche quando alcuni file opzionali di storage non esistono.

### Server e connessioni

- Aggiunto il **riordino manuale dei server/network** tramite drag & drop, accessibile da **Riordina**; l’ordine è persistente e usa UUID stabili senza riconnettere o ricreare le connessioni attive.
- Corretto il dominio predefinito Simosnap da `irc.simosnap.com` a `irc.simosnap.org`; le configurazioni esistenti vengono migrate automaticamente, compresi gli indirizzi di fallback.
- Rafforzate le regole R8/ProGuard per impedire che handler IRC, capability e filtri necessari vengano rimossi o rinominati nelle build release.
- Corretta la gestione delle eccezioni dei certificati TLS non affidabili, che torna a mostrare correttamente la richiesta di conferma invece di rifiutare silenziosamente la connessione.
- Corretto un crash del catalogo network nelle build minificate dovuto alla deserializzazione Gson delle classi interne.

### Stabilità e interfaccia

- Corretto un ANR/blocco al ritorno dell’app in primo piano riducendo gli aggiornamenti non necessari delle chat non attive e spostando alcune notifiche fuori dalle sezioni sincronizzate.
- Eliminati flicker, schermate nere temporanee e perdita della posizione di scorrimento nella lista utenti del drawer destro grazie ad aggiornamenti differenziali.
- Tutte le operazioni **Copia** salvano ora sempre testo semplice negli appunti, senza formattazioni Spannable residue.
- Corretto il crash `ClassCastException` degli alias di comando/autocompletamento causato dalla deserializzazione Gson, mantenendo compatibilità con dati salvati da versioni precedenti.

### Lingue e localizzazione

- Portata a copertura completa la localizzazione in **Italiano, English, Deutsch, Español, Français, Polski, Português (Brasil), Suomi e Română**, tutte selezionabili dalle impostazioni Lingua.
- Riviste e corrette le traduzioni TIARCA specifiche, con particolare attenzione a terminologia IRC, moderazione, DCC, MONITOR, impostazioni e messaggi dinamici.
- Uniformati i placeholder Android alla forma posizionale (`%1$s`, `%2$s`, `%1$d`, ecc.) e aggiornato `SpannableStringHelper` per gestirli preservando gli span di formattazione.
- Aggiunto un audit automatico delle risorse di traduzione alla CI per rilevare risorse mancanti, extra, duplicate e placeholder incompatibili.

### Build, test e rilascio

- Aggiunta una pipeline CI con audit traduzioni, test automatici e build dell’APK debug.
- Aggiunto il workflow **Candidate** per produrre manualmente APK release firmati, minificati e con resource shrinking usando la stessa chiave della release ufficiale.
- Aggiornate le GitHub Actions alle versioni correnti e consolidati i workflow permanenti in **CI**, **Candidate** e **Release**.

## v0.7.14

- Corretto il posizionamento dei messaggi nei PVT aperti dalla lista **Utenti monitorati**.
- La ricerca utenti nel drawer mantiene ora testo, focus e tastiera durante gli aggiornamenti del canale.
- L'azione **Monitora utente** dal menu rapido precompila correttamente il nickname selezionato.

## v0.7.13

- Corretto un crash durante la connessione causato dalla minificazione dei tipi Gson usati per le menzioni.

## v0.7.12

- Corretto un crash all'avvio durante la migrazione delle liste di utenti monitorati esistenti.

## v0.7.11

- Corretto definitivamente il campo **Messaggio personalizzato…** nei dialog Kick, Kickban e TBAN: il testo è ora editabile e la tastiera si apre quando il dialog riacquista il focus.

## v0.7.10

- Ripristinate le ottimizzazioni R8 e la rimozione delle risorse inutilizzate nella build release, riducendo sensibilmente le dimensioni dell'APK.

## v0.7.9

- Corretto il tap intermittente sui nickname e link nei messaggi: un tocco normale non dipende più da una soglia temporale troppo breve, mentre il long press mantiene il menu rapido.

## v0.7.8

- Aggiunta la ricerca client-side nella lista utenti del drawer destro per i canali.
- La ricerca filtra per nickname, è case-insensitive e aggiorna la lista senza richiedere nuove query al server.
- L'ordinamento esistente (owner/admin/op/half-op/voice/utente normale) viene mantenuto anche nei risultati filtrati.
- Il campo di ricerca compare solo quando il drawer mostra una vera lista utenti di canale.

## v0.7.7

- Corretto il comportamento del campo **Messaggio personalizzato…** nei dialog Kick, Kickban e TBAN: la tastiera può aprirsi anche con i bottom sheet e l'input mantiene correttamente il focus.

## v0.7.6

- Aggiunto il pulsante **X** per chiudere il campo di ricerca nella lista utenti del drawer destro.
- Il campo di ricerca utenti viene ora nascosto quando il drawer destro mostra il topic o altri contenuti che non sono una lista utenti.

## v0.7.5

- Aggiunta la ricerca nella lista utenti del drawer destro dei canali, con filtro locale per nickname e aggiornamento immediato dei risultati.
- Migliorato il comportamento del focus/tastiera durante la ricerca per evitare che il campo venga ricreato o perda il testo digitato.

## v0.7.4

- Corretto il crash nella gestione delle eccezioni ban (`+e`) causato dalla registrazione duplicata del gestore MODE.

## v0.7.3

- Aggiunto supporto alle eccezioni ban (`+e`) nella schermata Ban, con tab dedicato, ricerca e rimozione multipla.
- La schermata è stata rinominata in **Ban | Eccezioni**.

## v0.7.2

- Aggiunto supporto al numeric IRC 348/349 per la lista eccezioni ban di InspIRCd.

## v0.7.1

- Corretta la schermata lista ban per mantenere il contesto del canale durante aggiornamenti e navigazione.

## v0.7.0

- Introdotta la schermata dedicata alla gestione ban, con caricamento della lista `+b`, ricerca e rimozione multipla.

## v0.6.0

- Prima release pubblica TIARCA derivata da Revolution IRC.