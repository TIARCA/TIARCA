package io.mrarm.irc.lua;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Bounded reservations, including deferred events. Work is run outside the monitor. */
final class LuaDispatchSlots {
    static final class Ticket {
        final String script, server;
        final boolean serial;
        Runnable task;
        Ticket(String script, String server, boolean serial) {
            this.script = script; this.server = server; this.serial = serial;
        }
    }

    private final int capacity;
    private final Set<Ticket> live = new HashSet<>();
    private final Map<String, Ticket> active = new HashMap<>();
    private final Map<String, ArrayDeque<Ticket>> waiting = new HashMap<>();

    LuaDispatchSlots(int capacity) { this.capacity = capacity; }

    synchronized Ticket reserve(String script, String server, boolean serial) {
        if (live.size() >= capacity) return null;
        Ticket ticket = new Ticket(script, server, serial);
        live.add(ticket);
        return ticket;
    }

    synchronized boolean valid(Ticket ticket) { return live.contains(ticket); }
    synchronized int size() { return live.size(); }

    /** Returns work ready to run now, or retains it in the bounded FIFO. */
    synchronized Runnable submit(Ticket ticket, Runnable task) {
        if (!live.contains(ticket)) return null;
        if (!ticket.serial) return task;
        if (active.containsKey(ticket.script)) {
            ticket.task = task;
            waiting.computeIfAbsent(ticket.script, ignored -> new ArrayDeque<>()).add(ticket);
            return null;
        }
        active.put(ticket.script, ticket);
        return task;
    }

    /** Idempotent, including stale replies after reset. */
    synchronized Runnable finish(Ticket ticket) {
        if (!live.remove(ticket) || !ticket.serial) return null;
        if (active.get(ticket.script) != ticket) {
            ArrayDeque<Ticket> queue = waiting.get(ticket.script);
            if (queue != null) {
                queue.remove(ticket);
                if (queue.isEmpty()) waiting.remove(ticket.script);
            }
            ticket.task = null;
            return null;
        }
        active.remove(ticket.script);
        ArrayDeque<Ticket> queue = waiting.get(ticket.script);
        if (queue == null) return null;
        while (!queue.isEmpty()) {
            Ticket next = queue.removeFirst();
            if (live.contains(next)) {
                active.put(next.script, next);
                Runnable task = next.task;
                next.task = null;
                if (queue.isEmpty()) waiting.remove(ticket.script);
                return task;
            }
        }
        waiting.remove(ticket.script);
        return null;
    }

    synchronized void clear() {
        live.clear(); active.clear(); waiting.clear();
    }
}
