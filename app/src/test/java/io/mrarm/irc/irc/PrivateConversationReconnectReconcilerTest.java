package io.mrarm.irc.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrivateConversationReconnectReconcilerTest {

    @Test
    public void renamesStalePrivateQueryByUniqueAccountMatch() {
        Map<String, String> known = mapOf("Resilienza_out", "resilienza");
        Map<String, String> current = mapOf("Resilienza", "resilienza");

        Map<String, String> renames = PrivateConversationReconnectReconciler.findRenames(
                Arrays.asList("Resilienza_out"), known, current);

        assertEquals("Resilienza", renames.get("Resilienza_out"));
    }

    @Test
    public void keepsQueryWhenOldNicknameIsStillCurrent() {
        Map<String, String> known = mapOf("Resilienza_out", "resilienza");
        Map<String, String> current = mapOf("RESILIENZA_OUT", "resilienza");

        Map<String, String> renames = PrivateConversationReconnectReconciler.findRenames(
                Arrays.asList("Resilienza_out"), known, current);

        assertTrue(renames.isEmpty());
    }

    @Test
    public void refusesAmbiguousAccountMatch() {
        Map<String, String> known = mapOf("OldNick", "same-account");
        Map<String, String> current = new LinkedHashMap<>();
        current.put("NewNickA", "same-account");
        current.put("NewNickB", "same-account");

        Map<String, String> renames = PrivateConversationReconnectReconciler.findRenames(
                Arrays.asList("OldNick"), known, current);

        assertTrue(renames.isEmpty());
    }

    @Test
    public void ignoresQueryWithoutHistoricalAccount() {
        Map<String, String> current = mapOf("NewNick", "account");

        Map<String, String> renames = PrivateConversationReconnectReconciler.findRenames(
                Arrays.asList("OldNick"), new LinkedHashMap<>(), current);

        assertFalse(renames.containsKey("OldNick"));
    }

    private static Map<String, String> mapOf(String nick, String account) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put(nick, account);
        return result;
    }
}
