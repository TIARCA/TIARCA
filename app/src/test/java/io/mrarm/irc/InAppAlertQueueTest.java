package io.mrarm.irc;

import org.junit.Test;

import java.util.UUID;

import io.mrarm.chatlib.irc.IRCCaseMapping;

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
    public void privateAlertKeepsExactMessageIdentityWithoutMentionFlag() {
        InAppAlertQueue queue = new InAppAlertQueue();
        InAppAlertQueue.Alert direct = new InAppAlertQueue.Alert(UUID.fromString(
                "00000000-0000-0000-0000-000000000002"), "Alice", "Alice",
                "Private message", true, "2026-11-03:private-42", false);
        assertTrue(queue.add(direct));
        assertSame(direct, queue.current());
        assertEquals("2026-11-03:private-42", queue.current().messageId);
        assertTrue(queue.current().privateMessage);
        assertFalse(queue.current().mention);
    }

    @Test
    public void changingAlertCategoriesDropsOnlyExcludedQueuedAlerts() {
        InAppAlertQueue queue = new InAppAlertQueue();
        InAppAlertQueue.Alert mention = alert("alice");
        InAppAlertQueue.Alert direct = new InAppAlertQueue.Alert(UUID.fromString(
                "00000000-0000-0000-0000-000000000002"), "bob", "bob",
                "hello privately", true, "pvt-1", false);
        assertTrue(queue.add(mention));
        assertTrue(queue.add(direct));
        queue.removeDisabledTypes(false, true);
        assertSame(mention, queue.current());
        assertEquals(0, queue.waitingCount());
        queue.removeDisabledTypes(true, false);
        assertNull(queue.current());

        assertTrue(queue.add(mention));
        assertTrue(queue.add(direct));
        queue.removeDisabledTypes(true, false);
        assertSame(direct, queue.current());
        queue.removeDisabledTypes(true, true);
        assertSame(direct, queue.current());
    }

    @Test
    public void openingPvtClearsAllItsQueuedAlertsButKeepsMentionsAndOtherConversations() {
        UUID server = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID otherServer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        InAppAlertQueue queue = new InAppAlertQueue();
        InAppAlertQueue.Alert first = new InAppAlertQueue.Alert(server,
                "Alice", "Alice", "first", true, "pvt-1", false);
        InAppAlertQueue.Alert mention = alert("channel");
        InAppAlertQueue.Alert second = new InAppAlertQueue.Alert(server,
                "aLiCe", "Alice", "second", true, "pvt-2", false);
        InAppAlertQueue.Alert other = new InAppAlertQueue.Alert(otherServer,
                "Alice", "Alice", "another server", true, "pvt-3", false);
        InAppAlertQueue.Alert bob = new InAppAlertQueue.Alert(server,
                "Bob", "Bob", "different PVT", true, "pvt-4", false);
        queue.add(first);
        queue.add(mention);
        queue.add(second);
        queue.add(other);
        queue.add(bob);

        assertTrue(queue.dismissPrivateConversation(server, "ALICE", IRCCaseMapping.RFC1459));
        assertSame(mention, queue.current());
        assertEquals(3, queue.size());
        assertEquals(2, queue.waitingCount());
        assertFalse(queue.dismissPrivateConversation(server, "#test", IRCCaseMapping.RFC1459));
        assertFalse(queue.dismissPrivateConversation(server, null, IRCCaseMapping.RFC1459));
        assertSame(mention, queue.current());
        assertTrue(queue.dismissPrivateConversation(otherServer, "Alice", IRCCaseMapping.RFC1459));
        assertEquals(2, queue.size());
        assertTrue(queue.dismissPrivateConversation(server, "Bob", IRCCaseMapping.RFC1459));
        assertSame(mention, queue.current());
        assertEquals(1, queue.size());
    }

    @Test
    public void callerIdRequestRemainsInQueueWhenPrivateConversationIsViewed() {
        UUID server = UUID.fromString("00000000-0000-0000-0000-000000000001");
        InAppAlertQueue queue = new InAppAlertQueue();
        InAppAlertQueue.Alert mention = alert("channel");
        InAppAlertQueue.Alert request = InAppAlertQueue.Alert.callerId(server, "Alice");
        queue.add(mention);
        queue.add(request);
        assertFalse(queue.dismissPrivateConversation(server, "alice", IRCCaseMapping.RFC1459));
        assertSame(mention, queue.current());
        assertEquals(1, queue.waitingCount());
        queue.dismissCurrent();
        assertSame(request, queue.current());
        assertTrue(request.callerId);
        queue.removeDisabledTypes(false, true);
        assertNull(queue.current());
    }

    @Test
    public void nickRenameRetargetsOnlyThisServersPvtAndInvalidatesMergedRowIds() {
        UUID server = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID anotherServer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        InAppAlertQueue queue = new InAppAlertQueue();
        queue.add(new InAppAlertQueue.Alert(server,
                "Resilienza", "Resilienza", ":*", true, "2026-11-15:9", false));
        queue.add(alert("channel"));
        queue.add(new InAppAlertQueue.Alert(server,
                "Resilienza_out", "Resilienza_out", "new nick", true, "2026-11-15:3", false));
        queue.add(new InAppAlertQueue.Alert(anotherServer,
                "Resilienza", "Resilienza", "different network", true, "2026-11-15:4", false));
        queue.add(InAppAlertQueue.Alert.callerId(server, "Resilienza"));

        assertTrue(queue.renamePrivateConversation(server, "resilienza", "RESILIENZA_OUT",
                IRCCaseMapping.RFC1459));
        assertEquals(5, queue.size());
        assertEquals("RESILIENZA_OUT", queue.current().target);
        assertEquals("RESILIENZA_OUT", queue.current().sender);
        assertEquals(":*", queue.current().message);
        assertNull(queue.current().messageId);
        assertFalse(queue.current().mention);

        assertTrue(queue.dismissPrivateConversation(server, "resilienza_out",
                IRCCaseMapping.RFC1459));
        assertEquals(3, queue.size());
        assertTrue(queue.current().mention);
        queue.dismissCurrent();
        assertEquals(anotherServer, queue.current().serverUUID);
        assertEquals("2026-11-15:4", queue.current().messageId);
        queue.dismissCurrent();
        assertTrue(queue.current().callerId);
        assertEquals("Resilienza", queue.current().sender);
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
