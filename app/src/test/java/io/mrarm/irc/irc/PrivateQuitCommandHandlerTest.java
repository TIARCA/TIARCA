package io.mrarm.irc.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.handlers.QuitCommandHandler;

import static org.junit.Assert.*;

/** The PVT event must not depend on successful shared-channel cleanup. */
public class PrivateQuitCommandHandlerTest {

    @Test public void receivesQuitReasonBeforeOrdinaryChannelCleanup() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> reason = new AtomicReference<>();
        QuitCommandHandler delegate = new QuitCommandHandler() {
            @Override public void handle(ServerConnectionData data, MessagePrefix sender,
                    String command, java.util.List<String> params,
                    java.util.Map<String, String> tags) {
                calls.incrementAndGet();
                assertEquals("Ping timeout", reason.get());
            }
        };
        PrivateQuitCommandHandler handler = new PrivateQuitCommandHandler(delegate,
                (data, sender, text) -> reason.set(text));
        handler.handle(new ServerConnectionData(), new MessagePrefix("Alice"),
                "QUIT", Collections.singletonList("Ping timeout"), Collections.emptyMap());
        assertEquals(1, calls.get());
        assertEquals("Ping timeout", reason.get());
    }

    @Test public void observerStillSeesQuitWhenChannelLookupFails() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        QuitCommandHandler failingDelegate = new QuitCommandHandler() {
            @Override public void handle(ServerConnectionData data, MessagePrefix sender,
                    String command, java.util.List<String> params,
                    java.util.Map<String, String> tags) {
                throw new IllegalStateException("Simulated stale channel member");
            }
        };
        PrivateQuitCommandHandler handler = new PrivateQuitCommandHandler(failingDelegate,
                (data, sender, text) -> calls.incrementAndGet());
        try {
            handler.handle(new ServerConnectionData(), new MessagePrefix("Alice"),
                    "QUIT", Arrays.asList("bye"), Collections.emptyMap());
            fail("The existing handler should still report its own failure");
        } catch (IllegalStateException expected) {
            assertEquals(1, calls.get());
        }
    }

    @Test public void missingSenderDoesNotCreatePrivateQuit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        QuitCommandHandler stub = new QuitCommandHandler() {
            @Override public void handle(ServerConnectionData data, MessagePrefix sender,
                    String command, java.util.List<String> params,
                    java.util.Map<String, String> tags) {
            }
        };
        PrivateQuitCommandHandler handler = new PrivateQuitCommandHandler(stub,
                (data, sender, text) -> calls.incrementAndGet());
        handler.handle(new ServerConnectionData(), null, "QUIT",
                Collections.singletonList("bye"), Collections.emptyMap());
        assertEquals(0, calls.get());
    }
}
