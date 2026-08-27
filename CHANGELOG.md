# TIARCA — Changelog

## v0.7.5

### Ignore evoluto

- Aggiunto Ignore contestuale: il nickname è il criterio predefinito, mentre ident e host sono selezionabili esplicitamente quando disponibili.
- Le categorie messaggi canale, messaggi privati, notice canale e notice privati sono configurabili separatamente.
- Le regole sono permanenti per impostazione predefinita oppure possono avere una durata personalizzata in ore; sono modificabili e rimovibili dalla stessa Ignore list.
- Le regole e i backup esistenti restano compatibili.

### Utenti monitorati

- Aggiunta la schermata **Utenti monitorati** per network, basata su IRC MONITOR rilevato tramite `ISUPPORT MONITOR=<limite>` e sui numerici 730–734.
- L'accesso principale nel drawer apre una panoramica dei server configurati e delle rispettive liste; sono mostrati gli stati Online, Away e Offline, l'assenza del supporto MONITOR e le entry oltre il limite del server.
- La sync iniziale e dopo reconnect non genera false notifiche; le notifiche opzionali di accesso/disconnessione aprono il PVT del nickname corrente.
- Più nickname possono essere raggruppati manualmente sotto un singolo utente monitorato; i cambi nickname osservati aggiungono e conservano automaticamente un alias nel gruppo, rispettano il case mapping IRC e proteggono dal riuso del vecchio nickname da parte di un altro utente.
- Configurazione e preferenze sono persistenti e incluse nel backup/ripristino; nessun polling è utilizzato. Lo stato away esistente viene riutilizzato quando disponibile.
- Aggiungere o rimuovere utenti e alias durante una connessione aggiorna MONITOR senza riavviare la connessione IRC.

### Menu rapido nickname e Interfaccia

- Aggiunto un menu rapido con long press sui nickname, disponibile anche nella lista utenti e nel drawer destro, con accesso alle azioni già disponibili: PVT, menzione, WHOIS, monitoraggio, Ignore e moderazione quando consentita.
- Corretto il conflitto tra long press sui nickname e selezione del testo: la selezione/copia normale e i link restano utilizzabili.
- Semplificate le impostazioni Interfaccia: il completamento usa sempre pulsante, suggerimenti `@` e canali; la navigazione della cronologia usa sempre swipe orizzontali; la selezione moderna dei messaggi è sempre attiva e mantiene Copy, Share e Delete.
- La scheda **Server** è sempre visibile nel drawer.

## v0.7.3

- Aggiunto il supporto IRCv3 `away-notify`, con aggiornamento in tempo reale dello stato away degli utenti sui server compatibili senza mostrare gli eventi tecnici AWAY nella scheda SERVER.
- Corretta la selezione e copia del testo nella scheda SERVER.
- Corretta la chiusura delle conversazioni private, che poteva interferire con la chiusura dei canali.
- Corretti i messaggi inviati nelle query aperte tramite **Scrivi all'utente**, ora mostrati correttamente anche localmente.

## v0.7.2

- Migliorato il primo avvio dei server: se manca il nickname, il tap apre direttamente **Modifica un server**. Salvando senza nickname viene generato un valore `TIARCA####`; viene generato e mantenuto anche un ident privacy-friendly `TIARCA####`. Il nome utente personalizzato è ora una scelta esplicita nelle Informazioni sull'utente.
- Corretto il comportamento delle schermate con moduli quando è aperta la tastiera: **Modifica un server** e la personalizzazione tema restano scorribili e i campi non vengono più coperti dall'IME.
- Corretto il flusso **Scatta foto** alla prima esecuzione: TIARCA richiede ora il permesso fotocamera prima di avviare l'acquisizione.
- Modificato l'intervallo della pulizia automatica dei ban da 6–30 ore a 48–72 ore.
- Migliorati i dialog delle azioni di moderazione: i motivi predefiniti non duplicano più il campo testo ed è disponibile **Messaggio personalizzato…**. Aggiunta l’azione **Kickban ident**, che applica la mask `*!ident@*` e poi espelle l’utente.

## v0.7.1

- Resi cliccabili i nickname strutturati nei messaggi evento IRC JOIN, PART, QUIT, KICK, cambio nickname, MODE utente e TOPIC. Il tocco riutilizza il pannello utente esistente con contesto canale e fallback WHOIS/WHOWAS.

## v0.6.6

- Corretto il focus delle conversazioni private: l'arrivo di un nuovo PVT non sposta più automaticamente la conversazione attiva, evitando di inviare per errore il testo al destinatario sbagliato.
- Corretta la barra di navigazione delle menzioni, che non si sovrappone più alle ultime righe della chat.
- Sistemato il drawer laterale con tastiera aperta: la lista di server, canali e conversazioni resta utilizzabile e scorre correttamente nello spazio visibile sopra l'IME.
- Aggiunto anche al comando **Kick** il selettore delle motivazioni predefinite già disponibile per Kickban e TBAN.
- Rimossa la doppia freccia/indicatore grafico nei selettori delle motivazioni, lasciando un solo indicatore.
- Aggiunto l'upload diretto delle immagini presenti negli appunti tramite **SimoSnap**: incollando un'immagine nel campo messaggio viene avviato lo stesso flusso di upload e viene inserito/inviato il relativo link.
- Aggiunto il supporto al rich content di Gboard: toccando direttamente la miniatura di un'immagine negli Appunti della tastiera, TIARCA la inoltra allo stesso flusso SimoSnap senza richiedere il comando Incolla.
- Il visualizzatore multimediale interno supporta ora la riproduzione delle **GIF animate**.
- Aggiunta diagnostica mirata per le disconnessioni IRC in background su rete mobile: vengono tracciati lifecycle del servizio, cambi rete, stato socket/reader, ricezione PING, invio/fallimento PONG e principali eccezioni di rete, senza registrare credenziali o token sensibili. La causa definitiva del timeout in background resta da verificare su dispositivo reale.
- La funzione **Pulizia 6–30 ore** della lista ban esclude ora tutti gli extban `u:` e tutti i ban con ident specifico, anche quando contengono anche un host specifico; restano candidati alla pulizia automatica i normali ban basati sull'host, se rispettano gli altri criteri temporali e di sicurezza.
- Aggiunto il long press alle voci del drawer laterale per **query/PVT, canali e server**, riutilizzando le stesse azioni contestuali già disponibili con il long press sulla riga superiore. Funzione verificata sul dispositivo.
- Modificato il comportamento del gesto/pulsante **Indietro**: da chat, PVT o scheda server si torna prima a **Gestisci server**; un secondo Indietro da Gestisci server lascia l'app. Eventuali drawer aperti vengono chiusi prima di applicare questa navigazione. Funzione verificata sul dispositivo.
- Corretto il routing degli eventi **QUIT** dopo l'uscita da un canale: i QUIT degli utenti appartenenti al canale appena lasciato non vengono più riversati impropriamente nella scheda SERVER. Correzione verificata sul dispositivo.
- Ritoccato il **tema scuro predefinito TIARCA**, mantenendo l'impostazione grafica precedente ma con colori più accesi e leggibili.
- Corretto il crash all'avvio introdotto durante l'aggiornamento del tema scuro; la palette aggiornata resta attiva. Correzione verificata sul dispositivo.
- Aggiunto un workflow GitHub Actions per produrre build release dal repository.
- Rafforzata la compatibilità delle build release con Gson/R8 tramite regole ProGuard dedicate; successivamente la minificazione release è stata disattivata per evitare regressioni di serializzazione nelle configurazioni persistenti.
- Aggiunta `distribution/` al `.gitignore` per evitare di versionare gli artefatti locali di distribuzione.

### Funzioni già presenti nel sorgente iniziale della v0.6.6 ma non databili con certezza rispetto alla v0.5.5.38

Le seguenti funzioni risultano presenti nel primo sorgente GitHub della v0.6.6, ma il repository non contiene lo storico precedente necessario per dimostrare in quale revisione siano state introdotte. Vanno quindi considerate **non verificate cronologicamente** rispetto alla v0.5.5.38:

- strumenti operatore da WHOIS, inclusi Kick, Kickban host, TBAN, Mute host, Audi ident, rimozione del ban host e Voice;
- condivisione di immagini, file, audio e video tramite link temporanei SimoSnap;
- ricerca nei messaggi, menzioni e contatori non letti;
- catalogo di network IRC e configurazione multiserver.

## v0.5.5.38

- Aggiunte fino a cinque motivazioni predefinite e modificabili per Kickban e TBAN.
- Valori iniziali: `no hot`, `non ripetere`, `no annunci`, `no disagio`; il quinto campo è libero.
- La nuova sezione **Azioni operatore** nelle impostazioni permette di modificare, svuotare o sostituire ciascuna motivazione.
- Nei dialoghi Kickban e TBAN un selettore inserisce rapidamente il motivo scelto nel campo di testo.
- Il motivo selezionato rimane modificabile prima della conferma e resta disponibile l’opzione **Motivo personalizzato**.
- Le motivazioni vuote o duplicate non vengono mostrate nel selettore.

## v0.5.5.37

- Corretta la chiusura delle conversazioni private dopo che l’interlocutore ha cambiato nickname.
- Il comando **Chiudi conversazione** rimuove ora sia il nickname visibile sia il vecchio nome ancora conservato internamente da chatlib 0.3.3.
- Gestiti anche più cambi nickname consecutivi e il caso in cui esistano voci interne sia per il vecchio sia per il nuovo nickname.
- La chiusura non elimina la cronologia della conversazione, che rimane disponibile nella chat history.

## v0.5.5.36

- Corretto il salvataggio degli indirizzi di un server: sostituendo o riordinando la lista, il vecchio indirizzo primario non viene più reinserito automaticamente al primo posto.
- La lista viene ora normalizzata direttamente dai valori dell’editor, eliminando spazi, voci vuote e duplicati senza recuperare dati obsoleti.
- Il failover prova immediatamente il secondo, il terzo e gli eventuali altri server configurati, anche quando la riconnessione automatica generale è disabilitata.
- Il normale intervallo di riconnessione viene applicato soltanto dopo che tutti gli indirizzi del network hanno fallito una volta.
- Evitata la doppia elaborazione dello stesso errore di connessione, che poteva saltare un indirizzo della lista.

## v0.5.5.35

- Ripristinato il gestore IRC `NICK` originale della libreria, eliminando il wrapper introdotto nella v0.5.5.34 che poteva interferire con l’avvio della connessione.
- Conservata la gestione prudente dei cambi nickname tramite le notifiche utente già fornite dalla libreria e gli alias locali delle conversazioni private.
- Sostituite le due icone Tastiera duplicate nel menu Impostazioni: Alias di comando usa ora una lista di comandi, mentre Comandi rapidi usa un grande punto esclamativo.
- Condivisione e media usa ora un’icona dedicata con immagine e freccia di caricamento.

## v0.5.5.34

- Corretta la causa interna delle doppie conversazioni private dopo un cambio nickname in `chatlib` 0.3.3.
- Aggiunto un gestore locale del comando IRC `NICK` che conserva l’elaborazione originale ma rinomina anche la query nella struttura effettiva della connessione.
- La conversazione col nuovo nickname viene registrata prima di rimuovere quella vecchia, evitando chiusure temporanee, finestre vuote e successive riaperture duplicate.
- Scheda, destinazione dei messaggi, cronologia SQLite, bozza e stato UI convergono ora sul nuovo nickname.

## v0.5.5.33

- Impedita la ricomparsa della scheda vuota col vecchio nickname dopo la rinomina della conversazione privata.
- Durante la connessione viene mantenuto un alias dal vecchio al nuovo nick, applicato anche alle liste conversazioni obsolete ricevute in ritardo dalla libreria IRC.
- Eliminato lo stato dei messaggi non letti, delle menzioni e della notifica Android rimasto indicizzato sotto il vecchio nickname.
- Gestiti anche cambi nickname multipli consecutivi, facendo convergere tutti gli alias verso il nickname più recente.

## v0.5.5.32

- Corrette le conversazioni private quando l’interlocutore cambia nickname: la scheda aperta segue ora il nuovo nick invece di crearne una seconda al messaggio successivo.
- Il cambio nick rinomina anche la cronologia SQLite di tutti i giorni conservati, mantenendo i messaggi precedenti nella stessa conversazione.
- Se esiste già una conversazione col nuovo nickname, le due cronologie vengono unite senza sovrascrivere i messaggi.
- Bozze e cronologia dei testi inviati restano associate alla conversazione rinominata.
- La gestione riguarda soltanto una conversazione privata già aperta; i normali cambi nick nei canali continuano a essere trattati come prima.

## v0.5.5.31

- Inclusa una chiave applicativa TMDb predefinita: `!movie` funziona senza configurazione da parte dell’utente.
- Il campo **Chiave API TMDb** rimane disponibile per sostituire facoltativamente la chiave dell’app e continua a mascherare il valore personalizzato.
- Aggiunta nei crediti l’attribuzione richiesta da TMDb: il prodotto usa la relativa API ma non è approvato né certificato da TMDb.
- La chiave incorporata va considerata pubblicamente recuperabile, come ogni credenziale distribuita all’interno di un’applicazione Android.

## v0.5.5.30

- Corretto `!dizionario`, che in alcune voci non riusciva a ricavare la definizione dal formato interno di Wikizionario.
- La definizione viene ora estratta in via principale dall’HTML già elaborato da Wikizionario, evitando dipendenze eccessive dalla sintassi variabile dei template wiki.
- Conservato un parser del sorgente wiki come ripiego per la compatibilità con risposte MediaWiki incomplete.
- Aggiunti test automatici per l’estrazione della definizione, inclusa l’esclusione degli esempi annidati.

## v0.5.5.29

- Aggiunti i comandi rapidi configurabili `!wiki`, `!calc`, `!movie`, `!ora` e `!dizionario`.
- `!wiki` interroga l’API ufficiale di Wikipedia italiana e invia titolo, descrizione sintetica e collegamento.
- `!calc` valuta localmente espressioni con parentesi, decimali, potenze, percentuale/modulo e le quattro operazioni, senza eseguire codice arbitrario.
- `!movie` ricerca film in italiano tramite TMDb e restituisce titolo, anno, voto, trama sintetica e collegamento; richiede una chiave API TMDb v3 personale.
- `!ora` mostra data, ora e fuso del dispositivo oppure di una città o zona indicata.
- `!dizionario` legge la prima definizione italiana disponibile dal Wikizionario e aggiunge il collegamento alla voce.
- Aggiunta **Impostazioni → Comandi rapidi**, con interruttore generale, attivazione separata dei sei comandi incluso `!yt` e parola di attivazione personalizzabile per ciascuno.
- Le parole personalizzate vengono normalizzate e controllate per evitare valori non validi o duplicati.
- La chiave TMDb è inseribile dalle impostazioni e viene mascherata nell’interfaccia; non è incorporata nell’APK.
- I comandi conservano nella cronologia il testo digitato, elaborano in background e inviano il risultato nel canale o nella conversazione privata correnti.
- I backup precedenti rimangono compatibili e assumono automaticamente i valori predefiniti; le nuove preferenze entrano nei normali backup successivi.

## v0.5.5.28

- Corretta la gestione del `KICK` subito dal proprio nickname tramite un handler locale che sostituisce il comportamento difettoso della libreria IRC.
- Il messaggio di kick viene ora registrato nella cronologia prima della rimozione del canale.
- Quando si viene espulsi, vengono eliminate le presenze residue di tutti gli utenti associate al canale lasciato.
- Gli eventi `QUIT` successivi non vengono più attribuiti a un canale dal quale si è già stati espulsi e non dovrebbero più apparire impropriamente nella scheda **Server**.
- Eventuali `KICK` duplicati o arrivati in ritardo per un canale già rimosso vengono ignorati in sicurezza.

## v0.5.5.27

- Limitati a 20 i messaggi temporaneamente conservati in RAM per costruire ogni notifica Android; contatori non letti e menzioni restano invariati.
- Limitata a 200 righe la crescita in tempo reale della scheda **Server**; la cronologia persistente non viene cancellata.
- Le conversazioni rimaste fuori schermo per almeno un minuto rilasciano i messaggi renderizzati dalla RAM e ricaricano automaticamente gli ultimi 100 messaggi da SQLite quando vengono riaperte.
- Le chat non visibili non aggiornano più continuamente il proprio adapter: registrano la presenza di novità e ricaricano una finestra limitata quando tornano visibili.
- Limitate le cache tecniche degli avatar: 4 MB per i bitmap, 1.024 avatar mancanti, 512 richieste canale recenti e 2.048 associazioni nick/account WHOX.
- Limitate a 32 le richieste WHOX contemporaneamente pendenti.
- Il rilevamento dei collegamenti media utilizza ora al massimo due thread invece di un pool potenzialmente illimitato.
- Invariati cronologia su disco, contatori, servizio IRC, ricezione in background e comportamento delle notifiche visibili.

## v0.5.5.26

- Corretto **Chiudi conversazione** per le chat private aperte con bot e service dalla scheda Server.
- La conversazione del service viene ora rimossa localmente dalla lista delle schede e l’app torna correttamente alla scheda **Server**.
- La chiusura non invia più erroneamente un comando di uscita da canale per queste conversazioni private.
- Nella traduzione italiana, l’intestazione **Chiacchierata** è stata corretta in **Chat**.

## v0.5.5.25

- Corretto il tocco sui nickname dei bot/service nella scheda **Server**: ora apre effettivamente la conversazione privata dedicata.
- Risolto il conflitto Android tra la selezione del testo e la gestione dei collegamenti cliccabili, che nella versione precedente rendeva inerti i nickname.
- La selezione e la copia dei messaggi Server rimangono disponibili, così come i normali collegamenti presenti nel testo.

## v0.5.5.24

- I nickname dei bot e service IRC riconosciuti sono ora cliccabili direttamente nei messaggi della scheda **Server**.
- Un tocco sul nickname apre la conversazione privata dedicata e la rende visibile tra le schede dell’app.
- All’apertura vengono recuperati gli eventuali messaggi già presenti nella cronologia della conversazione.
- Rimane disponibile la pressione prolungata sul messaggio del service per copiarne il contenuto o aprire esplicitamente la chat privata.

## v0.5.5.23

- Spostata l’impostazione **Abilita l’invio diretto tramite DCC** da **Interfaccia** a **Condivisione e media**.
- Rimossa la voce DCC separata dal menu principale della conversazione privata.
- Quando l’opzione è abilitata, **Invia tramite DCC** appare nel normale menu **Invia**, insieme a immagini, video, audio e altri file.
- Il menu **Invia** rimane disponibile anche se sono disabilitati i caricamenti esterni ma è attivo DCC.
- L’invio DCC conserva esplicitamente il server e il destinatario selezionati, anche quando viene avviato dal pannello WHOIS.

## v0.5.5.22

- Aggiunta in **Impostazioni → Interfaccia → Lingua** la selezione tra **Sistema**, **Italiano** e **English**.
- **Sistema** rimane la scelta predefinita e segue automaticamente la lingua configurata sul dispositivo.
- Il cambio della lingua viene applicato immediatamente all’interfaccia e resta memorizzato ai successivi avvii.
- La preferenza viene inclusa nei normali backup; i backup delle versioni precedenti rimangono compatibili e usano automaticamente la lingua di sistema.

## v0.5.5.21

- Aggiunta la traduzione italiana completa dell’interfaccia: chat, impostazioni, gestione server, notifiche, temi, cronologia, condivisione media, DCC e funzioni operatore.
- Adattati manualmente i termini IRC per conservare nomi e significati tecnici come nick, host, ident, mask, ban, kick, half-op, voice, SASL, TLS e comandi raw.
- Tradotti plurali, messaggi dinamici e descrizioni mantenendo invariati segnaposto e riferimenti alle risorse Android.
- L’inglese rimane la lingua di riserva sui dispositivi configurati con lingue diverse dall’italiano.

## v0.5.5.20

- Corretto definitivamente **Channel modes**: la finestra appare subito, gestisce la risposta numerica `324`, scade dopo otto secondi e non lascia dialoghi pendenti.
- Ogni network può conservare più endpoint ordinati. In caso di errore l’app prova il successivo e, terminata la lista, riparte dal primo secondo le regole di riconnessione.
- La modifica di un server mostra gli indirizzi multipli come chip, mantenendo compatibilità con configurazioni e backup precedenti.
- Il catalogo offline nasconde i network con mediana inferiore a 100 utenti, pur mantenendoli nel database di verifica esterno.
- IRCnet usa in via prioritaria `ssl.irc.atw-inter.net:6697` con TLS e dispone di endpoint TLS alternativi.
- La schermata di configurazione server mostra inizialmente solo le opzioni essenziali; TLS, SASL, certificati, canali, comandi e codifica sono raccolti nelle impostazioni avanzate.
- Premendo il contatore `@N` si apre la menzione non letta più vecchia; la visita la rimuove dal conteggio, che passa progressivamente a `@N-1`.
- Le menzioni raggiunte lampeggiano tre volte per essere più riconoscibili.
- Migliorata la ricerca: evidenziazione colorata, conteggio, navigazione precedente/successiva, salto immediato al risultato e ripristino della posizione alla chiusura.
- Se è già aperta una conversazione privata con un service o bot riconosciuto, le sue risposte arrivano in quella chat; altrimenti continuano a essere raccolte nella scheda Server.
- Aggiunta in Settings la voce **About TIARCA**, con versione, origine da Revolution IRC, licenza GPLv3, crediti essenziali e nota sul servizio media esterno.

## v0.5.5.19

- Gestione prudente dei service IRC nella scheda Server.
- Mentions limitate alle 50 più recenti e conteggi separati.
- Ritorno a Manage servers dopo il salvataggio dal catalogo o manuale.

---

# Storico precedente

## v17

- Aggiunto in **Ban list** il comando rapido **Clean up 6–30h** per selezionare e rimuovere i ban temporanei accumulati tra 6 e 30 ore prima.
- La pulizia considera soltanto mask su ident o host, comprese le varianti `m:` e `u:`, ed esclude sempre `j:`, `R:`, extban sconosciuti, mask senza data e ban fuori intervallo.
- Prima della rimozione vengono mostrati il numero di voci interessate, una conferma e la possibilità di rivedere o modificare la selezione.
- Le impostazioni grafiche correnti diventano i valori predefiniti per le nuove installazioni: tema scuro nero, testo chat a 12, eventi join/part nascosti e orario `[HH:mm.ss]`.
- Aggiunto **Simosnap** come server iniziale nelle installazioni senza server configurati, con `irc.simosnap.com`, porta TLS `6697`, UTF-8 e riconnessione ai canali.
- Il server preinstallato non contiene dati personali e non sovrascrive configurazioni esistenti o ripristinate da backup.

## v16

- Corretto un blocco dell'app dopo lunghi periodi in background, eliminando un deadlock nel salvataggio SQLite della cronologia.
- Corretta la perdita progressiva di risorse durante il salvataggio dei messaggi.
- Aggiunta la voce **Mentions** nei canali e nelle conversazioni private, con navigazione tra menzioni non esaminate.
- Aggiunti il conteggio separato delle menzioni, **Mark as read** e **Mark all mentions as read**.
- Per arretrati superiori a 99 messaggi è possibile scegliere se andare al primo non letto o alla fine della conversazione.

## v15

- Aggiunta **List channels** nel menu del server.
- Migliorate la scheda **Members** e l'autocompletamento dei nickname, anche quando la lista `NAMES` del server è incompleta.
- Descrizioni dei **Channel Modes** adattate al software del server, con profili InspIRCd 3, InspIRCd 4 e generici.
- Prima di condividere un file viene creata e aperta la conversazione privata del destinatario.

## v14

- Avatar WHOIS rifinito con formato quadrato e allineamento coerente con intestazione e testi.

## v13

- Avatar degli utenti nella scheda **Members** e avatar grande nel WHOIS, quando disponibili su Simosnap.
- Recupero degli account tramite WHOX per associare nickname e avatar.
- **Message user** riapre conversazioni presenti nella cronologia anche con utente offline.
- Corretto il crash all'apertura della scheda **Channel Modes**.

## v12

- Aggiunta la sezione **Sharing and media** nelle impostazioni, con opzioni separate per condivisione e acquisizione.
- Compatibilità mantenuta con i backup precedenti.
- Migliorata la registrazione video, compresa la messa a fuoco automatica continua della fotocamera posteriore.

## v11

- Registratore video interno più compatibile, con dimensioni e bitrate ridotti.
- Migliorata la gestione di orientamento, fotocamera e file temporanei.
- Correzioni alla riproduzione dei video caricati su Simosnap.

## v10

- Aggiunto **WHOIS** nel menu delle conversazioni private.
- Menu di invio disponibile nel WHOIS e nelle conversazioni private.
- Aggiunte le azioni **Scatta foto**, **Registra video** e **Registra vocale**.
- Le foto vengono ricodificate prima del caricamento, eliminando metadati EXIF e geolocalizzazione.
- Immagini, audio e video riconosciuti possono essere aperti direttamente dalla chat.

## v9

- Condivisione di immagini, audio, video e file tramite Simosnap.
- Supporto a `EXTJWT` con compatibilità di ripiego per le reti che non lo forniscono.
- Conferma di nome, dimensione e destinatario, controlli su file e avanzamento del caricamento.

## v8

- Comando `!yt <ricerca>` per pubblicare titolo e collegamento di un risultato YouTube tramite Invidious.
- Aggiunta la gestione visuale dei **Channel Modes**, con modi attivi già selezionati e spiegazioni.

## v7

- Pannello della lista ban del canale, con selezione multipla e ordinamento per host/mask, autore e data.

## v6

- Ricerca messaggi nei canali e nelle conversazioni private.
- Aggiunta l'azione operatore **Voice**.
- Conferma e modifica della mask per TBAN e Remove ban.

## v5

- Pannello operatore visibile solo con privilegi sufficienti nel canale: halfop, operatore, amministratore o proprietario.
- WHOIS da nickname con canale di provenienza automatico; WHOIS manuale con scelta del canale.
- Azioni host/ident disponibili anche con dati WHOWAS dopo una disconnessione rapida.

## v4

- Correzioni ai blocchi dopo lunga permanenza in background.
- Migliorata la sincronizzazione tra servizio IRC, conversazioni, contatori e notifiche.
- Migliorati ping, rilevamento connessioni inattive e riconnessione.

## v3

- Corretta la sintassi del ban temporaneo: `TBAN #canale 3h mask`.
- Kickban corretto: prima ban, poi KICK con motivo facoltativo.
- Evitati doppi KICK e l'errore IRC `441 They are not on that channel`.

## v2

- Aggiunto il pannello di azioni operatore nel WHOIS aperto da un canale.
- **Kick**, **Kickban host**, **TBAN host**, **Mute host**, **Audi ident** e rimozione del ban host esatto.
- Le azioni che richiedono host o ident attendono i dati WHOIS.

## v1

- Click su nickname nei messaggi e nella lista membri per aprire direttamente il WHOIS.
- Passaggio del canale di provenienza alla scheda WHOIS.
- Prime correzioni di compatibilità con Gradle, Android Studio e Flexbox.

## v0

- Base iniziale: Revolution IRC 0.5.5.
- Funzioni IRC originali: server, canali, conversazioni private, notifiche, cronologia e personalizzazione dell'interfaccia.
