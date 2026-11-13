package io.mrarm.irc.lua;

import java.util.HashMap;
import java.util.Map;

/**
 * Small io-thread-only scheduler for Lua moderator commands. Existing Lua APIs
 * may emit several actions in order, but these actions must not flood an IRC
 * server or accumulate indefinitely while Android/IRC is stalled.
 *
 * Reservation does not mean delivery: the dispatcher rechecks live consent,
 * operator rights, channel membership and the target at execution time.
 */
final class LuaModerationPacer {
    static final long SPACING_MS = 1500L;
    static final long MAX_WAIT_MS = 60000L;
    static final int MAX_PENDING_PER_SERVER = 40;
    static final int MAX_PENDING_PER_SCRIPT = 20;

    private static final class ServerState {
        long nextAllowedMs;
        int pending;
        final Map<String, Integer> byScript = new HashMap<>();
    }

    static final class Slot {
        final String server;
        final String scriptId;
        final ServerState owner;
        final long delayMs;

        private Slot(String server, String scriptId, ServerState owner, long delayMs) {
            this.server = server;
            this.scriptId = scriptId;
            this.owner = owner;
            this.delayMs = delayMs;
        }
    }

    private final Map<String, ServerState> servers = new HashMap<>();

    Slot reserve(String server, String scriptId, long nowMs) {
        ServerState state = servers.get(server);
        if (state == null) {
            state = new ServerState();
            servers.put(server, state);
        }
        if (state.pending >= MAX_PENDING_PER_SERVER ||
                state.byScript.getOrDefault(scriptId, 0) >= MAX_PENDING_PER_SCRIPT)
            return null;
        long dueMs = Math.max(nowMs, state.nextAllowedMs);
        if (dueMs - nowMs > MAX_WAIT_MS) return null;
        state.nextAllowedMs = dueMs + SPACING_MS;
        state.pending++;
        state.byScript.put(scriptId, state.byScript.getOrDefault(scriptId, 0) + 1);
        return new Slot(server, scriptId, state, dueMs - nowMs);
    }

    boolean isCurrent(Slot slot) {
        return servers.get(slot.server) == slot.owner;
    }

    void complete(Slot slot) {
        if (!isCurrent(slot)) return;
        ServerState state = slot.owner;
        if (state.pending == 0) return;
        state.pending--;
        int count = state.byScript.getOrDefault(slot.scriptId, 0);
        if (count <= 1) state.byScript.remove(slot.scriptId);
        else state.byScript.put(slot.scriptId, count - 1);
        // Keep nextAllowedMs even when the queue empties: a subsequent burst
        // must still respect the spacing after the most recent command.
    }

    void clearServer(String server) {
        servers.remove(server); // Invalidates all outstanding slots for this network.
    }

    void clear() {
        servers.clear(); // Also invalidates scheduled runnables after reload/stop.
    }
}
