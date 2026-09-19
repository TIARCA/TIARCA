package io.mrarm.irc.irc;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WhoXAccountHandlerTest {

    @Test
    public void disconnectKeepsHistoricalIdentityButClearsCurrentPresence() {
        WhoXAccountHandler handler = new WhoXAccountHandler();
        handler.remember("Resilienza_out", "resilienza");

        assertEquals("resilienza", handler.getAccount("RESILIENZA_OUT"));
        assertFalse(handler.snapshotCurrentAccounts().isEmpty());

        handler.onDisconnected();

        assertEquals("resilienza", handler.getAccount("Resilienza_out"));
        assertTrue(handler.snapshotCurrentAccounts().isEmpty());
    }

    @Test
    public void observedNickChangeMovesCurrentIdentityAndKeepsHistory() {
        WhoXAccountHandler handler = new WhoXAccountHandler();
        handler.remember("OldNick", "account");
        handler.renameNick("OldNick", "NewNick");

        assertEquals("account", handler.getAccount("OldNick"));
        assertEquals("account", handler.getAccount("NewNick"));

        Map<String, String> current = handler.snapshotCurrentAccounts();
        assertFalse(containsNick(current, "OldNick"));
        assertTrue(containsNick(current, "NewNick"));
    }

    private static boolean containsNick(Map<String, String> values, String nick) {
        for (String key : values.keySet()) {
            if (key.equalsIgnoreCase(nick))
                return true;
        }
        return false;
    }
}
