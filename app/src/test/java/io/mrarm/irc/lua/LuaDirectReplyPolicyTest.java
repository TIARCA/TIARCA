package io.mrarm.irc.lua;

import org.junit.Test;
import io.mrarm.chatlib.irc.IRCCaseMapping;
import static org.junit.Assert.*;

public class LuaDirectReplyPolicyTest {
    private static final String NET = "2598f719-dfb6-4bad-a69c-ff78cecb9315";

    private LuaScriptStore.Script script(boolean allow) {
        return new LuaScriptStore.Script(
                "0a14a509-49e4-4d79-8884-9504e76a252f",
                "test.lua", "digest", NET, true, allow, "");
    }

    @Test public void onlyOriginalChannelSenderOnApprovedNetwork() {
        assertTrue(LuaActionPolicy.permitsDirectReply(script(true), NET,
                "#test", "message", "Alice", "ALICE", "Non ripetere",
                IRCCaseMapping.RFC1459));
        assertFalse(LuaActionPolicy.permitsDirectReply(script(true), NET,
                "#test", "message", "Alice", "Bob", "Non ripetere",
                IRCCaseMapping.RFC1459));
        assertFalse(LuaActionPolicy.permitsDirectReply(script(false), NET,
                "#test", "message", "Alice", "Alice", "Test",
                IRCCaseMapping.RFC1459));
        assertFalse(LuaActionPolicy.permitsDirectReply(script(true), "other",
                "#test", "message", "Alice", "Alice", "Test",
                IRCCaseMapping.RFC1459));
        assertFalse(LuaActionPolicy.permitsDirectReply(script(true), NET,
                "Alice", "message", "Alice", "Alice", "Test",
                IRCCaseMapping.RFC1459));
        assertFalse(LuaActionPolicy.permitsDirectReply(script(true), NET,
                "#test", "command", "Alice", "Alice", "Test",
                IRCCaseMapping.RFC1459));
        assertFalse(LuaActionPolicy.permitsDirectReply(script(true), NET,
                "#test", "timer", "Alice", "Alice", "Test",
                IRCCaseMapping.RFC1459));
    }

    @Test public void rejectsInjectedTargetsOrMessages() {
        assertFalse(LuaActionPolicy.permitsDirectReply(script(true), NET,
                "#test", "message", "Alice", "Alice,Bob", "Test",
                IRCCaseMapping.RFC1459));
        assertFalse(LuaActionPolicy.permitsDirectReply(script(true), NET,
                "#test", "message", "Alice", "Alice", "Test\nBAD",
                IRCCaseMapping.RFC1459));
        assertFalse(LuaActionPolicy.permitsDirectReply(script(true), NET,
                "#test", "message", "Alice", "Alice", "\u0001VERSION",
                IRCCaseMapping.RFC1459));
        assertFalse(LuaActionPolicy.permitsDirectReply(script(true), NET,
                "#test", "message", "Alice", "Alice", "",
                IRCCaseMapping.RFC1459));
    }
}
