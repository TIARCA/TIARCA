# Sviluppare script Lua per TIARCA

[Indice bilingue](../README.md) · [API](API_REFERENCE.md) · [Eventi](EVENTS.md) · [Permessi](PERMISSIONS.md) · [Esempi](EXAMPLES.md) · [Collector moderazione](MODERATION_COLLECTOR.md) · [AutoMod B](AUTOMOD_B.md)


> **Disponibilità:** funzione sperimentale; guida del redesign non ancora integrato della PR #303, **non presente nell'APK stabile v0.9.10**.

## Il primo script

Crea un file di testo UTF-8 chiamato `saluto.lua`:

```lua
tiarca.on("connected", function(event)
  tiarca.log("Connesso alla rete " .. event.server)
end)
```

In una build che include Lua: **Impostazioni → Script Lua (sperimentale)** → importa il file tramite selettore documenti Android → abilitalo → scegli esplicitamente le reti IRC. Lo script è inizialmente disabilitato. Per questo esempio non occorrono autorizzazioni aggiuntive. Il messaggio va nel log locale dello script, **non** in un canale.

## Aggiornare uno script

In una build che include la funzione, usa **Aggiorna script** nella scheda dello script già importato, seleziona il nuovo file Lua e conferma. Il nome, l'identificativo, le reti, le autorizzazioni e lo storage persistente restano invariati; i dati in memoria vengono reinizializzati. Verifica che il nuovo codice sia affidabile e compatibile con il formato dei dati salvati. Eliminare e reimportare uno script, invece, cancella lo storage associato.

## Il modello di esecuzione

- Registra callback con `tiarca.on(nome_evento, funzione)`. Gli eventi sono normalizzati, non messaggi IRC RAW.
- Ogni script opera solo sulle reti selezionate dall'utente; alcune operazioni richiedono permessi separati.
- `/lua <testo>` genera un evento locale `command`, non invia comandi al server. Per esempio, `/lua ping`.
- Le variabili Lua ordinarie sopravvivono agli eventi della stessa VM ma **non** ai riavvii della VM; per conservare dati usa `tiarca.storage` con consenso.
- L'API non consente accesso diretto ad Android, file, socket o comandi IRC arbitrari.

## Percorso consigliato

Leggi [Eventi](EVENTS.md), poi [Riferimento API](API_REFERENCE.md) e [Autorizzazioni](PERMISSIONS.md). Parti dagli [esempi importabili](EXAMPLES.md). Per limiti e dettagli storici consulta [la documentazione tecnica](../../LUA_SCRIPTING_MVP.md).

Per segnalare un'API mancante o un esempio non funzionante, apri una issue indicando commit/build, rete IRC e log privo di dati sensibili.

## Redesign e compatibilità

[Guida migrazione identity v2](MIGRATION.md): API asincrone, legacy, storage, `/lua perf`. Comandi client riservati: help, list, status, log, stop, start, restart, perf; non arrivano agli script.
