package io.mrarm.irc.config;

import android.content.SharedPreferences;

/** Display choice for server-advertised IRC STATUSMSG targets. */
public final class StatusMessageDisplaySettings {

    public static final String MODE_PLAIN = "plain";
    public static final String MODE_PREFIX = "prefix";
    public static final String MODE_SEPARATE = "separate";

    private StatusMessageDisplaySettings() { }

    /** Prefer the new choice, otherwise honor the old stored checkbox after upgrade. */
    public static String getMode(SharedPreferences prefs) {
        String selected = prefs.getString(ChatSettings.PREF_STATUS_MESSAGE_MODE, null);
        if (MODE_PLAIN.equals(selected) || MODE_PREFIX.equals(selected) ||
                MODE_SEPARATE.equals(selected))
            return selected;
        if (prefs.contains(ChatSettings.PREF_SEPARATE_STATUS_MESSAGES))
            return prefs.getBoolean(ChatSettings.PREF_SEPARATE_STATUS_MESSAGES, false)
                    ? MODE_SEPARATE : MODE_PLAIN;
        return MODE_PREFIX;
    }

    /** Migrate only existing explicit checkbox values; fresh installs use the new default. */
    public static void migrateLegacyIfNeeded(SharedPreferences prefs) {
        if (!prefs.contains(ChatSettings.PREF_STATUS_MESSAGE_MODE) &&
                prefs.contains(ChatSettings.PREF_SEPARATE_STATUS_MESSAGES)) {
            prefs.edit().putString(ChatSettings.PREF_STATUS_MESSAGE_MODE,
                    getMode(prefs)).apply();
        }
    }

    public static boolean isSeparate() {
        return MODE_SEPARATE.equals(getMode(SettingsHelper.getPreferences()));
    }

    public static boolean showPrefix() {
        return MODE_PREFIX.equals(getMode(SettingsHelper.getPreferences()));
    }
}
