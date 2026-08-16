package io.mrarm.irc.dialog;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

import io.mrarm.irc.MainActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.config.SharingSettings;
import io.mrarm.irc.config.ChatSettings;

/** Shared attachment menu used by WHOIS and direct-message chats. */
public final class SimosnapSendMenu {

    private SimosnapSendMenu() { }

    public static void show(Activity activity, ServerConnectionInfo connection, String target) {
        if (!(activity instanceof MainActivity))
            return;
        MainActivity main = (MainActivity) activity;
        String[] all = activity.getResources().getStringArray(R.array.simosnap_send_options);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (SharingSettings.uploadsEnabled(activity)) {
            add(activity, SharingSettings.PREF_PICK_IMAGE, all[0], labels, actions,
                    () -> main.pickSimosnapFile(connection, target, "image/*"));
            add(activity, SharingSettings.PREF_TAKE_PHOTO, all[1], labels, actions,
                    () -> main.captureSimosnapMedia(connection, target, false));
            add(activity, SharingSettings.PREF_PICK_VIDEO, all[2], labels, actions,
                    () -> main.pickSimosnapFile(connection, target, "video/*"));
            add(activity, SharingSettings.PREF_RECORD_VIDEO, all[3], labels, actions,
                    () -> main.recordSimosnapVideo(connection, target));
            add(activity, SharingSettings.PREF_PICK_AUDIO, all[4], labels, actions,
                    () -> main.pickSimosnapFile(connection, target, "audio/*"));
            add(activity, SharingSettings.PREF_RECORD_VOICE, all[5], labels, actions,
                    () -> main.recordSimosnapVoice(connection, target));
            add(activity, SharingSettings.PREF_OTHER_FILES, all[6], labels, actions,
                    () -> main.pickSimosnapFile(connection, target, "*/*"));
        }
        if (ChatSettings.isDccSendVisible()) {
            labels.add(activity.getString(R.string.action_dcc_send));
            actions.add(() -> main.pickDccFile(connection, target));
        }
        if (labels.isEmpty())
            return;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.file_send_menu)
                .setItems(labels.toArray(new String[0]),
                        (dialog, which) -> {
                            main.openDirectConversationForSharing(connection, target);
                            actions.get(which).run();
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static void add(Activity activity, String preference, String label,
                            List<String> labels, List<Runnable> actions, Runnable action) {
        if (SharingSettings.enabled(activity, preference)) {
            labels.add(label);
            actions.add(action);
        }
    }
}
