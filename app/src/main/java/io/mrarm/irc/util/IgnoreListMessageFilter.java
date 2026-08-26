package io.mrarm.irc.util;

import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.irc.MessageFilter;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.irc.config.ServerConfigData;

public class IgnoreListMessageFilter implements MessageFilter {

    private ServerConfigData mConfig;

    public IgnoreListMessageFilter(ServerConfigData server) {
        mConfig = server;
    }

    @Override
    public boolean filter(ServerConnectionData serverConnectionData, String channel, MessageInfo message) {
        if (mConfig.ignoreList != null && message.getSender() != null) {
            for (ServerConfigData.IgnoreEntry entry : mConfig.ignoreList) {
                if (entry.isExpired(System.currentTimeMillis()))
                    continue;
                if (entry.nick == null && entry.user == null && entry.host == null)
                    continue;
                if (!matchesMessageType(serverConnectionData, channel, message, entry))
                    continue;
                if (entry.nickRegex == null && entry.userRegex == null && entry.hostRegex == null)
                    entry.updateRegexes();
                if (entry.nickRegex != null && !entry.nickRegex.matcher(message.getSender().getNick()).matches())
                    continue;
                if (entry.userRegex != null && (message.getSender().getUser() == null || !entry.userRegex.matcher(message.getSender().getUser()).matches()))
                    continue;
                if (entry.hostRegex != null && (message.getSender().getHost() == null || !entry.hostRegex.matcher(message.getSender().getHost()).matches()))
                    continue;
                return false;
            }
        }
        return true;
    }

    private boolean matchesMessageType(ServerConnectionData connection, String channel,
                                       MessageInfo message, ServerConfigData.IgnoreEntry entry) {
        if (message.getType() != MessageInfo.MessageType.NORMAL &&
                message.getType() != MessageInfo.MessageType.NOTICE &&
                message.getType() != MessageInfo.MessageType.ME)
            return true;
        boolean direct = channel == null || channel.isEmpty() || !connection.getSupportList()
                .getSupportedChannelTypes().contains(channel.charAt(0));
        boolean notice = message.getType() == MessageInfo.MessageType.NOTICE;
        if (direct)
            return notice ? entry.matchDirectNotices : entry.matchDirectMessages;
        return notice ? entry.matchChannelNotices : entry.matchChannelMessages;
    }
}
