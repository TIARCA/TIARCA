TIARCA
======

TIARCA (*TIARCA Is Another Relay Chat App*) è un client IRC Android basato su Revolution IRC.

Questo repository contiene il codice sorgente di TIARCA. Non rappresenta una pubblicazione su F-Droid o Google Play; gli APK distribuiti tramite GitHub Releases saranno indicati esplicitamente nella pagina delle release.

## Caratteristiche aggiunte in TIARCA

* strumenti di moderazione dal WHOIS e gestione dei mode di canale;
* condivisione di immagini, file, audio e video tramite link temporanei;
* ricerca nei messaggi, menzioni e contatori non letti;
* comandi rapidi configurabili, inclusi `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` e `!dizionario`;
* catalogo di network IRC, configurazione multiserver e numerose correzioni di compatibilità Android.

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
