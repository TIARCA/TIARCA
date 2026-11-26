package io.mrarm.irc.lua;

import org.junit.Test;
import static org.junit.Assert.*;

public class LuaEngineMetricsTest {
    @Test public void outageCountersExcludePausedIngressAndKeepSlotsSeparate() {
        LuaEngineMetrics m = new LuaEngineMetrics();
        m.unavailable(true);
        m.beginOutage(100);
        m.unavailable(false);
        m.unavailable(true);
        m.add(LuaEngineMetrics.Count.OUTAGE_SLOTS, 3);
        m.beginOutage(200);
        LuaEngineMetrics.Gap gap = m.endOutage(1100, true);
        assertEquals(1000, gap.durationMs);
        assertEquals(1, gap.ingress);
        assertEquals(3, gap.slots);
        assertEquals(1, m.count(LuaEngineMetrics.Count.RECOVERED));
        assertNull(m.endOutage(1200, true));
        m.beginOutage(2000);
        assertTrue(m.outageLine(2300).contains("totale 1300 ms; attuale 300 ms"));
        assertNull(m.endOutage(2400, false));
        assertEquals(1, m.count(LuaEngineMetrics.Count.RECOVERED));
    }

    @Test public void separateIngressDeliveryStorageAndMemory() {
        LuaEngineMetrics m = new LuaEngineMetrics();
        m.inc(LuaEngineMetrics.Count.RECEIVED);
        m.inc(LuaEngineMetrics.Count.RECEIVED);
        m.inc(LuaEngineMetrics.Count.FILTERED);
        m.inc(LuaEngineMetrics.Count.ENQUEUED);
        m.add(LuaEngineMetrics.Count.ROSTER_ROWS, 128);
        m.inc(LuaEngineMetrics.Count.STORAGE_NOOP);
        m.memory(100);
        m.memory(50);
        m.queue(1000000);
        m.queue(3000000);
        m.nativeCall(-1);
        String result = String.join("\n", m.lines());
        assertTrue(result.contains("ricevuti 2"));
        assertTrue(result.contains("righe inviate 128"));
        assertTrue(result.contains("scritture riuscite 0; no-op 1"));
        assertTrue(result.contains("ultimo campione 50; massimo campionato 100"));
        assertTrue(result.contains("2.00/3.00 ms (n=2)"));
        assertTrue(result.contains("JNI n/d"));
        m.memoryUnavailable();
        assertTrue(String.join("\n", m.lines()).contains("ultimo campione n/d; massimo campionato 100"));
    }
}
