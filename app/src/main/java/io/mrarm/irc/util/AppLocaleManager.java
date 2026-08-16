package io.mrarm.irc.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** Applies and persists the language explicitly selected inside the app. */
public final class AppLocaleManager {

    public static final String PREF_APP_LANGUAGE = "app_language";
    public static final String LANGUAGE_SYSTEM = "system";

    private AppLocaleManager() {
    }

    public static String getLanguage(SharedPreferences preferences) {
        return preferences.getString(PREF_APP_LANGUAGE, LANGUAGE_SYSTEM);
    }

    public static void applyStoredLanguage(Context context) {
        applyLanguage(getLanguage(DefaultPreferences.get(context)));
    }

    public static void applyLanguage(String language) {
        LocaleListCompat locales = LANGUAGE_SYSTEM.equals(language) || language == null
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(language);
        if (!AppCompatDelegate.getApplicationLocales().equals(locales))
            AppCompatDelegate.setApplicationLocales(locales);
    }
}
