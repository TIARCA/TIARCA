package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Host-JVM acceptance test for the real Lua 5.5.1 native bridge. */
public final class HostLuaRuntimeTest {
    private static final String ID = "8a1bb9fa-8100-40cc-9193-d00a45ab719a";
    private static final String OTHER = "16a6f13c-2b6d-4e0e-a16b-6f37e40be6d0";

    // Simulate the Java C6 persistence bridge between native event callbacks.
    private static final java.util.Map<String, String> automodStorage =
            new java.util.LinkedHashMap<>();
    private static boolean automodActive = false;

    private static byte[][] automodSnapshot() {
        if (!automodActive) return new byte[0][];
        byte[][] out = new byte[automodStorage.size() * 2][];
        int i = 0;
        for (java.util.Map.Entry<String, String> entry : automodStorage.entrySet()) {
            out[i++] = bytes(entry.getKey());
            out[i++] = bytes(entry.getValue());
        }
        return out;
    }

    private static String[] collect(byte[][] data) {
        check(data != null, "Expected script response");
        java.util.ArrayList<String> output = new java.util.ArrayList<>();
        output.add(decode(data[0]));
        for (int i = 1; i < data.length; i += 3) {
            String kind = decode(data[i]);
            String key = decode(data[i + 1]);
            String value = decode(data[i + 2]);
            if (automodActive && "store".equals(kind)) {
                automodStorage.put(key, value);
            } else if (automodActive && "erase".equals(kind)) {
                automodStorage.remove(key);
            } else {
                output.add(kind);
                output.add(key);
                output.add(value);
            }
        }
        return output.toArray(new String[0]);
    }

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
                new byte[0][], false, false, automodSnapshot(), automodActive);
        return collect(data);
    }

    private static String[] eventAt(String id, String kind, String channel,
                                    String nick, String text, long when) {
        return eventAs(id, kind, channel, nick, text, when, "", "*");
    }

    private static String[] eventAs(String id, String kind, String channel,
                                    String nick, String text, long when,
                                    String prefix, String account) {
        byte[][] roster = "message".equals(kind) ?
                new byte[][]{bytes(nick), bytes(prefix), bytes("ident"),
                        bytes("host.example"), bytes(account)} :
                new byte[0][];
        byte[][] reply = NativeLuaRuntime.dispatch(id, kind, bytes("test-server"),
                bytes(channel), bytes(nick), bytes(text), bytes("normal"), when,
                new byte[0][], new int[0], false,
                roster, false, true, automodSnapshot(), automodActive);
        return collect(reply);
    }

    private static String[] automodOn(String server, String kind, String nick,
                                       String text, long when) {
        byte[][] roster = "message".equals(kind) ? new byte[][]{
                bytes(nick), bytes(""), bytes("ident"), bytes("host.example"), bytes("*")
        } : new byte[0][];
        return collect(NativeLuaRuntime.dispatch(OTHER, kind, bytes(server),
                bytes("#room"), bytes(nick), bytes(text), bytes("normal"), when,
                new byte[0][], new int[0], false, roster, false, true,
                automodSnapshot(), automodActive));
    }

    private static String[] twoUserMessage(String nick, String text, long when) {
        byte[][] roster = new byte[][]{
                bytes("Nick-1"), bytes(""), bytes("id1"), bytes("host1.example"), bytes("*"),
                bytes("Nick-2"), bytes(""), bytes("id2"), bytes("host2.example"), bytes("*")
        };
        return collect(NativeLuaRuntime.dispatch(OTHER, "message", bytes("test-server"),
                bytes("#room"), bytes(nick), bytes(text), bytes("normal"), when,
                new byte[0][], new int[0], false,
                roster, false, true, automodSnapshot(), automodActive));
    }


    private static String[] collectorEvent(String event, String nick, String text,
                                           String messageType, long when) {
        byte[][] roster = new byte[][]{
                bytes("Target"), bytes(""), bytes("ident"),
                bytes("host.example"), bytes("account")
        };
        return collect(NativeLuaRuntime.dispatch(ID, event, bytes("test-server"),
                bytes("#room"), bytes(nick), bytes(text), bytes(messageType), when,
                new byte[0][], new int[0], false,
                roster, false, true, automodSnapshot(), true));
    }

    private static String[] historyWithStoredSender() {
        byte[][] roster = new byte[][]{
                bytes("Target"), bytes(""), bytes("ident"),
                bytes("host.example"), bytes("account")
        };
        return collect(NativeLuaRuntime.dispatchWithIdentity(ID, "history",
                bytes("test-server"), bytes("#room"), bytes("Target"),
                bytes("Messaggio precedente"), bytes("1"), 1794404999L,
                new byte[0][], new int[0], false, roster, false, true,
                automodSnapshot(), true, new byte[][]{
                        bytes("saved-ident"), bytes("saved.host.example")
                }));
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
        String[] fromServerTab = event(OTHER, "command", "", "self",
                "mask #example *!*@*.example.net");
        check(fromServerTab.length == 4 && "mask".equals(fromServerTab[1]) &&
                        "#example".equals(fromServerTab[2]) &&
                        "*!*@*.example.net".equals(fromServerTab[3]),
                "Mask Finder supports explicit joined channel from Server tab");
        String[] fromOtherChannel = event(OTHER, "command", "#another", "self",
                "mask #example pippo");
        check(fromOtherChannel.length == 4 && "mask".equals(fromOtherChannel[1]) &&
                        "#example".equals(fromOtherChannel[2]) &&
                        "pippo".equals(fromOtherChannel[3]),
                "Mask Finder supports explicit joined channel from different chat");
        String[] fromPrivate = event(OTHER, "command", "friend", "self",
                "mask #example nick!ident@host");
        check(fromPrivate.length == 4 && "mask".equals(fromPrivate[1]) &&
                        "#example".equals(fromPrivate[2]),
                "Mask Finder supports explicit joined channel from private tab");
        check(event(OTHER, "command", "", "self", "mask pippo").length == 4,
                "Without a channel, Mask Finder returns a local usage warning");
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

        // A network restart loads a new VM identity for that network only.
        String isolatedCounter = "local n=0;tiarca.on('message',function(e) "
                + "n=n+1;tiarca.log(tostring(n)) end)";
        check("OK".equals(decode(NativeLuaRuntime.load(ID, bytes(isolatedCounter)))),
                "Initial script VM should load");
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(isolatedCounter)))),
                "Scoped network VM should load");
        check("1".equals(event(ID, "message", "#x", "a", "one")[3]),
                "Original VM counter starts at one");
        check("1".equals(event(OTHER, "message", "#x", "a", "one")[3]),
                "Scoped VM independently starts at one");
        check("2".equals(event(ID, "message", "#x", "a", "two")[3]),
                "Original VM retains its state");
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, bytes(isolatedCounter)))),
                "Reinitialize scoped network VM");
        check("1".equals(event(OTHER, "message", "#x", "a", "again")[3]),
                "Scoped network state cleared");
        check("3".equals(event(ID, "message", "#x", "a", "three")[3]),
                "Other network state remains intact");

        // Import the actual user-facing AutoMod source. Also check size.
        byte[] automod = Files.readAllBytes(
                Path.of("docs/lua-examples/automod_antiripetizione.lua"));
        check(automod.length <= 24576, "AutoMod fits 24 KiB source limit");
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, automod))),
                "AutoMod loads into official Lua native sandbox");
        automodActive = true;
        String[] close = event(OTHER, "command", "#room", "me", "mod close");
        check(close.length >= 4 && "closepm".equals(close[1]) &&
                        "".equals(close[2]) && "1".equals(close[3]),
                "/lua mod close requests local-only cleanup");
        String[] invalidClose = event(OTHER, "command", "#room", "me", "mod close extra");
        check(invalidClose.length >= 4 && !"closepm".equals(invalidClose[1]),
                "/lua mod close rejects unexpected arguments");
        String[] help = event(OTHER, "command", "#room", "me", "mod help");
        boolean hasRestart = false, hasWhitelist = false, hasCategories = false, hasClose = false;
        for (String field : help) {
            if (field.contains("/lua restart")) hasRestart = true;
            if (field.contains("whitelist")) hasWhitelist = true;
            if (field.contains("registrati")) hasCategories = true;
            if (field.contains("/lua mod close")) hasClose = true;
        }
        check(help.length > 10 && "show".equals(help[1]) &&
                        hasRestart && hasWhitelist && hasCategories && hasClose,
                "Grouped AutoMod help documents all command families");

        // Actual AutoMod escalation: warning, kick, temporary kick+ban,
        // permanent kick+ban. A kick/rejoin must not erase infraction level.
        String repeat = "io maschio cerco ragazza per divertimento";
        check(eventAt(OTHER, "message", "#room", "Target", repeat,
                1794399980L).length == 1,
                "Default with no channels must not monitor messages");
        String[] defaultState = eventAt(OTHER, "command", "#room", "me",
                "mod stato", 1794399981L);
        check(defaultState.length == 22 &&
                        java.util.Arrays.toString(defaultState).contains("azione monitor") &&
                        java.util.Arrays.toString(defaultState).contains("parole 3") &&
                        java.util.Arrays.toString(defaultState).contains("soglia 75%") &&
                        java.util.Arrays.toString(defaultState).contains("occorrenze 3; ultimi 8") &&
                        java.util.Arrays.toString(defaultState).contains("pausa 0s") &&
                        java.util.Arrays.toString(defaultState).contains("utenti attuali 0; utenti storici 0"),
                "New AutoMod configuration defaults to private warnings and three words");
        String[] emptyList = eventAt(OTHER, "command", "#room", "me",
                "mod canali", 1794399981L);
        check(emptyList.length == 4 && emptyList[3].contains("nessuno"),
                "Channel list starts empty");
        String[] invalid = eventAt(OTHER, "command", "#room", "me",
                "mod canali add nonsensical", 1794399982L);
        check(invalid.length == 4 && invalid[3].contains("Uso:"),
                "Commands reject invalid channel targets");
        String[] added = eventAt(OTHER, "command", "#room", "me",
                "mod canali add #ROOM", 1794399983L);
        check(added.length == 4 && added[3].contains("#room"),
                "Channel add accepts a channel and normalizes case");
        String[] channelList = eventAt(OTHER, "command", "#room", "me",
                "mod canali", 1794399984L);
        check(channelList.length == 4 && channelList[3].contains("#room"),
                "Channel list shows only explicitly added channels");
        check(eventAt(OTHER, "message", "#unlisted", "Target", repeat,
                1794399985L).length == 1,
                "Unlisted channels must remain inactive");
        byte[][] otherNetwork = NativeLuaRuntime.dispatch(OTHER, "message",
                bytes("second-server"), bytes("#room"), bytes("Target"),
                bytes(repeat), bytes("normal"), 1794399986L,
                new byte[0][], new int[0], false, new byte[0][], false,
                false, new byte[0][], false);
        check(otherNetwork != null && otherNetwork.length == 1 &&
                        "OK".equals(decode(otherNetwork[0])),
                "Adding on one network must not monitor on another");
        String[] configured = eventAt(OTHER, "command", "#room", "me",
                "mod azione escalation", 1794400000L);
        check(configured.length == 4 && "show".equals(configured[1]),
                "AutoMod escalation selected");

        // X=3 matches within Y=8 eligible messages in Z=30 minutes.
        // The current message is the third occurrence: it must warn now,
        // and each further comparable message escalates with pause=0.
        automodOn("three-server","command","me","mod canali add #room",1794400000L);
        automodOn("three-server","command","me","mod azione escalation",1794400000L);
        String exact = "cerco ragazza di roma", approximate = "cerco amica di roma";
        check(automodOn("three-server","message","Three",exact,1794400001L).length==1,
                "First occurrence does not warn");
        check(automodOn("three-server","message","Three",exact,1794400002L).length==1,
                "Second occurrence does not warn");
        String[] threeWarning= automodOn("three-server","message","Three",approximate,
                1794400003L);
        check(threeWarning.length==7 && "pm".equals(threeWarning[4]),
                "Third occurrence at exactly 75 percent warns, not fourth");
        String[] threeKick=automodOn("three-server","message","Three",exact,1794400003L);
        check(threeKick.length==7 && "kick".equals(threeKick[4]),
                "A fourth matching message immediately KICKs with zero pause");
        String[] threeTemp=automodOn("three-server","message","Three",exact,1794400003L);
        check(threeTemp.length==7 && "ktban".equals(threeTemp[4]),
                "Next occurrence requests temporary kick+ban");
        String[] threePermanent=automodOn("three-server","message","Three",exact,1794400003L);
        check(threePermanent.length==7 && "kban".equals(threePermanent[4]),
                "Next occurrence requests permanent kick+ban");
        String[] invalidY=automodOn("three-server","command","me",
                "mod ultimi 2",1794400004L);
        check(invalidY.length==4 && invalidY[3].contains("Occorrenze"),
                "Reject a window smaller than the occurrence threshold");
        String[] persistedThree=automodOn("three-server","command","me",
                "mod stato",1794400004L);
        check(java.util.Arrays.toString(persistedThree).contains(
                "occorrenze 3; ultimi 8"),
                "Invalid window command must not damage saved settings");

        automodOn("window-server","command","me","mod canali add #room",1794400000L);
        automodOn("window-server","command","me","mod azione escalation",1794400000L);
        automodOn("window-server","message","Walker",exact,1794400001L);
        for (int i=1;i<=5;i++) automodOn("window-server","message","Walker",
                "filler"+i+" alpha"+i+" beta"+i,1794400001L+i);
        automodOn("window-server","message","Walker",exact,1794400007L);
        for (int i=1;i<=12;i++) automodOn("window-server","message","Walker",
                "ok",1794400007L);
        String[] withinWindow=automodOn("window-server","message","Walker",
                approximate,1794400008L);
        check(withinWindow.length==7 && "pm".equals(withinWindow[4]),
                "Third occurrence counts within 8 eligible messages; short messages do not evict");

        automodOn("outside-server","command","me","mod canali add #room",1794400000L);
        automodOn("outside-server","command","me","mod azione escalation",1794400000L);
        automodOn("outside-server","message","Walker",exact,1794400001L);
        for (int i=1;i<=6;i++) automodOn("outside-server","message","Walker",
                "other"+i+" no"+i+" common"+i,1794400001L+i);
        automodOn("outside-server","message","Walker",exact,1794400008L);
        check(automodOn("outside-server","message","Walker",approximate,
                1794400009L).length==1,
                "A matching message outside the last 8 eligible messages is not counted");

        automodOn("expired-server","command","me","mod canali add #room",1794400000L);
        automodOn("expired-server","command","me","mod azione escalation",1794400000L);
        automodOn("expired-server","message","Walker",exact,1794400001L);
        automodOn("expired-server","message","Walker",exact,1794400002L);
        check(automodOn("expired-server","message","Walker",approximate,
                1794401803L).length==1,
                "Occurrences older than Z=30 minutes do not contribute");

        // Keep historical two-occurrence acceptance checks using an explicit
        // setting, while the independent fresh-server tests verify the new defaults.
        eventAt(OTHER, "command", "#room", "me",
                "mod occorrenze 2",1794400000L);
        eventAt(OTHER, "command", "#room", "me",
                "mod ultimi 4",1794400000L);

        // Mentions are not words: two users talking to one another must not
        // trigger each other's history, even with hyphenated IRC nicknames.
        check(twoUserMessage("Nick-1", "Nick-2: ciao", 1794400000L).length == 1,
                "First addressed one-word message is safe");
        check(twoUserMessage("Nick-2", "Nick-1: salve", 1794400000L).length == 1,
                "Different sender does not inherit the first sender's history");
        check(twoUserMessage("Nick-1", "@Nick-2, ciao", 1794400000L).length == 1,
                "Repeated one-word message with nickname mention is too short");
        check(twoUserMessage("Nick-2", "Nick-1: salve", 1794400000L).length == 1,
                "Repeated short reply must not trigger escalation");
        check(twoUserMessage("Nick-2", "Nick-1: questa frase viene ripetuta",
                1794400000L).length == 1, "Long addressed message is monitored");
        String[] genuineRepeat = twoUserMessage("Nick-2",
                "Nick-1: questa frase viene ripetuta", 1794400000L);
        check(genuineRepeat.length == 7 && "pm".equals(genuineRepeat[4]),
                "Repeated multi-word content still triggers first warning");
        check(eventAt(OTHER, "message", "#room", "Target", repeat,
                1794400001L).length == 1, "First message not a violation");
        String[] warning = eventAt(OTHER, "message", "#room", "Target", repeat,
                1794400002L);
        check(warning.length == 7 && "pm".equals(warning[4]) &&
                        "Target".equals(warning[5]) &&
                        "non ripetere su #room".equals(warning[6]),
                "Default warning is a private channel-specific message");
        String[] kick = eventAt(OTHER, "message", "#room", "Target", repeat,
                1794400014L);
        check(kick.length == 7 && "kick".equals(kick[4]) &&
                        ("Target" + (char)9 + "non ripetere").equals(kick[6]),
                "Second repetition KICK reason must be non ripetere");
        eventAt(OTHER, "part", "#room", "Target", "", 1794400015L);
        eventAt(OTHER, "join", "#room", "Target", "", 1794400016L);
        check(eventAt(OTHER, "message", "#room", "Target", repeat,
                1794400020L).length == 1,
                "Rejoin clears message history but not infractions");
        String[] temporary = eventAt(OTHER, "message", "#room", "Target", repeat,
                1794400030L);
        check(temporary.length == 7 && "ktban".equals(temporary[4]) &&
                        ("Target" + (char)9 + "10m" + (char)9 + "non ripetere")
                                .equals(temporary[6]),
                "Third infraction requests KICK then timed BAN atomically");
        eventAt(OTHER, "quit", "#room", "Target", "", 1794400031L);
        eventAt(OTHER, "join", "#room", "Target", "", 1794400032L);
        eventAt(OTHER, "message", "#room", "Target", repeat, 1794400045L);
        String[] permanent = eventAt(OTHER, "message", "#room", "Target", repeat,
                1794400056L);
        check(permanent.length == 7 && "kban".equals(permanent[4]) &&
                        ("Target" + (char)9 + "non ripetere").equals(permanent[6]),
                "Fourth infraction requests KICK then permanent BAN atomically");
        String[] actionCounts = eventAt(OTHER, "command", "#room", "me",
                "mod stato", 1794400056L);
        check(actionCounts.length == 22 &&
                        java.util.Arrays.toString(actionCounts).contains("avvisi richiesti 2") &&
                        java.util.Arrays.toString(actionCounts).contains("kick richiesti 3") &&
                        java.util.Arrays.toString(actionCounts).contains("ban richiesti 2") &&
                        java.util.Arrays.toString(actionCounts).contains("utenti storici "),
                "Warnings, kicks and bans are counted without double-counting combos");
        eventAt(OTHER, "nick", "#room", "Target", "NewNick", 1794400057L);
        eventAt(OTHER, "message", "#room", "NewNick", repeat, 1794400070L);
        String[] renamed = eventAt(OTHER, "message", "#room", "NewNick", repeat,
                1794400082L);
        check(renamed.length == 7 && "kban".equals(renamed[4]),
                "Nick change preserves the escalation counter");
        String[] dropped = eventAt(OTHER, "command", "#room", "me",
                "mod canali drop #room", 1794400083L);
        check(dropped.length == 4 && dropped[3].contains("rimosso: #room"),
                "Channel drop is acknowledged");
        check(eventAt(OTHER, "message", "#room", "NewNick", repeat,
                1794400095L).length == 1,
                "Dropped channel must ignore repeated messages");
        eventAt(OTHER, "command", "#room", "me",
                "mod canali add #room", 1794400100L);
        check(eventAt(OTHER, "message", "#room", "NewNick", repeat,
                1794400102L).length == 1,
                "Readding dropped channel starts with clean message history");

        // Registered and channel-privileged users are whitelisted by default.
        String[] registered = eventAs(OTHER, "message", "#room", "RegUser",
                repeat, 1794400110L, "", "knownaccount");
        check(registered.length == 1, "Registered accounts are exempt by default");
        for (String prefix : new String[]{"+", "%", "@", "&", "~"}) {
            String[] privileged = eventAs(OTHER, "message", "#room",
                    "Privileged", repeat, 1794400111L, prefix, "*");
            check(privileged.length == 1, "Prefixed " + prefix + " is whitelisted");
        }
        String[] unknown = eventAs(OTHER, "message", "#room", "Unknown",
                repeat, 1794400112L, "", "");
        check(unknown.length == 4 && "who".equals(unknown[1]) &&
                        "#room".equals(unknown[2]),
                "Unknown account fails closed and requests bounded WHOX");
        String[] diagnostic = eventAt(OTHER, "command", "#room", "me",
                "mod stato", 1794400112L);
        check(diagnostic.length == 22 &&
                        java.util.Arrays.toString(diagnostic).contains("ricevuti ") &&
                        java.util.Arrays.toString(diagnostic).contains("esenti ") &&
                        java.util.Arrays.toString(diagnostic).contains("ignoti 1") &&
                        java.util.Arrays.toString(diagnostic).contains("monitorati "),
                "Status separates incoming, exempt, unknown and monitored");

        // A missing account tag is not evidence of exemption or guilt.
        // Keep known text evidence while withholding any moderation until
        // the IRC server supplies an authenticated account/logout.
        check(eventAs(OTHER, "message", "#room", "PendingUser", repeat,
                1794400113L, "", "").length == 1,
                "Unknown account retains first fingerprint without punishment");
        check(eventAs(OTHER, "message", "#room", "PendingUser", repeat,
                1794400114L, "", "").length == 1,
                "Unknown repeat is still not punished");
        String[] verifiedRepeat = eventAs(OTHER, "message", "#room", "PendingUser",
                repeat, 1794400115L, "", "*");
        check(verifiedRepeat.length == 7 && "pm".equals(verifiedRepeat[4]),
                "After confirmed logout, repeat uses retained unknown history");
        check(eventAs(OTHER, "message", "#room", "ExemptPending", repeat,
                1794400116L, "", "").length == 1,
                "Unknown future-exempt user is never punished");
        check(eventAs(OTHER, "message", "#room", "ExemptPending", repeat,
                1794400117L, "", "").length == 1,
                "Repeated unknown future-exempt user is never punished");
        check(eventAs(OTHER, "message", "#room", "ExemptPending", repeat,
                1794400118L, "", "registered").length == 1,
                "Confirmed registered exemption prevails over retained history");

        eventAt(OTHER, "command", "#room", "me",
                "mod azione kick", 1794400113L);
        eventAt(OTHER, "command", "#room", "me",
                "mod blacklist add trouble", 1794400114L);
        String[] blacklistList = eventAt(OTHER, "command", "#room", "me",
                "mod blacklist", 1794400115L);
        check(blacklistList.length == 4 &&
                        blacklistList[3].contains("trouble"),
                "Blacklist lists added authenticated accounts");
        check(eventAs(OTHER, "message", "#room", "VoiceUser",
                repeat, 1794400120L, "+", "trouble").length == 1,
                "First blacklisted voice message initializes history");
        String[] forced = eventAs(OTHER, "message", "#room", "VoiceUser",
                repeat, 1794400121L, "+", "trouble");
        check(forced.length == 7 && "blkmod".equals(forced[4]) &&
                        ("VoiceUser" + (char)9 + "trouble" + (char)9 +
                                "kick" + (char)9 + "10m").equals(forced[6]),
                "Blacklisted account overrides default voice and registered whitelist");
        eventAt(OTHER, "command", "#room", "me",
                "mod blacklist drop trouble", 1794400122L);
        check(eventAs(OTHER, "message", "#room", "VoiceUser",
                repeat, 1794400123L, "+", "trouble").length == 1,
                "Dropped blacklist account becomes exempt again");
        eventAt(OTHER, "command", "#room", "me",
                "mod whitelist categoria registrati off", 1794400124L);
        eventAt(OTHER, "command", "#room", "me",
                "mod whitelist add personal", 1794400125L);
        check(eventAs(OTHER, "message", "#room", "PersonalUser",
                repeat, 1794400126L, "", "personal").length == 1,
                "Individually whitelisted account stays exempt if category is off");
        eventAt(OTHER, "command", "#room", "me",
                "mod whitelist categoria voice off", 1794400127L);
        check(eventAs(OTHER, "message", "#room", "VoiceOther",
                repeat, 1794400128L, "+", "*").length == 1,
                "Voice can be excluded from default whitelist");
        String[] voiceUnprotected = eventAs(OTHER, "message", "#room",
                "VoiceOther", repeat, 1794400129L, "+", "*");
        check(voiceUnprotected.length == 7 &&
                        "blkmod".equals(voiceUnprotected[4]) &&
                        ("VoiceOther" + (char)9 + "*" + (char)9 +
                                "kick" + (char)9 + "10m").equals(voiceUnprotected[6]),
                "Voice category toggle requests verified logged-out override");

        // Persist preferences, not volatile message/infraction history.
        check(automodStorage.containsKey("automod.test-server"),
                "AutoMod must issue persistent C6 writes");
        String firstState = automodStorage.get("automod.test-server");
        check(firstState.startsWith("v2;") && firstState.contains("C#room")
                && firstState.contains("Wpersonal"),
                "Channels and individual account allowlist must be saved");
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, automod))),
                "AutoMod VM restart must load");
        String[] resumedChannels = eventAt(OTHER, "command", "#room", "me",
                "mod canali", 1794400130L);
        check(resumedChannels.length == 4 && resumedChannels[3].contains("#room"),
                "Previously approved channel must survive VM restart");
        String[] resumedWhite = eventAt(OTHER, "command", "#room", "me",
                "mod whitelist", 1794400131L);
        check(resumedWhite.length >= 7 && resumedWhite[3].contains("personal") &&
                        java.util.Arrays.toString(resumedWhite).contains("voice=off") &&
                        java.util.Arrays.toString(resumedWhite).contains("registrati=off"),
                "Account and category exceptions must survive VM restart");
        String[] resumedStatus = eventAt(OTHER, "command", "#room", "me",
                "mod stato", 1794400132L);
        check(resumedStatus.length == 22 &&
                        java.util.Arrays.toString(resumedStatus).contains("azione kick") &&
                        java.util.Arrays.toString(resumedStatus).contains("canali 1") &&
                        java.util.Arrays.toString(resumedStatus).contains("registrati=off") &&
                        java.util.Arrays.toString(resumedStatus).contains("voice=off"),
                "Action and channel counts must survive VM restart");

        byte[][] secondary = NativeLuaRuntime.dispatch(OTHER, "command",
                bytes("second-server"), bytes("#secondary"), bytes("me"),
                bytes("mod canali add #secondary"), bytes("normal"), 1794400133L,
                new byte[0][], new int[0], false, new byte[0][], false,
                true, automodSnapshot(), true);
        String[] secondaryResult = collect(secondary);
        check(secondaryResult.length == 4 && secondaryResult[3].contains("#secondary"),
                "Second network settings are editable independently");
        check(automodStorage.containsKey("automod.second-server") &&
                        !automodStorage.get("automod.test-server").contains("#secondary"),
                "Second network must not overwrite first network");
        String[] secondaryStatus = collect(NativeLuaRuntime.dispatch(
                OTHER, "command", bytes("second-server"), bytes("#secondary"),
                bytes("me"), bytes("mod stato"), bytes("normal"), 1794400133L,
                new byte[0][], new int[0], false, new byte[0][], false,
                true, automodSnapshot(), true));
        check(secondaryStatus.length == 22 &&
                        java.util.Arrays.toString(secondaryStatus).contains(
                                "utenti attuali 0; utenti storici 0") &&
                        java.util.Arrays.toString(secondaryStatus).contains(
                                "ricevuti 0"),
                "Status must not mix user history and counters from other networks");
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, automod))),
                "Second restart must load AutoMod");
        byte[][] secondaryRestored = NativeLuaRuntime.dispatch(OTHER, "command",
                bytes("second-server"), bytes("#secondary"), bytes("me"),
                bytes("mod canali"), bytes("normal"), 1794400134L,
                new byte[0][], new int[0], false, new byte[0][], false,
                true, automodSnapshot(), true);
        check(collect(secondaryRestored)[3].contains("#secondary"),
                "Settings for second network survive VM restart");

        // Legacy v2 persisted counters and recent user histories must migrate
        // without pretending that older, expired users can be reconstructed.
        automodStorage.put("automod.migration-server",
                "v1;1;80;3;30;3;5;10m;monitor;pvt;000000\nC#room");
        automodStorage.put("amst.migration-server",
                "v2;5;2;1;2;1;1;0;0\n#room\toldnick\t1\t1794400130\t1794400131\t1794400132\t1794400132:salve ciao amico");
        String[] legacyStatus = collect(NativeLuaRuntime.dispatch(
                OTHER, "command", bytes("migration-server"), bytes("#room"),
                bytes("me"), bytes("mod stato"), bytes("normal"), 1794400134L,
                new byte[0][], new int[0], false, new byte[0][], false,
                true, automodSnapshot(), true));
        check(legacyStatus.length == 22 &&
                        java.util.Arrays.toString(legacyStatus).contains(
                                "utenti attuali 1; utenti storici 1") &&
                        java.util.Arrays.toString(legacyStatus).contains(
                                "ricevuti 5; esenti 2; ignoti 1") &&
                        java.util.Arrays.toString(legacyStatus).contains(
                                "soglia 80%; occorrenze 3; ultimi 4") &&
                        java.util.Arrays.toString(legacyStatus).contains(
                                "pausa 5s"),
                "v2 migration retains historical counters and seeds user history: " +
                        java.util.Arrays.toString(legacyStatus));
        byte[][] migratedRoster = new byte[][]{
                bytes("oldnick"), bytes(""), bytes("ident"),
                bytes("host.example"), bytes("*")};
        collect(NativeLuaRuntime.dispatch(OTHER, "message",
                bytes("migration-server"), bytes("#room"), bytes("oldnick"),
                bytes("unrelated three words"), bytes("normal"), 1794400135L,
                new byte[0][], new int[0], false,
                migratedRoster, false, true, automodSnapshot(), true));
        check(automodStorage.get("amst.migration-server").startsWith("v3;"),
                "A v2 history is persisted in v3 after the next message");

        // Fill the documented maximum configuration on one network. This is
        // substantially larger than the former 384-byte persistent value cap.
        for (int i = 1; i <= 15; i++) {
            String channelName = "#c" + "x".repeat(58)
                    + String.format(java.util.Locale.ROOT, "%02d", i);
            String[] result = eventAt(OTHER, "command", "#room", "me",
                    "mod canali add " + channelName, 1794400140L + i);
            check("OK".equals(result[0]), "Adding maximum channel entry should work");
        }
        for (int i = 1; i <= 31; i++) {
            String account = "w" + "x".repeat(60)
                    + String.format(java.util.Locale.ROOT, "%02d", i);
            String[] result = eventAt(OTHER, "command", "#room", "me",
                    "mod whitelist add " + account, 1794400170L + i);
            check("OK".equals(result[0]), "Adding maximum whitelist entry should work");
        }
        for (int i = 1; i <= 32; i++) {
            String account = "b" + "y".repeat(60)
                    + String.format(java.util.Locale.ROOT, "%02d", i);
            String[] result = eventAt(OTHER, "command", "#room", "me",
                    "mod blacklist add " + account, 1794400210L + i);
            check("OK".equals(result[0]), "Adding maximum blacklist entry should work");
        }
        String capacityState = automodStorage.get("automod.test-server");
        check(capacityState != null && bytes(capacityState).length > 4096 &&
                        bytes(capacityState).length <= 8192,
                "Full per-network AutoMod preferences must fit 8 KiB");
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, automod))),
                "Large saved AutoMod preferences must survive reload");
        String[] fullState = eventAt(OTHER, "command", "#room", "me",
                "mod stato", 1794400250L);
        check(fullState.length == 22 &&
                        java.util.Arrays.toString(fullState).contains("canali 16") &&
                        java.util.Arrays.toString(fullState).contains("white 32") &&
                        java.util.Arrays.toString(fullState).contains("black 32") &&
                        java.util.Arrays.toString(fullState).contains("altri (/lua mod whitelist)") &&
                        java.util.Arrays.toString(fullState).contains("altri (/lua mod blacklist)"),
                "All maximum-size channel and account exception lists must restore");

        // IRC-facing warnings may not disclose the internal script name,
        // regardless of whether sent publicly, via PM, or via NOTICE.
        eventAt(OTHER, "command", "#room", "me",
                "mod azione avviso", 1794400260L);
        for (String method : new String[]{"pvt", "notice"}) {
            String nick = method.equals("pvt") ? "TestPrivate" : "TestNotice";
            eventAt(OTHER, "command", "#room", "me",
                    "mod avviso " + method, 1794400270L);
            check(eventAs(OTHER, "message", "#room", nick, repeat,
                    1794400271L, "", "*").length == 1,
                    "First " + method + " message initializes history");
            String[] response = eventAs(OTHER, "message", "#room", nick, repeat,
                    1794400272L, "", "*");
            check(response.length == 7 &&
                            (method.equals("pvt") ? "pm" : "notice").equals(response[4]) &&
                            nick.equals(response[5]) &&
                            "non ripetere su #room".equals(response[6]),
                    "The " + method + " warning must not disclose the script name");
        }

        eventAt(OTHER, "command", "#room", "me",
                "mod avviso pubblico", 1794400280L);
        check(eventAs(OTHER, "message", "#room", "TestPublic", repeat,
                1794400281L, "", "*").length == 1,
                "First public-warning message initializes history");
        String[] publicWarning = eventAs(OTHER, "message", "#room", "TestPublic", repeat,
                1794400282L, "", "*");
        check(publicWarning.length == 7 && "send".equals(publicWarning[4]) &&
                        "#room".equals(publicWarning[5]) &&
                        "TestPublic: non ripetere".equals(publicWarning[6]),
                "Explicit public warning has the short nick-prefixed format");

        // A transport disconnect must not clear the recent escalation state,
        // and a new Lua worker must restore it from per-script storage.
        eventAt(OTHER, "command", "#room", "me",
                "mod azione escalation", 1794400283L);
        check(eventAs(OTHER, "message", "#room", "Retained", repeat,
                1794400284L, "", "*").length == 1,
                "Fresh nickname initializes persisted history");
        String[] retainedWarn = eventAs(OTHER, "message", "#room", "Retained", repeat,
                1794400285L, "", "*");
        check(retainedWarn.length == 7 && "send".equals(retainedWarn[4]),
                "First infraction on persisted history is a warning");
        String[] beforeDisconnect = eventAt(OTHER, "command", "#room", "me",
                "mod stato", 1794400286L);
        eventAt(OTHER, "disconnected", "", "", "", 1794400287L);
        String[] afterDisconnect = eventAt(OTHER, "command", "#room", "me",
                "mod stato", 1794400288L);
        check(afterDisconnect.length == 22 &&
                        java.util.Arrays.toString(afterDisconnect).contains("utenti attuali ") &&
                        java.util.Arrays.toString(afterDisconnect).contains("ricevuti ") &&
                        java.util.Arrays.equals(afterDisconnect,beforeDisconnect),
                "Disconnect preserves active fingerprints and cumulative counters");
        check(automodStorage.containsKey("amst.test-server"),
                "Active AutoMod state is saved separately from configuration");
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, automod))),
                "Worker restart restores persisted state");
        String[] afterWorkerRestart = eventAt(OTHER, "command", "#room", "me",
                "mod stato", 1794400290L);
        check(afterWorkerRestart.length == 22 &&
                        java.util.Arrays.equals(afterWorkerRestart,beforeDisconnect),
                "Counters and active users survive a Lua VM restart");
        String[] nextEscalation = eventAs(OTHER, "message", "#room", "Retained", repeat,
                1794400300L, "", "*");
        check(nextEscalation.length == 7 && "kick".equals(nextEscalation[4]),
                "Second infraction KICKs after disconnect and Lua restart");
        check("OK".equals(decode(NativeLuaRuntime.load(OTHER, automod))),
                "Worker reload for expiration test succeeds");
        check(eventAs(OTHER, "message", "#room", "Retained", repeat,
                1794402301L, "", "*").length == 1,
                "Expired message and escalation history is dropped after reload");
        String[] freshWarn = eventAs(OTHER, "message", "#room", "Retained", repeat,
                1794402302L, "", "*");
        check(freshWarn.length == 7 && "send".equals(freshWarn[4]),
                "New natural-expiry window starts from first warning");


        // Standalone Collector: load the actual shipped Lua source and exercise
        // generic script actions, rather than a Java host-side collector.
        automodStorage.clear();
        automodActive = true;
        String collectorSource = Files.readString(
                Path.of("docs/lua-examples/moderation_collector.lua"));
        String collectorLoad = decode(NativeLuaRuntime.load(ID, bytes(collectorSource)));
        check("OK".equals(collectorLoad),
                "Standalone collector Lua source must load: " + collectorLoad);

        String[] collectorKick = collectorEvent("kick", "ChannelOp", "spam",
                "Target", 1794405000L);
        check(collectorKick.length == 4 && "OK".equals(collectorKick[0]) &&
                        "history".equals(collectorKick[1]) &&
                        "#room".equals(collectorKick[2]) &&
                        ("Target" + (char)9 + "1").equals(collectorKick[3]),
                "KICK asks for the target's last stored channel message");
        String[] kickHistory = historyWithStoredSender();
        check(kickHistory.length == 4 && "journal".equals(kickHistory[1]) &&
                        kickHistory[3].contains("\"event\":\"kick\"") &&
                        kickHistory[3].contains("\"operator\":\"ChannelOp\"") &&
                        kickHistory[3].contains("\"reason\":\"spam\"") &&
                        kickHistory[3].contains("\"last_message\":{") &&
                        kickHistory[3].contains("\"ident\":\"saved-ident\"") &&
                        kickHistory[3].contains("\"host\":\"saved.host.example\"") &&
                        kickHistory[3].contains("Messaggio precedente"),
                "KICK JSONL must contain operator, reason and SQLite message/identity");

        String[] collectorAutoMod = collectorEvent("moderation_log", "Target",
                "AutoMod test-ser/#room Target 78.0% infrazione 2 (mute)",
                "automod_antiripetizione.lua", 1794405010L);
        check(collectorAutoMod.length == 4 && "history".equals(collectorAutoMod[1]) &&
                        ("Target" + (char)9 + "2").equals(collectorAutoMod[3]),
                "AutoMod mute decision asks for last historical public message");
        String[] autoHistory = collectorEvent("history", "Target",
                "Messaggio ripetuto", "2", 1794405010L);
        check(autoHistory.length == 4 && "journal".equals(autoHistory[1]) &&
                        autoHistory[3].contains("\"event\":\"automod\"") &&
                        autoHistory[3].contains("\"action\":\"mute\"") &&
                        autoHistory[3].contains("\"similarity_percent\":78.0") &&
                        autoHistory[3].contains("\"infraction_level\":2") &&
                        autoHistory[3].contains("Messaggio ripetuto"),
                "AutoMod JSONL must contain action, detection data and context");

        String[] collectorBan = collectorEvent("ban", "ChannelOp",
                "Target!*@*", "", 1794405020L);
        check(collectorBan.length == 4 && "history".equals(collectorBan[1]) &&
                        ("Target" + (char)9 + "3").equals(collectorBan[3]),
                "Exact-nick ban requests target history");
        String[] banHistory = collectorEvent("history", "Target",
                "Ultimo ban", "3", 1794405019L);
        check(banHistory.length == 4 && "journal".equals(banHistory[1]) &&
                        banHistory[3].contains("\"event\":\"ban\"") &&
                        banHistory[3].contains("\"operator\":\"ChannelOp\"") &&
                        banHistory[3].contains("\"ban_mask\":\"Target!*@*\""),
                "Observed ban JSONL must contain setter, mask and message context");

        String[] ambiguous = collectorEvent("ban", "ChannelOp", "*!*@other.example",
                "", 1794405030L);
        check(ambiguous.length == 4 && "journal".equals(ambiguous[1]) &&
                        ambiguous[3].contains("\"nickname\":null") &&
                        ambiguous[3].contains("\"last_message\":null"),
                "Generic unmatched ban must not invent a victim or chat evidence");

        String[] off = collectorEvent("command", "self", "collector off",
                "", 1794405040L);
        check("OK".equals(off[0]) &&
                        "off".equals(automodStorage.get("collector.test-server")),
                "OFF state must be persisted by the Lua script");
        String[] silent = collectorEvent("kick", "ChannelOp", "again",
                "Target", 1794405050L);
        check(silent.length == 1 && "OK".equals(silent[0]),
                "With Collector OFF: no SQLite history request or journal action");
        String[] on = collectorEvent("command", "self", "collector on",
                "", 1794405060L);
        check("OK".equals(on[0]) &&
                        "on".equals(automodStorage.get("collector.test-server")),
                "ON state must be persisted by the Lua script");
        check(collectorEvent("kick", "ChannelOp", "again",
                "Target", 1794405070L).length == 4,
                "Collector resumes after ON");
        String[] offWithPending = collectorEvent("command", "self", "collector off",
                "", 1794405071L);
        check(offWithPending.length == 7 &&
                        "journal".equals(offWithPending[1]) &&
                        offWithPending[3].contains("\"event\":\"kick\"") &&
                        offWithPending[3].contains("\"last_message\":null") &&
                        "show".equals(offWithPending[4]),
                "OFF flushes already detected moderation events before stopping");
        collectorEvent("command", "self", "collector on", "", 1794405072L);
        check(collectorEvent("kick", "ChannelOp", "disconnect",
                "Target", 1794405073L).length == 4,
                "A pending record remains before disconnection");

        String[] disconnectedCollector = collectorEvent("disconnected", "", "",
                "", 1794405080L);
        check(disconnectedCollector.length == 4 &&
                        "journal".equals(disconnectedCollector[1]) &&
                        disconnectedCollector[3].contains("\"event\":\"kick\"") &&
                        disconnectedCollector[3].contains("\"last_message\":null"),
                "A disconnect must flush pending moderation records without fabricating context");

        // Explicit compound moderation API must remain one structured action,
        // including when the command originates in the server tab.
        String ejectScript = "tiarca.on('command', function(e) "
                + "if e.text == 'eject-test' then "
                + "tiarca.eject('#room', 'Target', 'utente non gradito') end end)";
        check("OK".equals(decode(NativeLuaRuntime.load(ID, bytes(ejectScript)))),
                "Eject script should load");
        String[] fromServer = event(ID, "command", "", "", "eject-test");
        check(fromServer.length == 4 && "OK".equals(fromServer[0]) &&
                        "eject".equals(fromServer[1]) &&
                        "#room".equals(fromServer[2]) &&
                        "Target\tutente non gradito".equals(fromServer[3]),
                "Server-tab eject must emit one validated compound action");
        String[] fromChannel = event(ID, "command", "#room", "", "eject-test");
        check(fromChannel.length == 4 && "eject".equals(fromChannel[1]) &&
                        "#room".equals(fromChannel[2]),
                "Channel-tab eject should emit the same structured action");

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
