package io.mrarm.chatlib.irc;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class IrcIdentityCacheTest {
    @Test public void normalWhoReplyOnlySetsFieldsThatAreReallyKnown() {
        IrcIdentityCache cache = new IrcIdentityCache();
        cache.noteNumeric("352", Arrays.asList("me", "#room", "ident", "cloak.host",
                "server", "Alice", "H", "0 test"));
        assertEquals("ident", cache.get("alice").ident);
        assertEquals("cloak.host", cache.get("ALICE").host);
        assertNull(cache.get("alice").account);
        cache.noteNumeric("330", Arrays.asList("me", "Alice", "registered", "logged in"));
        assertEquals("registered", cache.get("Alice").account);
        cache.rename("Alice", "Alice2");
        assertNull(cache.get("Alice"));
        assertEquals("cloak.host", cache.get("alice2").host);
        cache.forget("Alice2");
        assertNull(cache.get("Alice2"));
    }

    @Test public void unknownWhoxTokenMustNotPopulateData() {
        IrcIdentityCache cache = new IrcIdentityCache();
        cache.noteNumeric("354", Arrays.asList("me", "042", "ident", "host",
                "nick", "H", "account"));
        assertNull(cache.get("nick"));
        cache.expectWhox();
        cache.noteNumeric("354", Arrays.asList("me", "other", "ident", "host",
                "nick", "H", "account"));
        assertNull(cache.get("nick"));
        cache.noteNumeric("354", Arrays.asList("me", "042", "ident", "host",
                "nick", "H", "account"));
        assertEquals("account", cache.get("nick").account);
        cache.clear();
        assertNull(cache.get("nick"));
    }
}
