package io.mrarm.irc.config;

import android.content.Context;
import android.content.SharedPreferences;

import io.mrarm.irc.util.DefaultPreferences;

/** Shared preference for rendering message timestamps at the far right of each row. */
public final class RightClockSettings {

    public static final String PREF_MESSAGE_TIME_RIGHT = "message_time_right";

    private RightClockSettings() {
    }

    public static boolean isEnabled(Context context) {
        return DefaultPreferences.get(context).getBoolean(PREF_MESSAGE_TIME_RIGHT, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences.Editor editor = DefaultPreferences.get(context).edit();
        editor.putBoolean(PREF_MESSAGE_TIME_RIGHT, enabled);
        editor.apply();
    }
}
