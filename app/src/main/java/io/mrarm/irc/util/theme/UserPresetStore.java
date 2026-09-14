package io.mrarm.irc.util.theme;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Local registry that distinguishes imported presets from editable custom color themes. */
public final class UserPresetStore {

    private static final String PREFS = "tiarca_user_presets";
    private static final String KEY_IDS = "ids";

    private UserPresetStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void mark(Context context, ThemeInfo theme) {
        if (theme == null || theme.uuid == null)
            return;
        Set<String> ids = new HashSet<>(prefs(context).getStringSet(KEY_IDS,
                java.util.Collections.emptySet()));
        ids.add(theme.uuid.toString());
        prefs(context).edit().putStringSet(KEY_IDS, ids).apply();
    }

    public static void unmark(Context context, ThemeInfo theme) {
        if (theme == null || theme.uuid == null)
            return;
        Set<String> ids = new HashSet<>(prefs(context).getStringSet(KEY_IDS,
                java.util.Collections.emptySet()));
        if (ids.remove(theme.uuid.toString()))
            prefs(context).edit().putStringSet(KEY_IDS, ids).apply();
    }

    public static boolean contains(Context context, ThemeInfo theme) {
        return theme != null && theme.uuid != null && prefs(context)
                .getStringSet(KEY_IDS, java.util.Collections.emptySet())
                .contains(theme.uuid.toString());
    }

    public static Set<UUID> snapshotThemeIds(ThemeManager manager) {
        Set<UUID> result = new HashSet<>();
        if (manager == null)
            return result;
        for (ThemeInfo theme : manager.getCustomThemes()) {
            if (theme != null && theme.uuid != null)
                result.add(theme.uuid);
        }
        return result;
    }

    public static ThemeInfo findImportedTheme(ThemeManager manager, Set<UUID> before) {
        if (manager == null)
            return null;
        Collection<ThemeInfo> themes = manager.getCustomThemes();
        for (ThemeInfo theme : themes) {
            if (theme != null && theme.uuid != null && (before == null || !before.contains(theme.uuid)))
                return theme;
        }
        return manager.getCurrentCustomTheme();
    }

    public static String nameFromFile(String displayName) {
        if (displayName == null)
            return null;
        String name = displayName.trim();
        if (name.isEmpty())
            return null;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(ThemeArchive.FILE_EXTENSION))
            name = name.substring(0, name.length() - ThemeArchive.FILE_EXTENSION.length()).trim();
        return name.isEmpty() ? null : name;
    }
}
