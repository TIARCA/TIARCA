package io.mrarm.irc.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.util.ArrayList;

import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.config.ServerConfigManager;

/** Reusable editor for an IgnoreEntry pre-filled from known IRC user data. */
public final class IgnoreUserDialog {

    private IgnoreUserDialog() { }

    public static void show(Context context, ServerConnectionInfo connection, String nick,
                            String user, String host) {
        if (connection == null || isBlank(nick))
            return;
        ServerConfigData server = ServerConfigManager.getInstance(context).findServer(connection.getUUID());
        if (server == null)
            return;
        ServerConfigData.IgnoreEntry entry = findEntry(server, nick, user, host);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_ignore_user, null);
        CheckBox nickBox = view.findViewById(R.id.nick);
        CheckBox userBox = view.findViewById(R.id.user);
        CheckBox hostBox = view.findViewById(R.id.host);
        CheckBox channelMessages = view.findViewById(R.id.channel_messages);
        CheckBox directMessages = view.findViewById(R.id.direct_messages);
        CheckBox channelNotices = view.findViewById(R.id.channel_notices);
        CheckBox directNotices = view.findViewById(R.id.direct_notices);
        RadioGroup durationGroup = view.findViewById(R.id.duration_group);
        RadioButton always = view.findViewById(R.id.always);
        RadioButton custom = view.findViewById(R.id.custom);
        EditText hours = view.findViewById(R.id.hours);

        nickBox.setChecked(entry == null || entry.nick != null);
        nickBox.setText(context.getString(R.string.ignore_nickname_value, nick));
        userBox.setEnabled(!isBlank(user));
        hostBox.setEnabled(!isBlank(host));
        if (userBox.isEnabled())
            userBox.setText(context.getString(R.string.ignore_ident_value, user));
        if (hostBox.isEnabled())
            hostBox.setText(context.getString(R.string.ignore_host_value, host));
        userBox.setChecked(entry != null && entry.user != null && userBox.isEnabled());
        hostBox.setChecked(entry != null && entry.host != null && hostBox.isEnabled());
        channelMessages.setChecked(entry == null || entry.matchChannelMessages);
        directMessages.setChecked(entry == null || entry.matchDirectMessages);
        channelNotices.setChecked(entry == null || entry.matchChannelNotices);
        directNotices.setChecked(entry == null || entry.matchDirectNotices);
        if (entry != null && entry.expiresAt > System.currentTimeMillis()) {
            custom.setChecked(true);
            long remainingHours = Math.max(1L, (entry.expiresAt - System.currentTimeMillis() + 3599999L) / 3600000L);
            hours.setText(String.valueOf(remainingHours));
            hours.setVisibility(View.VISIBLE);
        } else {
            always.setChecked(true);
        }
        durationGroup.setOnCheckedChangeListener((group, checkedId) ->
                hours.setVisibility(checkedId == R.id.custom ? View.VISIBLE : View.GONE));

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.ignore_user_title, nick))
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ignore, null);
        if (entry != null)
            builder.setNeutralButton(R.string.action_remove_ignore, null);
        AlertDialog dialog = builder.create();
        ServerConfigData.IgnoreEntry existing = entry;
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!nickBox.isChecked() && !userBox.isChecked() && !hostBox.isChecked()) {
                    Toast.makeText(context, R.string.ignore_select_identity, Toast.LENGTH_SHORT).show();
                    return;
                }
                long expiresAt = 0L;
                if (durationGroup.getCheckedRadioButtonId() == R.id.custom) {
                    try {
                        long value = Long.parseLong(hours.getText().toString().trim());
                        if (value <= 0L || value > Long.MAX_VALUE / 3600000L)
                            throw new NumberFormatException();
                        expiresAt = Math.addExact(System.currentTimeMillis(), value * 3600000L);
                    } catch (NumberFormatException | ArithmeticException e) {
                        hours.setError(context.getString(R.string.ignore_invalid_duration));
                        return;
                    }
                }
                ServerConfigData.IgnoreEntry target = existing;
                if (target == null) {
                    target = new ServerConfigData.IgnoreEntry();
                    if (server.ignoreList == null)
                        server.ignoreList = new ArrayList<>();
                    server.ignoreList.add(target);
                }
                target.nick = nickBox.isChecked() ? nick : null;
                target.user = userBox.isChecked() ? user : null;
                target.host = hostBox.isChecked() ? host : null;
                target.matchChannelMessages = channelMessages.isChecked();
                target.matchDirectMessages = directMessages.isChecked();
                target.matchChannelNotices = channelNotices.isChecked();
                target.matchDirectNotices = directNotices.isChecked();
                target.expiresAt = expiresAt;
                target.updateRegexes();
                if (save(context, server))
                    dialog.dismiss();
            });
            if (existing != null) {
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                            server.ignoreList.remove(existing);
                            if (save(context, server))
                                dialog.dismiss();
                        });
            }
        });
        dialog.show();
    }

    private static ServerConfigData.IgnoreEntry findEntry(ServerConfigData server, String nick,
                                                           String user, String host) {
        if (server.ignoreList == null)
            return null;
        for (ServerConfigData.IgnoreEntry entry : server.ignoreList) {
            if (entry.isExpired(System.currentTimeMillis()) || !same(entry.nick, nick))
                continue;
            if (entry.user != null && !same(entry.user, user))
                continue;
            if (entry.host != null && !same(entry.host, host))
                continue;
            return entry;
        }
        return null;
    }

    private static boolean save(Context context, ServerConfigData server) {
        try {
            ServerConfigManager.getInstance(context).saveServer(server);
            return true;
        } catch (IOException e) {
            Toast.makeText(context, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
