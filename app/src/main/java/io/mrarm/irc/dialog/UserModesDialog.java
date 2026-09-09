package io.mrarm.irc.dialog;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.CommandHandlerList;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.handlers.ModeCommandHandler;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.irc.ChannelModeSnapshotHandler;
import io.mrarm.irc.irc.IrcModeRegistry;

/** Server-tab editor for the current user's modes. */
public final class UserModesDialog {
    private static final long LOAD_TIMEOUT_MS = 8000L;
    private final Activity activity;
    private final ServerConnectionInfo connection;

    public UserModesDialog(Activity activity, ServerConnectionInfo connection) {
        this.activity = activity;
        this.connection = connection;
    }

    public void show() {
        if (!(connection.getApiInstance() instanceof IRCConnection)) {
            fail(); return;
        }
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        String nick = irc.getServerConnectionData().getUserNick();
        if (nick == null || nick.isEmpty()) { fail(); return; }

        CommandHandlerList handlers = irc.getServerConnectionData().getCommandHandlerList();
        ChannelModeSnapshotHandler handler = handlers.getHandler(ChannelModeSnapshotHandler.class);
        if (handler == null) {
            CommandHandler delegate = handlers.getHandler(ModeCommandHandler.class);
            handler = new ChannelModeSnapshotHandler(delegate);
            if (delegate != null) handlers.unregisterHandler(delegate);
            handlers.registerHandler(handler);
        }

        AlertDialog loading = new AlertDialog.Builder(activity)
                .setTitle(R.string.user_modes_title)
                .setMessage(R.string.user_modes_loading)
                .setNegativeButton(R.string.action_cancel, null).create();
        loading.show();
        final boolean[] completed = {false};
        final ChannelModeSnapshotHandler finalHandler = handler;
        ChannelModeSnapshotHandler.Callback callback = snapshot -> activity.runOnUiThread(() -> {
            if (completed[0] || activity.isFinishing()) return;
            completed[0] = true;
            loading.dismiss();
            showEditor(irc, nick, snapshot);
        });
        handler.requestUserModes(nick, callback);
        loading.setOnCancelListener(d -> {
            completed[0] = true;
            finalHandler.cancelUserModes(callback);
        });
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (completed[0] || activity.isFinishing()) return;
            completed[0] = true;
            finalHandler.cancelUserModes(callback);
            loading.dismiss();
            Toast.makeText(activity, R.string.user_modes_timeout, Toast.LENGTH_LONG).show();
        }, LOAD_TIMEOUT_MS);
        irc.sendCommandRaw("MODE " + nick, null, error -> activity.runOnUiThread(() -> {
            if (completed[0]) return;
            completed[0] = true;
            finalHandler.cancelUserModes(callback);
            loading.dismiss(); fail();
        }));
    }

    private void showEditor(IRCConnection irc, String nick, ChannelModeSnapshotHandler.Snapshot snapshot) {
        IrcModeRegistry registry = IrcModeRegistry.forConnection(activity, connection, irc);
        Set<Character> available = new HashSet<>(snapshot.active);
        String advertised = IrcModeRegistry.advertisedUserModes(irc);
        if (advertised != null) {
            for (int i = 0; i < advertised.length(); i++) {
                char c = advertised.charAt(i);
                if (Character.isLetter(c)) available.add(c);
            }
        }
        List<Character> modes = new ArrayList<>(available);
        Collections.sort(modes);
        int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, padding / 2);
        TextView profile = new TextView(activity);
        profile.setText(activity.getString(R.string.user_modes_profile, registry.getProfileLabel()));
        profile.setPadding(0, 0, 0, padding / 2);
        content.addView(profile);
        Map<Character, CheckBox> boxes = new HashMap<>();
        for (char mode : modes) {
            CheckBox box = new CheckBox(activity);
            box.setText("+" + mode + " - " + registry.description(IrcModeRegistry.Target.USER, mode));
            box.setChecked(snapshot.active.contains(mode));
            box.setEnabled(registry.isEditable(IrcModeRegistry.Target.USER, mode));
            boxes.put(mode, box);
            content.addView(box);
        }
        if (modes.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(R.string.user_modes_none);
            content.addView(empty);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.user_modes_title)
                .setView(scroll)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_apply, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    List<String> changes = new ArrayList<>();
                    for (Map.Entry<Character, CheckBox> entry : boxes.entrySet()) {
                        if (!entry.getValue().isEnabled()) continue;
                        boolean before = snapshot.active.contains(entry.getKey());
                        boolean after = entry.getValue().isChecked();
                        if (before != after) changes.add((after ? "+" : "-") + entry.getKey());
                    }
                    if (changes.isEmpty()) {
                        Toast.makeText(activity, R.string.channel_modes_no_changes, Toast.LENGTH_SHORT).show();
                        dialog.dismiss(); return;
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.operator_confirm_title)
                            .setMessage(android.text.TextUtils.join("\n", changes))
                            .setNegativeButton(R.string.action_cancel, null)
                            .setPositiveButton(R.string.action_apply, (d, which) -> {
                                for (String change : changes)
                                    irc.sendCommandRaw("MODE " + nick + " " + change, null, null);
                                Toast.makeText(activity, R.string.operator_command_sent, Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }).show();
                }));
        dialog.show();
    }

    private void fail() {
        Toast.makeText(activity, R.string.user_modes_failed, Toast.LENGTH_LONG).show();
    }
}
