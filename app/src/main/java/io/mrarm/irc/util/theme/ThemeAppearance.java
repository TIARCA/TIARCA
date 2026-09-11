package io.mrarm.irc.util.theme;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.config.MessageFormatSettings;
import io.mrarm.irc.config.RightClockSettings;
import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.setting.ListWithCustomSetting;
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.MessageBuilder;

/** Bridges sectioned irctheme data and the visual preferences used by the app. */
final class ThemeAppearance {

    private static final String ASSET_DIR = "assets";

    private ThemeAppearance() {
    }

    static void capture(Context context, ThemeInfo theme) {
        SharedPreferences prefs = DefaultPreferences.get(context);
        theme.formatVersion = ThemeArchive.FORMAT_VERSION;

        ThemeInfo.UiSection ui = new ThemeInfo.UiSection();
        ui.appBarCompactMode = getStringPreference(prefs, ChatSettings.PREF_APPBAR_COMPACT_MODE);
        theme.ui = ui;

        ThemeInfo.ChatSection chat = new ThemeInfo.ChatSection();
        chat.font = getStringPreference(prefs, ChatSettings.PREF_FONT);
        chat.fontSize = getIntPreference(prefs, ChatSettings.PREF_FONT_SIZE);
        theme.chat = chat;

        if (ListWithCustomSetting.isPrefCustomValue(chat.font)) {
            String originalName = ListWithCustomSetting.getPrefCustomValue(chat.font);
            File source = ListWithCustomSetting.getCustomFile(
                    context, ChatSettings.PREF_FONT, originalName);
            if (source != null && source.isFile()) {
                String ext = extensionOf(source.getName());
                chat.fontAsset = ThemeArchive.ASSET_PREFIX + "font" +
                        (ext.isEmpty() ? "" : "." + ext);
                if (theme.uuid != null) {
                    try {
                        copyFile(source, getThemeAssetFile(context, theme, chat.fontAsset));
                    } catch (IOException ignored) {
                        // Export can still use the active preference file directly.
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
        layout.timeFixedWidth = builder.isMessageTimeFixedWidth();
        layout.timeRight = RightClockSettings.isEnabled(context);
        theme.messageLayout = layout;
    }

    static void apply(Context context, ThemeInfo theme) {
        SharedPreferences prefs = DefaultPreferences.get(context);
        SharedPreferences.Editor editor = prefs.edit();

        if (theme.ui != null && theme.ui.appBarCompactMode != null)
            editor.putString(ChatSettings.PREF_APPBAR_COMPACT_MODE,
                    theme.ui.appBarCompactMode);

        if (theme.chat != null) {
            if (theme.chat.fontSize != null)
                editor.putInt(ChatSettings.PREF_FONT_SIZE, theme.chat.fontSize);
            if (theme.chat.font != null) {
                if (ListWithCustomSetting.isPrefCustomValue(theme.chat.font)) {
                    if (restoreCustomFont(context, theme))
                        editor.putString(ChatSettings.PREF_FONT, theme.chat.font);
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
            if (layout.timeFixedWidth != null)
                builder.setMessageTimeFixedWidth(layout.timeFixedWidth);
            builder.saveFormats();
            if (layout.timeRight != null)
                RightClockSettings.setEnabled(context, layout.timeRight);
        }
    }

    static InputStream openFontForExport(Context context, ThemeInfo theme) throws IOException {
        if (theme.chat == null || theme.chat.fontAsset == null)
            return null;

        if (theme.uuid != null) {
            File stored = getThemeAssetFile(context, theme, theme.chat.fontAsset);
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

    static void storeImportedFont(Context context, ThemeInfo theme, byte[] data,
                                  String archiveEntry) throws IOException {
        if (data == null || archiveEntry == null || theme.uuid == null)
            return;
        File destination = getThemeAssetFile(context, theme, archiveEntry);
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Unable to create theme asset directory");
        try (FileOutputStream out = new FileOutputStream(destination)) {
            out.write(data);
        }
    }

    static void deleteAssets(Context context, ThemeInfo theme) {
        if (theme == null || theme.uuid == null)
            return;
        deleteRecursively(new File(new File(context.getFilesDir(), "themes/" + ASSET_DIR),
                theme.uuid.toString()));
    }

    static boolean isVisualPreferenceKey(String key) {
        return ChatSettings.PREF_FONT.equals(key)
                || ChatSettings.PREF_FONT_SIZE.equals(key)
                || ChatSettings.PREF_APPBAR_COMPACT_MODE.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_MENTION.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_ACTION.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_ACTION_MENTION.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_NOTICE.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_EVENT.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_EVENT_HOSTNAME.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_TIME_FORMAT.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_TIME_FIXED_WIDTH.equals(key)
                || RightClockSettings.PREF_MESSAGE_TIME_RIGHT.equals(key);
    }

    private static boolean restoreCustomFont(Context context, ThemeInfo theme) {
        if (theme.chat == null || theme.chat.fontAsset == null || theme.uuid == null)
            return false;
        String originalName = ListWithCustomSetting.getPrefCustomValue(theme.chat.font);
        if (originalName == null)
            return false;
        File source = getThemeAssetFile(context, theme, theme.chat.fontAsset);
        File destination = ListWithCustomSetting.getCustomFile(
                context, ChatSettings.PREF_FONT, originalName);
        if (!source.isFile() || destination == null)
            return false;
        try {
            copyFile(source, destination);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static File getThemeAssetFile(Context context, ThemeInfo theme, String archiveEntry) {
        String name = archiveEntry.substring(ThemeArchive.ASSET_PREFIX.length());
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
