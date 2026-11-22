# Esempi di script

[Guida](README.md) · [English](../en/EXAMPLES.md)

Gli esempi sono file UTF-8 da importare manualmente: **non** sono plugin installati automaticamente. Prima di attivarli controlla il sorgente e i permessi.

| Esempio | Scopo | Autorizzazioni |
|---|---|---|
| [automod_antiripetizione.lua](../../lua-examples/automod_antiripetizione.lua) | Modera ripetizioni con filtri per canale | Identità, persistenza, moderazione; invio per avvisi |
| [automod_annunci.lua](../../lua-examples/automod_annunci.lua) | [AutoMod B](AUTOMOD_B.md): riconoscimento annunci, concentrazione retroattiva, escalation | Membri e identità, persistenza, invio e moderazione in modalità live |
| [espelli.lua](../../lua-examples/espelli.lua) | Espulsione manuale: TBAN host `N:` e `m:U:` per 6h, KICK e ban nick/account; senza +N globale | Membri e identità, moderazione; OP |
| [mask_finder.lua](../../lua-examples/mask_finder.lua) | Cerca nickname, ident e host | Membri e identità |
| [moderation_collector.lua](../../lua-examples/moderation_collector.lua) | Registra eventi di moderazione in JSONL | Identità e persistenza |

### Esempio minimale copiabile

```lua
tiarca.on("command", function(event)
  if event.text == "help-mio" then
    tiarca.log("Comandi: /lua help-mio")
  end
end)
```

Importa, abilita sulla rete desiderata e digita `/lua help-mio`. Il log è locale.

**Prima di condividere uno script:** controlla rete autorizzata, dati `nil`, riavvio, backup, rate limit, assenza di RAW, comportamento senza privilegi OP e test manuali su una Candidate. Consulta [permessi](PERMISSIONS.md).
