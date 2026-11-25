package io.mrarm.irc.lua;

import java.util.Locale;

/** Process-wide, bounded, in-memory diagnostic counters. No IRC text or identities. */
final class LuaEngineMetrics {
    enum Count {
        RECEIVED, FILTERED, UNAVAILABLE, ENQUEUED, INPUT_FULL, POST_FAILED,
        STALE_INPUT, PREPARED, SLOT_FULL, DISPATCH_REJECTED, CANCELLED_SLOTS,
        IPC_SENT, IPC_FAILED, REPLIED, CALLBACK_FAILED, COMPLETED, EFFECTS_SKIPPED,
        ROSTER_CAPTURE, ROSTER_ROWS, IDENTITY_QUERY, STORAGE_SNAPSHOT,
        STORAGE_TRANSACTION, STORAGE_WRITE, STORAGE_NOOP, STORAGE_FAILURE, JOURNAL_WRITE, JOURNAL_FAILURE,
        SANDBOX_EXIT, EXIT_LOW_MEMORY, EXIT_SYSTEM, EXIT_CRASH, EXIT_UNKNOWN,
        BIND_ATTEMPT, BIND_FAILURE, RECOVERED, OUTAGE_INGRESS, OUTAGE_SLOTS
    }

    private final long[] counts = new long[Count.values().length];
    private final Latency queue = new Latency(), ipc = new Latency(),
            nativeCall = new Latency(), total = new Latency(), storage = new Latency();
    private final long since = System.nanoTime();
    private long memory = -1, peak = -1;
    private long outageSince = -1, outageTotal, outageIngressStart, outageSlotsStart;

    static final class Gap {
        final long durationMs, ingress, slots;
        Gap(long durationMs, long ingress, long slots) {
            this.durationMs = durationMs;
            this.ingress = ingress;
            this.slots = slots;
        }
    }

    synchronized void beginOutage(long now) {
        if (outageSince >= 0) return;
        outageSince = now;
        outageIngressStart = count(Count.OUTAGE_INGRESS);
        outageSlotsStart = count(Count.OUTAGE_SLOTS);
    }

    synchronized void unavailable(boolean engineAbsent) {
        inc(Count.UNAVAILABLE);
        if (engineAbsent && outageSince >= 0) inc(Count.OUTAGE_INGRESS);
    }

    synchronized Gap endOutage(long now, boolean recovered) {
        if (outageSince < 0) return null;
        long duration = Math.max(0, now - outageSince);
        outageTotal += duration;
        outageSince = -1;
        if (!recovered) return null;
        inc(Count.RECOVERED);
        return new Gap(duration, count(Count.OUTAGE_INGRESS) - outageIngressStart,
                count(Count.OUTAGE_SLOTS) - outageSlotsStart);
    }

    synchronized String outageLine(long now) {
        long current = outageSince < 0 ? 0 : Math.max(0, now - outageSince);
        return "Interruzioni: totale " + (outageTotal + current) + " ms; attuale " + current
                + " ms; ingressi non disponibili " + c(Count.OUTAGE_INGRESS)
                + "; slot annullati per arresto " + c(Count.OUTAGE_SLOTS);
    }

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
            "Sandbox: arresti osservati " + c(Count.SANDBOX_EXIT)
                    + "; LOW_MEMORY " + c(Count.EXIT_LOW_MEMORY) + "; sistema " + c(Count.EXIT_SYSTEM)
                    + "; crash/ANR " + c(Count.EXIT_CRASH) + "; incerti " + c(Count.EXIT_UNKNOWN),
            "Sandbox: tentativi bind " + c(Count.BIND_ATTEMPT) + "; falliti "
                    + c(Count.BIND_FAILURE) + "; connessioni ripristinate " + c(Count.RECOVERED),
            "Latenza media/max: coda " + queue.display() + "; IPC " + ipc.display(),
            "Latenza media/max: JNI " + nativeCall.display() + "; totale " + total.display(),
            "Roster: snapshot legacy " + c(Count.ROSTER_CAPTURE) + "; righe inviate " + c(Count.ROSTER_ROWS)
                    + "; query identita " + c(Count.IDENTITY_QUERY),
            "Storage: snapshot IPC " + c(Count.STORAGE_SNAPSHOT) + "; transazioni " + c(Count.STORAGE_TRANSACTION)
                    + "; scritture riuscite " + c(Count.STORAGE_WRITE) + "; no-op " + c(Count.STORAGE_NOOP)
                    + "; errori " + c(Count.STORAGE_FAILURE),
            "Storage media/max: " + storage.display(),
            "Journal: scritture " + c(Count.JOURNAL_WRITE) + "; errori " + c(Count.JOURNAL_FAILURE),
            "Allocazioni Lua (byte): ultimo campione " + (memory < 0 ? "n/d" : memory)
                    + "; massimo campionato " + (peak < 0 ? "n/d" : peak)
        };
    }
}
