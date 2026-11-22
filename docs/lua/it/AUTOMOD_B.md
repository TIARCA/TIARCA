# AutoMod B — riconoscimento degli annunci ripetitivi

Script indipendente: [automod_annunci.lua](../../lua-examples/automod_annunci.lua). Non sostituisce l'AutoMod antiripetizione generale. Va importato separatamente da **Impostazioni → Script Lua**, autorizzando dati persistenti, lettura membri e identità e, se si vogliono le azioni, invio e moderazione.

## Configurazione

Da una scheda server della rete scelta:

- `/lua b help` — istruzioni.
- `/lua b canali add #amicizia` e `/lua b canali add #chatitaly` — selezionare i canali.
- `/lua b on` — attivare il monitoraggio degli eventi sui canali scelti.
- `/lua b stato` — mostrare stato, canali e parametri.
- `/lua b libreria` — mostrare le famiglie riconosciute.
- `/lua b modo monitor` — simulare l'escalation senza azioni.
- `/lua b modo live` — **abilitare espressamente** gli interventi reali.
- `/lua b reset` — ricominciare la valutazione senza cancellare le preferenze.

**Impostazione iniziale:** OFF, nessun canale, modalità monitor. Cambiare modalità o resettare avvia una nuova epoca di valutazione, senza ereditare livelli di escalation di un precedente test shadow.

## Regole operative

Il motore B identifica una **prima ripetizione** quando uno stesso utente invia almeno due messaggi riconosciuti dallo stesso schema entro **20 minuti**, anche con sole **due parole**. Solo in quel momento valuta retroattivamente fino agli ultimi **10 messaggi** dell'utente (inclusi quelli che hanno attivato il controllo). La concentrazione è sufficiente quando i messaggi riconosciuti sono almeno **2/3**, calcolati senza arrotondare. Dopo la prima infrazione, ogni nuovo messaggio riconosciuto può avanzare di livello se la concentrazione resta sufficiente. Non serve una seconda coppia.

La memoria dell'escalation dura **24 ore dall'ultima infrazione** ed è unica per l'utente sulla stessa rete e sui canali abilitati. La categoria di annuncio può cambiare senza azzerare il livello.

| Livello | Azione |
|---|---|
| 1 | PVT: «non ripetere su #canale» |
| 2 | `tiarca.tmute`, 2 minuti; spiegazione in PVT |
| 3 | Kick: «non ripetere su #canale, rischi il ban» |
| 4 | `tiarca.tmute`, 3 ore, mask `m:*!*@host` (InspIRCd) |
| 5 | `tiarca.kickban`, motivo «non ripetere» |

Il mute temporaneo viene affidato al server (`TBAN` su InspIRCd); non viene rimosso dall'app al termine. Il client controlla rete, permessi, ruolo operatore, presenza, host corrente ed extban. La registrazione della richiesta **non prova** che il server l'abbia eseguita.

## Libreria e identificazione

Le regole B01–B10 e B14–B16 sono candidate operative; B11–B13 sono ancora **solo osservazione** e non incrementano la concentrazione. Ogni messaggio conta al massimo una volta anche se soddisfa più regole.

Identità: account autenticato, altrimenti host completo o ident distintivo, con protezioni per account differenti e ident concorrenti. Il cambio di nick comunicato dal server mantiene la continuità della sessione. La semplice somiglianza dei nick non vale come prova. I profili con prefissi di canale (voice, op, ecc.) sono esclusi dagli interventi.

Lo script conserva soltanto identificativi osservati, marcatori delle ultime 10 classificazioni e timestamp, **non il testo integrale di tutti i messaggi**. I limiti per sicurezza/memoria comprendono 360 identità e 12 partizioni persistenti da 8 KiB. In assenza di identità affidabile, permessi o spazio persistente, la moderazione non procede. I normali messaggi IRC non sono disponibili in modo completo nei vecchi export del Collector; pertanto accuratezza e falsi positivi restano **non verificati** senza test su una Candidate.

## Verifiche prima del modo live

Provare in un canale di test: 2 occorrenze, cambio nick, riconnessione con stesso ident/host, due account diversi dietro lo stesso host, 10 messaggi e soglia 2/3, finestra 20 minuti, ripresa delle 24 ore dopo riavvio, cinque livelli, risposta IRC del comando TBAN e autorizzazioni negate.

Anche con AutoMod A in funzione, due script possono rilevare lo stesso messaggio: prima dell'uso live verificare le impostazioni di entrambi per evitare doppie sanzioni.
