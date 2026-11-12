package io.mrarm.irc.lua;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.dto.NickPrefixList;
import io.mrarm.chatlib.irc.ChannelData;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.user.SimpleUserInfoApi;

import static org.junit.Assert.*;

/** Two independent network caches, including a same-named IRC channel. */
public class LuaNetworkIsolationTest {
    private static final String A = "2598f719-dfb6-4bad-a69c-ff78cecb9315";
    private static final String B = "22d1fa48-7d17-4cab-a34c-88d63cd9885a";
    private static final String SCRIPT = "0a14a509-49e4-4d79-8884-9504e76a252f";

    @Test public void identicalChannelAndNickOnTwoNetworksKeepSeparateRosters()
            throws Exception {
        ServerConnectionData first = new ServerConnectionData();
        ServerConnectionData second = new ServerConnectionData();
        SimpleUserInfoApi aUsers = new SimpleUserInfoApi();
        SimpleUserInfoApi bUsers = new SimpleUserInfoApi();
        first.setUserInfoApi(aUsers);
        second.setUserInfoApi(bUsers);
        first.onChannelJoined("#shared");
        first.onChannelJoined("#only-a");
        second.onChannelJoined("#shared");
        second.onChannelJoined("#only-b");

        UUID aId = aUsers.resolveUser("Sam", null, null, null, null).get();
        UUID bId = bUsers.resolveUser("Sam", null, null, null, null).get();
        first.getJoinedChannelData("#shared").setMembers(
                Collections.singletonList(new ChannelData.Member(aId,
                        new ModeList("o"), new NickPrefixList("@"))));
        second.getJoinedChannelData("#shared").setMembers(
                Collections.singletonList(new ChannelData.Member(bId,
                        new ModeList(""), new NickPrefixList(""))));
        first.getIdentityCache().note("Sam", "a", "alpha.example", "aAccount");
        second.getIdentityCache().note("Sam", "b", "beta.example", "bAccount");

        assertEquals(Arrays.asList("#only-a", "#shared"),
                LuaChannelSnapshot.capture(first).names);
        assertEquals(Arrays.asList("#only-b", "#shared"),
                LuaChannelSnapshot.capture(second).names);

        LuaUserSnapshot fromA = LuaUserSnapshot.capture(first, "#shared");
        LuaUserSnapshot fromB = LuaUserSnapshot.capture(second, "#shared");
        assertEquals(Arrays.asList("Sam", "@", "a", "alpha.example", "aAccount"),
                fromA.fields);
        assertEquals(Arrays.asList("Sam", "", "b", "beta.example", "bAccount"),
                fromB.fields);
        assertEquals(0, LuaUserSnapshot.capture(first, "#only-b").fields.size());
        assertEquals(0, LuaUserSnapshot.capture(second, "#only-a").fields.size());

        // Clearing a disconnected network does not flush the other cache.
        first.getIdentityCache().clear();
        assertEquals("", LuaUserSnapshot.capture(first, "#shared").fields.get(3));
        assertEquals("beta.example",
                LuaUserSnapshot.capture(second, "#shared").fields.get(3));
    }

    @Test public void sendWhoAndModerationCannotCrossApprovedNetwork() {
        LuaScriptStore.Script script = new LuaScriptStore.Script(SCRIPT,
                "one-network.lua", "digest", A, true, true, true, true, "");
        assertTrue(LuaActionPolicy.permitsSend(script, A, "#shared",
                "message", "#shared", "Hello"));
        assertFalse(LuaActionPolicy.permitsSend(script, B, "#shared",
                "message", "#shared", "Hello"));
        assertTrue(LuaModerationPolicy.scoped(script, A, "#shared",
                "command", "#shared", false));
        assertTrue(LuaModerationPolicy.scoped(script, A, "#shared",
                "command", "#shared", true));
        assertFalse(LuaModerationPolicy.scoped(script, B, "#shared",
                "command", "#shared", false));
        assertFalse(LuaModerationPolicy.scoped(script, B, "#shared",
                "command", "#shared", true));
        assertFalse(LuaModerationPolicy.scoped(script, A, "#shared",
                "command", "#only-b", true));
    }
}
