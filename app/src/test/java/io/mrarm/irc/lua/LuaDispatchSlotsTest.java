package io.mrarm.irc.lua;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

public class LuaDispatchSlotsTest {
    @Test public void storageEventsWaitAcrossNetworksButOtherScriptsProceed() {
        LuaDispatchSlots slots = new LuaDispatchSlots(4);
        ArrayList<String> events = new ArrayList<>();
        LuaDispatchSlots.Ticket a = slots.reserve("mod", "network1", true);
        LuaDispatchSlots.Ticket b = slots.reserve("mod", "network2", true);
        LuaDispatchSlots.Ticket c = slots.reserve("mod", "network1", true);
        LuaDispatchSlots.Ticket d = slots.reserve("mask", "network1", false);
        assertNull(slots.reserve("another", "network1", true));
        slots.submit(a, () -> events.add("a")).run();
        assertNull(slots.submit(b, () -> events.add("b")));
        assertNull(slots.submit(c, () -> events.add("c")));
        slots.submit(d, () -> events.add("d")).run();
        assertEquals(Arrays.asList("a", "d"), events);
        assertNull(slots.finish(d));
        slots.finish(a).run();
        assertEquals(Arrays.asList("a", "d", "b"), events);
        slots.finish(b).run();
        assertNull(slots.finish(c));
        assertEquals(Arrays.asList("a", "d", "b", "c"), events);
        assertEquals(0, slots.size());
    }

    @Test public void oldRepliesAndCancelledDeferredEventsCannotReleaseNewGeneration() {
        LuaDispatchSlots slots = new LuaDispatchSlots(2);
        LuaDispatchSlots.Ticket a = slots.reserve("mod", "n1", true);
        LuaDispatchSlots.Ticket b = slots.reserve("mod", "n2", true);
        slots.submit(a, () -> {});
        assertNull(slots.submit(b, () -> fail("Cancelled event ran")));
        assertNull(slots.finish(b));
        slots.clear();
        LuaDispatchSlots.Ticket c = slots.reserve("mod", "n1", true);
        assertNotNull(slots.submit(c, () -> {}));
        assertNull(slots.finish(a));
        assertTrue(slots.valid(c));
        assertEquals(1, slots.size());
        assertNull(slots.finish(c));
        assertEquals(0, slots.size());
    }
}
