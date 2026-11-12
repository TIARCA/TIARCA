package io.mrarm.chatlib.irc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One explicit channel WHO/WHOX snapshot in the IRC core. This is independent
 * of the small passive identity LRU, so users beyond its 512 entries are not
 * silently lost. The caller decides whether the data may reach Lua features.
 */
public final class WhoSnapshotCollector {
    public static final int MAX_ENTRIES = 16384;

    public static final class User {
        public final String nick;
        public final String ident;
        public final String host;

        User(String nick, String ident, String host) {
            this.nick = nick;
            this.ident = ident;
            this.host = host;
        }

        public String mask() {
            return nick + "!" + ident + "@" + host;
        }
    }

    public static final class Result {
        public final List<User> users;
        public final boolean truncated;

        Result(List<User> users, boolean truncated) {
            this.users = users;
            this.truncated = truncated;
        }
    }

    private static final class Request {
        final String channel;
        final String token;
        final Consumer<Result> callback;
        final Map<String, User> found = new LinkedHashMap<>();
        boolean truncated;

        Request(String channel, String token, Consumer<Result> callback) {
            this.channel = channel;
            this.token = token;
            this.callback = callback;
        }
    }

    private Request active;

    /** At most one explicit WHO snapshot per network at a time. */
    public synchronized boolean begin(String channel, String token, Consumer<Result> callback) {
        if (active != null || channel == null || channel.isEmpty() || callback == null)
            return false;
        active = new Request(channel, token == null ? "" : token, callback);
        return true;
    }

    public synchronized void cancel() {
        active = null;
    }

    private static boolean atom(String text, int limit) {
        if (text == null || text.isEmpty() || text.length() > limit) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c <= 32 || c >= 127 || c == '!' || c == '@' || c == ',')
                return false;
        }
        return true;
    }

    /** Called after parsing a server numeric, even if another handler consumes it. */
    public void noteNumeric(String numeric, List<String> params) {
        if (params == null) return;
        Consumer<Result> callback = null;
        Result result = null;
        synchronized (this) {
            Request req = active;
            if (req == null) return;
            String nick = null, ident = null, host = null;
            if ("352".equals(numeric) && params.size() >= 6 &&
                    req.token.isEmpty() && req.channel.equalsIgnoreCase(params.get(1))) {
                ident = params.get(2);
                host = params.get(3);
                nick = params.get(5);
            } else if ("354".equals(numeric) && params.size() >= 7 &&
                    !req.token.isEmpty() && req.token.equals(params.get(1))) {
                ident = params.get(2);
                host = params.get(3);
                nick = params.get(4);
            } else if ("315".equals(numeric) && params.size() >= 2 &&
                    req.channel.equalsIgnoreCase(params.get(1))) {
                active = null;
                callback = req.callback;
                result = new Result(new ArrayList<>(req.found.values()), req.truncated);
            }
            if (nick != null && atom(nick, 64) && atom(ident, 64) && atom(host, 192)) {
                String key = nick.toLowerCase(Locale.ROOT);
                if (!req.found.containsKey(key) && req.found.size() >= MAX_ENTRIES)
                    req.truncated = true;
                else
                    req.found.put(key, new User(nick, ident, host));
            }
        }
        if (callback != null) callback.accept(result);
    }
}
