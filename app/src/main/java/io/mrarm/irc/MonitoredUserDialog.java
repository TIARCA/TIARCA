package io.mrarm.irc;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import io.mrarm.chatlib.irc.ServerConnectionApi;
import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.irc.MonitoredUsersManager;

/** Shared add/edit entry point, also suitable for a future nickname context action. */
public final class MonitoredUserDialog {
    private MonitoredUserDialog() { }

    public static void show(Context context, ServerConnectionInfo connection, ServerConfigData.MonitoredUser existing,
                            Runnable onChanged) {
        LinearLayout content = new LinearLayout(context);
        int padding = (int) (24 * context.getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        content.setOrientation(LinearLayout.VERTICAL);
        EditText nick = new EditText(context);
        nick.setHint(R.string.monitor_user_nickname);
        nick.setSingleLine(true);
        nick.setInputType(InputType.TYPE_CLASS_TEXT);
        if (existing != null) {
            nick.setText(existing.currentNick == null ? existing.nick : existing.currentNick);
            nick.setEnabled(false);
        }
        CheckBox presence = new CheckBox(context);
        presence.setText(R.string.monitor_user_presence);
        presence.setChecked(true);
        presence.setEnabled(false);
        CheckBox notifyOnline = new CheckBox(context);
        notifyOnline.setText(R.string.monitor_user_notify_online);
        notifyOnline.setChecked(existing != null && existing.notifyOnline);
        CheckBox notifyOffline = new CheckBox(context);
        notifyOffline.setText(R.string.monitor_user_notify_offline);
        notifyOffline.setChecked(existing != null && existing.notifyOffline);
        content.addView(nick);
        content.addView(presence);
        content.addView(notifyOnline);
        content.addView(notifyOffline);

        String titleNick = existing == null ? "" : (existing.currentNick == null ? existing.nick : existing.currentNick);
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.monitor_user_title, titleNick))
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, null);
        if (existing != null) builder.setNeutralButton(R.string.action_remove_monitored_user, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String target = nick.getText().toString().trim();
                if (target.isEmpty()) { nick.setError(context.getString(R.string.monitor_user_nickname)); return; }
                ServerConnectionApi api = connection.getApiInstance() instanceof ServerConnectionApi
                        ? (ServerConnectionApi) connection.getApiInstance() : null;
                if (api == null) { Toast.makeText(context, R.string.error_generic, Toast.LENGTH_SHORT).show(); return; }
                MonitoredUsersManager manager = connection.getMonitoredUsersManager();
                if (existing == null)
                    manager.addMonitoredUser(api.getServerConnectionData(), target,
                            notifyOnline.isChecked(), notifyOffline.isChecked());
                else
                    manager.updateNotificationPreferences(api.getServerConnectionData(), target,
                            notifyOnline.isChecked(), notifyOffline.isChecked());
                onChanged.run();
                dialog.dismiss();
            });
            if (existing != null) dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                ServerConnectionApi api = connection.getApiInstance() instanceof ServerConnectionApi
                        ? (ServerConnectionApi) connection.getApiInstance() : null;
                if (api != null) connection.getMonitoredUsersManager().removeMonitoredUser(
                        api.getServerConnectionData(), existing.currentNick == null ? existing.nick : existing.currentNick);
                onChanged.run();
                dialog.dismiss();
            });
        });
        dialog.show();
    }
}
