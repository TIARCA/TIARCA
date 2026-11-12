package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;
import io.mrarm.chatlib.irc.IRCCaseMapping;

/**
 * Pure-Java validation: Lua only requests named operator actions, never raw
 * commands. The caller must additionally recheck live channel membership and
 * operator status, and enforce a per-script/server action rate limit.
 */
final class LuaModerationPolicy {
    private LuaModerationPolicy() { }

    static boolean scoped(LuaScriptStore.Script script, String server, String sourceChannel,
                          String event, String targetChannel, boolean moderate) {
        if (script == null || !script.enabled || !script.serverId.equals(server) ||
                (moderate ? !script.allowModeration : !script.allowUserInfo) ||
                sourceChannel == null || sourceChannel.isEmpty() ||
                !sourceChannel.equals(targetChannel) ||
                !("command".equals(event) || (!moderate &&
                        "message".equals(event)) || (moderate &&
                        ("message".equals(event) || "join".equals(event) ||
                                "part".equals(event) || "quit".equals(event) ||
                                "nick".equals(event)))))
            return false;
        // Require one IRC channel; never a PVT, server tab or target list.
        return validAtom(targetChannel, 64, 96) &&
                "#&+!".indexOf(targetChannel.charAt(0)) >= 0;
    }

    static boolean validAtom(String atom, int maxChars, int maxBytes) {
        if (atom == null || atom.isEmpty() || atom.length() > maxChars ||
                atom.getBytes(StandardCharsets.UTF_8).length > maxBytes)
            return false;
        for (int i = 0; i < atom.length(); i++) {
            char c = atom.charAt(i);
            if (c <= 0x20 || c >= 0x7f || c == ':' || c == ',' ||
                    c == '*' || c == '?' || c == '!' || c == '@' ||
                    c == '#')
                if (!(i == 0 && (c == '#' || c == '!')))
                    return false;
        }
        return true;
    }

    static boolean validNick(String nick) {
        return validAtom(nick, 64, 96) &&
                "#&+!".indexOf(nick.charAt(0)) < 0;
    }

    static boolean validReason(String reason) {
        if (reason == null || reason.isEmpty() || reason.length() > 160 ||
                reason.getBytes(StandardCharsets.UTF_8).length > 220)
            return false;
        for (int i = 0; i < reason.length(); i++) {
            char c = reason.charAt(i);
            if (c == '\0' || c == '\r' || c == '\n' || c == '\001' || c == '\t')
                return false;
        }
        return true;
    }

    static boolean validBanHost(String host) {
        if (host == null || host.isEmpty() || host.length() > 192 ||
                host.getBytes(StandardCharsets.UTF_8).length > 220)
            return false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c <= 0x20 || c >= 0x7f || c == '*' || c == '?' ||
                    c == '@' || c == '!' || c == ',' || c == ':')
                return false;
        }
        return true;
    }

    static boolean isSelf(IRCCaseMapping mapping, String targetNick, String ownNick) {
        if (targetNick == null || ownNick == null) return false;
        return (mapping == null ? IRCCaseMapping.RFC1459 : mapping)
                .equals(targetNick, ownNick);
    }

    /** All live gates must still be satisfied at execution, not at callback time. */
    static boolean liveTargetAllowed(boolean connected, boolean operator,
                                     boolean targetPresent, boolean self,
                                     String nick) {
        return connected && operator && targetPresent && !self && validNick(nick);
    }

    static boolean permittedMode(String mode) {
        return "+o".equals(mode) || "-o".equals(mode) ||
                "+v".equals(mode) || "-v".equals(mode);
    }
}
