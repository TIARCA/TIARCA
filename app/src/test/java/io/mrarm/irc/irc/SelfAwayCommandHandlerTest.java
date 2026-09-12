package io.mrarm.irc.irc;

import org.junit.Test;

import java.util.Collections;

import io.mrarm.chatlib.irc.ServerConnectionData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelfAwayCommandHandlerTest {

    @Test
    public void numeric306ConfirmsAwayAnd305ConfirmsReturn() throws Exception {
        boolean[] away = { false };
        int[] callbacks = { 0 };
        SelfAwayCommandHandler handler = new SelfAwayCommandHandler(value -> {
            away[0] = value;
            callbacks[0]++;
        });
        ServerConnectionData connection = new ServerConnectionData();

        handler.handle(connection, null, "306", Collections.emptyList(), Collections.emptyMap());
        assertTrue(away[0]);
        assertEquals(1, callbacks[0]);

        handler.handle(connection, null, "305", Collections.emptyList(), Collections.emptyMap());
        assertFalse(away[0]);
        assertEquals(2, callbacks[0]);
    }

    @Test
    public void awayTextCannotInjectAnotherIrcLine() {
        assertEquals("A cena  NICK bad",
                AwayStateManager.sanitizeMessage("  A cena\r\nNICK bad  "));
        assertEquals("_away", AwayStateManager.sanitizeSuffix("  _away  "));
        assertEquals("", AwayStateManager.sanitizeSuffix("_ away"));
    }
}
