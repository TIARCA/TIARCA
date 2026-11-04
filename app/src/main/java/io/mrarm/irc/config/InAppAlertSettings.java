package io.mrarm.irc.config;

import io.mrarm.chatlib.dto.MessageInfo;

/** Separate foreground alerts from Android notification channels and their permissions. */
public final class InAppAlertSettings {

    public static final String PREF_ENABLED = "in_app_message_alerts_enabled";
    public static final String PREF_SOUND = "in_app_message_alerts_sound";
    public static final String PREF_PRIVATE_MESSAGES = "in_app_message_alerts_private";
    public static final String PREF_MENTIONS = "in_app_message_alerts_mentions";

    private InAppAlertSettings() {
    }

    public static boolean isEnabled() {
        return SettingsHelper.getPreferences().getBoolean(PREF_ENABLED, true);
    }

    public static boolean isSoundEnabled() {
        return SettingsHelper.getPreferences().getBoolean(PREF_SOUND, false);
    }

    /** Both categories default on for existing installations and older restored backups. */
    public static boolean isPrivateMessagesEnabled() {
        return SettingsHelper.getPreferences().getBoolean(PREF_PRIVATE_MESSAGES, true);
    }

    public static boolean isMentionsEnabled() {
        return SettingsHelper.getPreferences().getBoolean(PREF_MENTIONS, true);
    }

    public static boolean shouldAlert(boolean fromOtherUser, boolean direct,
                                      boolean mention, boolean playback,
                                      MessageInfo.MessageType type,
                                      boolean privateMessagesEnabled, boolean mentionsEnabled) {
        if (!fromOtherUser || playback ||
                (type != MessageInfo.MessageType.NORMAL && type != MessageInfo.MessageType.ME))
            return false;
        return direct ? privateMessagesEnabled : mention && mentionsEnabled;
    }
}
