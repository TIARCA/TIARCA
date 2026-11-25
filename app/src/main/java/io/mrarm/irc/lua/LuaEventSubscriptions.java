package io.mrarm.irc.lua;

import java.util.HashMap;
import java.util.Map;

/**
 * Dispatch optimization only; never grants permissions. An unknown or loading
 * VM conservatively receives events until its real handler mask is confirmed.
 * All access is synchronized across the IRC, coordinator and reply threads.
 */
final class LuaEventSubscriptions {
    private static final String[] EVENTS = {
            "message", "connected", "disconnected", "command", "join", "part",
            "quit", "nick", "timer", "kick", "ban", "history", "moderation_log", "identity",
            "mode", "topic", "own_message"
    };
    private static final int IDENTITY_V2 = 1 << 20;
    private static final int VALID_MASK = ((1 << EVENTS.length) - 1) | IDENTITY_V2;
    private static final int UNKNOWN = -1;

    private static final class Entry {
        final int requestId;
        int mask = UNKNOWN;
        Entry(int requestId) { this.requestId = requestId; }
    }

    private final Map<String, Entry> entries = new HashMap<>();

    private static String key(String script, String network) {
        return script + ":" + network;
    }

    synchronized void clear() { entries.clear(); }

    synchronized void begin(String script, String network, int requestId) {
        entries.put(key(script, network), new Entry(requestId));
    }

    synchronized void complete(String script, String network, int requestId, int mask) {
        Entry entry = entries.get(key(script, network));
        // Old load replies cannot replace metadata for a newer VM or reset.
        if (entry != null && entry.requestId == requestId)
            entry.mask = mask >= 0 && (mask & ~VALID_MASK) == 0 ? mask : UNKNOWN;
    }

    synchronized boolean mayUseIdentityV2(String script, String network) {
        Entry entry = entries.get(key(script, network));
        if (entry == null) entry = entries.get(key(script, ""));
        return entry == null || entry.mask == UNKNOWN || (entry.mask & IDENTITY_V2) != 0;
    }

    synchronized boolean usesIdentityV2(String script, String network) {
        Entry entry = entries.get(key(script, network));
        if (entry == null) entry = entries.get(key(script, ""));
        return entry != null && entry.mask != UNKNOWN && (entry.mask & IDENTITY_V2) != 0;
    }

    synchronized boolean accepts(String script, String network, String event) {
        Entry entry = entries.get(key(script, network));
        if (entry == null) entry = entries.get(key(script, ""));
        if (entry == null || entry.mask == UNKNOWN) return true;
        for (int i = 0; i < EVENTS.length; i++)
            if (EVENTS[i].equals(event)) return (entry.mask & (1 << i)) != 0;
        return true; // Future event kinds must not be silently suppressed.
    }
}
