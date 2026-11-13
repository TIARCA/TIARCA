package io.mrarm.irc.lua;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Volatile, network-scoped Lua console state. No permissions are persisted or
 * changed by stop/start/restart. All callers may run on different threads.
 */
final class LuaConsoleState {
    static final int MAX_LOG_LINES = 16;

    static final class LogLine {
        final String script;
        final String message;

        LogLine(String script, String message) {
            this.script = script;
            this.message = message;
        }
    }

    private static final class NetworkState {
        boolean paused;
        long generation;
        final ArrayDeque<LogLine> recent = new ArrayDeque<>();
    }

    private final Map<String, NetworkState> networks = new HashMap<>();

    private NetworkState get(String server) {
        NetworkState state = networks.get(server);
        if (state == null) {
            state = new NetworkState();
            networks.put(server, state);
        }
        return state;
    }

    synchronized boolean isPaused(String server) {
        NetworkState state = networks.get(server);
        return state != null && state.paused;
    }

    synchronized long generation(String server) {
        NetworkState state = networks.get(server);
        return state == null ? 0 : state.generation;
    }

    synchronized long stop(String server) {
        NetworkState state = get(server);
        state.paused = true;
        return ++state.generation;
    }

    synchronized long start(String server) {
        NetworkState state = get(server);
        state.paused = false;
        return ++state.generation;
    }

    synchronized long beginRestart(String server) {
        return stop(server);
    }

    synchronized boolean completeRestart(String server, long generation) {
        NetworkState state = get(server);
        if (state.generation != generation || !state.paused) return false;
        state.paused = false;
        ++state.generation;
        return true;
    }

    synchronized void log(String server, String script, String text) {
        if (server == null || server.isEmpty() || text == null) return;
        NetworkState state = get(server);
        String clean = text.replace('\r', ' ').replace('\n', ' ')
                .replace('\u0000', ' ');
        if (clean.length() > 180) clean = clean.substring(0, 180);
        if (state.recent.size() == MAX_LOG_LINES) state.recent.removeFirst();
        state.recent.addLast(new LogLine(script, clean));
    }

    synchronized List<LogLine> logs(String server) {
        NetworkState state = networks.get(server);
        return state == null ? new ArrayList<>() : new ArrayList<>(state.recent);
    }
}
