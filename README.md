TIARCA
======

TIARCA (*TIARCA Is Another Relay Chat App*) è un fork di [Revolution IRC](https://github.com/MCMrARM/revolution-irc), client IRC Android originariamente sviluppato da MrARM/MCMrARM.

TIARCA ne prosegue lo sviluppo come progetto indipendente, mantenendo la licenza GNU GPLv3 e introducendo nuove funzionalità, correzioni e aggiornamenti per le versioni moderne di Android. Il lavoro originale di Revolution IRC e le successive modifiche di TIARCA restano chiaramente distinti.

## Ultima release — TIARCA 0.9.1.4

La release stabile corrente è **TIARCA 0.9.1.4**.

- [Scarica TIARCA 0.9.1.4 APK](https://github.com/TIARCA/TIARCA/releases/download/v0.9.1.4/TIARCA-v0.9.1.4.apk)
- [Pagina della release 0.9.1.4](https://github.com/TIARCA/TIARCA/releases/tag/v0.9.1.4)
- [Novità e changelog completo](CHANGELOG.md)

La 0.9.1.4 rifinisce la visualizzazione dei record WHOWAS: icona Copia grigia e visibile con tema chiaro e scuro, righe più compatte senza gli spazi verticali eccessivi introdotti dai pulsanti. Include naturalmente la correzione di instradamento WHOWAS della 0.9.1.3 e tutte le novità della serie 0.9.1.

Questo repository contiene il codice sorgente di TIARCA. Non rappresenta una pubblicazione su F-Droid o Google Play; gli APK ufficiali vengono distribuiti tramite GitHub Releases.

## Caratteristiche aggiunte in TIARCA

* strumenti di moderazione dal WHOIS e WHOWAS strutturato con copia rapida di nickname, ident, host e server;
* gestione unificata di ban ed eccezioni di canale;
* utenti monitorati tramite IRC MONITOR con stato persistente;
* ricerca nei messaggi, menzioni, contatori non letti e navigazione contestuale nella cronologia;
* riordino manuale dei server/network;
* onboarding dei server con SASL semplificato, canali predefiniti modificabili e LIST usabile come selettore;
* controllo aggiornamenti integrato con opt-in, verifica periodica settimanale e installazione assistita degli APK ufficiali GitHub;
* backup Android cifrato e trasferimento dispositivo, mantenendo separato il backup manuale TIARCA;
* condivisione di immagini, file, audio e video tramite link temporanei;
* comandi rapidi configurabili, inclusi `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` e `!dizionario`;
* catalogo di network IRC, configurazione multiserver e numerose correzioni di compatibilità Android;
* interfaccia disponibile in Italiano, English, Deutsch, Español, Français, Polski, Português (Brasil), Suomi e Română.

## Changelog

Consulta il [changelog completo](CHANGELOG.md) per lo storico delle modifiche di TIARCA.

## Compilazione

Aprire il progetto con Android Studio e usare JDK 21 per Gradle. Il progetto richiede Android SDK 36; per una build di sviluppo eseguire:

```text
gradlew :app:assembleDebug
```

Le release pubbliche devono essere firmate con una chiave privata che non va mai inserita nel repository. Vedere `keystore.properties.example`.

## Licenza e attribuzioni

TIARCA è distribuita con licenza GPLv3, come il progetto di origine Revolution IRC. I relativi avvisi di copyright e le licenze delle dipendenze restano applicabili.

La funzione `!movie` usa TMDB; TIARCA include l’attribuzione richiesta: “This product uses the TMDB API but is not endorsed or certified by TMDB.”

---

This client features a modern Material design as well as many other awesome features:

* Stays in background properly, even on more recent Android versions
* Store chat messages to be displayed after reconnecting to the server later
* Nick/channel/command autocomplete
* Ignore list
* mIRC color formatting support
* SSL certificate exception list
* Command list to run after connecting
* Customization: custom command aliases, notification rules, reconnection interval, chat font, message format, app colors

...and much more!