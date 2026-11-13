package io.mrarm.chatlib.irc.handlers;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.mrarm.chatlib.dto.WhoisInfo;
import io.mrarm.chatlib.irc.ErrorCommandHandler;
import io.mrarm.chatlib.irc.NumericReplyException;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.user.SimpleUserInfoApi;

import static org.junit.Assert.*;

public class WhoisTimeoutTest {

    @Test
    public void duplicatePanelsShareOneRequestButCanRetryAfterTimeout() throws Exception {
        WhoisCommandHandler handler = new WhoisCommandHandler(new ErrorCommandHandler(), 40);
        CountDownLatch failures = new CountDownLatch(2);
        AtomicInteger errorCalls = new AtomicInteger();

        assertTrue(handler.onRequested("Pippo", info -> fail("Unexpected reply"),
                (nick, code, message) -> { errorCalls.incrementAndGet(); failures.countDown(); }));
        assertFalse(handler.onRequested("PIPPO", info -> fail("Unexpected reply"),
                (nick, code, message) -> { errorCalls.incrementAndGet(); failures.countDown(); }));

        assertTrue("Both panels must be notified of the timeout",
                failures.await(3, TimeUnit.SECONDS));
        assertEquals(2, errorCalls.get());
        assertFalse(handler.hasRequest("pippo"));
        assertTrue("A new WHOIS must be sent after timeout",
                handler.onRequested("Pippo", info -> {}, (nick, code, message) -> {}));
        handler.onDisconnected();
    }

    @Test
    public void completeWhoisCancelsTimeoutAndReportsOneResult() throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        SimpleUserInfoApi users = new SimpleUserInfoApi();
        connection.setUserInfoApi(users);
        users.resolveUser("Pippo", "ident", "host.example", null, null).get();

        WhoisCommandHandler handler = new WhoisCommandHandler(new ErrorCommandHandler(), 70);
        AtomicReference<WhoisInfo> result = new AtomicReference<>();
        CountDownLatch unexpectedTimeout = new CountDownLatch(1);

        assertTrue(handler.onRequested("Pippo", result::set,
                (nick, code, message) -> unexpectedTimeout.countDown()));
        handler.handle(connection, null, "311",
                Arrays.asList("self", "Pippo", "ident", "host.example", "*", "Real name"),
                Collections.emptyMap());
        handler.handle(connection, null, "318",
                Arrays.asList("self", "Pippo", "End of WHOIS"), Collections.emptyMap());

        assertNotNull(result.get());
        assertFalse(handler.hasRequest("pippo"));
        assertFalse("Successful WHOIS must not trigger a late timeout",
                unexpectedTimeout.await(200, TimeUnit.MILLISECONDS));
        handler.onDisconnected();
    }

    @Test
    public void numericErrorClearsRequestAndAllowsImmediateRetry() {
        WhoisCommandHandler handler = new WhoisCommandHandler(new ErrorCommandHandler(), 2000);
        AtomicInteger errors = new AtomicInteger();
        handler.onRequested("Pippo", info -> {}, (nick, code, msg) -> errors.incrementAndGet());
        assertTrue(handler.onError(401, Arrays.asList("self", "Pippo", "No such nick")));
        assertEquals(1, errors.get());
        assertFalse(handler.hasRequest("pippo"));
        assertTrue(handler.onRequested("Pippo", info -> {}, (nick, code, msg) -> {}));
        handler.onDisconnected();
    }

    @Test
    public void numericReplyExceptionRetainsErrorCodeForWhowasFallback() {
        NumericReplyException error = new NumericReplyException(401, "No such nick");
        assertEquals(401, error.getErrorCommandId());
        assertEquals("No such nick", error.getErrorMessage());
    }
}
