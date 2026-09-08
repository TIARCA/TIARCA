package io.mrarm.chatlib.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.mrarm.chatlib.irc.handlers.WhoisCommandHandler;

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
}
