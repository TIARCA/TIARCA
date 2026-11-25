package io.mrarm.irc.lua;

import org.junit.Test;
import static org.junit.Assert.*;

public class LuaEngineMetricsTest {
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
