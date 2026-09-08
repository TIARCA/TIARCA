TIARCA
======

TIARCA (*TIARCA Is Another Relay Chat App*) è un fork di [Revolution IRC](https://github.com/MCMrARM/revolution-irc), client IRC Android originariamente sviluppato da MrARM/MCMrARM.

TIARCA ne prosegue lo sviluppo come progetto indipendente, mantenendo la licenza GNU GPLv3 e introducendo nuove funzionalità, correzioni e aggiornamenti per le versioni moderne di Android. Il lavoro originale di Revolution IRC e le successive modifiche di TIARCA restano chiaramente distinti.

## Ultima release — TIARCA 0.8.1

La release stabile corrente è **TIARCA 0.8.1**.

- [Scarica TIARCA 0.8.1 APK](https://github.com/TIARCA/TIARCA/releases/download/v0.8.1/TIARCA-v0.8.1.apk)
- [Pagina della release 0.8.1](https://github.com/TIARCA/TIARCA/releases/tag/v0.8.1)
- [Novità e changelog completo](CHANGELOG.md)

La 0.8.1 introduce, tra le altre cose, il riordino manuale dei server, la schermata unificata **Ban | Eccezioni**, miglioramenti a MONITOR, una navigazione completamente rivista di menzioni e risultati di ricerca, il pulsante rapido per tornare all’ultimo messaggio, numerose correzioni di stabilità e la copertura completa delle nove lingue supportate.

Questo repository contiene il codice sorgente di TIARCA. Non rappresenta una pubblicazione su F-Droid o Google Play; gli APK ufficiali vengono distribuiti tramite GitHub Releases.

## Caratteristiche aggiunte in TIARCA

* strumenti di moderazione dal WHOIS e gestione dei mode di canale;
* gestione unificata di ban ed eccezioni di canale;
* utenti monitorati tramite IRC MONITOR con stato persistente;
* ricerca nei messaggi, menzioni, contatori non letti e navigazione contestuale nella cronologia;
* riordino manuale dei server/network;
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
