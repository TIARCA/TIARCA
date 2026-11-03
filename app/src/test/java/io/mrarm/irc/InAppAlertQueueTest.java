package io.mrarm.irc;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class InAppAlertQueueTest {

    private static InAppAlertQueue.Alert alert(String sender) {
        return new InAppAlertQueue.Alert(UUID.fromString(
                "00000000-0000-0000-0000-000000000001"), "#test", sender,
                "Hello", false, "2026-11-03:" + sender, true);
    }

    @Test
    public void keepsFirstAlertAndCountsPendingMessages() {
        InAppAlertQueue queue = new InAppAlertQueue();
        InAppAlertQueue.Alert first = alert("alice");
        InAppAlertQueue.Alert second = alert("bob");
        InAppAlertQueue.Alert third = alert("charlie");

        assertTrue(queue.add(first));
        assertTrue(queue.add(second));
        assertTrue(queue.add(third));
        assertSame(first, queue.current());
        assertEquals("2026-11-03:alice", queue.current().messageId);
        assertTrue(queue.current().mention);
        assertEquals(2, queue.waitingCount());

        assertTrue(queue.dismissCurrent());
        assertSame(second, queue.current());
        assertEquals("2026-11-03:bob", queue.current().messageId);
        assertEquals(1, queue.waitingCount());

        assertTrue(queue.dismissCurrent());
        assertSame(third, queue.current());
        assertEquals(0, queue.waitingCount());

        assertTrue(queue.dismissCurrent());
        assertNull(queue.current());
        assertEquals(0, queue.waitingCount());
        assertFalse(queue.dismissCurrent());
    }

    @Test
    public void isBoundedWithoutReplacingCurrentOrOlderWaitingAlerts() {
        InAppAlertQueue queue = new InAppAlertQueue();
        InAppAlertQueue.Alert first = alert("first");
        assertTrue(queue.add(first));
        for (int i = 1; i < InAppAlertQueue.MAX_ALERTS; i++)
            assertTrue(queue.add(alert("nick" + i)));

        assertFalse(queue.add(alert("overflow")));
        assertSame(first, queue.current());
        assertEquals(InAppAlertQueue.MAX_ALERTS - 1, queue.waitingCount());
        queue.clear();
        assertEquals(0, queue.size());
        assertNull(queue.current());
    }
}
