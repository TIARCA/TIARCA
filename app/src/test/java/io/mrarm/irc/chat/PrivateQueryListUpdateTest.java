package io.mrarm.irc.chat;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PrivateQueryListUpdateTest {

    @Test
    public void detectsPrivateQueryNickRename() {
        List<String> before = Arrays.asList("#amicizia", "OldNick", "speed2");
        List<String> after = Arrays.asList("#amicizia", "Resilienza", "speed2");

        PrivateQueryListUpdate.Rename rename = PrivateQueryListUpdate
                .findSingleRename(before, after, "#&+!");

        assertEquals("OldNick", rename.from);
        assertEquals("Resilienza", rename.to);
    }

    @Test
    public void ignoresOrdinaryChannelReplacement() {
        List<String> before = Arrays.asList("#old", "speed2");
        List<String> after = Arrays.asList("#new", "speed2");

        assertNull(PrivateQueryListUpdate.findSingleRename(before, after, "#&+!"));
    }

    @Test
    public void doesNotGuessWhenSeveralPrivateQueriesChange() {
        List<String> before = Arrays.asList("OldNick", "OtherOld");
        List<String> after = Arrays.asList("NewNick", "OtherNew");

        assertNull(PrivateQueryListUpdate.findSingleRename(before, after, "#&+!"));
    }

    @Test
    public void findsCurrentNameCaseInsensitively() {
        assertEquals("Resilienza", PrivateQueryListUpdate.findIgnoreCase(
                Arrays.asList("#amicizia", "Resilienza"), "RESILIENZA"));
    }
}
