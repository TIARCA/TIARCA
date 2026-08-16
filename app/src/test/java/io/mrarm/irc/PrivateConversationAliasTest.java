package io.mrarm.irc;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

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
    public void normalPrivateConversationStillClosesNormally() {
        List<String> targets = PrivateConversationAliases
                .buildCloseTargets(new LinkedHashMap<>(), "RegularNick");

        assertEquals(1, targets.size());
        assertEquals("RegularNick", targets.get(0));
    }
}
