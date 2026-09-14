package io.mrarm.irc.util.theme;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.mrarm.irc.config.AutomatedSenderSettings;
import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.config.EventDisplaySettings;
import io.mrarm.irc.config.MessageFormatSettings;
import io.mrarm.irc.config.RightClockSettings;
import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.setting.ListWithCustomSetting;
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.MessageBuilder;

/** Bridges sectioned portable-preset data and the interface preferences used by the app. */
final class ThemeAppearance {

    private static final String ASSET_DIR = "assets";

    private ThemeAppearance() {
    }

    static void capture(Context context, ThemeInfo theme) {
        SharedPreferences prefs = DefaultPreferences.get(context);
        theme.formatVersion = ThemeArchive.FORMAT_VERSION;
        if (theme.assets == null)
            theme.assets = new HashMap<>();

        ThemeInfo.UiSection ui = new ThemeInfo.UiSection();
        ui.appearancePreset = prefs.getString(
                AppearancePresetManager.PREF_APPEARANCE_PRESET,
                AppearancePreset.CUSTOM.getId());
        ui.appBarCompactMode = getStringPreference(prefs, ChatSettings.PREF_APPBAR_COMPACT_MODE);
        theme.ui = ui;

        ThemeInfo.ChatSection chat = new ThemeInfo.ChatSection();
        chat.font = getStringPreference(prefs, ChatSettings.PREF_FONT);
        chat.fontSize = getIntPreference(prefs, ChatSettings.PREF_FONT_SIZE);
        chat.globalFontEnabled = getBooleanPreference(
                prefs, ChatSettings.PREF_GLOBAL_FONT_ENABLED);
        chat.textAutocorrectEnabled = getBooleanPreference(
                prefs, ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED);
        chat.sendBoxAlwaysMultiline = getBooleanPreference(
                prefs, ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE);
        chat.monochromeBots = prefs.getBoolean(
                AutomatedSenderSettings.PREF_MONOCHROME_BOTS, false);
        chat.messageAvatars = prefs.getBoolean(
                MessageFormatSettings.PREF_MESSAGE_AVATARS, false);
        chat.customAvatars = prefs.getBoolean(
                MessageFormatSettings.PREF_MESSAGE_CUSTOM_AVATARS, false);
        chat.monochromeModeEvents = prefs.getBoolean(
                EventDisplaySettings.PREF_MONOCHROME_MODE, false);
        chat.monochromeKickEvents = prefs.getBoolean(
                EventDisplaySettings.PREF_MONOCHROME_KICK, false);
        chat.monochromeQuitEvents = prefs.getBoolean(
                EventDisplaySettings.PREF_MONOCHROME_QUIT, false);
        chat.monochromeJoinPartEvents = prefs.getBoolean(
                EventDisplaySettings.PREF_MONOCHROME_JOIN_PART, false);
        theme.chat = chat;

        theme.assets.remove(ThemeInfo.ASSET_FONT);
        if (ListWithCustomSetting.isPrefCustomValue(chat.font)) {
            String originalName = ListWithCustomSetting.getPrefCustomValue(chat.font);
            File source = ListWithCustomSetting.getCustomFile(
                    context, ChatSettings.PREF_FONT, originalName);
            if (source != null && source.isFile()) {
                String ext = extensionOf(source.getName());
                chat.fontAsset = ThemeArchive.ASSET_PREFIX + "font" +
                        (ext.isEmpty() ? "" : "." + ext);
                theme.assets.put(ThemeInfo.ASSET_FONT, chat.fontAsset);
                if (theme.uuid != null) {
                    try {
                        copyFile(source, getThemeAssetFile(context, theme, chat.fontAsset));
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        MessageBuilder builder = MessageBuilder.getInstance(context);
        ThemeInfo.MessageLayoutSection layout = new ThemeInfo.MessageLayoutSection();
        layout.normal = serialize(builder.getMessageFormat());
        layout.mention = serialize(builder.getMentionMessageFormat());
        layout.action = serialize(builder.getActionMessageFormat());
        layout.actionMention = serialize(builder.getActionMentionMessageFormat());
        layout.notice = serialize(builder.getNoticeMessageFormat());
        layout.event = serialize(builder.getEventMessageFormat());
        layout.eventHostname = builder.getEventMessageShowHostname();
        layout.timeFormat = builder.getMessageTimeFormat().toPattern();
        // Legacy compatibility only: old TIARCA versions still read this field. The current
        // renderer always aligns a left-side clock automatically, so new presets advertise the
        // safe legacy value without treating it as a user preference.
        layout.timeFixedWidth = true;
        layout.timeRight = RightClockSettings.isEnabled(context);
        theme.messageLayout = layout;
    }

    static void apply(Context context, ThemeInfo theme) {
        // Applying an old supported preset must never inherit unrelated state from this device.
        ThemePresetMigrator.migrate(context, theme);

        SharedPreferences prefs = DefaultPreferences.get(context);
        SharedPreferences.Editor editor = prefs.edit();
        String appearancePreset = theme.ui == null ? null : theme.ui.appearancePreset;

        if (theme.ui != null && theme.ui.appBarCompactMode != null)
            editor.putString(ChatSettings.PREF_APPBAR_COMPACT_MODE,
                    theme.ui.appBarCompactMode);

        if (theme.chat != null) {
            if (theme.chat.fontSize != null)
                editor.putInt(ChatSettings.PREF_FONT_SIZE, theme.chat.fontSize);
            if (theme.chat.globalFontEnabled != null)
                editor.putBoolean(ChatSettings.PREF_GLOBAL_FONT_ENABLED,
                        theme.chat.globalFontEnabled);
            if (theme.chat.textAutocorrectEnabled != null)
                editor.putBoolean(ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED,
                        theme.chat.textAutocorrectEnabled);
            if (theme.chat.sendBoxAlwaysMultiline != null)
                editor.putBoolean(ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE,
                        theme.chat.sendBoxAlwaysMultiline);
            if (theme.chat.monochromeBots != null)
                editor.putBoolean(AutomatedSenderSettings.PREF_MONOCHROME_BOTS,
                        theme.chat.monochromeBots);
            if (theme.chat.messageAvatars != null)
                editor.putBoolean(MessageFormatSettings.PREF_MESSAGE_AVATARS,
                        theme.chat.messageAvatars);
            if (theme.chat.customAvatars != null)
                editor.putBoolean(MessageFormatSettings.PREF_MESSAGE_CUSTOM_AVATARS,
                        theme.chat.customAvatars);
            if (theme.chat.monochromeModeEvents != null)
                editor.putBoolean(EventDisplaySettings.PREF_MONOCHROME_MODE,
                        theme.chat.monochromeModeEvents);
            if (theme.chat.monochromeKickEvents != null)
                editor.putBoolean(EventDisplaySettings.PREF_MONOCHROME_KICK,
                        theme.chat.monochromeKickEvents);
            if (theme.chat.monochromeQuitEvents != null)
                editor.putBoolean(EventDisplaySettings.PREF_MONOCHROME_QUIT,
                        theme.chat.monochromeQuitEvents);
            if (theme.chat.monochromeJoinPartEvents != null)
                editor.putBoolean(EventDisplaySettings.PREF_MONOCHROME_JOIN_PART,
                        theme.chat.monochromeJoinPartEvents);
            if (theme.chat.font != null) {
                if (ListWithCustomSetting.isPrefCustomValue(theme.chat.font)) {
                    if (restoreCustomFont(context, theme))
                        editor.putString(ChatSettings.PREF_FONT, theme.chat.font);
                    else
                        editor.putString(ChatSettings.PREF_FONT, "default");
                } else {
                    editor.putString(ChatSettings.PREF_FONT, theme.chat.font);
                }
            }
        }
        editor.apply();

        ThemeInfo.MessageLayoutSection layout = theme.messageLayout;
        if (layout != null) {
            MessageBuilder builder = MessageBuilder.getInstance(context);
            trySetFormat(context, layout.normal, builder::setMessageFormat);
            trySetFormat(context, layout.mention, builder::setMentionMessageFormat);
            trySetFormat(context, layout.action, builder::setActionMessageFormat);
            trySetFormat(context, layout.actionMention, builder::setActionMentionMessageFormat);
            trySetFormat(context, layout.notice, builder::setNoticeMessageFormat);
            trySetFormat(context, layout.event, builder::setEventMessageFormat);
            if (layout.eventHostname != null)
                builder.setEventMessageShowHostname(layout.eventHostname);
            if (layout.timeFormat != null) {
                try {
                    builder.setMessageTimeFormat(layout.timeFormat);
                } catch (IllegalArgumentException ignored) {
                }
            }
            // timeFixedWidth is deliberately ignored. It remains in ThemeInfo only so that old
            // preset files can still be parsed and new files remain friendly to old app versions.
            builder.saveFormats();
            if (layout.timeRight != null)
                RightClockSettings.setEnabled(context, layout.timeRight);
        }

        if (appearancePreset != null)
            prefs.edit().putString(AppearancePresetManager.PREF_APPEARANCE_PRESET,
                    appearancePreset).apply();
    }

    /** Opens every currently available asset declared by the generic manifest. */
    static Map<String, InputStream> openAssetsForExport(Context context, ThemeInfo theme)
            throws IOException {
        if (theme == null)
            return Collections.emptyMap();
        Map<String, InputStream> result = new LinkedHashMap<>();
        for (String path : ThemeArchive.getDeclaredAssetPaths(theme)) {
            File source = null;
            if (theme.uuid != null) {
                File stored = getThemeAssetFile(context, theme, path);
                if (stored.isFile())
                    source = stored;
            }
            if (source == null && theme.chat != null && path.equals(theme.chat.fontAsset)
                    && ListWithCustomSetting.isPrefCustomValue(theme.chat.font)) {
                String name = ListWithCustomSetting.getPrefCustomValue(theme.chat.font);
                File active = ListWithCustomSetting.getCustomFile(
                        context, ChatSettings.PREF_FONT, name);
                if (active != null && active.isFile())
                    source = active;
            }
            if (source != null)
                result.put(path, new FileInputStream(source));
        }
        return result;
    }

    /** Stores all declared imported assets under this preset's private asset directory. */
    static void storeImportedAssets(Context context, ThemeInfo theme, Map<String, byte[]> assets)
            throws IOException {
        if (assets == null || assets.isEmpty() || theme == null || theme.uuid == null)
            return;
        for (Map.Entry<String, byte[]> entry : assets.entrySet()) {
            if (entry.getValue() == null)
                continue;
            File destination = getThemeAssetFile(context, theme, entry.getKey());
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                throw new IOException("Unable to create theme asset directory");
            try (FileOutputStream out = new FileOutputStream(destination)) {
                out.write(entry.getValue());
            }
        }
    }

    /** Compatibility helper for callers/tests from the v2 single-font API. */
    static InputStream openFontForExport(Context context, ThemeInfo theme) throws IOException {
        if (theme.chat == null || theme.chat.fontAsset == null)
            return null;
        String path = ThemeArchive.sanitizeAssetEntry(theme.chat.fontAsset);
        if (theme.uuid != null) {
            File stored = getThemeAssetFile(context, theme, path);
            if (stored.isFile())
                return new FileInputStream(stored);
        }
        if (ListWithCustomSetting.isPrefCustomValue(theme.chat.font)) {
            String name = ListWithCustomSetting.getPrefCustomValue(theme.chat.font);
            File active = ListWithCustomSetting.getCustomFile(context, ChatSettings.PREF_FONT, name);
            if (active != null && active.isFile())
                return new FileInputStream(active);
        }
        return null;
    }

    /** Compatibility helper for the v2 single-font import API. */
    static void storeImportedFont(Context context, ThemeInfo theme, byte[] data,
                                  String archiveEntry) throws IOException {
        if (data == null || archiveEntry == null)
            return;
        storeImportedAssets(context, theme,
                Collections.singletonMap(ThemeArchive.sanitizeAssetEntry(archiveEntry), data));
    }

    static void deleteAssets(Context context, ThemeInfo theme) {
        if (theme == null || theme.uuid == null)
            return;
        deleteRecursively(new File(new File(context.getFilesDir(), "themes/" + ASSET_DIR),
                theme.uuid.toString()));
    }

    static boolean isVisualPreferenceKey(String key) {
        return AppearancePresetManager.PREF_APPEARANCE_PRESET.equals(key)
                || ChatSettings.PREF_FONT.equals(key)
                || ChatSettings.PREF_FONT_SIZE.equals(key)
                || ChatSettings.PREF_GLOBAL_FONT_ENABLED.equals(key)
                || ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED.equals(key)
                || ChatSettings.PREF_APPBAR_COMPACT_MODE.equals(key)
                || ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE.equals(key)
                || AutomatedSenderSettings.PREF_MONOCHROME_BOTS.equals(key)
                || EventDisplaySettings.PREF_MONOCHROME_MODE.equals(key)
                || EventDisplaySettings.PREF_MONOCHROME_KICK.equals(key)
                || EventDisplaySettings.PREF_MONOCHROME_QUIT.equals(key)
                || EventDisplaySettings.PREF_MONOCHROME_JOIN_PART.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_MENTION.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_ACTION.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_ACTION_MENTION.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_NOTICE.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_EVENT.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_EVENT_HOSTNAME.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_TIME_FORMAT.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_AVATARS.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_CUSTOM_AVATARS.equals(key)
                || RightClockSettings.PREF_MESSAGE_TIME_RIGHT.equals(key);
    }

    private static boolean restoreCustomFont(Context context, ThemeInfo theme) {
        if (theme.chat == null || theme.chat.fontAsset == null || theme.uuid == null)
            return false;
        String originalName = ListWithCustomSetting.getPrefCustomValue(theme.chat.font);
        if (originalName == null)
            return false;
        File destination = ListWithCustomSetting.getCustomFile(
                context, ChatSettings.PREF_FONT, originalName);
        if (destination == null)
            return false;
        try {
            File source = getThemeAssetFile(context, theme, theme.chat.fontAsset);
            if (!source.isFile())
                return false;
            copyFile(source, destination);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static File getThemeAssetFile(Context context, ThemeInfo theme, String archiveEntry)
            throws IOException {
        String safeEntry = ThemeArchive.sanitizeAssetEntry(archiveEntry);
        String name = safeEntry.substring(ThemeArchive.ASSET_PREFIX.length());
        File themeDir = new File(new File(context.getFilesDir(), "themes/" + ASSET_DIR),
                theme.uuid.toString());
        return new File(themeDir, name);
    }

    private static String serialize(CharSequence value) {
        return SettingsHelper.getGson().toJson(MessageBuilder.spannableToJson(value));
    }

    private static void trySetFormat(Context context, String json, FormatSetter setter) {
        if (json == null)
            return;
        try {
            JsonObject object = SettingsHelper.getGson().fromJson(json, JsonObject.class);
            setter.set(MessageBuilder.spannableFromJson(context, object));
        } catch (RuntimeException ignored) {
        }
    }

    private static String getStringPreference(SharedPreferences prefs, String key) {
        Object defaultValue = SettingsHelper.getDefaultValue(key);
        String fallback = defaultValue instanceof String ? (String) defaultValue : null;
        return prefs.getString(key, fallback);
    }

    private static Integer getIntPreference(SharedPreferences prefs, String key) {
        Object defaultValue = SettingsHelper.getDefaultValue(key);
        int fallback = defaultValue instanceof Integer ? (Integer) defaultValue : -1;
        return prefs.getInt(key, fallback);
    }

    private static Boolean getBooleanPreference(SharedPreferences prefs, String key) {
        Object defaultValue = SettingsHelper.getDefaultValue(key);
        boolean fallback = defaultValue instanceof Boolean && (Boolean) defaultValue;
        return prefs.getBoolean(key, fallback);
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
    }

    private static void copyFile(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Unable to create directory");
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1)
                out.write(buffer, 0, count);
        }
    }

    private static void deleteRecursively(File file) {
        if (!file.exists())
            return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null)
                for (File child : children)
                    deleteRecursively(child);
        }
        file.delete();
    }

    private interface FormatSetter {
        void set(CharSequence value);
    }
}
