package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import io.mrarm.chatlib.NoSuchChannelException;
import io.mrarm.chatlib.irc.ChannelData;
import io.mrarm.chatlib.irc.IrcIdentityCache;
import io.mrarm.chatlib.irc.ServerConnectionData;

/** Main-process, bounded, consent-gated IRC roster (not a WHO live request). */
final class LuaUserSnapshot {
    static final int MAX_USERS = 128;
    static final int FIELDS = 5; // nick, prefix, ident, host, account

    final ArrayList<String> fields = new ArrayList<>();
    boolean truncated;

    static LuaUserSnapshot capture(ServerConnectionData data, String channel) {
        return capture(data, channel, null);
    }

    /** Include the event sender even if the joined channel has over 128 users. */
    static LuaUserSnapshot capture(ServerConnectionData data, String channel,
                                   String sender) {
        LuaUserSnapshot out = new LuaUserSnapshot();
        if (data == null || channel == null || channel.isEmpty() ||
                !data.getSupportList().getSupportedChannelTypes().contains(channel.charAt(0)))
            return out;
        try {
            ChannelData target = data.getJoinedChannelData(channel);
            out.truncated = target.getMemberCount() > MAX_USERS;
            List<ChannelData.Member> members = target.getMemberSnapshot(MAX_USERS);
            if (valid(sender, 64, 96)) {
                // Read only the current channel's actual member. Never synthesize
                // privileges from an identity cache or an absent user.
                UUID senderId = data.getUserInfoApi()
                        .getUser(sender, null, null, null, null).get().getUUID();
                ChannelData.Member origin = target.getMember(senderId);
                if (origin != null && !members.contains(origin)) {
                    if (members.size() >= MAX_USERS)
                        members.remove(members.size() - 1);
                    members.add(0, origin);
                }
            }
            ArrayList<UUID> ids = new ArrayList<>();
            for (ChannelData.Member m : members) ids.add(m.getUserUUID());
            Map<UUID, String> nicks = data.getUserInfoApi().getUsersNicks(
                    ids, null, null).get();
            List<String[]> entries = new ArrayList<>();
            for (ChannelData.Member member : members) {
                String nick = nicks.get(member.getUserUUID());
                if (!valid(nick, 64, 96)) continue;
                String prefix = member.getNickPrefixes() == null ? "" :
                        member.getNickPrefixes().toString();
                if (!valid(prefix, 16, 32)) prefix = "";
                IrcIdentityCache.Identity identity = data.getIdentityCache().get(nick);
                String ident = identity == null ? "" : safe(identity.ident, 64, 96);
                String host = identity == null ? "" : safe(identity.host, 192, 256);
                // "*" is a confirmed logout. "" is unknown; scripts must
                // not assume unknown accounts are safely unregistered.
                String account = identity == null ? "" :
                        identity.account != null ? safe(identity.account, 64, 96) :
                        identity.accountKnown ? "*" : "";
                entries.add(new String[]{nick, prefix, ident, host, account});
            }
            entries.sort(Comparator.comparing(v -> v[0].toLowerCase(java.util.Locale.ROOT)));
            for (String[] entry : entries)
                for (String value : entry) out.fields.add(value);
        } catch (NoSuchChannelException | ExecutionException | RuntimeException ignored) {
            // The channel parted or the local roster was temporarily unavailable.
            out.fields.clear();
            out.truncated = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.fields.clear();
            out.truncated = false;
        }
        return out;
    }

    private static boolean valid(String text, int maxChars, int maxBytes) {
        return text != null && !text.isEmpty() && text.length() <= maxChars &&
                text.getBytes(StandardCharsets.UTF_8).length <= maxBytes &&
                text.indexOf('\0') < 0 && text.indexOf('\r') < 0 &&
                text.indexOf('\n') < 0 && text.indexOf(' ') < 0 &&
                text.indexOf(',') < 0;
    }

    private static String safe(String text, int maxChars, int maxBytes) {
        return valid(text, maxChars, maxBytes) ? text : "";
    }
}
