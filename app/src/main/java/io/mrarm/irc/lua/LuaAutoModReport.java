package io.mrarm.irc.lua;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formats actual AutoMod detection logs as bounded local-only server-tab notices. */
final class LuaAutoModReport {
    private static final Pattern DETECTION = Pattern.compile(
            "^AutoMod(B|U|V4)? ([^\\s/]{1,12})/([^\\s]{1,64}) ([^\\s]{1,64}) " +
            "([0-9]{1,3}(?:\\.[0-9])?)% infrazione ([0-9]{1,6}) " +
            "\\((monitor|avviso|kick|ban|mute|tmute|tban|bankick|tbankick|escalation)\\)(?: motore ([AB]))?" +
            "(?: rule ([A-Z_]{1,24}) score (-?[0-9]{1,10}) threshold ([0-9]{1,3}) count ([0-9]{1,10}) refs ([a-f0-9:-]{1,64}(?:,[a-f0-9:-]{1,64})?))?" +
            "(?: aliases ([^\\s/:]{1,64}(?:/[^\\s/:]{1,64}){0,7}))?$");

    private LuaAutoModReport() { }

    static String fromEventLog(String event, String server, String channel,
                               String nick, String line) {
        return fromEventLog(event, server, channel, nick, line, 0, null);
    }

    static String fromEventLog(String event, String server, String channel,
                               String nick, String line, long eventTimestamp,
                               String eventText) {
        if (!"message".equals(event) || server == null || channel == null ||
                nick == null || line == null) return null;
        Matcher match = DETECTION.matcher(line);
        if (!match.matches() || ((match.group(9) != null || match.group(14) != null)
                && !"V4".equals(match.group(1)))) return null;
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
        boolean ad = "B".equals(match.group(1)) || "B".equals(match.group(8));
        String explanation = ad
                ? ("V4".equals(match.group(1))
                    ? ": annunci riconosciuti, infrazione "
                    : ": annunci, concentrazione " + match.group(5) + "%, infrazione ")
                : ": ripetizione " + match.group(5) + "%, infrazione ";
        String aliases = match.group(14);
        String result = match.group(3) + " - " + match.group(4) +
                (aliases == null ? "" : "/" + aliases) +
                explanation + match.group(6) + ", " + action;
        if ("V4".equals(match.group(1))) {
            if (eventTimestamp > 0 && eventTimestamp <= Long.MAX_VALUE / 1000L)
                result += " | " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ROOT)
                        .format(new Date(eventTimestamp * 1000L));
            if (eventText != null) {
                // Keep the triggering text, not the Lua-normalized version. Bound
                // local presentation and prevent control characters from forging lines.
                String raw = eventText.replaceAll("[\\x00-\\x1F\\x7F]", " ");
                if (raw.length() > 1024) {
                    int end = Character.isHighSurrogate(raw.charAt(1023)) ? 1023 : 1024;
                    raw = raw.substring(0, end) + "…";
                }
                result += " | Messaggio: " + raw;
            }
        }
        return result;
    }

    /** Preserve the V4 wire format consumed by already installed Harvester scripts. */
    static String canonicalLine(String line) {
        int at = line.lastIndexOf(" aliases ");
        return at < 0 ? line : line.substring(0, at);
    }
}
