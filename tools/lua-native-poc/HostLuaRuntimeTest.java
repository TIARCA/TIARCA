package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Host-JVM acceptance test for the real Lua 5.5.1 native bridge. */
public final class HostLuaRuntimeTest {
    private static final String ID = "8a1bb9fa-8100-40cc-9193-d00a45ab719a";
    private static final String OTHER = "16a6f13c-2b6d-4e0e-a16b-6f37e40be6d0";

    private static void check(boolean okay, String message) {
        if (!okay) throw new AssertionError(message);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String decode(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    private static String[] event(String id, String event, String channel,
                                   String nick, String message) {
        return eventWithChannels(id, event, channel, nick, message,
                new byte[0][], new int[0], false);
    }

    private static String[] eventWithChannels(String id, String event, String channel,
            String nick, String message, byte[][] channelNames, int[] memberCounts,
            boolean truncated) {
        byte[][] data = NativeLuaRuntime.dispatch(id, event, bytes("test-server"),
                bytes(channel), bytes(nick), bytes(message), bytes("normal"), 1794400000L,
                channelNames, memberCounts, truncated,
                new byte[0][], false, false, new byte[0][], false);
        check(data != null, "Expected script response");
        String[] values = new String[data.length];
        for (int i = 0; i < data.length; i++) values[i] = decode(data[i]);
        return values;
    }

    public static void main(String[] args) throws Exception {
        String source = "local counter = 0\n"
                + "tiarca.on('message', function(event)\n"
                + " counter = counter + 1\n"
                + " tiarca.log(event.nick .. ':' .. counter)\n"
                + " if event.text == '!ping' then tiarca.send(event.channel, 'pong') end\n"
                + "end)\n"
                + "tiarca.on('connected', function(event)\n"
                + " tiarca.log('connected:' .. event.server)\n"
                + "end)\n";
        check("OK".equals(decode(NativeLuaRuntime.load(ID, bytes(source)))),
                "Script should load");

        String[] first = event(ID, "message", "#example", "alice", "hello");
        check(first.length == 4 && "OK".equals(first[0]) &&
                        "log".equals(first[1]) && "alice:1".equals(first[3]),
                "First callback expected");

        String[] second = event(ID, "message", "#example", "alice", "!ping");
        check(second.length == 7 && "OK".equals(second[0]) &&
                        "alice:2".equals(second[3]) && "send".equals(second[4]) &&
                        "#example".equals(second[5]) && "pong".equals(second[6]),
                "Persistent counter and send action expected");

        String[] connected = event(ID, "connected", "", "", "");
        check(connected.length == 4 && "connected:test-server".equals(connected[3]),
                "Connection event callback expected");

        String commands = "tiarca.on('command', function(e) "
                + "if e.text == 'ping' then tiarca.log('pong') end end)";
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(commands)))),
                "Command callback should register");
        String[] invoked = event(OTHER, "command", "#example", "myNick", "ping");
        check(invoked.length == 4 && "OK".equals(invoked[0]) &&
                        "log".equals(invoked[1]) && "pong".equals(invoked[3]),
                "Only explicit command invocation should trigger command callbacks");
        String[] notInvoked = event(OTHER, "message", "#example", "alice", "ping");
        check(notInvoked.length == 1 && "OK".equals(notInvoked[0]),
                "Incoming chat text must not be treated as a local command");

        // Import the exact user-facing example, not a separate simplified stub.
        String maskScript = Files.readString(Path.of("docs/lua-examples/mask_finder.lua"));
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(maskScript)))),
                "Mask Finder Lua action should load");
        String[] maskResult = event(OTHER, "command", "#example", "self", "mask pippo");
        check(maskResult.length == 4 && "OK".equals(maskResult[0]) &&
                        "mask".equals(maskResult[1]) &&
                        "#example".equals(maskResult[2]) &&
                        "pippo".equals(maskResult[3]),
                "Mask Finder native action should preserve command channel and query");
        check(event(OTHER, "message", "#example", "stranger", "mask pippo").length == 1,
                "Mask Finder script must ignore remote chat input");

        String channelsScript = "tiarca.on('command', function(e) "
                + "local channels = tiarca.get_channels() "
                + "if #channels == 0 then "
                + "tiarca.log('empty:' .. tostring(channels.truncated)) "
                + "else tiarca.log(#channels .. ':' .. channels[1].name .. ':' "
                + ".. channels[1].users .. ':' .. tostring(channels.truncated)) end end)";
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(channelsScript)))),
                "Channel snapshot callback should register");
        String[] withChannels = eventWithChannels(OTHER, "command", "#example", "myNick",
                "channels", new byte[][]{bytes("#alpha"), bytes("#beta")},
                new int[]{4, 8}, true);
        check(withChannels.length == 4 && "OK".equals(withChannels[0]) &&
                        "2:#alpha:4:true".equals(withChannels[3]),
                "Bounded channel snapshot should reach the native Lua callback");
        String[] emptyChannels = event(OTHER, "command", "#example", "myNick", "channels");
        check(emptyChannels.length == 4 && "empty:false".equals(emptyChannels[3]),
                "An empty event must not leak a previous channel snapshot");
        check(decode(NativeLuaRuntime.load(OTHER,
                        bytes("tiarca.get_channels()"))).contains("only available in a callback"),
                "Channel data must not be accessible during script initialization");

        String rosterScript = "tiarca.on('command', function(e) "
                + "local users = tiarca.get_users(e.channel) "
                + "if #users > 0 then tiarca.log(users[1].nick .. ':' "
                + ".. tostring(users[1].mask) .. ':' .. users[1].account) "
                + "else tiarca.log('empty:' .. tostring(users.truncated)) end "
                + "tiarca.refresh_users(e.channel) "
                + "tiarca.kick(e.channel, 'Target', 'Reason') "
                + "tiarca.ban(e.channel, 'Target') "
                + "tiarca.mute(e.channel, 'Target') "
                + "tiarca.tban(e.channel, 'Target', '3h') "
                + "tiarca.mode(e.channel, 'Target', '+v') end)";
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(rosterScript)))),
                "Advanced scripting callbacks should register");
        byte[][] userValues = {bytes("Target"), bytes("@"), bytes("ident"),
                bytes("host.example"), bytes("account")};
        byte[][] rosterActions = NativeLuaRuntime.dispatch(OTHER, "command",
                bytes("test-server"), bytes("#example"), bytes("self"), bytes("mod"),
                bytes(""), 1794400000L, new byte[0][], new int[0], false,
                userValues, false, true, new byte[0][], false);
        check(rosterActions != null && rosterActions.length == 22 &&
                        "OK".equals(decode(rosterActions[0])) &&
                        "Target:Target!ident@host.example:account".equals(decode(rosterActions[3])) &&
                        "who".equals(decode(rosterActions[4])) &&
                        "kick".equals(decode(rosterActions[7])) &&
                        "ban".equals(decode(rosterActions[10])) &&
                        "mute".equals(decode(rosterActions[13])) &&
                        "tban".equals(decode(rosterActions[16])) &&
                        "mode".equals(decode(rosterActions[19])),
                "C3 roster and C5 structured actions should reach the JNI host");
        byte[][] noUsers = NativeLuaRuntime.dispatch(OTHER, "command",
                bytes("test-server"), bytes("#example"), bytes("self"), bytes("mod"),
                bytes(""), 1794400001L, new byte[0][], new int[0], false,
                new byte[0][], false, false, new byte[0][], false);
        check(noUsers != null && noUsers.length == 1 &&
                        decode(noUsers[0]).contains("not authorized"),
                "C3 must reject missing user-data permission");
        String presence = "tiarca.on('join', function(e) tiarca.log('joined:'..e.nick) end) "
                + "tiarca.on('timer', function(e) tiarca.log('tick:'..e.type) end)";
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(presence)))),
                "C4 join/timer callbacks should register");
        String[] joinEvent = event(OTHER, "join", "#example", "alice", "");
        check(joinEvent.length == 4 && "joined:alice".equals(joinEvent[3]),
                "C4 join event should be dispatched");
        String[] tickEvent = event(OTHER, "timer", "", "", "");
        check(tickEvent.length == 4 && "tick:timer".equals(tickEvent[3]),
                "C4 timer event should be dispatched");

        String newApis = "tiarca.on('command', function(e) "
                + "if e.text == 'set' then "
                + "tiarca.storage.set('counter','7') "
                + "tiarca.log(tiarca.storage.get('counter')) "
                + "tiarca.show('Counter','Saved value 7') "
                + "elseif e.text == 'read' then "
                + "tiarca.log(tiarca.storage.get('counter') or 'missing') "
                + "elseif e.text == 'erase' then "
                + "tiarca.storage.delete('counter') "
                + "tiarca.show_list('Nicknames', {'Alice','Bob'}) "
                + "end end)";
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(newApis)))),
                "C6/C7 callbacks should load");
        byte[][] wrote = NativeLuaRuntime.dispatch(OTHER, "command", bytes("test-server"),
                bytes("#room"), bytes("me"), bytes("set"), bytes(""), 1794400002L,
                new byte[0][], new int[0], false, new byte[0][], false, false,
                new byte[0][], true);
        check(wrote != null && wrote.length == 10 &&
                        "OK".equals(decode(wrote[0])) &&
                        "store".equals(decode(wrote[1])) &&
                        "counter".equals(decode(wrote[2])) &&
                        "7".equals(decode(wrote[3])) &&
                        "log".equals(decode(wrote[4])) &&
                        "7".equals(decode(wrote[6])) &&
                        "show".equals(decode(wrote[7])),
                "C6 writes and C7 local output should be structured actions");
        byte[][] withoutConsent = NativeLuaRuntime.dispatch(OTHER, "command",
                bytes("test-server"), bytes("#room"), bytes("me"), bytes("read"),
                bytes(""), 1794400002L, new byte[0][], new int[0], false,
                new byte[0][], false, false, new byte[0][], false);
        check(withoutConsent != null && withoutConsent.length == 1 &&
                        decode(withoutConsent[0]).contains("not authorized"),
                "C6 read without explicit consent must be rejected");
        byte[][] persisted = NativeLuaRuntime.dispatch(OTHER, "command", bytes("test-server"),
                bytes("#room"), bytes("me"), bytes("read"), bytes(""), 1794400003L,
                new byte[0][], new int[0], false, new byte[0][], false, false,
                new byte[][]{bytes("counter"), bytes("7")}, true);
        check(persisted != null && persisted.length == 4 &&
                        "7".equals(decode(persisted[3])),
                "C6 values must be available from a fresh host snapshot");
        byte[][] erased = NativeLuaRuntime.dispatch(OTHER, "command", bytes("test-server"),
                bytes("#room"), bytes("me"), bytes("erase"), bytes(""), 1794400004L,
                new byte[0][], new int[0], false, new byte[0][], false, false,
                new byte[][]{bytes("counter"), bytes("7")}, true);
        check(erased != null && erased.length == 7 &&
                        "erase".equals(decode(erased[1])) &&
                        "counter".equals(decode(erased[2])) &&
                        "list".equals(decode(erased[4])) &&
                        "Alice\nBob".equals(decode(erased[6])),
                "C6 deletion and C7 bounded list should be structured actions");
        String invalidKey = "tiarca.on('command', function(e) "
                + "tiarca.storage.set('bad/key','oops') end)";
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(invalidKey)))),
                "Invalid key probe should load");
        byte[][] rejected = NativeLuaRuntime.dispatch(OTHER, "command",
                bytes("test-server"), bytes("#room"), bytes("me"), bytes("bad"),
                bytes(""), 1794400005L, new byte[0][], new int[0], false,
                new byte[0][], false, false, new byte[0][], true);
        check(rejected != null && rejected.length == 1 &&
                        decode(rejected[0]).contains("invalid key"),
                "Path-like storage keys must be blocked in the sandbox");

        String noLibs = "tiarca.on('message', function(e) "
                + "tiarca.log(type(io)..':'..type(os)..':'..type(package)..':'"
                + "..type(debug)..':'..type(require)) end)";
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(noLibs)))),
                "Restricted script should load");
        String[] denied = event(OTHER, "message", "#x", "b", "hi");
        check(denied.length == 4 && "nil:nil:nil:nil:nil".equals(denied[3]),
                "Dangerous standard libraries unavailable");

        String loop = "tiarca.on('message', function(e) while true do end end)";
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(loop)))),
                "Loop callback should register");
        String[] stopped = event(OTHER, "message", "#x", "b", "hi");
        check(stopped.length == 1 && stopped[0].contains("instruction budget exceeded"),
                "Infinite loop must stop");

        String memory = "tiarca.on('message', function(e) "
                + "local x = string.rep('a', 32 * 1024 * 1024) end)";
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(memory)))),
                "Memory callback should register");
        String[] exhausted = event(OTHER, "message", "#x", "b", "hi");
        check(exhausted.length == 1 &&
                        (exhausted[0].contains("memory") || exhausted[0].contains("allocation")),
                "Memory budget must stop script");

        NativeLuaRuntime.unload(ID);
        check(NativeLuaRuntime.dispatch(ID, "message", bytes("test-server"),
                bytes("#x"), bytes("a"), bytes("b"), bytes("normal"), 0L,
                new byte[0][], new int[0], false,
                new byte[0][], false, false, new byte[0][], false) == null,
                "Unloaded script must disappear");
        NativeLuaRuntime.clear();
        System.out.println("Native Lua script JNI: commands, channel snapshots and sandbox acceptance passed.");
    }
}
