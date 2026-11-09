package io.mrarm.irc.dialog;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds operator commands without depending on Android UI state. */
public final class OperatorCommandComposer {
    private OperatorCommandComposer() { }

    /**
     * EXTBAN ISUPPORT describes syntax, not semantics. Only mappings explicitly known for
     * InspIRCd's letter syntax and UnrealIRCd 6's named syntax are generated automatically.
     */
    public static final class Dialect {
        private final String prefix;
        private final String advertised;
        private final String accountAdvertised;
        private final boolean named;

        public Dialect(String extban, String accountExtban) {
            if (extban == null || !extban.contains(",")) {
                prefix = null;
                advertised = "";
                named = false;
            } else {
                int comma = extban.indexOf(',');
                prefix = extban.substring(0, comma);
                advertised = extban.substring(comma + 1);
                named = "~".equals(prefix);
            }
            accountAdvertised = accountExtban == null ? "" : accountExtban;
        }

        public boolean isInspStyle() { return prefix != null && prefix.isEmpty(); }
        public boolean isUnrealStyle() { return named; }
        public boolean hasExtbans() { return prefix != null; }

        private boolean hasLetter(char code) {
            return isInspStyle() && advertised.indexOf(code) >= 0;
        }

        private boolean hasName(String name) {
            if (!named || name == null) return false;
            for (String token : advertised.split(",")) {
                if (token.equalsIgnoreCase(name)) return true;
            }
            return false;
        }

        public String acting(char code) {
            if (hasLetter(code)) return code + ":";
            if (!named) return null;
            String name;
            switch (code) {
                case 'm': name = "quiet"; break;
                case 'N': name = "nickchange"; break;
                default: return null;
            }
            return hasName(name) ? "~" + name + ":" : null;
        }

        public String matching(char code) {
            if (hasLetter(code)) return code + ":";
            if (!named) return null;
            String name;
            switch (code) {
                case 'R': name = "account"; break;
                case 'U': name = "account"; break;
                case 'G': name = "country"; break;
                case 'r': name = "realname"; break;
                case 'j': name = "channel"; break;
                case 'z': name = "certfp"; break;
                case 's': name = null; break;
                default: return null;
            }
            return hasName(name) ? "~" + name + ":" : null;
        }

        public boolean supportsActing(char code) { return acting(code) != null; }
        public boolean supportsMatching(char code) { return matching(code) != null; }

        public boolean supportsTimed() {
            return isInspStyle() || hasName("time");
        }

        public String matchingMask(char code, String hostMask, String account,
                                   Map<Character, String> values) {
            String token = matching(code);
            if (token == null) return null;
            String value;
            if (code == 'U') {
                // UnrealIRCd uses ~account:0 for every unauthenticated user.
                // It cannot preserve a host mask, so never silently broaden a host-specific ban.
                if (named) return "*!*@*".equals(hostMask) ? "~account:0" : null;
                return token + hostMask;
            }
            if (code == 'R') {
                value = account;
            } else {
                value = values.get(code);
            }
            if (!validParameter(value)) return null;
            return token + value;
        }

        public String timedCommand(String channel, String duration, String mask) {
            if (!duration.matches("(?i)[1-9][0-9]*[smhdw]")) return null;
            if (isInspStyle()) return "TBAN " + channel + " " + duration + " " + mask;
            if (!hasName("time")) return null;
            long count;
            try { count = Long.parseLong(duration.substring(0, duration.length() - 1)); }
            catch (NumberFormatException ex) { return null; }
            char unit = Character.toLowerCase(duration.charAt(duration.length() - 1));
            long multiplier = unit == 's' ? 0 : unit == 'm' ? 1 :
                    unit == 'h' ? 60 : unit == 'd' ? 1440 : 10080;
            // UnrealIRCd's ~time accepts minutes; do not silently round sub-minute durations.
            if (multiplier == 0 || count > Integer.MAX_VALUE / multiplier) return null;
            return "MODE " + channel + " +b ~time:" + (count * multiplier) + ":" + mask;
        }
    }

    public static final class State {
        public String channel, nick, maskNick, ident, host, account, reason = "", duration = "3h";
        public boolean kick, ban, timed, useNick, useIdent, useHost = true;
        public final Map<Character, Boolean> currentModes = new LinkedHashMap<>();
        public final Map<Character, Boolean> wantedModes = new LinkedHashMap<>();
        public final Set<Character> acting = new LinkedHashSet<>();
        public final Set<Character> matching = new LinkedHashSet<>();
        public final Map<Character, String> matchingValues = new LinkedHashMap<>();

        public String hostMask() {
            return (useNick && validParameter(maskNick) ? maskNick : "*") + "!" +
                    (useIdent && validParameter(ident) ? ident : "*") + "@" +
                    (useHost && validParameter(host) ? host : "*");
        }
    }

    public static final class Result {
        public final List<String> commands;
        public final String error;
        private Result(List<String> commands, String error) {
            this.commands = Collections.unmodifiableList(commands);
            this.error = error;
        }
        public String preview() {
            StringBuilder text = new StringBuilder();
            for (String command : commands) {
                if (text.length() != 0) text.append('\n');
                text.append('/').append(command);
            }
            return text.toString();
        }
    }

    public static Result build(State s, Dialect dialect) {
        List<String> commands = new ArrayList<>();
        if (!validParameter(s.channel) || !validParameter(s.nick))
            return new Result(commands, "Invalid channel or nickname");
        for (Map.Entry<Character, Boolean> entry : s.wantedModes.entrySet()) {
            Boolean previous = s.currentModes.get(entry.getKey());
            if (previous != null && previous.booleanValue() != entry.getValue()) {
                commands.add("MODE " + s.channel + " " +
                        (entry.getValue() ? "+" : "-") + entry.getKey() + " " + s.nick);
            }
        }
        if (s.kick) {
            String message = s.reason == null ? "" : s.reason.trim().replace('\r', ' ').replace('\n', ' ');
            commands.add("KICK " + s.channel + " " + s.nick +
                    (message.isEmpty() ? "" : " :" + message));
        }
        if (s.ban || !s.acting.isEmpty()) {
            List<String> targets = new ArrayList<>();
            if (s.matching.isEmpty()) {
                targets.add(s.hostMask());
            } else {
                for (char matching : s.matching) {
                    String target = dialect.matchingMask(matching, s.hostMask(), s.account,
                            s.matchingValues);
                    if (target == null)
                        return new Result(commands, "Unsupported or incomplete matching extban: " + matching);
                    targets.add(target);
                }
            }
            List<String> finalMasks = new ArrayList<>();
            if (s.acting.isEmpty()) {
                finalMasks.addAll(targets);
            } else {
                for (char action : s.acting) {
                    String actingToken = dialect.acting(action);
                    if (actingToken == null)
                        return new Result(commands, "Unsupported acting extban: " + action);
                    for (String target : targets) finalMasks.add(actingToken + target);
                }
            }
            for (String mask : finalMasks) {
                if (s.timed) {
                    String timed = dialect.timedCommand(s.channel, s.duration, mask);
                    if (timed == null) return new Result(commands, "Unsupported or invalid timed ban");
                    commands.add(timed);
                } else {
                    commands.add("MODE " + s.channel + " +b " + mask);
                }
            }
        }
        return new Result(commands, null);
    }

    private static boolean validParameter(String v) {
        return v != null && !v.isEmpty() && !v.matches(".*[\\s\\p{Cntrl}].*");
    }

    /**
     * Preview is executable: each line is a separate command. Restrict it to moderation
     * commands targeting the opened channel; no arbitrary commands or embedded CR/LF.
     * Return raw protocol commands (without the client-side leading slash).
     */
    public static Result parsePreview(String text, String channel) {
        List<String> commands = new ArrayList<>();
        if (text == null || !validParameter(channel))
            return new Result(commands, "Invalid preview/channel");
        for (String raw : text.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("/")) line = line.substring(1);
            if (line.indexOf('\r') >= 0 || line.indexOf('\0') >= 0 ||
                    line.getBytes(StandardCharsets.UTF_8).length > 510)
                return new Result(commands, "Invalid or oversized IRC command");
            String[] parts = line.split("\\s+", 3);
            if (parts.length < 3 || !parts[1].equalsIgnoreCase(channel) ||
                    !(parts[0].equalsIgnoreCase("MODE") ||
                      parts[0].equalsIgnoreCase("KICK") ||
                      parts[0].equalsIgnoreCase("TBAN")))
                return new Result(commands, "Only channel MODE, KICK or TBAN commands are allowed");
            commands.add(line);
            if (commands.size() > 20)
                return new Result(commands, "Too many commands");
        }
        if (commands.isEmpty()) return new Result(commands, "No commands selected");
        return new Result(commands, null);
    }
}
