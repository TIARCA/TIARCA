package io.mrarm.irc.irc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class WhowasCommandHandlerCallerIdTest {

    @Test
    public void extractsCallerNickFromNumeric718Params() {
        assertEquals("Resilienza",
                WhowasCommandHandler.getCallerIdNick(Arrays.asList(
                        "mimancaunvenerdi",
                        "uid711950@gateway/ipv6/irccloud/Resilienza",
                        "Resilienza",
                        "is messaging you, and you have user mode +g set.")));
    }

    @Test
    public void ignoresIncompleteNumeric718Params() {
        assertNull(WhowasCommandHandler.getCallerIdNick(Collections.singletonList(
                "mimancaunvenerdi")));
    }
}
