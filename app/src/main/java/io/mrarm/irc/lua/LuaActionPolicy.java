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
        if (script == null || !script.isEnabledOn(server) || !script.allowSend ||
                (!"message".equals(event) && !"command".equals(event) &&
                        !"join".equals(event) && !"part".equals(event) &&
                        !"quit".equals(event) && !"nick".equals(event)) ||
                channel == null || channel.isEmpty() ||
                // Presence hooks can only send to the actual IRC channel.
                (("join".equals(event) || "part".equals(event) ||
                        "quit".equals(event) || "nick".equals(event)) &&
                        "#&+!".indexOf(channel.charAt(0)) < 0) ||
                !channel.equals(target) || text == null || text.isEmpty() ||
                text.length() > 400 ||
                text.getBytes(StandardCharsets.UTF_8).length > 400)
            return false;
        // IRC target must be one conversation, never a comma-separated target
        // list or an injected protocol delimiter from a malicious server.
        for (int i = 0; i < target.length(); i++) {
            char c = target.charAt(i);
            if (c <= 0x20 || c == 0x7f || c == ',' || c == ':') return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0' || c == '\001') return false;
        }
        return true;
    }
}
