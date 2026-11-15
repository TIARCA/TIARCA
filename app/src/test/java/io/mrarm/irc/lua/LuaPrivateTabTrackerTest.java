package io.mrarm.irc.lua;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class LuaPrivateTabTrackerTest {
    @Test public void closesOnlyTabsCreatedByTheSameScriptAndNetwork() {
        LuaPrivateTabTracker tracker = new LuaPrivateTabTracker();
        tracker.track("network-a", "auto", "Nick1");
        tracker.track("network-a", "other", "Nick2");
        tracker.track("network-b", "auto", "Nick3");
        tracker.track("network-a", "other", "nick1");
        assertEquals(Collections.singletonList("Nick1"), tracker.take(
                "network-a", "auto", Arrays.asList("Nick1", "Nick2", "Manual")));
        assertEquals(Collections.singletonList("Nick2"), tracker.take(
                "network-a", "other", Arrays.asList("Nick1", "Nick2", "Manual")));
        assertEquals(Collections.singletonList("Nick3"), tracker.take(
                "network-b", "auto", Collections.singletonList("Nick3")));
        assertFalse(tracker.hasTracked("network-a"));
        assertFalse(tracker.hasTracked("network-b"));
    }

    @Test public void manualCloseRemovesOwnershipBeforeReopeningSameNick() {
        LuaPrivateTabTracker tracker = new LuaPrivateTabTracker();
        tracker.track("server", "auto", "Nick");
        tracker.prune("server", Collections.emptyList());
        assertTrue(tracker.take("server", "auto",
                Collections.singletonList("Nick")).isEmpty());
    }

    @Test public void onlyCurrentlyOpenTabsCanBeClosed() {
        LuaPrivateTabTracker tracker = new LuaPrivateTabTracker();
        tracker.track("server", "auto", "Nick1");
        tracker.track("server", "auto", "Nick2");
        assertEquals(Collections.singletonList("Nick2"), tracker.take(
                "server", "auto", Collections.singletonList("NICK2")));
        assertTrue(tracker.take("server", "auto",
                Collections.singletonList("Nick1")).isEmpty());
    }
}
