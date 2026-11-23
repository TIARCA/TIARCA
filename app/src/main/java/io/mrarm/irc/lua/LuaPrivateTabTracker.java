package io.mrarm.irc.lua;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Tracks only PVTs created by successful script sends in this app process. Main thread only. */
final class LuaPrivateTabTracker {
    private static final class Owned {
        final String scriptId;
        final String nick;
        Owned(String scriptId, String nick) {
            this.scriptId = scriptId;
            this.nick = nick;
        }
    }

    private final Map<String, Map<String, Owned>> byServer = new HashMap<>();

    void track(String server, String scriptId, String nick) {
        if (server == null || scriptId == null || nick == null || nick.isEmpty()) return;
        byServer.computeIfAbsent(server, ignored -> new LinkedHashMap<>())
                .putIfAbsent(nick.toLowerCase(Locale.ROOT), new Owned(scriptId, nick));
    }

    void prune(String server, List<String> currentlyOpen) {
        Map<String, Owned> tabs = byServer.get(server);
        if (tabs == null) return;
        Set<String> open = new HashSet<>();
        if (currentlyOpen != null) {
            for (String nick : currentlyOpen)
                if (nick != null) open.add(nick.toLowerCase(Locale.ROOT));
        }
        tabs.keySet().retainAll(open);
        if (tabs.isEmpty()) byServer.remove(server);
    }

    List<String> take(String server, String scriptId, List<String> currentlyOpen) {
        prune(server, currentlyOpen);
        List<String> result = new ArrayList<>();
        Map<String, Owned> tabs = byServer.get(server);
        if (tabs == null) return result;
        List<String> remove = new ArrayList<>();
        for (Map.Entry<String, Owned> entry : tabs.entrySet()) {
            if (entry.getValue().scriptId.equals(scriptId)) {
                result.add(entry.getValue().nick);
                remove.add(entry.getKey());
            }
        }
        for (String nick : remove) tabs.remove(nick);
        if (tabs.isEmpty()) byServer.remove(server);
        return result;
    }

    boolean isOwned(String server, String nick) {
        Map<String, Owned> tabs = byServer.get(server);
        return tabs != null && nick != null &&
                tabs.containsKey(nick.toLowerCase(Locale.ROOT));
    }

    boolean hasTracked(String server) {
        return byServer.containsKey(server);
    }
}
