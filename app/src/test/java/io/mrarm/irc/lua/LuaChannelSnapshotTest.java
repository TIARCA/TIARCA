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

public class LuaChannelSnapshotTest {
    @Test public void snapshotsOnlyJoinedIrcChannelsNotPrivateTabs() {
        ServerConnectionData data = new ServerConnectionData();
        data.onChannelJoined("#zeta");
        data.onChannelJoined("PrivateNick");
        data.onChannelJoined("#alpha");

        LuaChannelSnapshot snap = LuaChannelSnapshot.capture(data);
        assertEquals(java.util.Arrays.asList("#alpha", "#zeta"), snap.names);
        assertArrayEquals(new int[]{0, 0}, snap.counts);
        assertFalse(snap.truncated);
    }

    @Test public void countsUseCachedMembersAndNotNetworkQueries() throws Exception {
        ServerConnectionData data = new ServerConnectionData();
        SimpleUserInfoApi users = new SimpleUserInfoApi();
        data.setUserInfoApi(users);
        data.onChannelJoined("#room");
        UUID id = users.resolveUser("Alice", null, null, null, null).get();
        ChannelData room = data.getJoinedChannelData("#room");
        room.setMembers(Collections.singletonList(new ChannelData.Member(id,
                new ModeList(""), new NickPrefixList(""))));

        LuaChannelSnapshot snap = LuaChannelSnapshot.capture(data);
        assertEquals(Collections.singletonList("#room"), snap.names);
        assertArrayEquals(new int[]{1}, snap.counts);
    }

    @Test public void largeChannelListIsSortedAndExplicitlyTruncated() {
        ServerConnectionData data = new ServerConnectionData();
        for (int i = 39; i >= 0; i--)
            data.onChannelJoined(String.format(java.util.Locale.ROOT, "#room%02d", i));
        LuaChannelSnapshot snap = LuaChannelSnapshot.capture(data);
        assertEquals(LuaChannelSnapshot.MAX_CHANNELS, snap.names.size());
        assertTrue(snap.truncated);
        assertEquals("#room00", snap.names.get(0));
        assertEquals("#room31", snap.names.get(31));
    }

    @Test public void nullConnectionHasNoChannelData() {
        LuaChannelSnapshot snap = LuaChannelSnapshot.capture(null);
        assertTrue(snap.names.isEmpty());
        assertEquals(0, snap.counts.length);
        assertFalse(snap.truncated);
    }
}
