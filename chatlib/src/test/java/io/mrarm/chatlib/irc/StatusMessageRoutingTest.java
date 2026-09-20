package io.mrarm.chatlib.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.mrarm.chatlib.dto.MessageFilterOptions;
import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.irc.handlers.ISupportCommandHandler;
import io.mrarm.chatlib.irc.handlers.MessageCommandHandler;
import io.mrarm.chatlib.message.SimpleMessageStorageApi;
import io.mrarm.chatlib.user.SimpleUserInfoApi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StatusMessageRoutingTest {

    @Test
    public void isupportTracksStatusMessagePrefixes() throws Exception {
        ServerConnectionData connection = createConnection();
        ISupportCommandHandler handler = new ISupportCommandHandler();

        handler.handle(connection, new MessagePrefix("irc.example"), "005",
                Arrays.asList("STATUSMSG=@+", "are supported by this server"),
                Collections.emptyMap());

        assertTrue(connection.getSupportList().getSupportedStatusMessagePrefixes().contains('@'));
        assertTrue(connection.getSupportList().getSupportedStatusMessagePrefixes().contains('+'));

        handler.handle(connection, new MessagePrefix("irc.example"), "005",
                Arrays.asList("-STATUSMSG", "are supported by this server"),
                Collections.emptyMap());

        assertFalse(connection.getSupportList().getSupportedStatusMessagePrefixes().contains('@'));
        assertFalse(connection.getSupportList().getSupportedStatusMessagePrefixes().contains('+'));
    }

    @Test
    public void statusMessageGoesToOriginalChannelByDefault() throws Exception {
        ServerConnectionData connection = createConnection();
        SimpleMessageStorageApi messages =
                (SimpleMessageStorageApi) connection.getMessageStorageApi();
        connection.getSupportList().setSupportedChannelTypes(new ModeList("#"));
        connection.getSupportList().setSupportedStatusMessagePrefixes(new ModeList("@"));
        connection.onChannelJoined("#amicizia");

        handleMessage(connection, "@#amicizia", "solo operatori");

        assertEquals(1, messageCount(messages, "#amicizia"));
        assertEquals(1, connection.getJoinedChannelList().size());
        assertEquals("#amicizia", connection.getJoinedChannelList().get(0));
    }

    @Test
    public void statusMessageCanUseSeparateVirtualTab() throws Exception {
        ServerConnectionData connection = createConnection();
        SimpleMessageStorageApi messages =
                (SimpleMessageStorageApi) connection.getMessageStorageApi();
        connection.getSupportList().setSupportedChannelTypes(new ModeList("#"));
        connection.getSupportList().setSupportedStatusMessagePrefixes(new ModeList("@"));
        connection.setSeparateStatusMessageTargetsSupplier(() -> true);
        connection.onChannelJoined("#amicizia");

        handleMessage(connection, "@#amicizia", "solo operatori");

        assertEquals(0, messageCount(messages, "#amicizia"));
        assertEquals(1, messageCount(messages, "@#amicizia"));
        assertTrue(connection.getJoinedChannelList().contains("#amicizia"));
        assertTrue(connection.getJoinedChannelList().contains("@#amicizia"));
    }

    @Test
    public void unadvertisedPrefixKeepsExistingTargetBehavior() throws Exception {
        ServerConnectionData connection = createConnection();
        connection.getSupportList().setSupportedChannelTypes(new ModeList("#"));
        connection.onChannelJoined("#amicizia");

        handleMessage(connection, "@#amicizia", "non advertised");

        assertTrue(connection.getJoinedChannelList().contains("@#amicizia"));
    }

    private ServerConnectionData createConnection() {
        ServerConnectionData connection = new ServerConnectionData();
        connection.setUserInfoApi(new SimpleUserInfoApi());
        connection.setMessageStorageApi(new SimpleMessageStorageApi());
        connection.setUserNick("self");
        return connection;
    }

    private void handleMessage(ServerConnectionData connection, String target, String text)
            throws Exception {
        new MessageCommandHandler().handle(connection,
                new MessagePrefix("Guest!ident@example.invalid"), "PRIVMSG",
                Arrays.asList(target, text), Collections.emptyMap());
    }

    private int messageCount(SimpleMessageStorageApi messages, String target) throws Exception {
        return messages.getMessages(target, 10, new MessageFilterOptions(), null,
                null, null).get().getMessages().size();
    }
}
