package io.mrarm.irc.lua;

import org.junit.Test;
import static org.junit.Assert.*;

public class LuaEventSubscriptionsTest {
    @Test public void commandOnlyScriptDoesNotReceiveChatOrTimer() {
        LuaEventSubscriptions routes = new LuaEventSubscriptions();
        routes.begin("mask", "", 1);
        routes.complete("mask", "", 1, 1 << 3);
        assertTrue(routes.accepts("mask", "a", "command"));
        assertFalse(routes.accepts("mask", "a", "message"));
        assertFalse(routes.accepts("mask", "a", "timer"));
        assertFalse(routes.accepts("mask", "b", "nick"));

        routes.begin("automod", "", 2);
        routes.complete("automod", "", 2, (1 << 0) | (1 << 3) | (1 << 7) | (1 << 8));
        assertTrue(routes.accepts("automod", "a", "message"));
        assertTrue(routes.accepts("automod", "a", "nick"));
        assertTrue(routes.accepts("automod", "a", "timer"));
        assertFalse(routes.accepts("automod", "a", "ban"));
    }

    @Test public void scopedReloadOverridesOnlyItsOwnNetwork() {
        LuaEventSubscriptions routes = new LuaEventSubscriptions();
        routes.begin("script", "", 1);
        routes.complete("script", "", 1, 1);
        routes.begin("script", "a", 2);
        assertTrue(routes.accepts("script", "a", "command")); // unknown during load
        assertFalse(routes.accepts("script", "b", "command"));
        routes.complete("script", "a", 2, 1 << 3);
        assertFalse(routes.accepts("script", "a", "message"));
        assertTrue(routes.accepts("script", "a", "command"));
        assertTrue(routes.accepts("script", "b", "message"));
        assertFalse(routes.accepts("script", "b", "command"));
    }

    @Test public void missingInvalidOrStaleMetadataCannotDropCallbacks() {
        LuaEventSubscriptions routes = new LuaEventSubscriptions();
        assertTrue(routes.accepts("script", "a", "message"));
        routes.begin("script", "", 1);
        routes.begin("script", "", 2);
        routes.complete("script", "", 1, 0);
        assertTrue(routes.accepts("script", "a", "message"));
        routes.complete("script", "", 2, 1 << 30);
        assertTrue(routes.accepts("script", "a", "message"));
        routes.clear();
        routes.complete("script", "", 2, 0);
        assertTrue(routes.accepts("script", "a", "message"));
        routes.begin("script", "", 3);
        routes.complete("script", "", 3, 0);
        assertFalse(routes.accepts("script", "a", "message"));
        assertTrue(routes.accepts("script", "a", "future_event"));
    }
}
