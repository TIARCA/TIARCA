package io.mrarm.irc.lua;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LuaCrashTrackerTest {
    @Test public void queuedRequestsAreNotMistakenForExecutingScript() {
        LuaCrashTracker tracker = new LuaCrashTracker();
        assertEquals(-1, tracker.activeRequestId());
        assertFalse(tracker.terminated(null, false));
        tracker.started(12);
        assertEquals(12, tracker.activeRequestId());
        assertFalse(tracker.terminated("script-A", true));
        assertEquals(-1, tracker.activeRequestId());
        tracker.started(19);
        assertFalse(tracker.terminated("script-B", true));
        tracker.started(23);
        assertFalse(tracker.terminated("script-A", true));
    }

    @Test public void repeatedDeathInSameScriptPhaseDisablesOnlyThatScript() {
        LuaCrashTracker tracker = new LuaCrashTracker();
        tracker.started(1);
        assertFalse(tracker.terminated("script-A", true));
        tracker.started(2);
        assertTrue(tracker.terminated("script-A", true));
        tracker.started(3);
        assertFalse(tracker.terminated("script-B", true));
        tracker.started(4);
        assertTrue(tracker.terminated("script-B", true));
    }

    @Test public void successfulRetryClearsOnlyMatchingPhaseHistory() {
        LuaCrashTracker tracker = new LuaCrashTracker();
        tracker.started(1);
        assertFalse(tracker.terminated("script-A", true));
        tracker.started(2);
        tracker.completed(2, "script-A", false);
        tracker.started(3);
        assertTrue(tracker.terminated("script-A", true));
        tracker.reset();
        tracker.started(4);
        assertFalse(tracker.terminated("script-A", false));
        tracker.started(5);
        tracker.completed(5, "script-A", false);
        tracker.started(6);
        assertFalse(tracker.terminated("script-A", false));
    }

    @Test public void successfulAndStaleRepliesAreHandledSeparately() {
        LuaCrashTracker tracker = new LuaCrashTracker();
        tracker.started(7);
        tracker.completed(6, "script-A", true);
        assertEquals(7, tracker.activeRequestId());
        tracker.completed(7, "script-A", true);
        assertEquals(-1, tracker.activeRequestId());
        tracker.started(8);
        tracker.reset();
        assertEquals(-1, tracker.activeRequestId());
        assertFalse(tracker.terminated(null, false));
    }
}
