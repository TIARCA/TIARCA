package io.mrarm.irc.lua;

import org.junit.Test;
import static org.junit.Assert.*;

public class LuaBanMaskMatcherTest {
    @Test public void exactNickIsNotInferredFromWildcards() {
        assertEquals("Alice", LuaBanMaskMatcher.exactNick("Alice!*@*"));
        assertNull(LuaBanMaskMatcher.exactNick("*!*@example.org"));
        assertNull(LuaBanMaskMatcher.exactNick("Al?ce!*@*"));
        assertNull(LuaBanMaskMatcher.exactNick("R:alice"));
    }
    @Test public void observedIdentityMatchesMask() {
        assertTrue(LuaBanMaskMatcher.matches("*!*@net.example.org","Alice","user","net.example.org"));
        assertTrue(LuaBanMaskMatcher.matches("*!user@*.example.org","Alice","user","net.example.org"));
        assertFalse(LuaBanMaskMatcher.matches("*!other@net.example.org","Alice","user","net.example.org"));
        assertFalse(LuaBanMaskMatcher.matches("*!*@net.example.org","Alice","user","other.net"));
        assertFalse(LuaBanMaskMatcher.matches("*!*@host","Alice","","host"));
        assertFalse(LuaBanMaskMatcher.matches("m:U:*!*@host","Alice","user","host"));
    }
    @Test public void blanketAndExtbansCannotNameOneTarget() {
        assertFalse(LuaBanMaskMatcher.mayIdentify("*!*@*"));
        assertFalse(LuaBanMaskMatcher.mayIdentify("R:registered"));
        assertFalse(LuaBanMaskMatcher.mayIdentify("m:*!*@host"));
        assertTrue(LuaBanMaskMatcher.mayIdentify("*!*@host"));
    }
}
