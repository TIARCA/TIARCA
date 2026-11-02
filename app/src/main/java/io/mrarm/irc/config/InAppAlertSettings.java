package io.mrarm.irc.config;

import io.mrarm.chatlib.dto.MessageInfo;

/** Separate foreground alerts from Android notification channels and their permissions. */
public final class InAppAlertSettings {

    public static final String PREF_ENABLED = "in_app_message_alerts_enabled";
    public static final String PREF_SOUND = "in_app_message_alerts_sound";

    private InAppAlertSettings() {
    }

    public static boolean isEnabled() {
        return SettingsHelper.getPreferences().getBoolean(PREF_ENABLED, true);
    }

    public static boolean isSoundEnabled() {
        return SettingsHelper.getPreferences().getBoolean(PREF_SOUND, false);
    }

    public static boolean shouldAlert(boolean fromOtherUser, boolean direct,
                                      boolean mention, boolean playback,
                                      MessageInfo.MessageType type) {
        return fromOtherUser && !playback &&
                (type == MessageInfo.MessageType.NORMAL || type == MessageInfo.MessageType.ME) &&
                (direct || mention);
    }
}
