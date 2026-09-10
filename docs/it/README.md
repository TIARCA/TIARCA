# Guida completa a TIARCA 0.9.3

[← Indice lingue](../README.md)

TIARCA è un client IRC per Android pensato sia per l'uso quotidiano sia per chi vuole accedere alle funzioni IRC più avanzate senza rinunciare a un'interfaccia moderna.

## 1. Installazione e primo avvio

Scarica l'APK firmato dalla sezione Releases del repository e installalo su Android. Al primo avvio puoi creare una nuova configurazione server/network. TIARCA supporta più server e mantiene separati configurazione, conversazioni e stato delle diverse connessioni.

![configurazione server](../images/12_configurazione_server.jpg)

## 2. Configurazione di server e autenticazione

Nella configurazione puoi impostare nome della rete/server, indirizzo, porta, nickname e parametri di connessione. Sono supportate connessioni TLS/SSL e autenticazione SASL. I canali da raggiungere dopo la connessione possono essere configurati e modificati. È inoltre possibile riordinare manualmente i server/network nell'interfaccia.

Le password e le credenziali sono dati sensibili: non condividerle negli screenshot o nei report di bug.

## 3. Interfaccia principale

Il drawer laterale raccoglie server, canali e conversazioni private. La barra superiore consente di passare rapidamente tra le conversazioni aperte. I contatori distinguono i normali messaggi non letti dalle menzioni. **Utenti monitorati** è disponibile nella parte superiore del drawer; **Impostazioni** rimane in fondo al menu `…`.

![drawer/menu](../images/01_drawer_menu.jpg)

## 4. Canali IRC

Una volta entrato in un canale puoi leggere e inviare messaggi, usare autocomplete per nickname/canali/comandi, formattazione e colori mIRC e consultare la cronologia conservata dall'app. Le normali operazioni IRC continuano a rispettare permessi e risposte del server.

![chat canale](../images/02_chat_canale.jpg)

## 5. Conversazioni private (PVT)

I PVT sono conversazioni dirette con un nickname. Nella toolbar sono disponibili azioni rapide **Invia** per file/media e **Ignora**, oltre al menu `…`. Quando sei in caller-ID mode `+g` e apri volontariamente un PVT, TIARCA gestisce automaticamente `ACCEPT +nickname`, permettendo all'interlocutore di rispondere senza un passaggio manuale aggiuntivo.

![PVT](../images/03_chat_privata_pvt.jpg)

## 6. WHOIS e WHOWAS

WHOIS mostra le informazioni fornite dal server su un utente e offre azioni dirette. TIARCA presenta inoltre WHOWAS in forma strutturata invece di lasciare le informazioni soltanto nel log grezzo del server. I dati disponibili dipendono dal demone IRC e dai servizi della rete.

![WHOIS](../images/04_whois.jpg)

## 7. Modalità utente

TIARCA dispone di un editor delle modalità utente. I mode vengono interpretati in base alle capacità e alla famiglia del server quando possibile; l'app conosce profili per implementazioni IRC diffuse e protegge le modalità che devono essere gestite dal server o dai services. Tra i mode supportati dall'interfaccia rientra caller-ID `+g` quando disponibile sulla rete.

![modalità utente](../images/06_modalita_utente.jpg)

## 8. Modalità canale

L'editor dei channel mode mostra stato e descrizione delle modalità disponibili. TIARCA evita di trattare tutti i server come identici: parametri e semantica possono variare tra implementazioni IRC. L'effettiva possibilità di modificare un mode dipende comunque dai privilegi concessi dal server nel canale.

![modalità canale](../images/07_modalita_canale.jpg)

## 9. Ban ed eccezioni

La moderazione include una gestione unificata delle liste di ban e delle eccezioni di canale. Puoi consultare le maschere restituite dal server e, quando disponi dei privilegi necessari, modificarle dall'interfaccia. Le regole effettive restano quelle della rete IRC a cui sei connesso.

![ban/eccezioni](../images/08_ban_eccezioni.jpg)

## 10. MONITOR e utenti monitorati

TIARCA supporta il comando IRC `MONITOR` sulle reti che lo implementano. La schermata **Utenti monitorati** permette di gestire l'elenco e visualizzare lo stato conosciuto dei nickname monitorati. Lo stato viene mantenuto in modo persistente dall'app dove previsto.

![utenti monitorati](../images/05_utenti_monitorati.jpg)

## 11. Caller-ID +g e ACCEPT

Sulle reti compatibili, `+g` limita chi può inviarti messaggi privati. TIARCA gestisce il numeric 718 e offre l'azione ACCEPT per autorizzare un utente. Dalla 0.9.2, se sei effettivamente in `+g` e sei tu ad avviare il PVT, l'utente viene aggiunto automaticamente alla accept list. Se `+g` non è attivo, l'apertura del PVT non aggiunge inutilmente il nickname alla lista.

## 12. Ricerca, cronologia e menzioni

La ricerca individua messaggi nella cronologia e consente di raggiungere il risultato nel suo contesto temporale. Le menzioni hanno un contatore separato dai normali non letti, così i messaggi che richiedono attenzione non vengono confusi con l'attività generale dei canali.

![ricerca messaggi](../images/09_ricerca_messaggi.jpg)

## 13. Ignore

La lista Ignore consente di filtrare utenti indesiderati. Nei PVT l'azione **Ignora** è direttamente accessibile dalla toolbar; le stesse funzioni sono integrate anche nelle informazioni utente dove appropriato.

## 14. File, immagini, audio e video

TIARCA può condividere contenuti multimediali e file tramite link temporanei. Nei PVT l'azione **Invia** è disponibile direttamente nella barra superiore. Prima di condividere materiale, considera che il link risultante viene inviato attraverso IRC e può quindi essere visto dai destinatari della conversazione.

## 15. Comandi IRC e comandi rapidi

Oltre all'invio di comandi IRC standard, TIARCA gestisce direttamente numerosi comandi nativi: `/whowas`, `/accept`, `/invite`, `/ison`, `/userhost`, `/motd`, `/version`, `/time`, `/admin`, `/info`, `/lusers`, `/links`, `/stats`, `/knock`, `/list`, `/names` e `/who`.

I **Comandi rapidi** sono scorciatoie configurabili che trasformano parole di attivazione in azioni/ricerche. La configurazione include comandi come `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` e `!dizionario`; possono essere personalizzati nelle impostazioni.

![comandi rapidi](../images/14_comandi_rapidi.jpg)

## 16. Aspetto e formato dei messaggi

TIARCA offre tema chiaro e scuro e un'ampia personalizzazione dei colori dell'app. Puoi intervenire anche sul font della chat e sul formato dei messaggi, compresa la visualizzazione opzionale dell'orario sul lato destro. Le preferenze consentono di adattare l'interfaccia senza cambiare il protocollo IRC sottostante.

![impostazioni interfaccia](../images/10_impostazioni_interfaccia.jpg)
![personalizzazione colori](../images/13_personalizzazione_colori.jpg)
![formato messaggi](../images/11_formato_messaggi.jpg)

## 17. Aggiornamenti

Il controllo aggiornamenti integrato è opt-in e può verificare periodicamente la presenza di nuove release ufficiali. L'installazione è assistita, mentre gli APK ufficiali restano pubblicati su GitHub Releases. Verifica sempre che il download provenga dal repository TIARCA.

## 18. Backup e trasferimento dispositivo

TIARCA supporta il backup Android cifrato/trasferimento dispositivo mantenendolo distinto dal backup manuale dell'app. La disponibilità concreta delle funzioni Android può dipendere dalla versione del sistema e dalla configurazione del dispositivo.

## 19. Compatibilità IRC

IRC non è un unico server software: network differenti possono usare InspIRCd, UnrealIRCd, Solanum, Ergo, IRCnet o altre implementazioni. TIARCA cerca di interpretare modalità e numerics in modo compatibile e strutturato, ma una funzione server-side può essere disponibile soltanto se la rete la implementa.

## 20. Risoluzione dei problemi

**Non riesco a connettermi:** controlla host, porta, TLS, nickname e credenziali SASL.  
**Un mode non può essere modificato:** potresti non avere i privilegi necessari oppure il mode può essere gestito dal server/services.  
**MONITOR non funziona:** verifica che il network supporti IRC MONITOR.  
**Un comando restituisce errore:** disponibilità e sintassi possono variare in base al server.  
**Non ricevo aggiornamenti:** controlla che il controllo aggiornamenti sia abilitato e che il dispositivo possa raggiungere GitHub.  
**Devo segnalare un bug:** indica versione TIARCA, versione Android, server IRC/implementazione se nota e i passaggi per riprodurlo; rimuovi password, token, IP/host personali e altri dati sensibili.

## 21. Privacy e sicurezza

IRC non va considerato automaticamente privato o cifrato end-to-end. TLS protegge il collegamento tra client e server quando utilizzato, ma non trasforma IRC in un sistema E2EE. Non pubblicare password, token SASL o informazioni personali nei canali, negli screenshot o nelle issue GitHub.

## 22. Licenza e progetto originale

TIARCA deriva da Revolution IRC di MrARM/MCMrARM e ne continua lo sviluppo come progetto indipendente. Il codice è distribuito secondo GNU GPLv3 e conserva le attribuzioni applicabili al progetto originale e alle dipendenze.
