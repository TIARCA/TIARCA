package io.mrarm.irc.chat;

import java.util.ArrayList;
import java.util.List;

/** Pure-Java helper for recognizing a private query renamed by an IRC NICK change. */
final class PrivateQueryListUpdate {

    private PrivateQueryListUpdate() {
    }

    static Rename findSingleRename(List<String> previous, List<String> current,
                                   String channelPrefixes) {
        if (previous == null || current == null)
            return null;

        List<String> removed = new ArrayList<>();
        for (String oldName : previous) {
            if (!containsIgnoreCase(current, oldName) && !isChannel(oldName, channelPrefixes))
                removed.add(oldName);
        }

        List<String> added = new ArrayList<>();
        for (String newName : current) {
            if (!containsIgnoreCase(previous, newName) && !isChannel(newName, channelPrefixes))
                added.add(newName);
        }

        // ServerConnectionInfo performs a NICK rename as one atomic list replacement. Be
        // deliberately conservative: if anything more complex changed, do not guess which
        // conversation is the renamed one.
        if (removed.size() != 1 || added.size() != 1)
            return null;
        return new Rename(removed.get(0), added.get(0));
    }

    static String findIgnoreCase(List<String> values, String value) {
        if (values == null || value == null)
            return null;
        for (String item : values) {
            if (item != null && item.equalsIgnoreCase(value))
                return item;
        }
        return null;
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        return findIgnoreCase(values, value) != null;
    }

    private static boolean isChannel(String value, String channelPrefixes) {
        return value != null && !value.isEmpty() && channelPrefixes != null &&
                channelPrefixes.indexOf(value.charAt(0)) >= 0;
    }

    static final class Rename {
        final String from;
        final String to;

        Rename(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }
}
