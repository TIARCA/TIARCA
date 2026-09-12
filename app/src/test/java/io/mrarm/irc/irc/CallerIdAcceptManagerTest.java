package io.mrarm.irc.irc;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class CallerIdAcceptManagerTest {

    @Test
    public void parsesServerAddConfirmation() {
        CallerIdAcceptManager.AcceptNotice notice = CallerIdAcceptManager.parseAcceptNotice(
                "Alice is now on your accept list");

        assertEquals("Alice", notice.nick);
        assertTrue(notice.accepted);
    }

    @Test
    public void parsesServerRemoveConfirmation() {
        CallerIdAcceptManager.AcceptNotice notice = CallerIdAcceptManager.parseAcceptNotice(
                "Alice is no longer on your accept list.");

        assertEquals("Alice", notice.nick);
        assertFalse(notice.accepted);
    }

    @Test
    public void ignoresUnrelatedServerNotice() {
        assertNull(CallerIdAcceptManager.parseAcceptNotice("Alice changed nickname"));
    }
}
