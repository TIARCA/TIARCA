package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;
import io.mrarm.chatlib.irc.IRCCaseMapping;

/**
 * Pure-Java outbound action gate. No arbitrary target, network or IRC command
 * may be chosen by an untrusted script; replies are confined to the current
 * incoming conversation on one explicitly approved network.
 */
final class LuaActionPolicy {
    private LuaActionPolicy() { }

    /** An incoming channel message may reply privately only to its own sender. */
    static boolean permitsDirectReply(LuaScriptStore.Script script, String server,
                                      String channel, String event, String sender,
                                      String target, String text, IRCCaseMapping mapping) {
        if (script == null || !script.isEnabledOn(server) || !script.allowSend ||
                !"message".equals(event) || channel == null || channel.isEmpty() ||
                "#&+!".indexOf(channel.charAt(0)) < 0 ||
                !LuaModerationPolicy.validNick(sender) ||
                !LuaModerationPolicy.validNick(target) ||
                !(mapping == null ? IRCCaseMapping.RFC1459 : mapping)
                        .equals(sender, target))
            return false;
        return validText(text);
    }

    private static boolean validText(String text) {
        if (text == null || text.isEmpty() || text.length() > 400 ||
                text.getBytes(StandardCharsets.UTF_8).length > 400)
            return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0' || c == '\001') return false;
        }
        return true;
    }

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
                !channel.equals(target) || !validText(text))
            return false;
        // IRC target must be one conversation, never a comma-separated target
        // list or an injected protocol delimiter from a malicious server.
        for (int i = 0; i < target.length(); i++) {
            char c = target.charAt(i);
            if (c <= 0x20 || c == 0x7f || c == ',' || c == ':') return false;
        }
        return true;
    }
}
