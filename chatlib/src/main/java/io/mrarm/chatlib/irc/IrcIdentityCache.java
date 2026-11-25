package io.mrarm.chatlib.irc;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Connection-local cache of server-observed IRC identities. Values may
 * be unavailable, and a cloaked host is not a user's real IP address.
 * No scripting permission is implied merely by caching data already received.
 */
public final class IrcIdentityCache {
    // Shared members are retained independently of transient WHO/PVT churn.
    // Bounds are safety limits, not protocol limits or performance measurements.
    static final int MAX_SHARED = 16384;
    static final int MAX_MEMBERSHIPS = 65536;
    static final int MAX_RECENT = 4096;
    private static final long WHOX_WINDOW_MS = 20_000L;
    private static final String WHOX_TOKEN = "042";

    public static final class Identity {
        public final String ident;
        public final String host;
        public final String account;
        /** True only after the server explicitly reported an account or logout. */
        public final boolean accountKnown;

        Identity(String ident, String host, String account, boolean accountKnown) {
            this.ident = ident;
            this.host = host;
            this.account = account;
            this.accountKnown = accountKnown;
        }
    }

    private final LinkedHashMap<String, Identity> recent =
            new LinkedHashMap<String, Identity>(128, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Identity> eldest) {
                    return size() > MAX_RECENT;
                }
            };
    private final Map<String, Identity> shared = new HashMap<>();
    private final Map<String, Set<String>> channelMembers = new HashMap<>();
    private final Map<String, Integer> memberships = new HashMap<>();
    private final Map<String, String> sharedNicks = new HashMap<>();
    private int membershipCount;
    private boolean membershipOverflow;
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

    private Identity find(String id) {
        return memberships.containsKey(id) ? shared.get(id) : recent.get(id);
    }

    private void remember(String id, Identity identity) {
        if (memberships.containsKey(id))
            shared.put(id, identity);
        else
            recent.put(id, identity);
    }

    public synchronized void note(String nick, String ident, String host, String account) {
        String id = key(nick);
        if (id == null) return;
        String user = valid(ident, 64);
        String hostname = valid(host, 192);
        String acct = valid(account, 64);
        boolean loggedOut = "0".equals(acct) || "*".equals(acct);
        if (loggedOut) acct = null;
        Identity old = find(id);
        // A reused nick or an unobserved mask change must not inherit an
        // account belonging to a different identity. Explicit CHGHOST is
        // handled separately and retains the verified account.
        if (old != null && ((user != null && old.ident != null && !user.equals(old.ident)) ||
                (hostname != null && old.host != null &&
                        !hostname.equalsIgnoreCase(old.host))))
            old = null;
        boolean accountKnown = loggedOut || acct != null ||
                (old != null && old.accountKnown);
        if (old != null) {
            if (user == null) user = old.ident;
            if (hostname == null) hostname = old.host;
            if (acct == null && !loggedOut) acct = old.account;
        }
        if (user != null || hostname != null || acct != null || accountKnown)
            remember(id, new Identity(user, hostname, acct, accountKnown));
    }

    /** An explicit server CHGHOST identifies the same connected user. */
    public synchronized void changeHost(String nick, String ident, String host) {
        String id = key(nick);
        String user = valid(ident, 64), hostname = valid(host, 192);
        if (id == null || user == null || hostname == null) return;
        Identity old = find(id);
        remember(id, new Identity(user, hostname, old == null ? null : old.account,
                old != null && old.accountKnown));
    }

    public synchronized Identity get(String nick) {
        String id = key(nick);
        return id == null ? null : find(id);
    }

    private static String channelKey(String channel) {
        String good = valid(channel, 192);
        return good == null ? null : good.toLowerCase(Locale.ROOT);
    }

    public synchronized void memberJoined(String channel, String nick) {
        String ch = channelKey(channel), id = key(nick);
        if (ch == null || id == null) return;
        Set<String> members = channelMembers.get(ch);
        if (members != null && members.contains(id)) {
            sharedNicks.put(id, nick);
            return;
        }
        if (membershipCount >= MAX_MEMBERSHIPS ||
                (!memberships.containsKey(id) && memberships.size() >= MAX_SHARED)) {
            membershipOverflow = true;
            return; // Never evict another still-shared identity to make room.
        }
        if (members == null) {
            members = new HashSet<>();
            channelMembers.put(ch, members);
        }
        members.add(id);
        membershipCount++;
        int refs = memberships.getOrDefault(id, 0);
        memberships.put(id, refs + 1);
        sharedNicks.put(id, nick);
        if (refs == 0) {
            Identity identity = recent.remove(id);
            if (identity != null) shared.put(id, identity);
        }
    }

    private void release(String id) {
        membershipCount--;
        int refs = memberships.getOrDefault(id, 0);
        if (refs <= 1) {
            memberships.remove(id);
            sharedNicks.remove(id);
            shared.remove(id);
            recent.remove(id);
        } else {
            memberships.put(id, refs - 1);
        }
    }

    public synchronized void memberLeft(String channel, String nick) {
        String ch = channelKey(channel), id = key(nick);
        if (ch == null || id == null) return;
        Set<String> members = channelMembers.get(ch);
        if (members != null && members.remove(id)) {
            release(id);
            if (members.isEmpty()) channelMembers.remove(ch);
        } else if (!memberships.containsKey(id)) {
            // A departure from an untracked roster must not leave stale data.
            recent.remove(id);
        }
    }

    /** Apply a completed NAMES roster, preserving identities still present. */
    public synchronized void replaceChannelMembers(String channel, Collection<String> nicks) {
        String ch = channelKey(channel);
        if (ch == null || nicks == null) return;
        Map<String, String> desired = new HashMap<>();
        for (String nick : nicks) {
            String id = key(nick);
            if (id != null) desired.put(id, nick);
        }
        Set<String> previous = channelMembers.get(ch);
        if (previous != null) {
            for (String id : new HashSet<>(previous))
                if (!desired.containsKey(id)) memberLeft(ch, id);
        }
        for (String nick : desired.values()) memberJoined(ch, nick);
    }

    public synchronized void channelLeft(String channel) {
        String ch = channelKey(channel);
        if (ch == null) return;
        Set<String> members = channelMembers.remove(ch);
        if (members != null)
            for (String id : members) release(id);
    }

    /** A fresh JOIN outside known shared channels cannot prove continuity of a nick. */
    public synchronized void prepareJoin(String nick) {
        String id = key(nick);
        if (id != null && !memberships.containsKey(id))
            recent.remove(id);
    }

    /** Record incomplete membership evidence without losing known good identities. */
    public synchronized void markMembershipIncomplete() {
        membershipOverflow = true;
    }

    /** Local membership evidence only; a negative result can be incomplete. */
    public synchronized boolean hasSharedMember(String channel, String nick) {
        String ch = channelKey(channel), id = key(nick);
        Set<String> members = ch == null ? null : channelMembers.get(ch);
        return id != null && members != null && members.contains(id);
    }

    /** Exact server-observed spelling, so point lookup need not scan the roster. */
    public synchronized String sharedMemberNick(String channel, String nick) {
        return hasSharedMember(channel, nick) ? sharedNicks.get(key(nick)) : null;
    }

    /** Sticky for the connection; callers must not assume an exhaustive cache. */
    public synchronized boolean membershipTrackingComplete() {
        return !membershipOverflow;
    }

    public synchronized void rename(String oldNick, String newNick) {
        String previous = key(oldNick), next = key(newNick);
        if (previous == null || next == null) return;
        if (previous.equals(next)) {
            if (memberships.containsKey(next)) sharedNicks.put(next, newNick);
            return;
        }
        Identity old = find(previous);
        // A nickname cannot have two live owners on the same IRC network.
        forget(newNick);
        int refs = memberships.getOrDefault(previous, 0);
        if (refs != 0) {
            memberships.remove(previous);
            memberships.put(next, refs);
            sharedNicks.remove(previous);
            sharedNicks.put(next, newNick);
            for (Set<String> members : channelMembers.values())
                if (members.remove(previous)) members.add(next);
        }
        shared.remove(previous);
        recent.remove(previous);
        if (old != null) remember(next, old);
    }

    public synchronized void forget(String nick) {
        String id = key(nick);
        if (id == null) return;
        java.util.Iterator<Map.Entry<String, Set<String>>> it =
                channelMembers.entrySet().iterator();
        while (it.hasNext()) {
            Set<String> members = it.next().getValue();
            if (members.remove(id)) release(id);
            if (members.isEmpty()) it.remove();
        }
        memberships.remove(id);
        sharedNicks.remove(id);
        shared.remove(id);
        recent.remove(id);
    }

    public synchronized void clear() {
        shared.clear();
        recent.clear();
        channelMembers.clear();
        memberships.clear();
        sharedNicks.clear();
        membershipCount = 0;
        membershipOverflow = false;
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
