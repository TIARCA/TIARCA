package io.mrarm.irc.util;

import io.mrarm.chatlib.NoSuchChannelException;
import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.irc.ChannelData;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.ServerConnectionInfo;

/** Shared channel membership and privilege checks for operator-only UI. */
public final class ChannelOperatorUtils {

    private ChannelOperatorUtils() { }

    public static boolean hasOperatorPrivileges(ServerConnectionInfo connection, String channel,
                                                boolean includeHalfop) {
        if (connection == null || !(connection.getApiInstance() instanceof IRCConnection))
            return false;
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        NickWithPrefix own = findMember(connection, channel,
                irc.getServerConnectionData().getUserNick());
        if (own == null || own.getNickPrefixes() == null)
            return false;
        String prefixes = own.getNickPrefixes().toString();
        ModeList supportedPrefixes = irc.getServerConnectionData().getSupportList()
                .getSupportedNickPrefixes();
        ModeList supportedModes = irc.getServerConnectionData().getSupportList()
                .getSupportedNickPrefixModes();
        for (int i = 0; i < prefixes.length(); i++) {
            char prefix = prefixes.charAt(i);
            int index = supportedPrefixes.find(prefix);
            if (index >= 0 && index < supportedModes.length()) {
                char mode = supportedModes.get(index);
                if (mode == 'o' || mode == 'a' || mode == 'q' ||
                        (includeHalfop && mode == 'h'))
                    return true;
            }
            if (prefix == '@' || prefix == '&' || prefix == '~' ||
                    (includeHalfop && prefix == '%'))
                return true;
        }
        return false;
    }

    public static boolean isNickPresent(ServerConnectionInfo connection, String channel,
                                        String nick) {
        return findMember(connection, channel, nick) != null;
    }

    public static boolean hasVoice(ServerConnectionInfo connection, String channel,
                                   String nick) {
        NickWithPrefix member = findMember(connection, channel, nick);
        return hasVoice(connection, member);
    }

    public static boolean hasVoice(ServerConnectionInfo connection, NickWithPrefix member) {
        if (member == null || member.getNickPrefixes() == null)
            return false;
        char voicePrefix = '+';
        if (connection != null && connection.getApiInstance() instanceof IRCConnection) {
            IRCConnection irc = (IRCConnection) connection.getApiInstance();
            ModeList supportedModes = irc.getServerConnectionData().getSupportList()
                    .getSupportedNickPrefixModes();
            ModeList supportedPrefixes = irc.getServerConnectionData().getSupportList()
                    .getSupportedNickPrefixes();
            int voiceIndex = supportedModes != null ? supportedModes.find('v') : -1;
            if (supportedPrefixes != null && voiceIndex >= 0 && voiceIndex < supportedPrefixes.length())
                voicePrefix = supportedPrefixes.get(voiceIndex);
        }
        return member.getNickPrefixes().contains(voicePrefix);
    }

    public static NickWithPrefix findMember(ServerConnectionInfo connection, String channel,
                                            String nick) {
        if (connection == null || channel == null || nick == null ||
                !(connection.getApiInstance() instanceof IRCConnection))
            return null;
        try {
            ChannelData data = ((IRCConnection) connection.getApiInstance())
                    .getServerConnectionData().getJoinedChannelData(channel);
            for (NickWithPrefix member : data.getMembersAsNickPrefixList()) {
                if (nick.equalsIgnoreCase(member.getNick()))
                    return member;
            }
        } catch (NoSuchChannelException | RuntimeException ignored) { }
        return null;
    }
}
