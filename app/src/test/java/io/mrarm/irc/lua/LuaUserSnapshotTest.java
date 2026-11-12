package io.mrarm.irc.lua;

import org.junit.Test;
import java.util.Collections;
import java.util.UUID;

import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.dto.NickPrefixList;
import io.mrarm.chatlib.irc.ChannelData;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.user.SimpleUserInfoApi;
import static org.junit.Assert.*;

public final class LuaUserSnapshotTest {
    @Test public void capturesKnownHostAndKeepsUnknownValuesEmpty() throws Exception {
        ServerConnectionData data = new ServerConnectionData();
        SimpleUserInfoApi api = new SimpleUserInfoApi();
        data.setUserInfoApi(api);
        data.onChannelJoined("#alpha");
        UUID id = api.resolveUser("alice", null, null, null, null).get();
        data.getJoinedChannelData("#alpha").setMembers(
                Collections.singletonList(new ChannelData.Member(
                        id, new ModeList("o"), new NickPrefixList("@"))));
        data.getIdentityCache().note("alice", "ident", "host.example", "account");
        LuaUserSnapshot result = LuaUserSnapshot.capture(data, "#alpha");
        assertEquals(5, result.fields.size());
        assertEquals("alice", result.fields.get(0));
        assertEquals("@", result.fields.get(1));
        assertEquals("ident", result.fields.get(2));
        assertEquals("host.example", result.fields.get(3));
        assertEquals("account", result.fields.get(4));
        assertFalse(result.truncated);

        data.getIdentityCache().clear();
        result = LuaUserSnapshot.capture(data, "#alpha");
        assertEquals("", result.fields.get(2));
        assertEquals("", result.fields.get(3));
        assertEquals("", result.fields.get(4));
    }

    @Test public void privateTabsAndUnjoinedChannelsCannotExposeUsers() {
        ServerConnectionData data = new ServerConnectionData();
        assertTrue(LuaUserSnapshot.capture(data, "Alice").fields.isEmpty());
        assertTrue(LuaUserSnapshot.capture(data, "#notjoined").fields.isEmpty());
        assertTrue(LuaUserSnapshot.capture(null, "#none").fields.isEmpty());
    }
}
