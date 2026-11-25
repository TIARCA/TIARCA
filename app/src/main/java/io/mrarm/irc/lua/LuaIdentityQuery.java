package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.chatlib.irc.ChannelData;
import io.mrarm.chatlib.irc.IrcIdentityCache;
import io.mrarm.chatlib.irc.IRCCaseMapping;
import io.mrarm.chatlib.irc.ServerConnectionData;

/** Queries local observations only: no network WHO, no private tabs, no Lua access to the host. */
final class LuaIdentityQuery {
    static final int MAX_SCAN = 65536;
    private static final int BATCH = 256;
    final String kind, token, channel;
    final int offset, limit;
    final String nick, ident, host, account, prefix, auth;
    private int pageNumber = 1;
    private boolean includeState;

    private LuaIdentityQuery(String kind, String token, String channel, int offset, int limit,
                             String nick, String ident, String host, String account,
                             String prefix, String auth) {
        this.kind = kind; this.token = token; this.channel = channel;
        this.offset = offset; this.limit = limit;
        this.nick = nick; this.ident = ident; this.host = host; this.account = account;
        this.prefix = prefix; this.auth = auth;
    }

    static LuaIdentityQuery parse(String channel, String wire) {
        if (channel == null || wire == null || wire.getBytes(StandardCharsets.UTF_8).length > 512)
            return null;
        String[] f = wire.split("\\t", -1);
        if (f.length < 2 || !valid(f[1], 64) || f[1].isEmpty()) return null;
        if ("c".equals(f[0]) && f.length == 2 && channel.isEmpty())
            return new LuaIdentityQuery("c", f[1], "", 0, 0, "", "", "", "", "", "");
        if (!valid(channel, 96) || channel.isEmpty() || channel.length() > 64) return null;
        if ("u".equals(f[0]) && f.length == 3 && valid(f[2], 95) &&
                !f[2].isEmpty() && f[2].length() <= 64)
            return new LuaIdentityQuery("u", f[1], channel, 0, 1, f[2], "", "", "", "", "");
        if (!"r".equals(f[0]) || (f.length != 10 && f.length != 11) ||
                (f.length == 11 && !"state".equals(f[10]))) return null;
        try {
            int offset = Integer.parseInt(f[2]), limit = Integer.parseInt(f[3]);
            int[] caps = {95, 95, 255, 95, 31, 16};
            for (int i = 0; i < caps.length; i++)
                if (!valid(f[i + 4], caps[i])) return null;
            if (offset < 0 || offset > MAX_SCAN || limit < 1 ||
                    limit > LuaUserSnapshot.MAX_USERS ||
                    !(f[9].isEmpty() || "registered".equals(f[9]) ||
                    "logged_out".equals(f[9]) || "unknown".equals(f[9]))) return null;
            LuaIdentityQuery result = new LuaIdentityQuery("r", f[1], channel, offset, limit,
                    f[4], f[5], f[6], f[7], f[8], f[9]);
            result.includeState = f.length == 11;
            return result;
        } catch (NumberFormatException invalid) { return null; }
    }

    /** Only local commands can initiate bulk work; a result can advance its own page. */
    boolean permitted(String event, String source, String resultMetadata, String resultToken) {
        boolean sameChannel = channel.equalsIgnoreCase(source);
        if ("command".equals(event)) return source.isEmpty() || sameChannel || "c".equals(kind);
        if ("identity".equals(event) && "r".equals(kind) && sameChannel &&
                token.equals(resultToken)) {
            String[] fields = resultMetadata.split(" ");
            return fields.length == 5 && "r".equals(fields[0]) &&
                    "ok".equals(fields[1]) && Integer.toString(offset).equals(fields[3]) &&
                    offset > 0;
        }
        return "u".equals(kind) && sameChannel && ("message".equals(event) ||
                "join".equals(event) || "part".equals(event) || "quit".equals(event) ||
                "nick".equals(event) || "kick".equals(event) || "ban".equals(event));
    }

    /** A local command may authorize at most 256 pages; no self-triggered query loops. */
    boolean continues(LuaIdentityQuery previous) {
        if (previous == null || previous.pageNumber >= 256 ||
                !"r".equals(previous.kind) || offset <= previous.offset ||
                !channel.equalsIgnoreCase(previous.channel) || !token.equals(previous.token) ||
                limit != previous.limit || !nick.equals(previous.nick) || !ident.equals(previous.ident) ||
                !host.equals(previous.host) || !account.equals(previous.account) ||
                !prefix.equals(previous.prefix) || !auth.equals(previous.auth) || includeState != previous.includeState) return false;
        pageNumber = previous.pageNumber + 1;
        return true;
    }

    static final class Result {
        final LuaUserSnapshot users = new LuaUserSnapshot();
        ArrayList<String> channelState; // null = not requested
        LuaChannelSnapshot channels = LuaChannelSnapshot.empty();
        String status = "ok";
        int total, next = -1;
        boolean complete = true;
        String metadata(String kind) {
            return kind + " " + status + " " + total + " " + next + " " + (complete ? 1 : 0);
        }
    }

    private Result unavailable() {
        Result r = new Result();
        r.status = "unavailable"; r.complete = false;
        return r;
    }

    Result execute(ServerConnectionData data) {
        if (data == null) return unavailable();
        try {
            if ("c".equals(kind)) {
                Result r = new Result();
                r.channels = LuaChannelSnapshot.capture(data);
                r.total = data.getJoinedChannelList().size();
                r.complete = !r.channels.truncated && r.total == r.channels.names.size();
                return r;
            }
            if (!data.getSupportList().getSupportedChannelTypes().contains(channel.charAt(0)))
                return unavailable();
            ChannelData target = data.getJoinedChannelData(channel);
            if ("u".equals(kind)) return point(data, target);
            return roster(data, target);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return unavailable();
        } catch (Exception error) {
            return unavailable();
        }
    }

    private Result point(ServerConnectionData data, ChannelData target) throws Exception {
        Result r = new Result();
        // Do not create a user record for arbitrary unobserved nicknames.
        String observedNick = data.getIdentityCache().sharedMemberNick(channel, nick);
        if (observedNick == null) {
            r.status = "not_observed"; r.complete = false;
            return r;
        }
        UUID id = data.getUserInfoApi().getUser(observedNick, null, null, null, null).get().getUUID();
        ChannelData.Member member = target.getMember(id);
        String observed = data.getUserInfoApi().getUsersNicks(
                Collections.singletonList(id), null, null).get().get(id);
        if (member == null || observed == null ||
                !data.getSupportList().getCaseMapping().equals(nick, observed)) {
            r.status = "not_observed"; r.complete = false;
            return r;
        }
        String[] row = entry(data, member, observed);
        if (row == null) return unavailable();
        addRow(r.users, row);
        r.total = 1;
        r.complete = !row[2].isEmpty() && !row[3].isEmpty() && !row[4].isEmpty();
        return r;
    }

    private Result roster(ServerConnectionData data, ChannelData target) throws Exception {
        Result r = new Result();
        if (includeState && offset == 0) {
            r.channelState = new ArrayList<>();
            for (String[] row : target.snapshotObservedModes(64, 2048)) {
                if (valid(row[0], 8) && valid(row[1], 512) && valid(row[2], 32))
                    Collections.addAll(r.channelState, row);
            }
        }
        int count = target.getMemberCount();
        List<ChannelData.Member> members = target.getMemberSnapshot(MAX_SCAN);
        r.complete = members.size() == count && data.getIdentityCache().membershipTrackingComplete();
        ArrayList<String[]> matches = new ArrayList<>();
        IRCCaseMapping mapping = data.getSupportList().getCaseMapping();
        for (int start = 0; start < members.size(); start += BATCH) {
            List<ChannelData.Member> batch = members.subList(start, Math.min(start + BATCH, members.size()));
            List<UUID> ids = new ArrayList<>(batch.size());
            for (ChannelData.Member m : batch) ids.add(m.getUserUUID());
            Map<UUID, String> names = data.getUserInfoApi().getUsersNicks(ids, null, null).get();
            for (ChannelData.Member member : batch) {
                String[] row = entry(data, member, names.get(member.getUserUUID()));
                if (row == null) { r.complete = false; continue; }
                int match = matches(row, mapping);
                if (match < 0) r.complete = false;
                else if (match == 1) matches.add(row);
            }
        }
        matches.sort(Comparator.comparing((String[] row) -> row[0].toLowerCase(Locale.ROOT))
                .thenComparing(row -> row[0]));
        r.total = matches.size();
        int end = Math.min(r.total, offset + limit);
        for (int i = offset; i < end; i++) addRow(r.users, matches.get(i));
        if (end < r.total) r.next = end;
        r.users.truncated = r.next >= 0 || !r.complete;
        if (target.getMemberCount() != count) {
            r.complete = false; r.users.truncated = true;
        }
        return r;
    }

    // 1: match; 0: definitely not; -1: filter cannot be evaluated from observations.
    int matches(String[] row, IRCCaseMapping mapping) {
        if (!nick.isEmpty() && !mapping.equals(nick, row[0])) return 0;
        if (!prefix.isEmpty() && !row[1].contains(prefix)) return 0;
        boolean unknown = false;
        String[] wanted = {ident, host, account};
        for (int i = 0; i < wanted.length; i++) {
            if (wanted[i].isEmpty()) continue;
            String actual = row[i + 2];
            if (actual.isEmpty()) { unknown = true; continue; }
            if (!(i == 0 ? wanted[i].equals(actual) : wanted[i].equalsIgnoreCase(actual))) return 0;
        }
        if (!auth.isEmpty()) {
            String actual = row[4];
            if (actual.isEmpty()) {
                if (!"unknown".equals(auth)) unknown = true;
            } else if ("unknown".equals(auth) ||
                    ("registered".equals(auth) && "*".equals(actual)) ||
                    ("logged_out".equals(auth) && !"*".equals(actual))) return 0;
        }
        return unknown ? -1 : 1;
    }

    private static void addRow(LuaUserSnapshot users, String[] row) {
        for (int i = 0; i < LuaUserSnapshot.FIELDS; i++) users.fields.add(row[i]);
        users.realnames.add(row[5]);
    }

    private static String[] entry(ServerConnectionData data, ChannelData.Member member, String nick) {
        if (nick == null || nick.isEmpty() || !valid(nick, 95)) return null;
        String prefix = member.getNickPrefixes() == null ? "" : safe(member.getNickPrefixes().toString(), 31);
        IrcIdentityCache.Identity i = data.getIdentityCache().get(nick);
        return new String[]{nick, prefix, i == null ? "" : safe(i.ident, 95),
                i == null ? "" : safe(i.host, 255), account(i),
                i != null && valid(i.realname, 256) ? "1" + i.realname : ""};
    }

    /** Capture the actual message's immutable sender fields before posting to the worker. */
    static String[] sender(ServerConnectionData data, String channel, MessageSenderInfo sender) {
        if (data == null || sender == null || sender.getUserUUID() == null) return null;
        try {
            if (channel == null || channel.isEmpty() ||
                    !data.getSupportList().getSupportedChannelTypes().contains(channel.charAt(0))) return null;
            ChannelData target = data.getJoinedChannelData(channel);
            String ident = safe(sender.getUser(), 95), host = safe(sender.getHost(), 255);
            IrcIdentityCache.Identity observed = data.getIdentityCache().get(sender.getNick());
            String realname = observed != null && observed.realname != null && !ident.isEmpty() && !host.isEmpty() &&
                    ident.equals(observed.ident) && host.equalsIgnoreCase(observed.host) &&
                    valid(observed.realname, 256) ? "1" + observed.realname : "";
            if (sender.hasLiveIdentityObservation()) {
                if (!sender.wasLiveChannelMember()) return null;
                String prefix = sender.getNickPrefixes() == null ? "" :
                        safe(sender.getNickPrefixes().toString(), 31);
                return new String[]{ident, host, prefix, safe(sender.getObservedAccount(), 95),
                        valid(sender.getObservedRealname(), 256) ? "1" + sender.getObservedRealname() : ""};
            }
            ChannelData.Member member = target.getMember(sender.getUserUUID());
            if (member == null) return null; // Never imply an absent user's privilege is known.
            IrcIdentityCache.Identity i = data.getIdentityCache().get(sender.getNick());
            String acct = i != null && !ident.isEmpty() && !host.isEmpty() &&
                    ident.equals(i.ident) && host.equalsIgnoreCase(i.host) ? account(i) : "";
            String prefix = sender.getNickPrefixes() == null ?
                    (member.getNickPrefixes() == null ? "" : safe(member.getNickPrefixes().toString(), 31)) :
                    safe(sender.getNickPrefixes().toString(), 31);
            return new String[]{ident, host, prefix, acct, realname};
        } catch (Exception unavailable) { return null; }
    }

    private static String account(IrcIdentityCache.Identity i) {
        return i == null ? "" : i.account != null ? safe(i.account, 95) : i.accountKnown ? "*" : "";
    }

    static boolean valid(String value, int cap) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > cap) return false;
        for (int i = 0; i < value.length(); i++)
            if (Character.isISOControl(value.charAt(i))) return false;
        return true;
    }

    private static String safe(String value, int cap) { return valid(value, cap) ? value : ""; }
}
