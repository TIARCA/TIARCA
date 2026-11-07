package io.mrarm.chatlib.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import io.mrarm.chatlib.dto.WhoisInfo;
import io.mrarm.chatlib.irc.handlers.AwayCommandHandler;
import io.mrarm.chatlib.irc.handlers.WhoisCommandHandler;
import io.mrarm.chatlib.user.SimpleUserInfoApi;

public class WhoisCommandHandlerTest {

    @Test
    public void orphanWhoisServerReplyIsAcceptedForWhowas() throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        WhoisCommandHandler handler = connection.getCommandHandlerList()
                .getHandler(WhoisCommandHandler.class);

        handler.handle(connection, null, "312",
                Arrays.asList("currentNick", "oldNick", "irc.example.net",
                        "Tue Sep 08 2026 20:25:12"),
                Collections.emptyMap());
    }
    @Test
    public void orphanWhoisServerReplyIsForwardedForWhowas() throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        WhoisCommandHandler handler = connection.getCommandHandlerList()
      .getHandler(WhoisCommandHandler.class);
        AtomicReference<java.util.List<String>> seen = new AtomicReference<>();
        handler.setOrphanServerReplyListener(seen::set);
        java.util.List<String> params = Arrays.asList("currentNick", "oldNick", "irc.example.net",
      "Tue Sep 08 2026 20:25:12");
        handler.handle(connection, null, "312", params, Collections.emptyMap());
        org.junit.Assert.assertEquals(params, seen.get());
    }


    @Test
    public void mixedCaseWhoisPreservesNickAndReportsAway() throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        SimpleUserInfoApi users = new SimpleUserInfoApi();
        connection.setUserInfoApi(users);
        String nick = "BastoneDelDominioAssente";
        users.resolveUser(nick, "ident", "host.example", null, null).get();

        WhoisCommandHandler handler = connection.getCommandHandlerList()
                .getHandler(WhoisCommandHandler.class);
        AtomicReference<WhoisInfo> received = new AtomicReference<>();
        handler.onRequested(nick, received::set, null);
        handler.handle(connection, null, "311",
                Arrays.asList("self", nick, "ident", "host.example", "*", "NobisLux"),
                Collections.emptyMap());
        new AwayCommandHandler().handle(connection, null, "301",
                Arrays.asList("self", nick, "Away for lunch"), Collections.emptyMap());
        handler.handle(connection, null, "318",
                Arrays.asList("self", nick, "End of WHOIS"), Collections.emptyMap());

        org.junit.Assert.assertNotNull(received.get());
        org.junit.Assert.assertEquals(nick, received.get().getNick());
        org.junit.Assert.assertEquals("Away for lunch", received.get().getAwayMessage());
        org.junit.Assert.assertTrue(users.getUser(nick, null, null, null, null).get().isAway());
        org.junit.Assert.assertEquals(1, users.findUsers("", null, null).get().size());
    }

    @Test
    public void whoisWithoutAwayReplyClearsPreviouslyCachedAwayState() throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        SimpleUserInfoApi users = new SimpleUserInfoApi();
        connection.setUserInfoApi(users);
        String nick = "BastoneDelDominioAssente";
        users.resolveUser(nick, "ident", "host.example", null, null).get();
        connection.setUserAway(nick, "ident", "host.example", true, "Old away");

        WhoisCommandHandler handler = connection.getCommandHandlerList()
                .getHandler(WhoisCommandHandler.class);
        AtomicReference<WhoisInfo> received = new AtomicReference<>();
        handler.onRequested(nick, received::set, null);
        handler.handle(connection, null, "311",
                Arrays.asList("self", nick, "ident", "host.example", "*", "NobisLux"),
                Collections.emptyMap());
        handler.handle(connection, null, "318",
                Arrays.asList("self", nick, "End of WHOIS"), Collections.emptyMap());

        org.junit.Assert.assertNotNull(received.get());
        org.junit.Assert.assertNull(received.get().getAwayMessage());
        org.junit.Assert.assertFalse(users.getUser(nick, null, null, null, null).get().isAway());
        org.junit.Assert.assertNull(users.getUser(nick, null, null, null, null).get().getAwayMessage());
    }
}
