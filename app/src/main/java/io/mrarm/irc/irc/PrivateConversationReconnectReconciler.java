package io.mrarm.irc.irc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure-Java matching used after reconnect to reconcile stale private-query nicknames. */
public final class PrivateConversationReconnectReconciler {

    private PrivateConversationReconnectReconciler() {
    }

    /**
     * Returns old-query -> current-nick renames only when the old query has a known account,
     * the old nickname is not currently observed, and exactly one current nickname is observed
     * for that same account.
     */
    public static Map<String, String> findRenames(List<String> privateQueries,
                                                   Map<String, String> knownAccounts,
                                                   Map<String, String> currentAccounts) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (privateQueries == null || knownAccounts == null || currentAccounts == null)
            return result;

        for (String oldNick : privateQueries) {
            if (oldNick == null || containsNick(currentAccounts, oldNick))
                continue;
            String account = getAccount(knownAccounts, oldNick);
            if (account == null)
                continue;

            String candidate = null;
            boolean ambiguous = false;
            for (Map.Entry<String, String> entry : currentAccounts.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null ||
                        !entry.getValue().equalsIgnoreCase(account))
                    continue;
                if (candidate != null && !candidate.equalsIgnoreCase(entry.getKey())) {
                    ambiguous = true;
                    break;
                }
                candidate = entry.getKey();
            }
            if (!ambiguous && candidate != null && !candidate.equalsIgnoreCase(oldNick))
                result.put(oldNick, candidate);
        }
        return result;
    }

    private static boolean containsNick(Map<String, String> values, String nick) {
        for (String key : values.keySet()) {
            if (key != null && key.equalsIgnoreCase(nick))
                return true;
        }
        return false;
    }

    private static String getAccount(Map<String, String> values, String nick) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(nick))
                return entry.getValue();
        }
        return null;
    }
}
