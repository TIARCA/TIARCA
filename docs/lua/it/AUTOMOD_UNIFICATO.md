# AutoMod V4 — Watchdog + Mod

Script da importare: [automod_v4.lua](../../lua-examples/automod_v4.lua).

**Non attivare contemporaneamente** \`automod_v4.lua\`, \`automod_unificato.lua\`, \`automod_antiripetizione.lua\` o \`automod_annunci.lua\` sulle stesse reti: produrrebbero rilevamenti e interventi duplicati. **Harvester/Collector è facoltativo e separato**: non serve per usare AutoMod V4.

## Avvio sicuro

1. Importare **automod_v4.lua** da Impostazioni → Script Lua; scegliere le reti e autorizzare archivio persistente e lettura degli utenti. Invio e moderazione servono per la modalità live.
2. Digitare \`/lua mod canali add #amicizia\` per ciascun canale da monitorare. Per impostazione predefinita nessun canale è attivo.
3. Digitare \`/lua mod stato\`: per impostazione predefinita è attiva la modalità **monitor**, senza avvisi PVT o provvedimenti.
4. Verificare i rilevamenti e la Candidate prima di usare \`/lua mod modo live\`. Passare a live non converte automaticamente i rilevamenti precedenti in infrazioni.

## Comandi raggruppati

| Comando | Scopo |
|---|---|
| \`/lua mod help\` | Comandi principali |
| \`/lua mod help watchdog\` | Motori e parametri |
| \`/lua mod help azioni\` | Escalation |
| \`/lua mod help whitelist\` | Esclusioni |
| \`/lua mod stato\` | Stato della rete |
| \`/lua mod on\`, \`/lua mod off\` | Abilita/disabilita |
| \`/lua mod modo monitor\`, \`/lua mod modo live\` | Osservazione o provvedimenti |
| \`/lua mod motore ripetizioni on|off\` | Motore generico (alias \`a\`) |
| \`/lua mod motore annunci on|off\` | Motore annunci (alias \`b\`) |
| \`/lua mod canali add #canale\`, \`drop #canale\`, \`list\` | Canali per rete |
| \`/lua mod parametro somiglianza 75\` | Percentuale per ripetizioni (50–100) |
| \`/lua mod parametro finestra 15\` | Minuti della finestra (1–120) |
| \`/lua mod parametro credito 2\` | Credito massimo negativo: 0, −1, −2 o −3 |
| \`/lua mod parametro minparole 3\` | Lunghezza minima ripetizione generica (3–12) |
| \`/lua mod whitelist add NICK\` | Aggiunge l'account NickServ osservato |
| \`/lua mod whitelist drop NICK\`, \`list\` | Rimuove/elenca |
| \`/lua mod reset\` | Azzera cronologia Watchdog e livello di escalation della rete |
| \`/lua mod close\` | Finestra Android di selezione dei PVT da chiudere |

Tutti i comandi appartengono alla famiglia **\`/lua mod\`**. \`/lua mod close\` viene intercettato dall'interfaccia di TIARCA; non cancella cronologia né invia comandi IRC.

## Watchdog: due riconoscimenti diversi

**Ripetizioni generiche (A):** tre occorrenze simili, soglia iniziale **75%** configurabile, misurata sulle parole normalizzate comuni secondo \`200 × comuni / (parole1 + parole2)\`. L'ordine delle parole non conta. Soli saluti, comandi e annunci non vengono trattati come ripetizioni generiche. Non basta che la seconda frase sia simile: la terza occorrenza deve completare il rilevamento.

**Annunci e microannunci (B):** classificazione **Sì/No**, senza percentuale di somiglianza. Espressioni differenti dello stesso utente alimentano un unico contatore: \`mxm reale\`, \`cerco m\`, \`donne pvt\`, \`f per conoscenza\`, \`calabria nessuna\` sono esempi della stessa categoria di rilevamento. Soglia: **seconda occorrenza**.

Le regole B combinano:
- **persone:** \`f\`, \`m\`, \`mxm\`, \`ragazza/e\`, \`ragazzo/i\`, \`donna/e\`, \`uomo/ini\`, \`qualcuna/o\`, \`nessuna/o\`, \`coetanea/i\`;
- **ricerca:** \`cerco\`, \`cerca\`, \`cercasi\`, \`chi\`, \`qualche\`, \`qualke\` e altre varianti indicate nel codice;
- **chat/contatto:** \`chat\`, \`chatta\`, \`conoscenza\`, \`pvt\`, \`priv\`, \`scrivimi\`, \`scrivetemi\`, \`telegram\`, \`whatsapp\`, ecc.;
- **incontri/località:** \`reale\`, \`incontro\`, \`ospito\`, nomi di località contenuti nel dizionario dello script, oppure il codice \`m09\`/\`f07\`.

Non basta una parola come \`roma\` o \`parlare\` per diventare annuncio: serve una combinazione prevista. Sono microannunci anche alcune **parole singole** (\`f?\`, \`donne?\`, \`qualcuna?\`, \`mxm\`) e **messaggi composti interamente di numeri**, anche con separatori: \`08\`, \`08 09\`, \`07/08/09\`, \`010 011\`. Anche una data composta soltanto da numeri può essere riconosciuta: è un falso positivo possibile da verificare nei test.

## Credito conversazionale

Il punteggio è separato per canale e per identità di rete. Ogni annuncio riconosciuto aumenta di 1 il punteggio. Un messaggio normale di **almeno tre parole** lo diminuisce di 1, fino al valore minimo configurabile: **−2** inizialmente, oppure −3 per maggiore tolleranza. Gli esempi \`ahah ahah ahah\` e \`ok va bene\` possono generare credito: per ora non facciamo filtraggio semantico dei riempitivi.

Non generano credito: saluti semplici (anche \`ciao a tutti\`), messaggi che iniziano con \`!\`, annunci, messaggi non-annuncio di una o due parole, soli simboli/emoji o URL brevi. Un saluto seguito da una presentazione articolata, per esempio \`ciao a tutti mi presento sono matteo\`, **può** generare credito.

La prima occorrenza di una frase generica viene conservata come possibile riferimento: se viene successivamente ripetuta con la somiglianza richiesta, il credito iniziale eventualmente attribuito viene annullato e la sequenza è trattata come ripetizione, non come conversazione. Non sono conservate cronologie testuali illimitate: Watchdog mantiene il riferimento e i contatori correnti in RAM.

La finestra iniziale è **15 minuti**, configurabile. Il punteggio scade quando non arrivano messaggi rilevanti nella finestra; annunci e riferimenti di ripetizione hanno inoltre scadenze proprie basate sull'ultima rispettiva occorrenza. I contatori brevi di Watchdog **non sono persistiti** dopo il riavvio del processo; lo storico di Mod sì.

## Identità, protezioni e persistenza

L'identità viene individuata **per rete IRC**: account NickServ verificato se presente, altrimenti host completo e ident distintivo quando affidabile; in assenza di ident affidabile viene usato l'host esatto, ma collisioni osservabili fra nickname vengono sospese per sicurezza. **Contatori e rilevamento sono separati per canale**, mentre l'escalation di Mod è per identità di rete.

Halfop (\`%\`) e privilegi superiori non vengono moderati, mentre voice e registrati possono esserlo. Crys autenticato come **Micaela** è protetto per impostazione predefinita. La whitelist aggiuntiva è per account autenticato e rete: \`/lua mod whitelist add NICK\` richiede che l'account sia osservabile in un canale monitorato.

V4 conserva **solo le infrazioni di Mod** nello storage persistente, non tutti gli utenti che parlano: nessun limite artificiale a 360 identità. Il runtime impone comunque **24 KiB di sorgente, 32 chiavi storage, 8 KiB per valore e 128 KiB totali**. In caso di quota esaurita o archivio non affidabile, V4 segnala il problema e sospende i provvedimenti, senza cancellare in silenzio gli utenti monitorati. La lista membri IRC è limitata a 128 voci nello snapshot Lua, ma TIARCA prova a includere il mittente anche nei canali più grandi; se la sua identità è comunque indisponibile, Watchdog non attribuisce messaggi a una persona incerta.

## Mod: escalation invariata (24 ore dall'ultima infrazione)

1. **Avviso in PVT**.
2. **Mute temporaneo 2 minuti** + spiegazione PVT.
3. **Mute temporaneo 15 minuti** + spiegazione PVT.
4. **Mute temporaneo 3 ore + kick**, spiegazione PVT.
5. **Ban temporaneo 24 ore + kick**, spiegazione PVT.

La motivazione di tutti i kick è **\`Messaggi ripetuti\`**. I testi PVT sono gli stessi già adottati nella PR #302; Mod non specifica all'utente il nome dello script. Il livello 5 è il massimo e non reinvia indefinitamente nuovi kick/ban. I mute e i ban temporanei utilizzano le API TIARCA, che preferiscono la maschera di account quando supportata dal server; la reale applicazione sull'IRCd non è garantita dal solo invio del comando.

L'evento di rilevamento viene riportato localmente nel formato \`AutoModU\` con motore A/B, più una riga \`WatchdogV4\` che indica la regola e il punteggio. Harvester potrà essere aggiornato successivamente in modo indipendente.

**Da verificare con Candidate e canali reali:** accuratezza e falsi positivi, ricongiungimento delle identità al cambio di stato NickServ, canali molto popolati, permessi e comportamento degli extban/account sul server, persistenza in caso di arresto improvviso, backup/ripristino e finestra dei PVT. La compilazione e i test automatici non sostituiscono queste verifiche.
