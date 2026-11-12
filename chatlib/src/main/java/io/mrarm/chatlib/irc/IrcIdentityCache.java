package io.mrarm.chatlib.irc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small, connection-local cache of server-observed IRC identities. Values may
 * be unavailable, and a cloaked host is not a user's real IP address.
 * No scripting permission is implied merely by caching data already received.
 */
public final class IrcIdentityCache {
    private static final int LIMIT = 512;
    private static final long WHOX_WINDOW_MS = 20_000L;
    private static final String WHOX_TOKEN = "042";

    public static final class Identity {
        public final String ident;
        public final String host;
        public final String account;

        Identity(String ident, String host, String account) {
            this.ident = ident;
            this.host = host;
            this.account = account;
        }
    }

    private final LinkedHashMap<String, Identity> known =
            new LinkedHashMap<String, Identity>(128, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Identity> eldest) {
                    return size() > LIMIT;
                }
            };
    private long whoxExpiresAt;

    private static String valid(String value, int max) {
        if (value == null || value.isEmpty() || value.length() > max ||
                value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 ||
                value.indexOf('\n') >= 0 || value.indexOf(' ') >= 0 ||
                value.indexOf(',') >= 0 || value.indexOf('\t') >= 0)
            return null;
        return value;
    }

    private static String key(String nick) {
        String good = valid(nick, 64);
        return good == null ? null : good.toLowerCase(Locale.ROOT);
    }

    public synchronized void note(String nick, String ident, String host, String account) {
        String id = key(nick);
        if (id == null) return;
        String user = valid(ident, 64);
        String hostname = valid(host, 192);
        String acct = valid(account, 64);
        // IRCv3 WHOX uses "0" for logged-out users. An explicit logout
        // must clear a formerly observed account, not preserve stale state.
        boolean loggedOut = "0".equals(acct) || "*".equals(acct);
        if (loggedOut) acct = null;
        Identity old = known.get(id);
        if (old != null) {
            if (user == null) user = old.ident;
            if (hostname == null) hostname = old.host;
            if (acct == null && !loggedOut) acct = old.account;
        }
        if (user != null || hostname != null || acct != null)
            known.put(id, new Identity(user, hostname, acct));
    }

    public synchronized Identity get(String nick) {
        String id = key(nick);
        return id == null ? null : known.get(id);
    }

    public synchronized void rename(String oldNick, String newNick) {
        String previous = key(oldNick), next = key(newNick);
        if (previous == null || next == null) return;
        Identity old = known.remove(previous);
        if (old != null) known.put(next, old);
    }

    public synchronized void forget(String nick) {
        String id = key(nick);
        if (id != null) known.remove(id);
    }

    public synchronized void clear() {
        known.clear();
        whoxExpiresAt = 0L;
    }

    /** Call only when the user approved an explicit WHOX request. */
    public synchronized void expectWhox() {
        whoxExpiresAt = System.currentTimeMillis() + WHOX_WINDOW_MS;
    }

    /**
     * Parses only the WHO/WHOIS fields with standardized positions; a WHOX
     * reply is accepted solely for our short-lived, explicit token.
     */
    public void noteNumeric(String command, List<String> params) {
        if (params == null) return;
        if ("352".equals(command) && params.size() >= 6) {
            note(params.get(5), params.get(2), params.get(3), null);
        } else if ("311".equals(command) && params.size() >= 4) {
            note(params.get(1), params.get(2), params.get(3), null);
        } else if ("330".equals(command) && params.size() >= 3) {
            note(params.get(1), null, null, params.get(2));
        } else if ("354".equals(command) && params.size() >= 7) {
            synchronized (this) {
                if (System.currentTimeMillis() >= whoxExpiresAt ||
                        !WHOX_TOKEN.equals(params.get(1))) return;
            }
            // IRCv3 WHOX replies always use canonical field order, regardless
            // of the order in %tnuhaf: token, user, host, nick, flags, account.
            // WHO #channel %tnuhaf,042 => 354 <me> 042 <user> <host>
            // <nick> <flags> <account>
            note(params.get(4), params.get(2), params.get(3), params.get(6));
        }
    }

    public static String whoxToken() {
        return WHOX_TOKEN;
    }
}
