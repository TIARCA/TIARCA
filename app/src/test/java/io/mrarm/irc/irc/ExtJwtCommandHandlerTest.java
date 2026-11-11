package io.mrarm.irc.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExtJwtCommandHandlerTest {
    @Test
    public void allowsOnlyOneOutstandingRequestAndClearsOnReply() throws Exception {
        ExtJwtCommandHandler handler = new ExtJwtCommandHandler();
        AtomicReference<String> first = new AtomicReference<>();
        AtomicInteger second = new AtomicInteger();
        assertTrue(handler.request(first::set));
        assertFalse(handler.request(token -> second.incrementAndGet()));
        handler.handle(null, null, "EXTJWT",
                Arrays.asList("*", "provider", "header.payload.signature"), Collections.emptyMap());
        assertEquals("header.payload.signature", first.get());
        assertEquals(0, second.get());
        assertTrue(handler.request(token -> second.incrementAndGet()));
    }

    @Test
    public void joinsFragmentedJwtWithoutExposingPartialToken() throws Exception {
        ExtJwtCommandHandler handler = new ExtJwtCommandHandler();
        AtomicReference<String> result = new AtomicReference<>();
        assertTrue(handler.request(result::set));
        handler.handle(null, null, "EXTJWT",
                Arrays.asList("*", "provider", "*", "header."), Collections.emptyMap());
        assertNull(result.get());
        handler.handle(null, null, "EXTJWT",
                Arrays.asList("*", "provider", "payload.signature"), Collections.emptyMap());
        assertEquals("header.payload.signature", result.get());
    }

    @Test
    public void ignoresLateRepliesAfterCancellation() throws Exception {
        ExtJwtCommandHandler handler = new ExtJwtCommandHandler();
        AtomicInteger count = new AtomicInteger();
        ExtJwtCommandHandler.Callback callback = token -> count.incrementAndGet();
        assertTrue(handler.request(callback));
        handler.cancel(callback);
        handler.handle(null, null, "EXTJWT",
                Arrays.asList("*", "provider", "header.payload.signature"), Collections.emptyMap());
        assertEquals(0, count.get());
    }

    @Test
    public void disconnectInvalidatesPendingToken() throws Exception {
        ExtJwtCommandHandler handler = new ExtJwtCommandHandler();
        AtomicInteger count = new AtomicInteger();
        AtomicReference<String> token = new AtomicReference<>("initial");
        assertTrue(handler.request(value -> {
            count.incrementAndGet();
            token.set(value);
        }));
        handler.onDisconnected();
        assertEquals(1, count.get());
        assertNull(token.get());
        assertTrue(handler.request(value -> {}));
    }

    @Test
    public void rejectsExcessiveTokenFragments() throws Exception {
        ExtJwtCommandHandler handler = new ExtJwtCommandHandler();
        AtomicInteger count = new AtomicInteger();
        AtomicReference<String> token = new AtomicReference<>("initial");
        assertTrue(handler.request(value -> {
            count.incrementAndGet();
            token.set(value);
        }));
        handler.handle(null, null, "EXTJWT",
                Arrays.asList("*", "provider", "a".repeat(8193)), Collections.emptyMap());
        assertEquals(1, count.get());
        assertNull(token.get());
    }
}
