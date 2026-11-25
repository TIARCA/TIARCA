package io.mrarm.irc.lua;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.Map;
import java.util.List;

import io.mrarm.chatlib.ResponseCallback;
import io.mrarm.chatlib.ResponseErrorCallback;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.dto.NickPrefixList;
import io.mrarm.chatlib.irc.ChannelData;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.user.SimpleUserInfoApi;
import static org.junit.Assert.*;

public class LuaIdentityQueryTest {
    private static String roster(int offset, int limit, String prefix, String auth) {
        return String.join("\t", "r", "token", Integer.toString(offset),
                Integer.toString(limit), "", "", "", "", prefix, auth);
    }

    @Test public void filterAllThousandMembersBeforePaging130Voices() throws Exception {
        ServerConnectionData data = new ServerConnectionData();
        SimpleUserInfoApi api = new SimpleUserInfoApi();
        data.setUserInfoApi(api);
        data.onChannelJoined("#room");
        ArrayList<ChannelData.Member> members = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String nick = "user" + i;
            UUID id = api.resolveUser(nick, null, null, null, null).get();
            boolean voice = i >= 870; // all matches are beyond the old 128-row cap
            members.add(new ChannelData.Member(id, new ModeList(voice ? "v" : ""),
                    new NickPrefixList(voice ? "+" : "")));
            data.getIdentityCache().memberJoined("#room", nick);
        }
        data.getJoinedChannelData("#room").setMembers(members);
        LuaIdentityQuery q1 = LuaIdentityQuery.parse("#room", roster(0, 100, "+", ""));
        LuaIdentityQuery.Result first = q1.execute(data);
        assertEquals("ok", first.status);
        assertEquals(130, first.total);
        assertEquals(100, first.next);
        assertTrue(first.complete);
        assertEquals(500, first.users.fields.size());
        LuaIdentityQuery q2 = LuaIdentityQuery.parse("#room", roster(100, 100, "+", ""));
        assertTrue(q2.permitted("identity", "#room", first.metadata("r"), "token"));
        assertTrue(q2.continues(q1));
        LuaIdentityQuery.Result second = q2.execute(data);
        assertEquals(130, second.total);
        assertEquals(-1, second.next);
        assertTrue(second.complete);
        assertEquals(150, second.users.fields.size());
        ArrayList<String> nicks = new ArrayList<>();
        for (LuaIdentityQuery.Result result : new LuaIdentityQuery.Result[]{first, second})
            for (int i = 0; i < result.users.fields.size(); i += 5) {
                assertEquals("+", result.users.fields.get(i + 1));
                assertFalse(nicks.contains(result.users.fields.get(i)));
                nicks.add(result.users.fields.get(i));
            }
        assertEquals(130, nicks.size());
    }

    @Test public void pointLookupReadsOnlyOneKnownMemberAndRetainsOriginalSender() throws Exception {
        ServerConnectionData data = new ServerConnectionData();
        SimpleUserInfoApi api = new SimpleUserInfoApi() {
            @Override public Future<Map<UUID,String>> getUsersNicks(List<UUID> ids,
                    ResponseCallback<Map<UUID,String>> callback, ResponseErrorCallback error) {
                assertEquals(1, ids.size());
                return super.getUsersNicks(ids, callback, error);
            }
        };
        data.setUserInfoApi(api);
        data.onChannelJoined("#room");
        UUID id = api.resolveUser("Alice", null, null, null, null).get();
        data.getJoinedChannelData("#room").setMembers(Collections.singletonList(
                new ChannelData.Member(id, new ModeList("v"), new NickPrefixList("+"))));
        data.getIdentityCache().memberJoined("#room", "Alice");
        data.getIdentityCache().note("Alice", "ident", "host", "oldAccount");
        LuaIdentityQuery q = LuaIdentityQuery.parse("#room", "u\ttoken\talice");
        LuaIdentityQuery.Result result = q.execute(data);
        assertEquals("ok", result.status);
        assertEquals("Alice", result.users.fields.get(0));
        assertEquals("oldAccount", result.users.fields.get(4));
        MessageSenderInfo original = new MessageSenderInfo("Alice", "ident", "host",
                new NickPrefixList("+"), id, false, true, true, "oldAccount");
        data.getIdentityCache().note("Alice", null, null, "newAccount");
        assertArrayEquals(new String[]{"ident", "host", "+", "oldAccount", ""},
                LuaIdentityQuery.sender(data, "#room", original));
        assertNull(LuaIdentityQuery.sender(data, "#private", original));
        assertEquals("not_observed", LuaIdentityQuery.parse("#room", "u\ttoken\tNobody")
                .execute(data).status);
    }

    @Test public void unknownAccountDoesNotBecomeLoggedOut() throws Exception {
        ServerConnectionData data = new ServerConnectionData();
        SimpleUserInfoApi api = new SimpleUserInfoApi();
        data.setUserInfoApi(api);
        data.onChannelJoined("#room");
        ArrayList<ChannelData.Member> members = new ArrayList<>();
        for (String nick : new String[]{"unknown", "guest", "registered"}) {
            UUID id = api.resolveUser(nick, null, null, null, null).get();
            members.add(new ChannelData.Member(id, new ModeList(""), new NickPrefixList("")));
            data.getIdentityCache().memberJoined("#room", nick);
            data.getIdentityCache().note(nick, "ident", "host",
                    "unknown".equals(nick) ? null : "guest".equals(nick) ? "*" : "acct");
        }
        data.getJoinedChannelData("#room").setMembers(members);
        LuaIdentityQuery.Result r = LuaIdentityQuery.parse("#room", roster(0, 100, "", "logged_out"))
                .execute(data);
        assertEquals(1, r.total);
        assertEquals("guest", r.users.fields.get(0));
        assertFalse(r.complete); // unknown account could be another match
        LuaIdentityQuery.Result unknown = LuaIdentityQuery.parse("#room", roster(0, 100, "", "unknown"))
                .execute(data);
        assertEquals(1, unknown.total);
        assertEquals("unknown", unknown.users.fields.get(0));
    }

    @Test public void scopeAndContinuationAreRestricted() {
        LuaIdentityQuery bulk = LuaIdentityQuery.parse("#room", roster(0, 100, "+", ""));
        assertNotNull(bulk);
        assertTrue(bulk.permitted("command", "", "", ""));
        assertFalse(bulk.permitted("command", "#other", "", ""));
        assertFalse(bulk.permitted("message", "#room", "", ""));
        assertFalse(bulk.permitted("identity", "#room", "r ok 130 100 1", "token"));
        LuaIdentityQuery page = LuaIdentityQuery.parse("#room", roster(100, 100, "@", ""));
        assertFalse(page.continues(bulk));
        LuaIdentityQuery point = LuaIdentityQuery.parse("#room", "u\ttoken\tAlice");
        assertTrue(point.permitted("message", "#room", "", ""));
        assertFalse(point.permitted("message", "#other", "", ""));
        assertFalse(point.permitted("identity", "#room", "", ""));
        assertEquals("unavailable", bulk.execute(new ServerConnectionData()).status);
        assertNull(LuaIdentityQuery.parse("#room", roster(0, 129, "+", "")));
        assertNull(LuaIdentityQuery.parse("#room", roster(0, 100, "+", "invalid")));
    }

    @Test public void rawIrcMessageKeepsItsAccountAfterSubsequentLogout() throws Exception {
        ServerConnectionData data = new ServerConnectionData();
        data.setUserNick("me");
        data.setUserInfoApi(new SimpleUserInfoApi());
        io.mrarm.chatlib.message.SimpleMessageStorageApi messages =
                new io.mrarm.chatlib.message.SimpleMessageStorageApi();
        data.setMessageStorageApi(messages);
        data.onChannelJoined("#room");
        io.mrarm.chatlib.irc.MessageHandler handler =
                new io.mrarm.chatlib.irc.MessageHandler(data);
        handler.handleLine(":Alice!ident@host JOIN #room oldAccount :Alice");
        java.util.concurrent.atomic.AtomicReference<MessageSenderInfo> observed =
                new java.util.concurrent.atomic.AtomicReference<>();
        messages.subscribeChannelMessages("#room", (channel, message, id) ->
                observed.set(message.getSender()), null, null).get();
        handler.handleLine("@account=oldAccount :Alice!ident@host PRIVMSG #room :hello");
        MessageSenderInfo original = observed.get();
        assertNotNull(original);
        assertTrue(original.hasLiveIdentityObservation());
        assertTrue(original.wasLiveChannelMember());
        data.getIdentityCache().note("Alice", null, null, "*");
        assertArrayEquals(new String[]{"ident", "host", "", "oldAccount", "1Alice"},
                LuaIdentityQuery.sender(data, "#room", original));
    }
}
