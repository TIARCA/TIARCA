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
    public void ignoresNicknameRegardlessOfLetterCase() {
        ServerConfigData config = configForPippo();
        ServerConfigData.IgnoreEntry entry = config.ignoreList.get(0);
        entry.nick = "NickName";
        entry.updateRegexes();
        IgnoreListMessageFilter filter = new IgnoreListMessageFilter(config);
        ServerConnectionData connection = new ServerConnectionData();

        assertFalse(filter.filter(connection, "#channel",
                message("niCkName", "ident", "host.example", MessageInfo.MessageType.NORMAL)));
        assertFalse(filter.filter(connection, "#channel",
                message("NICKNAME", "ident", "host.example", MessageInfo.MessageType.ME)));
        assertFalse(filter.filter(connection, "#channel",
                message("nickname", "ident", "host.example", MessageInfo.MessageType.NOTICE)));
        assertTrue(filter.filter(connection, "#channel",
                message("OtherName", "ident", "host.example", MessageInfo.MessageType.NORMAL)));
    }

    @Test
    public void nicknameWildcardsAlsoMatchRegardlessOfLetterCase() {
        ServerConfigData config = configForPippo();
        ServerConfigData.IgnoreEntry entry = config.ignoreList.get(0);
        entry.nick = "N?ck*";
        entry.updateRegexes();
        IgnoreListMessageFilter filter = new IgnoreListMessageFilter(config);
        ServerConnectionData connection = new ServerConnectionData();

        assertFalse(filter.filter(connection, "#channel",
                message("nIcKname", "ident", "host.example", MessageInfo.MessageType.NORMAL)));
        assertFalse(filter.filter(connection, "#channel",
                message("NiCk", "ident", "host.example", MessageInfo.MessageType.NORMAL)));
        assertTrue(filter.filter(connection, "#channel",
                message("Nixkname", "ident", "host.example", MessageInfo.MessageType.NORMAL)));
    }

    @Test
    public void nicknameMatchingRespectsServerCaseMapping() {
        ServerConfigData config = configForPippo();
        ServerConfigData.IgnoreEntry entry = config.ignoreList.get(0);
        entry.nick = "[Nick]^";
        entry.updateRegexes();
        IgnoreListMessageFilter filter = new IgnoreListMessageFilter(config);
        ServerConnectionData connection = new ServerConnectionData();

        // The default RFC1459 mapping equates []\ with {}|, and ^ with ~.
        assertFalse(filter.filter(connection, "#channel",
                message("{nICK}~", "ident", "host.example", MessageInfo.MessageType.NORMAL)));

        connection.getSupportList().setCaseMapping(io.mrarm.chatlib.irc.IRCCaseMapping.STRICT_RFC1459);
        assertTrue(filter.filter(connection, "#channel",
                message("{nICK}~", "ident", "host.example", MessageInfo.MessageType.NORMAL)));

        entry.nick = "[Nick]";
        entry.updateRegexes();
        assertFalse(filter.filter(connection, "#channel",
                message("{NICK}", "ident", "host.example", MessageInfo.MessageType.NORMAL)));

        connection.getSupportList().setCaseMapping(io.mrarm.chatlib.irc.IRCCaseMapping.ASCII);
        assertTrue(filter.filter(connection, "#channel",
                message("{NICK}", "ident", "host.example", MessageInfo.MessageType.NORMAL)));
        assertFalse(filter.filter(connection, "#channel",
                message("[nick]", "ident", "host.example", MessageInfo.MessageType.NORMAL)));
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
