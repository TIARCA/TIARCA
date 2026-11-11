package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.mrarm.chatlib.NoSuchChannelException;
import io.mrarm.chatlib.irc.ChannelData;
import io.mrarm.chatlib.irc.ServerConnectionData;

/**
 * Bounded, server-scoped channel data copied before IPC into the Lua sandbox.
 * Never exposes user identities, hostmasks or private conversation names.
 */
final class LuaChannelSnapshot {
    static final int MAX_CHANNELS = 32;

    final ArrayList<String> names;
    final int[] counts;
    final boolean truncated;

    private LuaChannelSnapshot(ArrayList<String> names, int[] counts, boolean truncated) {
        this.names = names;
        this.counts = counts;
        this.truncated = truncated;
    }

    static LuaChannelSnapshot empty() {
        return new LuaChannelSnapshot(new ArrayList<>(), new int[0], false);
    }

    static LuaChannelSnapshot capture(ServerConnectionData data) {
        if (data == null) return empty();
        List<String> joined = data.getJoinedChannelList();
        if (joined == null) return empty();

        List<ChannelEntry> entries = new ArrayList<>();
        for (String name : joined) {
            if (name == null || name.isEmpty() || name.length() > 64 ||
                    name.getBytes(StandardCharsets.UTF_8).length > 96 ||
                    name.indexOf('\0') >= 0 || name.indexOf('\r') >= 0 ||
                    name.indexOf('\n') >= 0 ||
                    !data.getSupportList().getSupportedChannelTypes().contains(name.charAt(0)))
                continue;
            try {
                ChannelData channel = data.getJoinedChannelData(name);
                entries.add(new ChannelEntry(name, channel.getMemberCount()));
            } catch (NoSuchChannelException ignored) {
                // The user parted between listing channels and capturing members.
            }
        }
        // HashMap iteration order is unstable; make the Lua API deterministic.
        entries.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
        boolean truncated = entries.size() > MAX_CHANNELS;
        int size = Math.min(entries.size(), MAX_CHANNELS);
        ArrayList<String> names = new ArrayList<>(size);
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            names.add(entries.get(i).name);
            counts[i] = entries.get(i).members;
        }
        return new LuaChannelSnapshot(names, counts, truncated);
    }

    private static final class ChannelEntry {
        final String name;
        final int members;

        ChannelEntry(String name, int members) {
            this.name = name;
            this.members = members;
        }
    }
}
