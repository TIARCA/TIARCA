package io.mrarm.irc.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.handlers.QuitCommandHandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PrivateQuitCommandHandlerTest {

    @Test public void privateQuitIsDeliveredBeforeChannelHandlerCanFail() {
        AtomicInteger events = new AtomicInteger();
        AtomicInteger channels = new AtomicInteger();
        QuitCommandHandler channelHandler = new QuitCommandHandler() {
            @Override public void handle(ServerConnectionData connection, MessagePrefix sender,
                                         String command, java.util.List<String> params,
                                         java.util.Map<String, String> tags)
                    throws InvalidMessageException {
                channels.incrementAndGet();
                throw new InvalidMessageException("stale channel");
            }
        };
        PrivateQuitCommandHandler handler = new PrivateQuitCommandHandler(
                channelHandler, (data, sender, reason) -> {
                    assertEquals("Alice", sender.getNick());
                    assertEquals("Ping timeout", reason);
                    assertEquals(0, channels.get());
                    events.incrementAndGet();
                });
        try {
            handler.handle(new ServerConnectionData(), new MessagePrefix("Alice!id@host"),
                    "QUIT", Collections.singletonList("Ping timeout"), Collections.emptyMap());
        } catch (InvalidMessageException expected) {
            assertEquals("stale channel", expected.getMessage());
        }
        assertEquals(1, events.get());
        assertEquals(1, channels.get());
    }

    @Test public void privateQuitWithoutReasonIsDeliveredAsNull() throws Exception {
        AtomicInteger count = new AtomicInteger();
        QuitCommandHandler channelHandler = new QuitCommandHandler() {
            @Override public void handle(ServerConnectionData data, MessagePrefix sender,
                                         String command, java.util.List<String> params,
                                         java.util.Map<String, String> tags) {
                count.incrementAndGet();
            }
        };
        PrivateQuitCommandHandler handler = new PrivateQuitCommandHandler(
                channelHandler, (data, sender, reason) -> assertNull(reason));
        handler.handle(new ServerConnectionData(), new MessagePrefix("Alice"),
                "QUIT", Collections.emptyList(), Collections.emptyMap());
        assertEquals(1, count.get());
    }

    @Test public void localPersistenceFailureDoesNotSuppressChannelQuit() throws Exception {
        AtomicInteger channels = new AtomicInteger();
        QuitCommandHandler channelHandler = new QuitCommandHandler() {
            @Override public void handle(ServerConnectionData data, MessagePrefix sender,
                                         String command, java.util.List<String> params,
                                         java.util.Map<String, String> tags) {
                channels.incrementAndGet();
            }
        };
        PrivateQuitCommandHandler handler = new PrivateQuitCommandHandler(
                channelHandler, (data, sender, reason) -> {
                    throw new IllegalStateException("simulated database failure");
                });
        handler.handle(new ServerConnectionData(), new MessagePrefix("Alice!id@host"),
                "QUIT", Arrays.asList("Gone"), Collections.emptyMap());
        assertEquals(1, channels.get());
        assertTrue(Arrays.asList(handler.getHandledCommands()).contains("QUIT"));
    }
}
