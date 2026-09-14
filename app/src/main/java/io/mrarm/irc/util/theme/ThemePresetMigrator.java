package io.mrarm.irc.util.theme;

import android.content.Context;

import java.io.IOException;
import java.util.HashMap;

import io.mrarm.irc.config.ChatBackgroundSettings;
import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.util.MessageBuilder;

/**
 * Upgrades portable presets to the current schema without consulting device preferences.
 *
 * Compatibility defaults in this class are deliberately explicit. Once a format version has
 * shipped, changing application defaults must not change the result of importing an old preset.
 */
public final class ThemePresetMigrator {

    private static final String COMPAT_FONT = "default";
    private static final int COMPAT_FONT_SIZE = 12;
    private static final String COMPAT_APPBAR_MODE = "auto";
    private static final String COMPAT_TIME_FORMAT = "[HH:mm.ss]";

    private ThemePresetMigrator() {
    }

    /**
     * Migrates a supported preset in-place. Future-format presets are left untouched so a newer
     * producer can still be read on a best-effort basis without us inventing semantics for fields
     * that did not exist yet.
     *
     * @return true when the object was changed.
     */
    public static boolean migrate(Context context, ThemeInfo theme) {
        if (theme == null)
            return false;

        int sourceVersion = theme.formatVersion == null ? 1 : theme.formatVersion;
        if (sourceVersion > ThemeArchive.FORMAT_VERSION)
            return false;

        boolean changed = false;

        if (theme.base == null) {
            theme.base = "default_dark";
            changed = true;
        }
        if (theme.colors == null) {
            theme.colors = new HashMap<>();
            changed = true;
        }
        if (theme.properties == null) {
            theme.properties = new HashMap<>();
            changed = true;
        }
        if (theme.savedColors == null) {
            theme.savedColors = new java.util.ArrayList<>();
            changed = true;
        }
        if (theme.assets == null) {
            theme.assets = new HashMap<>();
            changed = true;
        }

        if (theme.ui == null) {
            theme.ui = new ThemeInfo.UiSection();
            changed = true;
        }
        if (theme.ui.appearancePreset == null) {
            theme.ui.appearancePreset = AppearancePreset.CUSTOM.getId();
            changed = true;
        }
        if (theme.ui.appBarCompactMode == null) {
            theme.ui.appBarCompactMode = COMPAT_APPBAR_MODE;
            changed = true;
        }

        if (theme.chat == null) {
            theme.chat = new ThemeInfo.ChatSection();
            changed = true;
        }
        changed |= setChatDefaults(theme.chat);

        if (theme.chat.fontAsset != null) {
            try {
                String safeFontAsset = ThemeArchive.sanitizeAssetEntry(theme.chat.fontAsset);
                if (!safeFontAsset.equals(theme.chat.fontAsset)) {
                    theme.chat.fontAsset = safeFontAsset;
                    changed = true;
                }
                if (!safeFontAsset.equals(theme.assets.get(ThemeInfo.ASSET_FONT))) {
                    theme.assets.put(ThemeInfo.ASSET_FONT, safeFontAsset);
                    changed = true;
                }
            } catch (IOException invalidAsset) {
                theme.chat.fontAsset = null;
                theme.assets.remove(ThemeInfo.ASSET_FONT);
                changed = true;
            }
        } else if (theme.assets.remove(ThemeInfo.ASSET_FONT) != null) {
            changed = true;
        }

        String backgroundAsset = theme.assets.get(ThemeInfo.ASSET_CHAT_BACKGROUND);
        if (backgroundAsset != null) {
            try {
                String safe = ThemeArchive.sanitizeAssetEntry(backgroundAsset);
                if (!safe.equals(backgroundAsset)) {
                    theme.assets.put(ThemeInfo.ASSET_CHAT_BACKGROUND, safe);
                    changed = true;
                }
            } catch (IOException invalidAsset) {
                theme.assets.remove(ThemeInfo.ASSET_CHAT_BACKGROUND);
                backgroundAsset = null;
                changed = true;
            }
        }
        if (ChatBackgroundSettings.TYPE_IMAGE.equals(theme.chat.backgroundType)
                && backgroundAsset == null) {
            // A preset cannot promise an image background if it does not declare the asset.
            theme.chat.backgroundType = ChatBackgroundSettings.TYPE_COLOR;
            changed = true;
        } else if (!ChatBackgroundSettings.TYPE_IMAGE.equals(theme.chat.backgroundType)
                && theme.assets.remove(ThemeInfo.ASSET_CHAT_BACKGROUND) != null) {
            changed = true;
        }

        if (theme.messageLayout == null) {
            theme.messageLayout = new ThemeInfo.MessageLayoutSection();
            changed = true;
        }
        changed |= setMessageLayoutDefaults(context, theme.messageLayout);

        if (!Integer.valueOf(ThemeArchive.FORMAT_VERSION).equals(theme.formatVersion)) {
            theme.formatVersion = ThemeArchive.FORMAT_VERSION;
            changed = true;
        }
        return changed;
    }

    private static boolean setChatDefaults(ThemeInfo.ChatSection chat) {
        boolean changed = false;
        if (chat.font == null) {
            chat.font = COMPAT_FONT;
            changed = true;
        }
        if (chat.fontSize == null) {
            chat.fontSize = COMPAT_FONT_SIZE;
            changed = true;
        }
        if (chat.globalFontEnabled == null) {
            chat.globalFontEnabled = false;
            changed = true;
        }
        if (chat.textAutocorrectEnabled == null) {
            chat.textAutocorrectEnabled = true;
            changed = true;
        }
        if (chat.sendBoxAlwaysMultiline == null) {
            chat.sendBoxAlwaysMultiline = false;
            changed = true;
        }
        if (chat.monochromeBots == null) {
            chat.monochromeBots = false;
            changed = true;
        }
        if (chat.messageAvatars == null) {
            chat.messageAvatars = false;
            changed = true;
        }
        if (chat.customAvatars == null) {
            chat.customAvatars = false;
            changed = true;
        }
        if (chat.monochromeModeEvents == null) {
            chat.monochromeModeEvents = false;
            changed = true;
        }
        if (chat.monochromeKickEvents == null) {
            chat.monochromeKickEvents = false;
            changed = true;
        }
        if (chat.monochromeQuitEvents == null) {
            chat.monochromeQuitEvents = false;
            changed = true;
        }
        if (chat.monochromeJoinPartEvents == null) {
            chat.monochromeJoinPartEvents = false;
            changed = true;
        }
        if (!ChatBackgroundSettings.TYPE_COLOR.equals(chat.backgroundType)
                && !ChatBackgroundSettings.TYPE_IMAGE.equals(chat.backgroundType)) {
            // v1-v3 had no chat-background setting: theme background is the frozen compatibility
            // default. backgroundColor intentionally remains null in this case.
            chat.backgroundType = ChatBackgroundSettings.TYPE_COLOR;
            changed = true;
        }
        if (!ChatBackgroundSettings.SCALE_FILL.equals(chat.backgroundScale)
                && !ChatBackgroundSettings.SCALE_FIT.equals(chat.backgroundScale)
                && !ChatBackgroundSettings.SCALE_STRETCH.equals(chat.backgroundScale)
                && !ChatBackgroundSettings.SCALE_MATRIX.equals(chat.backgroundScale)) {
            chat.backgroundScale = ChatBackgroundSettings.SCALE_FILL;
            changed = true;
        }
        if (!validFloat(chat.backgroundZoom, ChatBackgroundSettings.DEFAULT_ZOOM,
                ChatBackgroundSettings.MAX_ZOOM)) {
            chat.backgroundZoom = ChatBackgroundSettings.DEFAULT_ZOOM;
            changed = true;
        }
        if (!validFloat(chat.backgroundFocusX, 0f, 1f)) {
            chat.backgroundFocusX = ChatBackgroundSettings.DEFAULT_FOCUS;
            changed = true;
        }
        if (!validFloat(chat.backgroundFocusY, 0f, 1f)) {
            chat.backgroundFocusY = ChatBackgroundSettings.DEFAULT_FOCUS;
            changed = true;
        }
        if (chat.backgroundOpacity == null || chat.backgroundOpacity < 0
                || chat.backgroundOpacity > 100) {
            chat.backgroundOpacity = ChatBackgroundSettings.DEFAULT_OPACITY;
            changed = true;
        }
        return changed;
    }

    private static boolean validFloat(Float value, float min, float max) {
        return value != null && !Float.isNaN(value) && !Float.isInfinite(value)
                && value >= min && value <= max;
    }

    private static boolean setMessageLayoutDefaults(Context context,
                                                     ThemeInfo.MessageLayoutSection layout) {
        boolean changed = false;
        if (layout.normal == null) {
            layout.normal = serialize(MessageBuilder.buildDefaultMessageFormat(context));
            changed = true;
        }
        if (layout.mention == null) {
            layout.mention = serialize(MessageBuilder.buildDefaultMentionMessageFormat(context));
            changed = true;
        }
        if (layout.action == null) {
            layout.action = serialize(MessageBuilder.buildDefaultActionMessageFormat(context));
            changed = true;
        }
        if (layout.actionMention == null) {
            layout.actionMention = serialize(
                    MessageBuilder.buildDefaultActionMentionMessageFormat(context));
            changed = true;
        }
        if (layout.notice == null) {
            layout.notice = serialize(MessageBuilder.buildDefaultNoticeMessageFormat(context));
            changed = true;
        }
        if (layout.event == null) {
            layout.event = serialize(MessageBuilder.buildDefaultEventMessageFormat(context));
            changed = true;
        }
        if (layout.eventHostname == null) {
            layout.eventHostname = false;
            changed = true;
        }
        if (layout.timeFormat == null) {
            layout.timeFormat = COMPAT_TIME_FORMAT;
            changed = true;
        }
        if (layout.timeFixedWidth == null) {
            // Legacy readers still understand this field. Current rendering is automatic.
            layout.timeFixedWidth = true;
            changed = true;
        }
        if (layout.timeRight == null) {
            layout.timeRight = false;
            changed = true;
        }
        return changed;
    }

    private static String serialize(CharSequence value) {
        return SettingsHelper.getGson().toJson(MessageBuilder.spannableToJson(value));
    }
}
