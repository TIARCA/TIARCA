# AutoMod unificato — A + B

Script: [automod_unificato.lua](../../lua-examples/automod_unificato.lua).

**Importante:** disabilitare i precedenti `automod_antiripetizione.lua` e `automod_annunci.lua` sulle reti dove si utilizza quello unificato. Tenerli attivi in parallelo provocherebbe doppie valutazioni e potenziali sanzioni duplicate.

## Avvio

1. Importare lo script da **Impostazioni → Script Lua** e scegliere le reti.
2. Autorizzare dati persistenti, lettura utenti/identità, invio e moderazione (le ultime due autorizzazioni sono necessarie soltanto per le azioni live).
3. Eseguire `/lua mod canali add #amicizia` nel canale desiderato.
4. Verificare `/lua mod stato`. Il sistema parte in modalità **monitor**; A e B sono attivi per impostazione predefinita.
5. **Soltanto dopo i test:** `/lua mod modo live` abilita avvisi e provvedimenti.

Comandi: `/lua mod on|off`; `/lua mod modo monitor|live`; `/lua mod motore a|b on|off`; `/lua mod canali add|drop #canale`; `/lua mod stato`; `/lua mod reset`; `/lua mod close`. I comandi `/lua b` sono mantenuti come alias per le opzioni supportate.

## Rilevamento

- **A**: messaggi di almeno tre parole, somiglianza >=75% e tre occorrenze negli ultimi otto messaggi conservati, nell'arco di 30 minuti; dopo la prima infrazione A può segnalare un'altra ripetizione.
- **B**: annunci riconosciuti anche con categorie differenti. Dal secondo annuncio in 20 minuti, soltanto con concentrazione >=2/3 sugli ultimi dieci messaggi valutati.
- **Micro-annunci** iniziali: `F?`, `donne?`, `mxm`, `qualcuna?`. Le espressioni ambigue `cerco` e `ho voglia` non bastano da sole.
- **Saluti neutri** come `ciao a tutti`, `buongiorno a tutti`, `buonasera a tutti` sono esclusi da A e non diluiscono la concentrazione di B.
- Quando entrambi i motori riconoscono una violazione sullo stesso messaggio, B ha precedenza: una sola infrazione e una sola escalation.

**Attenzione:** A mantiene una cronologia di confronto in RAM. Lo storico persistente B mantiene livello e identità ma il comportamento dopo riavvio completo dell'app deve essere verificato manualmente.

## Escalation comune (24 ore dall'ultima infrazione)

1. Avviso in PVT.
2. Mute temporaneo 2 minuti e spiegazione in PVT.
3. Mute temporaneo 15 minuti e spiegazione in PVT.
4. Mute temporaneo 3 ore, kick e spiegazione in PVT.
5. Ban temporaneo 24 ore, kick e spiegazione in PVT.

Motivazione di ogni kick: **`Messaggi ripetuti`**. Con rete compatibile e account NickServ verificato il ban/mute temporaneo preferisce l'account esatto; altrimenti si usa l'host completo. Le azioni sono **richieste** all'IRCd: un comando inviato non dimostra l'avvenuta esecuzione.

Di default, utenti registrati e `+v` sono monitorati; halfop `%` e privilegi superiori sono esclusi.

## Whitelist

Usare `/lua b whitelist add NICK` **all'interno di un canale monitorato** nel quale sia disponibile la lista degli utenti: l'account NickServ deve essere osservabile e verificabile. Disponibili anche `drop NICK` e `list`. Le esclusioni sono persistenti, per account e rete, e il Collector V3 le riceve in sola lettura quando entrambi gli script sono importati con i nomi indicati. Account con lo stesso nome su reti diverse non sono considerati automaticamente la stessa identità.

## Chiusura PVT

`/lua mod close` apre una finestra Android con i PVT delle reti, preseleziona quelli ancora attribuiti agli script e permette di scegliere individualmente, selezionare tutto oppure annullare. Dopo un riavvio il riconoscimento dell'origine può essere incompleto: non vengono preselezionati PVT non verificati. Non si inviano messaggi IRC.

## Verifiche

Prima di live verificare su Candidate: identità e cambi nick, due categorie B differenti, falsi positivi sui saluti, voice, contatori e loro persistenza, TBAN/account extban, esito dei comandi IRC, blacklist/whitelist, backup e ripristino, chiusura dei PVT. **Accuratezza e test reale in chat: non verificati.**
