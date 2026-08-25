package io.mrarm.chatlib.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import io.mrarm.chatlib.ChannelInfoListener;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.irc.cap.AwayNotifyCapability;
import io.mrarm.chatlib.irc.handlers.QuitCommandHandler;
import io.mrarm.chatlib.message.SimpleMessageStorageApi;
import io.mrarm.chatlib.user.SimpleUserInfoApi;
import io.mrarm.chatlib.user.UserInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AwayNotifyCapabilityTest {

    @Test
    public void bareAwayNotifyClearsAwayState() throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        SimpleUserInfoApi users = new SimpleUserInfoApi();
        connection.setUserInfoApi(users);
        connection.setMessageStorageApi(new SimpleMessageStorageApi());
        users.resolveUser("Pippo", "ident", "host", null, null).get();
        connection.setUserAway("Pippo", "ident", "host", true, "A cena");
        connection.getCommandHandlerList().registerHandler(new AwayNotifyCapability());

        new MessageHandler(connection).handleLine(":Pippo!ident@host AWAY");

        assertFalse(users.getUser("Pippo", null, null, null, null).get().isAway());
    }

    @Test
    public void awayNotifyUpdatesAllSharedChannelsAndClearsOnQuit() throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        SimpleUserInfoApi users = new SimpleUserInfoApi();
        connection.setUserInfoApi(users);
        connection.setMessageStorageApi(new SimpleMessageStorageApi());
        assertNotNull(connection.getCapabilityManager().getCapability(AwayNotifyCapability.class));

        UUID user = users.resolveUser("Pippo", "ident", "host", null, null).get();
        connection.onChannelJoined("#channelA");
        connection.onChannelJoined("#channelB");
        ChannelData.Member member = new ChannelData.Member(user, null, null);
        connection.getJoinedChannelData("#channelA").addMember(member);
        connection.getJoinedChannelData("#channelB").addMember(member);
        int[] memberListUpdates = {0};
        ChannelInfoListener listener = new ChannelInfoListener() {
            @Override
            public void onMemberListChanged(java.util.List<NickWithPrefix> newMembers) {
                memberListUpdates[0]++;
            }

            @Override
            public void onTopicChanged(String newTopic, MessageSenderInfo newTopicSetBy,
                                       java.util.Date newTopicSetOn) {
            }
        };
        connection.getJoinedChannelData("#channelA").subscribeInfo(listener);
        connection.getJoinedChannelData("#channelB").subscribeInfo(listener);

        AwayNotifyCapability capability = new AwayNotifyCapability();
        capability.handle(connection, new MessagePrefix("Pippo!ident@host"), "AWAY",
                Collections.singletonList("A cena"), Collections.emptyMap());

        UserInfo awayUser = users.getUser("Pippo", null, null, null, null).get();
        assertTrue(awayUser.isAway());
        assertEquals("A cena", awayUser.getAwayMessage());
        assertEquals(2, memberListUpdates[0]);

        users.setUserNick(user, "Pippo2", null, null).get();
        assertTrue(users.getUser("Pippo2", null, null, null, null).get().isAway());

        capability.handle(connection, new MessagePrefix("Pippo2!ident@host"), "AWAY",
                Collections.emptyList(), Collections.emptyMap());
        assertFalse(users.getUser("Pippo2", null, null, null, null).get().isAway());
        assertEquals(4, memberListUpdates[0]);

        capability.handle(connection, new MessagePrefix("Pippo2!ident@host"), "AWAY",
                Collections.singletonList("A cena"), Collections.emptyMap());
        new QuitCommandHandler().handle(connection, new MessagePrefix("Pippo2!ident@host"), "QUIT",
                Arrays.asList("Connection closed"), Collections.emptyMap());
        assertFalse(users.getUser("Pippo2", null, null, null, null).get().isAway());
    }
}
