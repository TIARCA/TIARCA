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
        ServerConnectionData connection = createConnection();
        SimpleMessageStorageApi messages = (SimpleMessageStorageApi) connection.getMessageStorageApi();
        connection.onChannelJoined("Mario");

        handleSelfMessage(connection, "Mario", "Ciao");

        assertMessageStored(messages, "Mario");
    }

    @Test
    public void staleUnregisteredPrivateConversationReceivesSelfMessage() throws Exception {
        ServerConnectionData connection = createConnection();
        SimpleMessageStorageApi messages = (SimpleMessageStorageApi) connection.getMessageStorageApi();

        // A remote NICK change can leave chatlib's private-query map under the old nickname while
        // TIARCA already displays and sends to the new nickname. The outgoing PRIVMSG must still
        // create/use the current target locally so the self message is not lost from the query.
        handleSelfMessage(connection, "Resilienza", "Ciao");

        assertMessageStored(messages, "Resilienza");
    }

    private ServerConnectionData createConnection() {
        ServerConnectionData connection = new ServerConnectionData();
        connection.setUserInfoApi(new SimpleUserInfoApi());
        connection.setMessageStorageApi(new SimpleMessageStorageApi());
        connection.setUserNick("self");
        return connection;
    }

    private void handleSelfMessage(ServerConnectionData connection, String target, String text)
            throws Exception {
        new MessageCommandHandler().handle(connection, new MessagePrefix("self"), "PRIVMSG",
                Arrays.asList(target, text), Collections.emptyMap());
    }

    private void assertMessageStored(SimpleMessageStorageApi messages, String target)
            throws Exception {
        assertEquals(1, messages.getMessages(target, 10, new MessageFilterOptions(), null,
                null, null).get().getMessages().size());
        assertEquals(MessageInfo.MessageType.NORMAL, messages.getMessages(target, 10,
                new MessageFilterOptions(), null, null, null).get().getMessages().get(0).getType());
    }
}
