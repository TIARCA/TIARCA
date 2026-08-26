package io.mrarm.irc.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;

import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.irc.config.ServerConfigData;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IgnoreListMessageFilterTest {

    @Test
    public void appliesIdentityAndMessageCategorySelections() {
        ServerConfigData config = configForPippo();
        ServerConfigData.IgnoreEntry entry = config.ignoreList.get(0);
        entry.user = "ident";
        entry.host = "host.example";
        entry.matchDirectMessages = false;
        entry.matchChannelNotices = false;
        entry.updateRegexes();
        IgnoreListMessageFilter filter = new IgnoreListMessageFilter(config);
        ServerConnectionData connection = new ServerConnectionData();

        assertTrue(filter.filter(connection, "Pippo", message(MessageInfo.MessageType.NORMAL)));
        assertFalse(filter.filter(connection, "#channel", message(MessageInfo.MessageType.NORMAL)));
        assertTrue(filter.filter(connection, "#channel", message("other", "ident", "host.example",
                MessageInfo.MessageType.NORMAL)));
        assertTrue(filter.filter(connection, "#channel", message("Pippo", "other", "host.example",
                MessageInfo.MessageType.NORMAL)));
        assertTrue(filter.filter(connection, "#channel", message("Pippo", "ident", "other.example",
                MessageInfo.MessageType.NORMAL)));
        assertTrue(filter.filter(connection, "#channel", message(MessageInfo.MessageType.NOTICE)));
        assertFalse(filter.filter(connection, "Pippo", message(MessageInfo.MessageType.NOTICE)));
    }

    @Test
    public void skipsExpiredEntriesAndKeepsLegacyEntriesPermanent() {
        ServerConfigData config = configForPippo();
        IgnoreListMessageFilter filter = new IgnoreListMessageFilter(config);
        ServerConnectionData connection = new ServerConnectionData();
        ServerConfigData.IgnoreEntry entry = config.ignoreList.get(0);

        assertFalse(filter.filter(connection, "#channel", message(MessageInfo.MessageType.NORMAL)));
        entry.expiresAt = System.currentTimeMillis() - 1L;
        assertTrue(filter.filter(connection, "#channel", message(MessageInfo.MessageType.NORMAL)));
        entry.expiresAt = 0L;
        assertFalse(filter.filter(connection, "#channel", message(MessageInfo.MessageType.NORMAL)));
    }

    private static ServerConfigData configForPippo() {
        ServerConfigData config = new ServerConfigData();
        config.ignoreList = new ArrayList<>();
        ServerConfigData.IgnoreEntry entry = new ServerConfigData.IgnoreEntry();
        entry.nick = "Pippo";
        entry.updateRegexes();
        config.ignoreList.add(entry);
        return config;
    }

    private static MessageInfo message(MessageInfo.MessageType type) {
        return message("Pippo", "ident", "host.example", type);
    }

    private static MessageInfo message(String nick, String user, String host, MessageInfo.MessageType type) {
        return new MessageInfo(new MessageSenderInfo(nick, user, host, null, null),
                new Date(), "test", type);
    }
}
