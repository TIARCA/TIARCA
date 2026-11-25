package io.mrarm.irc.lua;

import java.util.Locale;

/** Process-wide, bounded, in-memory diagnostic counters. No IRC text or identities. */
final class LuaEngineMetrics {
    enum Count {
        RECEIVED, FILTERED, UNAVAILABLE, ENQUEUED, INPUT_FULL, POST_FAILED,
        STALE_INPUT, PREPARED, SLOT_FULL, DISPATCH_REJECTED, CANCELLED_SLOTS,
        IPC_SENT, IPC_FAILED, REPLIED, CALLBACK_FAILED, COMPLETED, EFFECTS_SKIPPED,
        ROSTER_CAPTURE, ROSTER_ROWS, IDENTITY_QUERY, STORAGE_SNAPSHOT,
        STORAGE_TRANSACTION, STORAGE_WRITE, STORAGE_NOOP, STORAGE_FAILURE
    }

    private final long[] counts = new long[Count.values().length];
    private final Latency queue = new Latency(), ipc = new Latency(),
            nativeCall = new Latency(), total = new Latency(), storage = new Latency();
    private final long since = System.nanoTime();
    private long memory = -1, peak = -1;

    static final class Latency {
        long n, sum, max;
        void add(long nanos) {
            if (nanos < 0) return;
            n++;
            sum += nanos;
            max = Math.max(max, nanos);
        }
        String display() {
            return n == 0 ? "n/d" : String.format(Locale.ROOT,
                    "%.2f/%.2f ms (n=%d)", sum / (double)n / 1e6, max / 1e6, n);
        }
    }

    synchronized void inc(Count c) { counts[c.ordinal()]++; }
    synchronized void add(Count c, long n) { counts[c.ordinal()] += n; }
    synchronized long count(Count c) { return counts[c.ordinal()]; }
    synchronized void queue(long n) { queue.add(n); }
    synchronized void ipc(long n) { ipc.add(n); }
    synchronized void nativeCall(long n) { nativeCall.add(n); }
    synchronized void total(long n) { total.add(n); }
    synchronized void storage(long n) { storage.add(n); }
    synchronized void memory(long bytes) {
        if (bytes < 0) return;
        memory = bytes;
        peak = Math.max(peak, bytes);
    }
    synchronized void memoryUnavailable() { memory = -1; }
    private String c(Count k) { return Long.toString(counts[k.ordinal()]); }

    synchronized String[] lines() {
        return new String[]{
            "Motore Lua globale (tutte le reti), da " + Math.max(0, (System.nanoTime()-since)/1000000000L) + " s",
            "Eventi ingresso: ricevuti " + c(Count.RECEIVED) + "; accodati " + c(Count.ENQUEUED)
                    + "; senza destinatari " + c(Count.FILTERED) + "; sospesi/assenti " + c(Count.UNAVAILABLE),
            "Ingressi persi: coda piena " + c(Count.INPUT_FULL) + "; post fallito " + c(Count.POST_FAILED)
                    + "; invalidati " + c(Count.STALE_INPUT),
            "Consegne script: preparate " + c(Count.PREPARED) + "; saturazioni slot " + c(Count.SLOT_FULL)
                    + "; rifiuti " + c(Count.DISPATCH_REJECTED) + "; slot annullati " + c(Count.CANCELLED_SLOTS),
            "IPC: inviati " + c(Count.IPC_SENT) + "; errori invio " + c(Count.IPC_FAILED)
                    + "; risposte " + c(Count.REPLIED),
            "Consegne completate " + c(Count.COMPLETED) + "; callback fallite " + c(Count.CALLBACK_FAILED)
                    + "; effetti scartati " + c(Count.EFFECTS_SKIPPED),
            "Latenza media/max: coda " + queue.display() + "; IPC " + ipc.display(),
            "Latenza media/max: JNI " + nativeCall.display() + "; totale " + total.display(),
            "Roster: snapshot legacy " + c(Count.ROSTER_CAPTURE) + "; righe inviate " + c(Count.ROSTER_ROWS)
                    + "; query identita " + c(Count.IDENTITY_QUERY),
            "Storage: snapshot IPC " + c(Count.STORAGE_SNAPSHOT) + "; transazioni " + c(Count.STORAGE_TRANSACTION)
                    + "; scritture riuscite " + c(Count.STORAGE_WRITE) + "; no-op " + c(Count.STORAGE_NOOP)
                    + "; errori " + c(Count.STORAGE_FAILURE),
            "Storage media/max: " + storage.display(),
            "Allocazioni Lua (byte): ultimo campione " + (memory < 0 ? "n/d" : memory)
                    + "; massimo campionato " + (peak < 0 ? "n/d" : peak)
        };
    }
}
