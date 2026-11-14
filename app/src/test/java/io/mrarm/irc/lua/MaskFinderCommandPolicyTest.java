package io.mrarm.irc.lua;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

public class MaskFinderCommandPolicyTest {
    private static final String NETWORK = "00000000-0000-0000-0000-000000000111";
    private static final String OTHER = "00000000-0000-0000-0000-000000000222";

    private static LuaScriptStore.Script approved() {
        return new LuaScriptStore.Script("mask-test", "mask finder", "hash",
                NETWORK, false, false, "").configured(
                Collections.singletonList(NETWORK), true, false, true, false, false, "");
    }

    private static boolean permits(LuaScriptStore.Script script, String network,
                                   String event, String source, String command,
                                   String target, String mask) {
        return MaskFinderCommandPolicy.permits(script, network, event, source,
                command, target, mask);
    }

    @Test
    public void implicitChannelStillWorksOnlyFromSameChannel() {
        assertTrue(permits(approved(), NETWORK, "command", "#chat",
                "mask nick!ident@host", "#chat", "nick!ident@host"));
        assertFalse(permits(approved(), NETWORK, "command", "",
                "mask nick!ident@host", "#chat", "nick!ident@host"));
        assertFalse(permits(approved(), NETWORK, "command", "#other",
                "mask nick!ident@host", "#chat", "nick!ident@host"));
    }

    @Test
    public void explicitTargetWorksFromServerPrivateAndOtherChannel() {
        for (String source : new String[]{"", "friend", "#other"}) {
            assertTrue(permits(approved(), NETWORK, "command", source,
                    "mask #chat nick!ident@host", "#chat", "nick!ident@host"));
        }
    }

    @Test
    public void cannotSearchUnrequestedChannelOrQuery() {
        assertFalse(permits(approved(), NETWORK, "command", "#other",
                "mask #chat pippo", "#hidden", "pippo"));
        assertFalse(permits(approved(), NETWORK, "command", "#other",
                "mask #chat pippo", "#chat", "someone-else"));
        assertFalse(permits(approved(), NETWORK, "command", "",
                "mod stato", "#chat", "pippo"));
        assertFalse(permits(approved(), NETWORK, "message", "#chat",
                "mask #chat pippo", "#chat", "pippo"));
        assertFalse(permits(approved(), NETWORK, "timer", "",
                "mask #chat pippo", "#chat", "pippo"));
        assertFalse(permits(approved(), NETWORK, "command", "",
                "mask #chat pippo extra", "#chat", "pippo"));
    }

    @Test
    public void cannotCrossNetworksOrBypassIdentityPermission() {
        assertFalse(permits(approved(), OTHER, "command", "",
                "mask #chat pippo", "#chat", "pippo"));
        LuaScriptStore.Script denied = approved().configured(
                Collections.singletonList(NETWORK), true, false, false, false, false, "");
        assertFalse(permits(denied, NETWORK, "command", "",
                "mask #chat pippo", "#chat", "pippo"));
    }

    @Test
    public void invalidTargetsAndOversizedMasksFailClosed() {
        assertFalse(permits(approved(), NETWORK, "command", "",
                "mask friend pippo", "friend", "pippo"));
        assertFalse(permits(approved(), NETWORK, "command", "",
                "mask #chat pippo", "#chat", "x".repeat(193)));
        assertFalse(permits(approved(), NETWORK, "command", "",
                "mask #chat pippo", "#chat #other", "pippo"));
    }
}
