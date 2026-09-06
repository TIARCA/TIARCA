package io.mrarm.irc.irc;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.mrarm.chatlib.irc.InvalidMessageException;

import static org.junit.Assert.assertEquals;

public class ExceptionListCommandHandlerTest {

    @Test
    public void testExceptionListParsing() throws InvalidMessageException {
        ExceptionListCommandHandler handler = new ExceptionListCommandHandler();
        List<ExceptionListCommandHandler.Entry> received = new ArrayList<>();
        handler.request("#amicizia", received::addAll);

        // 348 RPL_EXCEPTLIST
        handler.handle(null, null, "348",
                Arrays.asList("nick", "#amicizia", "MBAREEE!*@*", "mimancaunvenerdi", "1788017084"),
                Collections.emptyMap());
        handler.handle(null, null, "348",
                Arrays.asList("nick", "#amicizia", "S:R:LALLERO", "irc.simosnap.com", "1774345535"),
                Collections.emptyMap());

        // 349 RPL_ENDOFEXCEPTLIST
        handler.handle(null, null, "349",
                Arrays.asList("nick", "#amicizia", "End of Channel Exception List"),
                Collections.emptyMap());

        assertEquals(2, received.size());
        assertEquals("MBAREEE!*@*", received.get(0).mask);
        assertEquals("mimancaunvenerdi", received.get(0).setter);
        assertEquals(1788017084L, received.get(0).timestamp);

        assertEquals("S:R:LALLERO", received.get(1).mask);
        assertEquals("irc.simosnap.com", received.get(1).setter);
        assertEquals(1774345535L, received.get(1).timestamp);
    }
}
