package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;

/**
 * Pure-Java outbound action gate. No arbitrary target, network or IRC command
 * may be chosen by an untrusted script; replies are confined to the current
 * incoming conversation on one explicitly approved network.
 */
final class LuaActionPolicy {
    private LuaActionPolicy() { }

    static boolean permitsSend(LuaScriptStore.Script script, String server,
                               String channel, String event, String target, String text) {
        if (script == null || !script.enabled || !script.allowSend ||
                server == null || !script.serverId.equals(server) ||
                !"message".equals(event) || channel == null || channel.isEmpty() ||
                !channel.equals(target) || text == null || text.isEmpty() ||
                text.length() > 400 ||
                text.getBytes(StandardCharsets.UTF_8).length > 400)
            return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0' || c == '\001') return false;
        }
        return true;
    }
}
