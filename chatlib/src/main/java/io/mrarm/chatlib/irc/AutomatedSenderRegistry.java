package io.mrarm.chatlib.irc;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-connection cache of automated identities confirmed by the IRC server.
 *
 * Bot identities are learned from server metadata (currently WHOIS numeric 335 on
 * supporting IRCds). Service identities use the same conservative identity rules
 * already used by TIARCA for trusted services; being an IRC operator alone is never
 * enough to classify a sender as automated.
 */
public final class AutomatedSenderRegistry {

    private static final Map<ServerConnectionData, Set<String>> BOT_NICKS = new WeakHashMap<>();

    private AutomatedSenderRegistry() {
    }

    public static synchronized void rememberBot(ServerConnectionData connection, String nick) {
        if (connection == null || nick == null || nick.trim().isEmpty())
            return;
        Set<String> nicks = BOT_NICKS.get(connection);
        if (nicks == null) {
            nicks = new HashSet<>();
            BOT_NICKS.put(connection, nicks);
        }
        nicks.add(normalize(nick));
    }

    public static synchronized boolean isBot(ServerConnectionData connection, String nick) {
        if (connection == null || nick == null)
            return false;
        Set<String> nicks = BOT_NICKS.get(connection);
        return nicks != null && nicks.contains(normalize(nick));
    }

    public static synchronized void renameBot(ServerConnectionData connection,
                                              String oldNick, String newNick) {
        if (connection == null || oldNick == null || newNick == null)
            return;
        Set<String> nicks = BOT_NICKS.get(connection);
        if (nicks != null && nicks.remove(normalize(oldNick)))
            nicks.add(normalize(newNick));
    }

    public static synchronized void clear(ServerConnectionData connection) {
        if (connection != null)
            BOT_NICKS.remove(connection);
    }

    public static boolean isTrustedServiceIdentity(String nick, String user, String host) {
        if (nick == null)
            return false;
        String n = normalize(nick);
        boolean serviceName = n.endsWith("serv") || n.equals("nickserv") ||
                n.equals("chanserv") || n.equals("memoserv") || n.equals("operserv") ||
                n.equals("hostserv") || n.equals("botserv") || n.equals("helpserv") ||
                n.equals("global");
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        String u = user == null ? "" : user.toLowerCase(Locale.ROOT);
        boolean serviceIdentity = h.contains("service") || h.contains("services") ||
                u.equals("services") || u.equals("service");
        return serviceName || serviceIdentity;
    }

    private static String normalize(String nick) {
        return nick.toLowerCase(Locale.ROOT);
    }
}
