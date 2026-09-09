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

    /**
     * Applies a NICK change without leaving alias chains or cycles behind.
     *
     * Every historical nickname that currently resolves to {@code oldNick} is flattened so it
     * points directly at {@code newNick}. If the user returns to an earlier nickname, that
     * nickname becomes canonical again and its stale alias is removed instead of creating a
     * cycle such as A -> B -> A.
     */
    static void applyNickChange(Map<String, String> aliases, String oldNick, String newNick) {
        if (aliases == null || oldNick == null || newNick == null ||
                oldNick.equalsIgnoreCase(newNick))
            return;

        String oldKey = oldNick.toLowerCase(Locale.ROOT);
        String newKey = newNick.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> historicalKeys = new LinkedHashSet<>();

        for (String key : new ArrayList<>(aliases.keySet())) {
            String resolved = resolve(aliases, key);
            if (resolved != null && resolved.equalsIgnoreCase(oldNick))
                historicalKeys.add(key.toLowerCase(Locale.ROOT));
        }
        historicalKeys.add(oldKey);

        // newNick is the canonical visible nickname now. Keeping an alias for it can create
        // A -> B -> A loops when a user returns to a previous nickname.
        aliases.remove(newKey);

        for (String key : historicalKeys) {
            if (key.equals(newKey))
                continue;
            aliases.put(key, newNick);
        }
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

    private static String resolve(Map<String, String> aliases, String nick) {
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
}
