package io.mrarm.chatlib.irc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IRCConnectionCommandSerializationTest {
    @Test public void serializesMonitorAddAndRemoveUsingProtocolParameters() {
        assertEquals("MONITOR + Pippo", IRCConnection.formatCommand("MONITOR", false, "+", "Pippo"));
        assertEquals("MONITOR - Pippo", IRCConnection.formatCommand("MONITOR", false, "-", "Pippo"));
    }

    @Test public void extractsOnlySafeCommandNameForDiagnostics() {
        assertEquals("CAP", IRCConnection.extractCommandName("CAP LS 302"));
        assertEquals("001", IRCConnection.extractCommandName(":irc.example.test 001 nick :welcome"));
        assertEquals("PRIVMSG", IRCConnection.extractCommandName(
                "@time=2026-10-08T00:00:00Z :nick!user@host PRIVMSG #chan :secret text"));
        assertEquals("AUTHENTICATE", IRCConnection.extractCommandName("AUTHENTICATE very-secret"));
        assertEquals("unknown", IRCConnection.extractCommandName(""));
    }
}
