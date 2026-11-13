package io.mrarm.irc.lua;

import org.junit.Test;
import static org.junit.Assert.*;

public class LuaModerationPacerTest {
    @Test public void multipleActionsKeepOrderWithoutTwelveSecondDrop() {
        LuaModerationPacer p = new LuaModerationPacer();
        LuaModerationPacer.Slot a = p.reserve("net", "automod", 1000L);
        LuaModerationPacer.Slot b = p.reserve("net", "automod", 1000L);
        LuaModerationPacer.Slot c = p.reserve("net", "automod", 1000L);
        LuaModerationPacer.Slot d = p.reserve("net", "automod", 1000L);
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);
        assertNotNull(d);
        assertEquals(0L, a.delayMs);
        assertEquals(1500L, b.delayMs);
        assertEquals(3000L, c.delayMs);
        assertEquals(4500L, d.delayMs);
        assertTrue(p.isCurrent(d));
    }

    @Test public void pacingIsPerNetworkAndAcrossScripts() {
        LuaModerationPacer p = new LuaModerationPacer();
        assertEquals(0L, p.reserve("A", "scriptA", 100L).delayMs);
        assertEquals(1500L, p.reserve("A", "scriptB", 100L).delayMs);
        assertEquals(0L, p.reserve("B", "scriptA", 100L).delayMs);
    }

    @Test public void boundsOutstandingCommandsPerScriptAndPerServer() {
        LuaModerationPacer p = new LuaModerationPacer();
        for (int i = 0; i < LuaModerationPacer.MAX_PENDING_PER_SCRIPT; i++)
            assertNotNull(p.reserve("A", "automod", 1000L));
        assertNull(p.reserve("A", "automod", 1000L));
        for (int i = LuaModerationPacer.MAX_PENDING_PER_SCRIPT;
             i < LuaModerationPacer.MAX_PENDING_PER_SERVER; i++)
            assertNotNull(p.reserve("A", "other", 1000L));
        assertNull(p.reserve("A", "third", 1000L));
    }

    @Test public void completedSlotFreesCapacityButPreservesPacing() {
        LuaModerationPacer p = new LuaModerationPacer();
        LuaModerationPacer.Slot first = p.reserve("A", "script", 0L);
        p.complete(first);
        LuaModerationPacer.Slot second = p.reserve("A", "script", 200L);
        assertEquals(1300L, second.delayMs);
    }

    @Test public void disconnectionOrReloadInvalidatesPendingActions() {
        LuaModerationPacer p = new LuaModerationPacer();
        LuaModerationPacer.Slot previous = p.reserve("A", "script", 0L);
        p.clearServer("A");
        assertFalse(p.isCurrent(previous));
        LuaModerationPacer.Slot next = p.reserve("A", "script", 100L);
        assertEquals(0L, next.delayMs);
        p.complete(previous);
        assertTrue(p.isCurrent(next));
        p.clear();
        assertFalse(p.isCurrent(next));
    }

    @Test public void expiredBacklogCannotBeScheduled() {
        LuaModerationPacer p = new LuaModerationPacer();
        for (int i = 0; i < LuaModerationPacer.MAX_PENDING_PER_SERVER; i++)
            assertNotNull(p.reserve("A", "script" + (i / 20), 0L));
        assertNull(p.reserve("A", "extra", 0L));
    }
}
