package io.mrarm.irc.config;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.List;

import io.mrarm.irc.util.DefaultPreferences;

/** Handles the generated IRC identity used until the user opts into a custom username. */
public final class IdentitySettings {

    public static final String PREF_AUTOMATIC_USERNAME = "automatic_username";
    public static final String PREF_CUSTOM_USERNAME_ENABLED = "custom_username_enabled";

    private static final SecureRandom RANDOM = new SecureRandom();

    private IdentitySettings() {
    }

    public static boolean hasConfiguredNickname(ServerConfigData data) {
        return hasNickname(data != null ? data.nicks : null) || hasNickname(AppSettings.getDefaultNicks());
    }

    public static boolean hasNickname(String[] nicks) {
        if (nicks == null)
            return false;
        for (String nick : nicks) {
            if (nick != null && !nick.trim().isEmpty())
                return true;
        }
        return false;
    }

    private static boolean hasNickname(List<String> nicks) {
        if (nicks == null)
            return false;
        for (String nick : nicks) {
            if (nick != null && !nick.trim().isEmpty())
                return true;
        }
        return false;
    }

    public static String createAutomaticIdentity() {
        return String.format(java.util.Locale.US, "TIARCA%04d", RANDOM.nextInt(10000));
    }

    public static synchronized String getAutomaticUsername(Context context) {
        SharedPreferences preferences = DefaultPreferences.get(context);
        String username = preferences.getString(PREF_AUTOMATIC_USERNAME, null);
        if (username != null && !username.trim().isEmpty())
            return username;
        username = createAutomaticIdentity();
        preferences.edit().putString(PREF_AUTOMATIC_USERNAME, username).commit();
        return username;
    }

    public static boolean isCustomUsernameEnabled(Context context) {
        SharedPreferences preferences = DefaultPreferences.get(context);
        if (preferences.contains(PREF_CUSTOM_USERNAME_ENABLED))
            return preferences.getBoolean(PREF_CUSTOM_USERNAME_ENABLED, false);
        if (hasText(AppSettings.getDefaultUser()))
            return true;
        for (ServerConfigData server : ServerConfigManager.getInstance(context).getServers()) {
            if (hasText(server.user))
                return true;
        }
        return false;
    }

    public static String getUsername(Context context, ServerConfigData data) {
        if (isCustomUsernameEnabled(context)) {
            if (data != null && hasText(data.user))
                return data.user;
            if (hasText(AppSettings.getDefaultUser()))
                return AppSettings.getDefaultUser();
        }
        return getAutomaticUsername(context);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
