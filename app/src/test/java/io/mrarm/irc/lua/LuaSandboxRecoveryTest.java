package io.mrarm.irc.lua;

import org.junit.Test;
import static org.junit.Assert.*;

public class LuaSandboxRecoveryTest {
    @Test public void backoffIsBoundedAndRapidReconnectsDoNotResetIt() {
        LuaSandboxRecovery r = new LuaSandboxRecovery();
        long now = 100;
        for (long delay : new long[]{1000, 2000, 5000, 10000, 30000, 30000}) {
            r.failed(now);
            assertEquals(delay, r.remaining(now));
            assertEquals(1, r.remaining(now + delay - 1));
            assertEquals(0, r.remaining(now + delay));
            now += delay;
            r.connected(now);
            now += 10;
        }
        r.connected(now);
        now += 60000;
        r.failed(now);
        assertEquals(1000, r.remaining(now));
    }

    @Test public void stopClearsRetryDeadline() {
        LuaSandboxRecovery r = new LuaSandboxRecovery();
        r.failed(100);
        r.reset();
        assertEquals(0, r.remaining(101));
        r.failed(200);
        assertEquals(1000, r.remaining(200));
    }
}
