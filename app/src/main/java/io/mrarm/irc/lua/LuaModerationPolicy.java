package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import io.mrarm.chatlib.irc.IRCCaseMapping;
import io.mrarm.chatlib.dto.NickWithPrefix;

/**
 * Pure-Java validation: Lua only requests named operator actions, never raw
 * commands. The caller must additionally recheck live channel membership and
 * operator status, and enforce a per-script/server action rate limit.
 */
final class LuaModerationPolicy {
    private LuaModerationPolicy() { }

    static boolean scoped(LuaScriptStore.Script script, String server, String sourceChannel,
                          String event, String targetChannel, boolean moderate) {
        if (script == null || !script.isEnabledOn(server) ||
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

    /**
     * Automatic punitive actions must not target ops, halfops or voiced users.
     * A null prefix list represents an ordinary (unprefixed) IRC member;
     * only an unknown member must fail closed. MODE remains exempt to allow
     * existing Lua voice/devoice actions.
     */
    /**
     * Exception for an explicitly targeted, authenticated account on the same
     * incoming channel message. No privilege or self check is bypassed here:
     * the caller must still check operator status, live membership and self.
     * The host never trusts just the Lua-provided account claim.
     */
    static boolean blacklistOverrideAllowed(LuaScriptStore.Script script, String server,
                                            String event, String eventNick,
                                            String targetNick, String claimedAccount,
                                            String observedAccount, boolean accountKnown,
                                            IRCCaseMapping mapping) {
        return script != null && script.isEnabledOn(server) &&
                script.allowModeration && script.allowUserInfo &&
                "message".equals(event) &&
                validNick(eventNick) && validNick(targetNick) &&
                isSelf(mapping, targetNick, eventNick) &&
                accountKnown && (
                    ("*".equals(claimedAccount) && observedAccount == null) ||
                    (validAccount(claimedAccount) && validAccount(observedAccount) &&
                     claimedAccount.equalsIgnoreCase(observedAccount)));
    }

    private static boolean validAccount(String account) {
        if (account == null || account.isEmpty() || account.equals("*") ||
                account.equals("0") || account.length() > 64) return false;
        for (int i = 0; i < account.length(); i++) {
            char c = account.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == '_' || c == '.' ||
                    c == '-')) return false;
        }
        return true;
    }

    static boolean unprivilegedTarget(NickWithPrefix member) {
        return member != null && (member.getNickPrefixes() == null
                || member.getNickPrefixes().length() == 0);
    }

    /** An explicitly typed /lua command may target a joined channel from
     * its own tab or the server tab, but never from another conversation. */
    static boolean scopedEject(LuaScriptStore.Script script, String server,
                               String sourceChannel, String event, String targetChannel) {
        return script != null && script.isEnabledOn(server) &&
                script.allowModeration && script.allowUserInfo &&
                "command".equals(event) && sourceChannel != null &&
                (sourceChannel.isEmpty() || sourceChannel.equals(targetChannel)) &&
                validAtom(targetChannel, 64, 96) &&
                "#&+!".indexOf(targetChannel.charAt(0)) >= 0;
    }

    /** The only prefixed member that can be expelled is a voice (+v).
     * Never allow an operator, halfop, owner or other privileged prefix. */
    static boolean ejectableTarget(NickWithPrefix member) {
        if (member == null) return false;
        String prefixes = member.getNickPrefixes() == null ? "" :
                member.getNickPrefixes().toString();
        return prefixes.isEmpty() || "+".equals(prefixes);
    }

    static boolean targetHasVoice(NickWithPrefix member) {
        return member != null && member.getNickPrefixes() != null &&
                "+".equals(member.getNickPrefixes().toString());
    }

    /** Deliberately broad suffix requested for a manual SimosNap moderation.
     * Unknown cloaks, malformed IPs and short domains fail closed. */
    static String revisedHost(String host) {
        if (!validBanHost(host)) return null;
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.startsWith("simosnap-")) {
            int firstDot = lower.indexOf('.');
            int net = firstDot < 0 ? -1 : lower.lastIndexOf("net.", firstDot);
            return net > "simosnap-".length() && firstDot == net + 3 &&
                    firstDot < host.length() - 2 ? "*" + host.substring(net) : null;
        }
        if (host.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) {
            String[] parts = host.split("\\.", -1);
            try {
                for (String part : parts) {
                    if (part.length() > 3 || Integer.parseInt(part) > 255)
                        return null;
                }
            } catch (NumberFormatException ex) {
                return null;
            }
            return "*" + host.substring(host.indexOf('.') + 1);
        }
        if (host.matches("[0-9.]+")) return null;
        int firstDot = host.indexOf('.');
        if (firstDot < 1 || firstDot == host.lastIndexOf('.') ||
                host.endsWith(".")) return null;
        return "*." + host.substring(firstDot + 1);
    }

    /** A timed mute must use the server-advertised quiet extban; never a plain ban. */
    static String timedMuteCommand(OperatorCommandComposer.Dialect dialect,
                                   String channel, String duration, String host) {
        if (dialect == null || !validBanHost(host) ||
                !duration.matches("(?i)[1-9][0-9]?[hmd]")) return null;
        String acting = dialect.acting('m');
        if (acting == null) return null;
        return dialect.timedCommand(channel, duration, acting + "*!*@" + host);
    }

    static boolean permittedMode(String mode) {
        return "+o".equals(mode) || "-o".equals(mode) ||
                "+v".equals(mode) || "-v".equals(mode);
    }
}
