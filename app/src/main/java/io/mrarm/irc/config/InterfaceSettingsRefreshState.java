package io.mrarm.irc.config;

/**
 * Tracks interface-setting changes that require the visible chat UI to be rebound when the user
 * leaves the Interface settings screen. This is intentionally process-local: if Android kills the
 * process while Settings is open, MainActivity will be recreated from the persisted preferences
 * anyway.
 */
public final class InterfaceSettingsRefreshState {

    private static boolean sRefreshPending;

    private InterfaceSettingsRefreshState() {
    }

    public static synchronized void markRefreshPending() {
        sRefreshPending = true;
    }

    public static synchronized boolean consumeRefreshPending() {
        boolean pending = sRefreshPending;
        sRefreshPending = false;
        return pending;
    }

    public static boolean isRefreshRelevantPreference(String key) {
        return AppSettings.PREF_THEME.equals(key)
                || ChatSettings.PREF_FONT.equals(key)
                || ChatSettings.PREF_FONT_SIZE.equals(key)
                || ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED.equals(key)
                || ChatSettings.PREF_APPBAR_COMPACT_MODE.equals(key)
                || ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE.equals(key)
                || EventDisplaySettings.PREF_MONOCHROME_MODE.equals(key)
                || EventDisplaySettings.PREF_MONOCHROME_KICK.equals(key)
                || EventDisplaySettings.PREF_MONOCHROME_QUIT.equals(key)
                || EventDisplaySettings.PREF_MONOCHROME_JOIN_PART.equals(key)
                || AutomatedSenderSettings.PREF_MONOCHROME_BOTS.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_MENTION.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_ACTION.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_ACTION_MENTION.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_NOTICE.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_EVENT.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_FORMAT_EVENT_HOSTNAME.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_TIME_FORMAT.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_TIME_FIXED_WIDTH.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_AVATARS.equals(key)
                || MessageFormatSettings.PREF_MESSAGE_CUSTOM_AVATARS.equals(key)
                || RightClockSettings.PREF_MESSAGE_TIME_RIGHT.equals(key);
    }
}
