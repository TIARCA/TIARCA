package io.mrarm.irc.config;

import android.content.Context;
import android.content.SharedPreferences;
import io.mrarm.irc.util.DefaultPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Preferences and validation for the locally handled ! commands. */
public final class QuickCommandSettings {

    // Application-level TMDb key. It is intentionally replaceable in Settings. As with every
    // credential shipped in a mobile application, it must be considered publicly recoverable.
    private static final String DEFAULT_TMDB_KEY_PART_1 = "ccce3f6bb0ed2801";
    private static final String DEFAULT_TMDB_KEY_PART_2 = "8f3823fa6de32b33";

    public static final String PREF_ENABLED = "quick_commands_enabled";
    public static final String PREF_YOUTUBE_ENABLED = "quick_command_youtube_enabled";
    public static final String PREF_WIKI_ENABLED = "quick_command_wiki_enabled";
    public static final String PREF_CALC_ENABLED = "quick_command_calc_enabled";
    public static final String PREF_MOVIE_ENABLED = "quick_command_movie_enabled";
    public static final String PREF_TIME_ENABLED = "quick_command_time_enabled";
    public static final String PREF_DICTIONARY_ENABLED = "quick_command_dictionary_enabled";
    public static final String PREF_YOUTUBE_TRIGGER = "quick_command_youtube_trigger";
    public static final String PREF_WIKI_TRIGGER = "quick_command_wiki_trigger";
    public static final String PREF_CALC_TRIGGER = "quick_command_calc_trigger";
    public static final String PREF_MOVIE_TRIGGER = "quick_command_movie_trigger";
    public static final String PREF_TIME_TRIGGER = "quick_command_time_trigger";
    public static final String PREF_DICTIONARY_TRIGGER = "quick_command_dictionary_trigger";
    public static final String PREF_TMDB_KEY = "quick_command_tmdb_key";

    public enum Command {
        YOUTUBE(PREF_YOUTUBE_ENABLED, PREF_YOUTUBE_TRIGGER, "!yt"),
        WIKI(PREF_WIKI_ENABLED, PREF_WIKI_TRIGGER, "!wiki"),
        CALC(PREF_CALC_ENABLED, PREF_CALC_TRIGGER, "!calc"),
        MOVIE(PREF_MOVIE_ENABLED, PREF_MOVIE_TRIGGER, "!movie"),
        TIME(PREF_TIME_ENABLED, PREF_TIME_TRIGGER, "!ora"),
        DICTIONARY(PREF_DICTIONARY_ENABLED, PREF_DICTIONARY_TRIGGER, "!dizionario");

        public final String enabledKey;
        public final String triggerKey;
        public final String defaultTrigger;

        Command(String enabledKey, String triggerKey, String defaultTrigger) {
            this.enabledKey = enabledKey;
            this.triggerKey = triggerKey;
            this.defaultTrigger = defaultTrigger;
        }
    }

    private QuickCommandSettings() { }

    private static SharedPreferences prefs(Context context) {
        return DefaultPreferences.get(context.getApplicationContext());
    }

    public static boolean isGloballyEnabled(Context context) {
        return prefs(context).getBoolean(PREF_ENABLED, true);
    }

    public static boolean isEnabled(Context context, Command command) {
        return isGloballyEnabled(context) && prefs(context).getBoolean(command.enabledKey, true);
    }

    public static String getTrigger(Context context, Command command) {
        return normalizeTrigger(prefs(context).getString(command.triggerKey,
                command.defaultTrigger), command.defaultTrigger);
    }

    public static String normalizeTrigger(String value, String fallback) {
        if (value == null) return fallback;
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!value.startsWith("!")) value = "!" + value;
        if (!value.matches("![a-z0-9_]{1,24}")) return fallback;
        return value;
    }

    public static Command match(Context context, String text) {
        if (!isGloballyEnabled(context) || text == null) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> seen = new HashSet<>();
        for (Command command : Command.values()) {
            if (!isEnabled(context, command)) continue;
            String trigger = getTrigger(context, command);
            // A duplicated custom word is deliberately assigned to the first command only.
            if (!seen.add(trigger)) continue;
            if (lower.equals(trigger) || (lower.startsWith(trigger) &&
                    lower.length() > trigger.length() &&
                    Character.isWhitespace(lower.charAt(trigger.length()))))
                return command;
        }
        return null;
    }

    public static String getArgument(Context context, Command command, String text) {
        String trigger = getTrigger(context, command);
        return text.length() <= trigger.length() ? "" : text.substring(trigger.length()).trim();
    }

    public static String getTmdbKey(Context context) {
        String custom = prefs(context).getString(PREF_TMDB_KEY, "").trim();
        return custom.isEmpty() ? DEFAULT_TMDB_KEY_PART_1 + DEFAULT_TMDB_KEY_PART_2 : custom;
    }
}
