# Esempi di script

[Guida](README.md) · [English](../en/EXAMPLES.md)

Gli esempi sono file UTF-8 da importare manualmente: **non** sono plugin installati automaticamente. Prima di attivarli controlla il sorgente e i permessi.

| Esempio | Scopo | Autorizzazioni |
|---|---|---|
| [count_messages.lua](../../lua-examples/count_messages.lua) | Conta messaggi nel log | Nessuna aggiuntiva |
| [auto_reply.lua](../../lua-examples/auto_reply.lua) | Risposta nella stessa conversazione | Invio messaggi |
| [local_command.lua](../../lua-examples/local_command.lua) | Comandi locali `/lua ping` e `/lua saluta` | Invio solo per saluto remoto |
| [persistent_results.lua](../../lua-examples/persistent_results.lua) | Salvataggio dati e risultati locali | Dati persistenti |
| [espelli.lua](../../lua-examples/espelli.lua) | Espulsione manuale con multi-ban su reti compatibili; abilita +N sul canale | Membri e identità, moderazione; OP |
| [mask_finder.lua](../../lua-examples/mask_finder.lua) | Cerca nickname/ident/host nel canale | Membri e identità |
| [automod_antiripetizione.lua](../../lua-examples/automod_antiripetizione.lua) | Rileva ripetizioni e, se configurato, modera | Identità, persistenza, moderazione; invio per avvisi |

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
