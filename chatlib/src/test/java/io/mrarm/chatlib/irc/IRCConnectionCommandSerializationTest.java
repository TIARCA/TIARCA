package io.mrarm.chatlib.irc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IRCConnectionCommandSerializationTest {
    @Test public void serializesMonitorAddAndRemoveUsingProtocolParameters() {
        assertEquals("MONITOR + Pippo", IRCConnection.formatCommand("MONITOR", false, "+", "Pippo"));
        assertEquals("MONITOR - Pippo", IRCConnection.formatCommand("MONITOR", false, "-", "Pippo"));
    }
}
