package io.mrarm.chatlib.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.mrarm.chatlib.dto.MessageFilterOptions;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.irc.handlers.MessageCommandHandler;
import io.mrarm.chatlib.message.SimpleMessageStorageApi;
import io.mrarm.chatlib.user.SimpleUserInfoApi;

import static org.junit.Assert.assertEquals;

public class PrivateConversationLocalEchoTest {

    @Test
    public void locallyRegisteredPrivateConversationReceivesSelfMessage() throws Exception {
        ServerConnectionData connection = new ServerConnectionData();
        SimpleMessageStorageApi messages = new SimpleMessageStorageApi();
        connection.setUserInfoApi(new SimpleUserInfoApi());
        connection.setMessageStorageApi(messages);
        connection.setUserNick("self");
        connection.onChannelJoined("Mario");

        new MessageCommandHandler().handle(connection, new MessagePrefix("self"), "PRIVMSG",
                Arrays.asList("Mario", "Ciao"), Collections.emptyMap());

        assertEquals(1, messages.getMessages("Mario", 10, new MessageFilterOptions(), null,
                null, null).get().getMessages().size());
        assertEquals(MessageInfo.MessageType.NORMAL, messages.getMessages("Mario", 10,
                new MessageFilterOptions(), null, null, null).get().getMessages().get(0).getType());
    }
}
