package io.mrarm.irc.lua;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formats actual AutoMod detection logs as bounded local-only server-tab notices. */
final class LuaAutoModReport {
    private static final Pattern DETECTION = Pattern.compile(
            "^AutoMod(B|U)? ([^\\s/]{1,12})/([^\\s]{1,64}) ([^\\s]{1,64}) " +
            "([0-9]{1,3}(?:\\.[0-9])?)% infrazione ([0-9]{1,6}) " +
            "\\((monitor|avviso|kick|ban|mute|tmute|tban|bankick|tbankick|escalation)\\)(?: motore ([AB]))?$");

    private LuaAutoModReport() { }

    static String fromEventLog(String event, String server, String channel,
                               String nick, String line) {
        if (!"message".equals(event) || server == null || channel == null ||
                nick == null || line == null) return null;
        Matcher match = DETECTION.matcher(line);
        if (!match.matches()) return null;
        if (!match.group(2).equals(server.substring(0, Math.min(8, server.length()))) ||
                !match.group(3).equalsIgnoreCase(channel) ||
                !match.group(4).equalsIgnoreCase(nick)) return null;

        String action;
        switch (match.group(7)) {
            case "monitor": action = "MONITORAGGIO"; break;
            case "avviso": action = "AVVISO"; break;
            case "kick": action = "KICK"; break;
            case "ban": action = "BAN"; break;
            case "tban": action = "BAN TEMPORANEO"; break;
            case "bankick": action = "KICK + BAN"; break;
            case "tbankick": action = "KICK + BAN TEMPORANEO"; break;
            case "mute": action = "MUTE"; break;
            case "tmute": action = "MUTE TEMPORANEO"; break;
            default: action = "ESCALATION"; break;
        }
        // This comes from the Lua decision. Authorization or the IRC server may
        // still reject the action; delivery is logged separately when attempted.
        return match.group(3) + " - " + match.group(4) +
                (("B".equals(match.group(1)) || "B".equals(match.group(8))) ?
                        ": annunci, concentrazione " : ": ripetizione ") +
                match.group(5) + "%, infrazione " + match.group(6) +
                ", " + action;
    }
}
