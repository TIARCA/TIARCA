package io.mrarm.irc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure-Java alias resolution for private IRC conversations. */
final class PrivateConversationAliases {

    private PrivateConversationAliases() {
    }

    /** Records a NICK change while keeping one acyclic alias set for the private query. */
    static void recordNickChange(Map<String, String> aliases, String oldNick, String newNick) {
        if (aliases == null || oldNick == null || newNick == null ||
                oldNick.equalsIgnoreCase(newNick))
            return;

        String oldKey = oldNick.toLowerCase(Locale.ROOT);
        String newKey = newNick.toLowerCase(Locale.ROOT);

        // Historical names of the same query follow the new current nickname directly.
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            if (alias.getValue() != null && alias.getValue().equalsIgnoreCase(oldNick))
                alias.setValue(newNick);
        }

        // The current nickname can never also be an alias source. This breaks A -> B -> A.
        aliases.remove(newKey);
        aliases.put(oldKey, newNick);
        aliases.remove(newKey);
    }

    static String resolve(Map<String, String> aliases, String nick) {
        String resolved = nick;
        Set<String> visited = new LinkedHashSet<>();
        while (resolved != null && visited.add(resolved.toLowerCase(Locale.ROOT))) {
            String next = aliases.get(resolved.toLowerCase(Locale.ROOT));
            if (next == null)
                break;
            resolved = next;
        }
        return resolved;
    }

    static List<String> buildCloseTargets(Map<String, String> aliases, String visibleNick) {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        if (visibleNick == null || visibleNick.isEmpty())
            return new ArrayList<>();
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            String resolved = resolve(aliases, alias.getValue());
            if (visibleNick.equalsIgnoreCase(resolved))
                targets.add(alias.getKey());
        }
        // The visible nickname may also have its own chatlib query entry.
        targets.add(visibleNick);
        return new ArrayList<>(targets);
    }
}
