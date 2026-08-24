package io.mrarm.chatlib.irc;

import io.mrarm.chatlib.dto.MessageFilterOptions;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.irc.handlers.QuitCommandHandler;
import io.mrarm.chatlib.message.SimpleMessageStorageApi;
import io.mrarm.chatlib.user.SimpleUserInfoApi;
import io.mrarm.chatlib.user.UserInfo;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuitCommandHandlerTest {

    @Test
    public void quitAfterLeavingChannelOnlyUsesRemainingChannel() throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        SimpleUserInfoApi users = new SimpleUserInfoApi();
        SimpleMessageStorageApi messages = new SimpleMessageStorageApi();
        connection.setUserInfoApi(users);
        connection.setMessageStorageApi(messages);

        UUID user = users.resolveUser("nick", "ident", "host", null, null).get();
        connection.onChannelJoined("#channelA");
        connection.onChannelJoined("#channelB");
        ChannelData.Member member = new ChannelData.Member(user, null, null);
        connection.getJoinedChannelData("#channelA").addMember(member);
        connection.getJoinedChannelData("#channelB").addMember(member);

        connection.onChannelLeft("#channelA");

        UserInfo userInfo = users.getUser(user, null, null).get();
        assertFalse(userInfo.getChannels().contains("#channelA"));
        assertTrue(userInfo.getChannels().contains("#channelB"));

        new QuitCommandHandler().handle(connection, new MessagePrefix("nick!ident@host"), "QUIT",
                Collections.singletonList("Client Quit"), Collections.emptyMap());

        assertEquals(1, messages.getMessages("#channelB", 10, new MessageFilterOptions(), null,
                null, null).get().getMessages().size());
        assertEquals(MessageInfo.MessageType.QUIT, messages.getMessages("#channelB", 10,
                new MessageFilterOptions(), null, null, null).get().getMessages().get(0).getType());
        assertTrue(connection.getServerStatusData().getMessages().isEmpty());
    }
}
