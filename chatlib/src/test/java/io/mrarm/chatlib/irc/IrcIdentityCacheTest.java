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

    @Test public void largeRosterDoesNotEvictRecentlyObservedAccounts() {
        IrcIdentityCache cache = new IrcIdentityCache();
        cache.note("first", "ident", "host.example", "registered");
        for (int i = 0; i < 1024; i++)
            cache.note("guest" + i, "ident", "guest.example", "0");
        assertNotNull("Active channel identities must survive a roster >512", cache.get("first"));
        assertEquals("registered", cache.get("first").account);
        assertTrue(cache.get("guest1023").accountKnown);
        assertNull(cache.get("guest1023").account);
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

    @Test public void sharedIdentitySurvivesTransientChurnAndLastPartClearsIt() {
        IrcIdentityCache cache = new IrcIdentityCache();
        cache.memberJoined("#a", "Alice");
        cache.memberJoined("#b", "ALICE");
        cache.note("Alice", "ident", "host.example", "registered");
        for (int i = 0; i < IrcIdentityCache.MAX_RECENT + 100; i++)
            cache.note("guest" + i, "ident", "guest.example", "*");
        assertEquals("registered", cache.get("Alice").account);
        cache.memberLeft("#A", "alice");
        assertNotNull(cache.get("Alice"));
        cache.channelLeft("#B");
        assertNull(cache.get("Alice"));
    }

    @Test public void completedRosterPromotesAndReconcilesSharedIdentities() {
        IrcIdentityCache cache = new IrcIdentityCache();
        cache.note("Alice", "ident", "host", "acct");
        cache.replaceChannelMembers("#a", Arrays.asList("Alice", "Bob"));
        cache.note("Bob", "ident", "host", "acct2");
        cache.replaceChannelMembers("#b", Arrays.asList("Bob"));
        cache.replaceChannelMembers("#a", Arrays.asList("Alice", "Charlie"));
        assertEquals("acct", cache.get("Alice").account);
        assertEquals("acct2", cache.get("Bob").account);
        cache.channelLeft("#b");
        assertNull(cache.get("Bob"));
        cache.rename("Alice", "NewAlice");
        assertNull(cache.get("Alice"));
        assertEquals("acct", cache.get("NewAlice").account);
        cache.forget("NewAlice");
        assertNull(cache.get("NewAlice"));
        cache.clear();
        assertTrue(cache.membershipTrackingComplete());
    }

    @Test public void unobservedMaskChangeDoesNotInheritAccountButChghostDoes() {
        IrcIdentityCache cache = new IrcIdentityCache();
        cache.memberJoined("#a", "Alice");
        cache.note("Alice", "oldident", "old.host", "registered");
        cache.changeHost("Alice", "newident", "new.host");
        assertEquals("registered", cache.get("Alice").account);
        assertEquals("new.host", cache.get("Alice").host);
        cache.note("Alice", "otherident", "other.host", null);
        assertFalse(cache.get("Alice").accountKnown);
        assertNull(cache.get("Alice").account);
        cache.note("Alice", null, null, "*");
        assertTrue(cache.get("Alice").accountKnown);
        assertNull(cache.get("Alice").account);
    }

    @Test public void sharedCapacityIsBoundedWithoutEvictingExistingMembers() {
        IrcIdentityCache cache = new IrcIdentityCache();
        cache.memberJoined("#a", "first");
        cache.note("first", "ident", "host", "registered");
        for (int i = 1; i < IrcIdentityCache.MAX_SHARED; i++)
            cache.memberJoined("#a", "member" + i);
        assertTrue(cache.membershipTrackingComplete());
        cache.memberJoined("#a", "overflow");
        assertFalse(cache.membershipTrackingComplete());
        assertEquals("registered", cache.get("first").account);
        cache.clear();
        assertNull(cache.get("first"));
        assertTrue(cache.membershipTrackingComplete());
    }

    @Test public void realJoinNamesPartAndChghostUpdateIdentityLifecycle() throws Exception {
        ServerConnectionData data = new ServerConnectionData();
        data.setUserNick("me");
        data.setUserInfoApi(new io.mrarm.chatlib.user.SimpleUserInfoApi());
        data.setMessageStorageApi(new io.mrarm.chatlib.message.SimpleMessageStorageApi());
        data.onChannelJoined("#room");
        data.onChannelJoined("#other");
        MessageHandler handler = new MessageHandler(data);
        handler.handleLine(":Bob!ident@host JOIN #room accountBob :Bob");
        handler.handleLine(":Bob!ident@host JOIN #other accountBob :Bob");
        handler.handleLine(":irc.example 353 me = #room :Bob Alice");
        handler.handleLine(":irc.example 366 me #room :End of NAMES");
        io.mrarm.chatlib.irc.cap.ChghostCapability cap = data.getCapabilityManager()
                .getCapability(io.mrarm.chatlib.irc.cap.ChghostCapability.class);
        assertNotNull(cap);
        data.getCommandHandlerList().registerHandler(cap);
        handler.handleLine(":Bob!ident@host CHGHOST ident2 host2");
        assertEquals("host2", data.getIdentityCache().get("Bob").host);
        assertEquals("accountBob", data.getIdentityCache().get("Bob").account);
        handler.handleLine(":Bob!ident2@host2 PART #room :bye");
        assertNotNull(data.getIdentityCache().get("Bob"));
        handler.handleLine(":Bob!ident2@host2 PART #other :bye");
        assertNull(data.getIdentityCache().get("Bob"));
    }
}
