package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;

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
                channelNames, memberCounts, truncated);
        check(data != null, "Expected script response");
        String[] values = new String[data.length];
        for (int i = 0; i < data.length; i++) values[i] = decode(data[i]);
        return values;
    }

    public static void main(String[] args) {
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
                new byte[0][], new int[0], false) == null,
                "Unloaded script must disappear");
        NativeLuaRuntime.clear();
        System.out.println("Native Lua script JNI: commands, channel snapshots and sandbox acceptance passed.");
    }
}
