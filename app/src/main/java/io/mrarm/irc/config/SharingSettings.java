package io.mrarm.irc.config;

import android.content.Context;
import android.content.SharedPreferences;
import io.mrarm.irc.util.DefaultPreferences;

import io.mrarm.irc.config.ChatSettings;

/** Sharing preferences intentionally default to enabled for old-backup compatibility. */
public final class SharingSettings {

    public static final String PREF_UPLOADS = "sharing_uploads_enabled";
    public static final String PREF_PICK_IMAGE = "sharing_pick_image";
    public static final String PREF_TAKE_PHOTO = "sharing_take_photo";
    public static final String PREF_PICK_VIDEO = "sharing_pick_video";
    public static final String PREF_RECORD_VIDEO = "sharing_record_video";
    public static final String PREF_PICK_AUDIO = "sharing_pick_audio";
    public static final String PREF_RECORD_VOICE = "sharing_record_voice";
    public static final String PREF_OTHER_FILES = "sharing_other_files";
    public static final String PREF_INTERNAL_VIEWER = "sharing_internal_media_viewer";

    private SharingSettings() { }

    private static SharedPreferences prefs(Context context) {
        return DefaultPreferences.get(context.getApplicationContext());
    }

    public static boolean enabled(Context context, String key) {
        return prefs(context).getBoolean(key, true);
    }

    public static boolean uploadsEnabled(Context context) {
        return enabled(context, PREF_UPLOADS);
    }

    public static boolean internalViewerEnabled(Context context) {
        return enabled(context, PREF_INTERNAL_VIEWER);
    }

    public static boolean hasAnySendOption(Context context) {
        return ChatSettings.isDccSendVisible() || (uploadsEnabled(context) &&
                (enabled(context, PREF_PICK_IMAGE) ||
                enabled(context, PREF_TAKE_PHOTO) || enabled(context, PREF_PICK_VIDEO) ||
                enabled(context, PREF_RECORD_VIDEO) || enabled(context, PREF_PICK_AUDIO) ||
                enabled(context, PREF_RECORD_VOICE) || enabled(context, PREF_OTHER_FILES)));
    }
}
