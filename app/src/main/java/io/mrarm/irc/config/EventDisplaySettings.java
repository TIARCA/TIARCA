package io.mrarm.irc.config;

/** Optional neutral-color presentation for IRC channel events. */
public final class EventDisplaySettings {

    public static final String PREF_MONOCHROME_MODE = "monochrome_event_mode";
    public static final String PREF_MONOCHROME_KICK = "monochrome_event_kick";
    public static final String PREF_MONOCHROME_QUIT = "monochrome_event_quit";
    public static final String PREF_MONOCHROME_JOIN_PART = "monochrome_event_join_part";

    private EventDisplaySettings() {
    }

    public static Object getDefaultValue(String key) {
        if (PREF_MONOCHROME_MODE.equals(key) ||
                PREF_MONOCHROME_KICK.equals(key) ||
                PREF_MONOCHROME_QUIT.equals(key) ||
                PREF_MONOCHROME_JOIN_PART.equals(key))
            return false;
        return null;
    }
}
