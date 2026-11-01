package io.mrarm.irc;

import java.util.List;
import io.mrarm.chatlib.irc.ServerSupportList;

/** Exact IRC-case-mapped PVT lookup, never a wildcard or approximate nickname match. */
final class PrivatePresenceTarget {
    private PrivatePresenceTarget() { }

    static String find(String nick, List<String> channels, ServerSupportList support) {
        if (nick == null || nick.isEmpty() || channels == null || support == null)
            return null;
        for (String channel : channels) {
            if (channel == null || channel.isEmpty() ||
                    support.getSupportedChannelTypes().contains(channel.charAt(0)))
                continue;
            if (support.getCaseMapping().equals(channel, nick))
                return channel;
        }
        return null;
    }
}
