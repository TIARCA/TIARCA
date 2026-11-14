# Audit dei branch residui — 14 novembre 2026

**Repo:** `TIARCA/TIARCA`. **Base verificata:** `main@350d10a0af3c9996a7c0e158c53657f744471e3b`.
**Perimetro:** 22 branch residui precedenti al 14 novembre, ordinati per *data dell'ultimo commit* (UTC), esclusi `main` e i due branch AutoMod del 14 novembre.

## Esito della revisione, dal più vecchio

| # | Ultimo commit (UTC) | Branch e SHA iniziale | Perché non è su `main` / decisione |
|---|---|---|---|
| 1 | 06-09 17:46 | `jules/mode-exceptions-cleanup-7733847109426317089` · `603ffb7b9ef7db13e5b8acbfe4d13a17347016f8` | PR [#22](https://github.com/TIARCA/TIARCA/pull/22) chiusa come superata; correzione BUG 13 riproposta correttamente e mergiata tramite [#23](https://github.com/TIARCA/TIARCA/pull/23). Elimina ramo duplicato. |
| 2 | 06-09 20:34 | `translations/sync-locales` · `9ad07cc1509e007fdc1261a6b9b0661f7bfb099f` | PR [#24](https://github.com/TIARCA/TIARCA/pull/24) chiusa senza merge; modifica di traduzioni / workflow temporanei, poi interventi alternativi ([#25](https://github.com/TIARCA/TIARCA/pull/25)). Non promuovere il vecchio branch. |
| 3 | 07-09 19:23 | `translations/sync-locales-14482738850864919158` · `f11e89d10f7c750c0156fb3f4d46fd877e4a2479` | PR [#25](https://github.com/TIARCA/TIARCA/pull/25) mergiata con HEAD precedente `478adff82e532bc01b93ce76a6294e23bac0cb10`. Il branch è avanzato dopo il merge: i commit residui NON sono automaticamente integrati; codice storico e workflow di riparazione temporanei, nessun merge automatico. |
| 4 | 08-09 10:48 | `fix/private-query-local-echo` · `a6f24e39dee5389e46cb2f0d6345415239c1fc14` | PR [#30](https://github.com/TIARCA/TIARCA/pull/30) chiusa senza merge. La correzione del caso locale è coperta dalla [#29](https://github.com/TIARCA/TIARCA/pull/29) già mergiata; test `PrivateConversationLocalEchoTest` verificato su `main`. |
| 5 | 08-09 14:49 | `fix/intentional-exit-save-race` · `7e6ab498152755b050531f3c060d9c5534630781` | Branch senza PR, con lock per `connected_servers.json` e `mDestroying` volatile. La [#33](https://github.com/TIARCA/TIARCA/pull/33) ha introdotto la gestione dell'uscita volontaria, **ma quel lock non risulta nel `ServerConnectionManager.java` attuale**. Rischio di race ancora presente: **non verificato**; non si fonde un vecchio commit su codice attuale senza regressioni. Conservare questo SHA come riferimento prima della rimozione branch. |
| 6 | 09-09 10:34 | `fix/private-query-nick-cycle-0917` · `a589cc7b35c984641e5a37b06886471e140bfd2e` | Branch senza PR; ciclo alias nickname A → B → A già affrontato dalla [#48](https://github.com/TIARCA/TIARCA/pull/48) mergiata. `PrivateConversationAliases` attuale normalizza gli alias. |
| 7 | 10-09 14:19 | `feature/editable-channel-topic-candidate` · `b6c17e699b18091e0efb81d82380f3b9f66966e7` | PR [#70](https://github.com/TIARCA/TIARCA/pull/70) non mergiata, sostituita dalla [#71](https://github.com/TIARCA/TIARCA/pull/71) mergiata. Sul `main` `ChannelInfoAdapter` espone `ChannelTopicEditor`. |
| 8 | 12-09 06:15 | `feat/message-format-2` · `60e462d21d95432d412caa99977f6414acec24a9` | Branch avanzato dopo merge della [#95](https://github.com/TIARCA/TIARCA/pull/95); clic sugli avatar formalizzato dalla [#96](https://github.com/TIARCA/TIARCA/pull/96) mergiata. Il vecchio HEAD non è un nuovo feature branch da fondere. |
| 9 | 06-10 04:30 | `diag/simosnap-avatar-large` · `4a6810287b6bd65e90c37965713c9ab6efe38410` | PR [#204](https://github.com/TIARCA/TIARCA/pull/204): **solo diagnostica temporanea** per avatar WHOIS, senza fix; chiusa il 14/11 senza merge. |
| 10 | 17-10 03:44 | `diag/network-handover-reconnect` · `7becc3ef267ae6c8690bc840369b3713a51f3b0a` | PR [#205](https://github.com/TIARCA/TIARCA/pull/205): solo diagnostica temporanea passaggio Wi-Fi/cellulare, senza modifica di riconnessione; chiusa il 14/11 senza merge. |
| 11 | 17-10 13:24 | `diag/background-resume-transport` · `bc9aecd03ce509442b55aaed7826a36bc5f6d575` | PR [#206](https://github.com/TIARCA/TIARCA/pull/206), dipendente dalla #205: soltanto diagnostica background; chiusa il 14/11 senza merge. |
| 12 | 01-11 09:02 | `fix/private-quit-record-before-channel-cleanup` · `44882f911773a24614d54cb0f1910cfb4d630357` | PR [#222](https://github.com/TIARCA/TIARCA/pull/222) chiusa come duplicata della [#221](https://github.com/TIARCA/TIARCA/pull/221) già mergiata; nota esplicita nella PR. |
| 13 | 01-11 09:10 | `fix/multiline-link-touch` · `907679e17c85f8cc2e3565a960c6441504743a38` | PR [#220](https://github.com/TIARCA/TIARCA/pull/220) chiusa come duplicata della [#219](https://github.com/TIARCA/TIARCA/pull/219) già mergiata; nota esplicita nella PR. |
| 14 | 07-11 06:05 | `validation/gradle-8.14.6` · `3242e5bae4494790bd20f395c932fc145968746b` | Validazione temporanea senza PR; toolchain `main` già avanzata rispetto a Gradle 8.14.6, come da CI del 14/11 (Gradle 9.9.0). |
| 15 | 07-11 06:28 | `validation/wrapper-agp-8.13.2` · `6c89f004930009604179454f4034a9173500a0fb` | Validazione temporanea del wrapper/AGP, senza PR; toolchain successivamente aggiornata tramite [#250](https://github.com/TIARCA/TIARCA/pull/250) e [#253](https://github.com/TIARCA/TIARCA/pull/253). |
| 16 | 07-11 07:21 | `validation/legacy-zip4j13-fixture` · `f54b2d9ceb601c1f64e18f66a8764860290c377a` | Fixture temporanea backup compatibilità Zip4j legacy, senza PR; migrazione principale [#246](https://github.com/TIARCA/TIARCA/pull/246) mergiata. Non assumere che ogni fixture sperimentale debba essere promossa. |
| 17 | 07-11 07:36 | `validation/phase-a-zip4j-gson` · `d3c4170797edd6e50541968328da23fda28739be` | Validazione della fase A già integrata tramite [#246](https://github.com/TIARCA/TIARCA/pull/246); branch di prova distinto da branch di release. |
| 18 | 07-11 08:27 | `validation/phase-b1-snakeyaml-27` · `ed2bcb5f19d5bdd70280ad911000d9b6ff8620b5` | Validazione SnakeYAML, implementazione già integrata con [#247](https://github.com/TIARCA/TIARCA/pull/247). |
| 19 | 07-11 11:39 | `validation/b2-native-keystore` · `53bde22c163756e4150e095e86561dcbee15d4a2` | Validazione Android Keystore, feature integrata con [#248](https://github.com/TIARCA/TIARCA/pull/248); parte sperimentale non necessaria in `main`. |
| 20 | 07-11 12:10 | `validation/b2-keystore-emulator` · `7d0525bffc7ca237a58543ad483d5d030a949e19` | Test emulatori temporanei per la stessa fase B2, [#248](https://github.com/TIARCA/TIARCA/pull/248) mergiata. Non equivale a test manuale eseguito oggi. |
| 21 | 08-11 09:35 | `fix/gradle-wrapper-download-timeout-20261108` · `d95926740dc16710859948944c17dfe0a0abcf5a` | PR [#252](https://github.com/TIARCA/TIARCA/pull/252) chiusa esplicitamente come superata dalla [#253](https://github.com/TIARCA/TIARCA/pull/253) già mergiata. |
| 22 | 08-11 12:56 | `experiment/gradle-enhanced-caching-20261108` · `9fb31c9fb0699ba9efd4eec2b430a66731cf3ac5` | PR [#255](https://github.com/TIARCA/TIARCA/pull/255), esperimento di Enhanced Caching senza consenso a un merge definitivo; chiusa il 14/11 senza merge. `main` mantiene Basic. |

**Interpretazione:** il confronto GitHub `main...branch` segnava i 22 branch come divergenti (`ahead_by > 0`): ciò non implica automaticamente che i comportamenti funzionali fossero assenti da `main`; molte PR sono state *squash-merge*, sostituite o chiuse. **Non si eseguono merge indiscriminati.** La richiesta dell'utente autorizza la **rimozione** dei 22 riferimenti ormai storici, con SHA registrati; non certifica i bugfix legacy come testati su `main`.

## Salvaguardie

- Conservare `main`, `fix/automod-private-warnings-defaults-20261114` (PR #272), `fix/lua-reconnect-and-automod-history-20261114` (PR #273).
- Non chiudere PR #272 e #273. Le PR vecchie #204, #205, #206 e #255 sono state chiuse senza merge il 14 novembre.
- Rimuovere **solo** i 22 nomi qui elencati con **SHA HEAD esatto** e dopo verifica live di assenza di PR aperte che li usino.
- Registrare l'esito del job e le SHA in un artifact di audit; cancellazione non significa merge né test; i commit possono diventare irraggiungibili dopo garbage collection.
- Nessuna modifica a `versionName`, `versionCode`, tag o release.
