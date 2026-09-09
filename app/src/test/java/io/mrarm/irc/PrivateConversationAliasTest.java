package io.mrarm.irc;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PrivateConversationAliasTest {

    @Test
    public void closesBackingNicknameAfterNickChange() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("guest9836", "Nudo");

        List<String> targets = PrivateConversationAliases.buildCloseTargets(aliases, "Nudo");

        assertEquals(2, targets.size());
        assertEquals("guest9836", targets.get(0));
        assertEquals("Nudo", targets.get(1));
    }

    @Test
    public void closesEveryBackingNicknameAfterMultipleChanges() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("guest9836", "SecondNick");
        aliases.put("secondnick", "FinalNick");

        List<String> targets = PrivateConversationAliases
                .buildCloseTargets(aliases, "FinalNick");

        assertEquals(3, targets.size());
        assertEquals("guest9836", targets.get(0));
        assertEquals("secondnick", targets.get(1));
        assertEquals("FinalNick", targets.get(2));
    }

    @Test
    public void returningToOriginalNickDoesNotCreateAliasCycle() {
        Map<String, String> aliases = new LinkedHashMap<>();

        PrivateConversationAliases.recordNickChange(aliases, "Alpha", "Beta");
        PrivateConversationAliases.recordNickChange(aliases, "Beta", "Alpha");

        assertFalse(aliases.containsKey("alpha"));
        assertEquals("Alpha", aliases.get("beta"));
        assertEquals("Alpha", PrivateConversationAliases.resolve(aliases, "Beta"));
        assertEquals("Alpha", PrivateConversationAliases.resolve(aliases, "Alpha"));
    }

    @Test
    public void longNickHistoryAlwaysResolvesToCurrentNick() {
        Map<String, String> aliases = new LinkedHashMap<>();

        PrivateConversationAliases.recordNickChange(aliases, "Alpha", "Beta");
        PrivateConversationAliases.recordNickChange(aliases, "Beta", "Gamma");
        PrivateConversationAliases.recordNickChange(aliases, "Gamma", "Alpha");
        PrivateConversationAliases.recordNickChange(aliases, "Alpha", "Delta");

        assertEquals("Delta", PrivateConversationAliases.resolve(aliases, "Alpha"));
        assertEquals("Delta", PrivateConversationAliases.resolve(aliases, "Beta"));
        assertEquals("Delta", PrivateConversationAliases.resolve(aliases, "Gamma"));
        assertEquals("Delta", PrivateConversationAliases.resolve(aliases, "Delta"));
    }

    @Test
    public void normalPrivateConversationStillClosesNormally() {
        List<String> targets = PrivateConversationAliases
                .buildCloseTargets(new LinkedHashMap<>(), "RegularNick");

        assertEquals(1, targets.size());
        assertEquals("RegularNick", targets.get(0));
    }
}
