package io.mrarm.irc.util.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import io.mrarm.irc.MessageFormatSettingsActivity;
import io.mrarm.irc.config.AppSettings;
import io.mrarm.irc.config.AutomatedSenderSettings;
import io.mrarm.irc.config.ChatBackgroundSettings;
import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.config.EventDisplaySettings;
import io.mrarm.irc.config.MessageFormatSettings;
import io.mrarm.irc.config.RightClockSettings;
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.MessageBuilder;

/** Applies complete visual presets to the existing appearance preferences. */
public final class AppearancePresetManager {

    public static final String PREF_APPEARANCE_PRESET = "appearance_preset";
    public static final String FONT_ATKINSON_HYPERLEGIBLE_NEXT =
            "atkinson_hyperlegible_next";

    private static AppearancePresetManager instance;

    public static synchronized AppearancePresetManager getInstance(Context context) {
        if (instance == null)
            instance = new AppearancePresetManager(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final SharedPreferences preferences;
    private boolean applyingPreset;

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (preferences, key) -> {
                if (MessageFormatSettings.PREF_MESSAGE_TIME_FIXED_WIDTH.equals(key)) {
                    if (preferences.contains(key))
                        preferences.edit().remove(key).apply();
                    return;
                }
                if (applyingPreset || PREF_APPEARANCE_PRESET.equals(key)
                        || !isPresetVisualPreference(key))
                    return;
                if (getCurrentPreset() != AppearancePreset.CUSTOM)
                    preferences.edit().putString(PREF_APPEARANCE_PRESET,
                            AppearancePreset.CUSTOM.getId()).apply();
            };

    AppearancePresetManager(Context context) {
        this(context, isFreshInstall(context));
    }

    AppearancePresetManager(Context context, boolean freshInstall) {
        this.context = context.getApplicationContext();
        preferences = DefaultPreferences.get(this.context);
        migrateLegacyFixedWidthPreference();
        initializeStoredPreset(freshInstall);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    private static boolean isFreshInstall(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.firstInstallTime == packageInfo.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void migrateLegacyFixedWidthPreference() {
        if (preferences.contains(MessageFormatSettings.PREF_MESSAGE_TIME_FIXED_WIDTH))
            preferences.edit().remove(MessageFormatSettings.PREF_MESSAGE_TIME_FIXED_WIDTH).apply();
    }

    private void initializeStoredPreset(boolean freshInstall) {
        if (preferences.contains(PREF_APPEARANCE_PRESET))
            return;
        if (freshInstall) {
            applyPreset(AppearancePreset.GRAPHIC_LIGHT);
            return;
        }
        AppearancePreset initial = detectUnmodifiedHistoricalDefault();
        preferences.edit().putString(PREF_APPEARANCE_PRESET, initial.getId()).apply();
    }

    private AppearancePreset detectUnmodifiedHistoricalDefault() {
        String[] nonThemeVisualKeys = {
                ChatSettings.PREF_FONT,
                ChatSettings.PREF_FONT_SIZE,
                ChatSettings.PREF_APPBAR_COMPACT_MODE,
                ChatBackgroundSettings.PREF_TYPE,
                ChatBackgroundSettings.PREF_COLOR,
                ChatBackgroundSettings.PREF_SCALE,
                EventDisplaySettings.PREF_MONOCHROME_MODE,
                EventDisplaySettings.PREF_MONOCHROME_KICK,
                EventDisplaySettings.PREF_MONOCHROME_QUIT,
                EventDisplaySettings.PREF_MONOCHROME_JOIN_PART,
                AutomatedSenderSettings.PREF_MONOCHROME_BOTS,
                MessageFormatSettings.PREF_MESSAGE_FORMAT,
                MessageFormatSettings.PREF_MESSAGE_FORMAT_MENTION,
                MessageFormatSettings.PREF_MESSAGE_FORMAT_ACTION,
                MessageFormatSettings.PREF_MESSAGE_FORMAT_ACTION_MENTION,
                MessageFormatSettings.PREF_MESSAGE_FORMAT_NOTICE,
                MessageFormatSettings.PREF_MESSAGE_FORMAT_EVENT,
                MessageFormatSettings.PREF_MESSAGE_FORMAT_EVENT_HOSTNAME,
                MessageFormatSettings.PREF_MESSAGE_TIME_FORMAT,
                MessageFormatSettings.PREF_MESSAGE_AVATARS,
                MessageFormatSettings.PREF_MESSAGE_CUSTOM_AVATARS,
                RightClockSettings.PREF_MESSAGE_TIME_RIGHT
        };
        for (String key : nonThemeVisualKeys) {
            if (preferences.contains(key))
                return AppearancePreset.CUSTOM;
        }
        String theme = preferences.getString(AppSettings.PREF_THEME, "default_dark");
        if ("default".equals(theme))
            return AppearancePreset.IRC_LIGHT;
        if ("default_dark".equals(theme))
            return AppearancePreset.IRC_DARK;
        return AppearancePreset.CUSTOM;
    }

    public AppearancePreset getCurrentPreset() {
        return AppearancePreset.fromId(preferences.getString(
                PREF_APPEARANCE_PRESET, AppearancePreset.CUSTOM.getId()));
    }

    public boolean isApplyingPreset() {
        return applyingPreset;
    }

    public void applyPreset(AppearancePreset preset) {
        if (preset == null || !preset.isApplicable())
            return;
        applyingPreset = true;
        try {
            applyTheme(preset);
            applySimplePreferences(preset);
            applyMessageFormats(preset);
            preferences.edit().putString(PREF_APPEARANCE_PRESET, preset.getId()).apply();
        } finally {
            applyingPreset = false;
        }
    }

    private void applyTheme(AppearancePreset preset) {
        String themeId;
        switch (preset) {
            case IRC_LIGHT:
            case GRAPHIC_LIGHT:
                themeId = "default";
                break;
            case TERMINAL:
                themeId = "terminal_dark";
                break;
            case COLOR_BLIND:
                themeId = "high_contrast_light";
                break;
            case IRC_DARK:
            case GRAPHIC_DARK:
            default:
                themeId = "default_dark";
                break;
        }
        preferences.edit().putString(AppSettings.PREF_THEME, themeId).apply();
    }

    private void applySimplePreferences(AppearancePreset preset) {
        boolean graphic = preset == AppearancePreset.GRAPHIC_LIGHT
                || preset == AppearancePreset.GRAPHIC_DARK;
        boolean terminal = preset == AppearancePreset.TERMINAL;
        boolean colorBlind = preset == AppearancePreset.COLOR_BLIND;

        String font = terminal ? "monospace" : colorBlind
                ? FONT_ATKINSON_HYPERLEGIBLE_NEXT : "default";
        int fontSize = colorBlind ? 16 : graphic ? 14 : terminal ? 10 : 12;
        boolean avatars = graphic || colorBlind;
        boolean monochrome = terminal || colorBlind;

        preferences.edit()
                .putString(ChatSettings.PREF_FONT, font)
                .putInt(ChatSettings.PREF_FONT_SIZE, fontSize)
                .putString(ChatSettings.PREF_APPBAR_COMPACT_MODE, "auto")
                .putBoolean(AutomatedSenderSettings.PREF_MONOCHROME_BOTS, monochrome)
                .putBoolean(EventDisplaySettings.PREF_MONOCHROME_MODE, monochrome)
                .putBoolean(EventDisplaySettings.PREF_MONOCHROME_KICK, monochrome)
                .putBoolean(EventDisplaySettings.PREF_MONOCHROME_QUIT, monochrome)
                .putBoolean(EventDisplaySettings.PREF_MONOCHROME_JOIN_PART, monochrome)
                .putBoolean(MessageFormatSettings.PREF_MESSAGE_AVATARS, avatars)
                .putBoolean(MessageFormatSettings.PREF_MESSAGE_CUSTOM_AVATARS, avatars)
                .putBoolean(RightClockSettings.PREF_MESSAGE_TIME_RIGHT, graphic)
                .apply();
        ChatBackgroundSettings.resetToThemeDefault(context);
    }

    private void applyMessageFormats(AppearancePreset preset) {
        boolean graphic = preset == AppearancePreset.GRAPHIC_LIGHT
                || preset == AppearancePreset.GRAPHIC_DARK;
        boolean terminal = preset == AppearancePreset.TERMINAL;
        boolean colorBlind = preset == AppearancePreset.COLOR_BLIND;
        boolean prefix = terminal || colorBlind;
        boolean underlineMentions = terminal || colorBlind;

        MessageBuilder builder = MessageBuilder.getInstance(context);
        int normalLayout = graphic ? 2 : terminal ? 1 : 0;
        CharSequence normal = MessageFormatSettingsActivity.buildPresetMessageFormat(
                context, normalLayout, false, prefix);
        CharSequence mention = MessageFormatSettingsActivity.buildPresetMessageFormat(
                context, normalLayout, true, prefix);
        CharSequence action = MessageFormatSettingsActivity.buildActionPresetMessageFormat(
                context, 0, false, prefix);
        CharSequence actionMention = MessageFormatSettingsActivity
                .buildActionPresetMessageFormat(context, 0, true, prefix);
        CharSequence notice = MessageFormatSettingsActivity.buildNoticePresetMessageFormat(
                context, terminal ? 1 : 0, prefix);

        normal = addSenderStyle(normal, true, false);
        mention = addSenderStyle(mention, true, underlineMentions);
        action = addSenderStyle(action, true, false);
        actionMention = addSenderStyle(actionMention, true, underlineMentions);
        notice = addSenderStyle(notice, true, false);

        builder.setMessageFormat(normal);
        builder.setMentionMessageFormat(mention);
        builder.setActionMessageFormat(action);
        builder.setActionMentionMessageFormat(actionMention);
        builder.setNoticeMessageFormat(notice);
        builder.setEventMessageFormat(MessageFormatSettingsActivity
                .buildEventPresetMessageFormat(context, graphic || colorBlind ? 1 : 0));
        builder.setEventMessageShowHostname(false);
        builder.setMessageTimeFormat(colorBlind ? "HH:mm" : terminal ? "[HH:mm:ss]"
                : graphic ? "HH:mm" : "[HH:mm.ss]");
        builder.setMessageAvatars(graphic || colorBlind);
        builder.setMessageCustomAvatars(graphic || colorBlind);
        builder.saveFormats();
    }

    private static CharSequence addSenderStyle(CharSequence source, boolean bold,
                                               boolean underline) {
        SpannableString result = new SpannableString(source);
        for (MessageBuilder.MetaChipSpan chip : result.getSpans(0, result.length(),
                MessageBuilder.MetaChipSpan.class)) {
            if (chip.getType() != MessageBuilder.MetaChipSpan.TYPE_SENDER)
                continue;
            int start = result.getSpanStart(chip);
            int end = result.getSpanEnd(chip);
            if (bold)
                result.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                        MessageBuilder.FORMAT_SPAN_FLAGS);
            if (underline)
                result.setSpan(new UnderlineSpan(), start, end,
                        MessageBuilder.FORMAT_SPAN_FLAGS);
        }
        return result;
    }

    public static boolean isPresetVisualPreference(String key) {
        if (PREF_APPEARANCE_PRESET.equals(key)
                || ChatSettings.PREF_GLOBAL_FONT_ENABLED.equals(key)
                || ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED.equals(key)
                || ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE.equals(key))
            return false;
        return AppSettings.PREF_THEME.equals(key) || ThemeAppearance.isVisualPreferenceKey(key);
    }

    void closeForTests() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
    }
}
