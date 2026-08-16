package io.mrarm.irc.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Accesses the same file historically used by
 * android.preference.PreferenceManager without depending on that deprecated API.
 */
public final class DefaultPreferences {

    private static final String FILE_SUFFIX = "_preferences";

    private DefaultPreferences() {
    }

    public static SharedPreferences get(Context context) {
        Context appContext = context.getApplicationContext();
        return appContext.getSharedPreferences(
                appContext.getPackageName() + FILE_SUFFIX, Context.MODE_PRIVATE);
    }

}
