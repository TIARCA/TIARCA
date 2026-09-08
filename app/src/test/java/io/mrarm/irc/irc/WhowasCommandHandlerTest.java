package io.mrarm.irc.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.mrarm.chatlib.irc.ServerConnectionData;

import static org.junit.Assert.*;

public class WhowasCommandHandlerTest {
    @Test
    public void collectsMultipleRecordsAnd312Metadata() throws Exception {
        WhowasCommandHandler handler = new WhowasCommandHandler();
        AtomicReference<List<WhowasCommandHandler.Result>> seen = new AtomicReference<>();
        handler.request("oldNick", new WhowasCommandHandler.Callback() {
  @Override public void onResult(List<WhowasCommandHandler.Result> results) { seen.set(results); }
  @Override public void onError(String message) { fail(message); }
        });
        ServerConnectionData data = new ServerConnectionData();
        handler.handle(data, null, "314", Arrays.asList("me", "oldNick", "ident1", "host1", "*", "Real One"), Collections.emptyMap());
        handler.handleOrphanServerReply(Arrays.asList("me", "oldNick", "irc.example.net", "Tue Sep 08 2026 20:25:12"));
        handler.handle(data, null, "314", Arrays.asList("me", "oldNick", "ident2", "host2", "*", "Real Two"), Collections.emptyMap());
        handler.handleOrphanServerReply(Arrays.asList("me", "oldNick", "irc2.example.net", "server description"));
        handler.handle(data, null, "369", Arrays.asList("me", "oldNick", "End of WHOWAS"), Collections.emptyMap());

        assertNotNull(seen.get());
        assertEquals(2, seen.get().size());
        assertEquals("ident1", seen.get().get(0).user);
        assertEquals("irc.example.net", seen.get().get(0).server);
        assertNotNull(seen.get().get(0).disconnectTime);
        assertEquals("host2", seen.get().get(1).host);
        assertEquals("server description", seen.get().get(1).serverInfo);
    }
}
