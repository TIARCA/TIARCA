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

    @Test public void standardWhoNumericsAreObservedWithoutHandlerCollision()
            throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        MessageHandler handler = new MessageHandler(connection);
        handler.handleLine(":irc.example 352 me #room ident cloak.host irc.example Alice H :0 Alice");
        assertEquals("ident", connection.getIdentityCache().get("Alice").ident);
        assertEquals("cloak.host", connection.getIdentityCache().get("Alice").host);
        connection.getIdentityCache().expectWhox();
        handler.handleLine(":irc.example 354 me 042 ident2 cloak2 Bob H account2");
        assertEquals("ident2", connection.getIdentityCache().get("Bob").ident);
        assertEquals("cloak2", connection.getIdentityCache().get("Bob").host);
        assertEquals("account2", connection.getIdentityCache().get("Bob").account);
        handler.handleLine(":irc.example 315 me #room :End of WHO");
    }

    @Test public void ircv3AccountEvidenceAndExplicitLogout() throws Exception {
        ServerConnectionData data = new ServerConnectionData();
        assertNotNull(data.getCapabilityManager().getCapability(
                io.mrarm.chatlib.irc.cap.AccountTagCapability.class));
        assertNotNull(data.getCapabilityManager().getCapability(
                io.mrarm.chatlib.irc.cap.ExtendedJoinCapability.class));
        assertNotNull(data.getCapabilityManager().getCapability(
                io.mrarm.chatlib.irc.cap.AccountNotifyCapability.class));
        data.setUserInfoApi(new io.mrarm.chatlib.user.SimpleUserInfoApi());
        data.setMessageStorageApi(new io.mrarm.chatlib.message.SimpleMessageStorageApi());
        data.onChannelJoined("#room");
        MessageHandler handler = new MessageHandler(data);
        handler.handleLine("@account=registered :Alice!ident@host PRIVMSG #room :hello");
        assertEquals("registered", data.getIdentityCache().get("Alice").account);
        handler.handleLine("@account=* :Alice!ident@host PRIVMSG #room :bye");
        assertTrue(data.getIdentityCache().get("Alice").accountKnown);
        assertNull(data.getIdentityCache().get("Alice").account);
        handler.handleLine(":Bob!ident@host JOIN #room accountBob :Real Name");
        assertEquals("accountBob", data.getIdentityCache().get("Bob").account);
        new io.mrarm.chatlib.irc.cap.AccountNotifyCapability().handle(data,
                new MessagePrefix("Bob!ident@host"), "ACCOUNT",
                Arrays.asList("*"), java.util.Collections.emptyMap());
        assertTrue(data.getIdentityCache().get("Bob").accountKnown);
        assertNull(data.getIdentityCache().get("Bob").account);
        handler.handleLine(":Unknown!ident@host PRIVMSG #room :hello");
        assertFalse("Unknown account must never imply logged-out",
                data.getIdentityCache().get("Unknown").accountKnown);
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
        cache.noteNumeric("354", Arrays.asList("me", "042", "ident", "host",
                "nick", "H", "0"));
        assertNull("Explicit WHOX logged-out state must clear old account",
                cache.get("nick").account);
        cache.clear();
        assertNull(cache.get("nick"));
    }
}
