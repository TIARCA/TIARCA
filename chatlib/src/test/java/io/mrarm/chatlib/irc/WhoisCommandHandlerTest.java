package io.mrarm.chatlib.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import io.mrarm.chatlib.irc.handlers.WhoisCommandHandler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void trustedServiceIdentityFromWhoisIsRememberedAsAutomated() throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        WhoisCommandHandler handler = connection.getCommandHandlerList()
                .getHandler(WhoisCommandHandler.class);

        handler.handle(connection, null, "311",
                Arrays.asList("currentNick", "BotAmIcIzIA", "services",
                        "services.simosnap.com", "*", "SimosNap service"),
                Collections.emptyMap());

        assertTrue(AutomatedSenderRegistry.isBot(connection, "BotAmIcIzIA"));
    }

    @Test
    public void ordinaryWhoisIdentityIsNotRememberedAsAutomated() throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        WhoisCommandHandler handler = connection.getCommandHandlerList()
                .getHandler(WhoisCommandHandler.class);

        handler.handle(connection, null, "311",
                Arrays.asList("currentNick", "OrdinaryNick", "ordinary",
                        "users.example.net", "*", "Ordinary user"),
                Collections.emptyMap());

        assertFalse(AutomatedSenderRegistry.isBot(connection, "OrdinaryNick"));
    }

}
