package io.mrarm.irc.dialog;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.dto.HostInfoMessageInfo;
import io.mrarm.chatlib.dto.StatusMessageInfo;
import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.CommandHandlerList;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.handlers.ModeCommandHandler;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.irc.ChannelModeSnapshotHandler;

/** Operator UI for current channel modes advertised by the IRC server. */
public final class ChannelModesDialog {

    private final Activity activity;
    private final ServerConnectionInfo connection;
    private final String channel;
    private ServerProfile serverProfile = ServerProfile.GENERIC;
    private static final long LOAD_TIMEOUT_MS = 8000L;

    public ChannelModesDialog(Activity activity, ServerConnectionInfo connection, String channel) {
        this.activity = activity;
        this.connection = connection;
        this.channel = channel;
    }

    public void show() {
        if (!(connection.getApiInstance() instanceof IRCConnection) || channel == null ||
                channel.isEmpty()) {
            Toast.makeText(activity, R.string.channel_modes_failed, Toast.LENGTH_LONG).show();
            return;
        }
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        CommandHandlerList handlers = irc.getServerConnectionData().getCommandHandlerList();
        ChannelModeSnapshotHandler handler = handlers.getHandler(ChannelModeSnapshotHandler.class);
        if (handler == null) {
            CommandHandler delegate = handlers.getHandler(ModeCommandHandler.class);
            handler = new ChannelModeSnapshotHandler(delegate);
            if (delegate != null)
                handlers.unregisterHandler(delegate);
            handlers.registerHandler(handler);
        }
        AlertDialog loading = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.channel_modes_title, channel))
                .setMessage(R.string.channel_modes_loading)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        loading.show();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        final boolean[] completed = {false};
        final ChannelModeSnapshotHandler finalHandler = handler;
        ChannelModeSnapshotHandler.Callback callback = snapshot ->
                activity.runOnUiThread(() -> {
                    if (completed[0] || activity.isFinishing())
                        return;
                    completed[0] = true;
                    loading.dismiss();
                    try {
                        showEditor(snapshot);
                    } catch (RuntimeException error) {
                        Log.e("ChannelModesDialog", "Unable to open channel modes", error);
                        Toast.makeText(activity, R.string.channel_modes_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
        handler.request(channel, callback);
        loading.setOnCancelListener(dialog -> {
            completed[0] = true;
            finalHandler.cancel(channel, callback);
        });
        mainHandler.postDelayed(() -> {
            if (completed[0] || activity.isFinishing())
                return;
            completed[0] = true;
            finalHandler.cancel(channel, callback);
            loading.dismiss();
            Toast.makeText(activity, R.string.channel_modes_timeout, Toast.LENGTH_LONG).show();
        }, LOAD_TIMEOUT_MS);
        irc.sendCommandRaw("MODE " + channel, null,
                error -> activity.runOnUiThread(() -> {
                    if (completed[0])
                        return;
                    completed[0] = true;
                    finalHandler.cancel(channel, callback);
                    loading.dismiss();
                    Toast.makeText(activity, R.string.channel_modes_failed,
                            Toast.LENGTH_LONG).show();
                }));
    }

    private void showEditor(ChannelModeSnapshotHandler.Snapshot snapshot) {
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        serverProfile = detectServerProfile(irc);
        ModeList flags = irc.getServerConnectionData().getSupportList()
                .getSupportedFlagChannelModes();
        List<Character> modes = new ArrayList<>();
        for (Character mode : flags)
            modes.add(mode);
        Collections.sort(modes);

        int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, padding / 2);
        TextView profile = new TextView(activity);
        profile.setText(activity.getString(R.string.channel_modes_profile,
                activity.getString(serverProfile.label)));
        profile.setPadding(0, 0, 0, padding / 2);
        content.addView(profile);
        Map<Character, CheckBox> boxes = new HashMap<>();
        for (char mode : modes) {
            CheckBox box = new CheckBox(activity);
            box.setText("+" + mode + " - " + description(mode));
            box.setChecked(snapshot.active.contains(mode));
            if (!isEditable(mode))
                box.setEnabled(false);
            boxes.put(mode, box);
            content.addView(box);
        }

        ValueControl key = null;
        ValueControl limit = null;
        if (supportsValueMode(irc, 'k')) {
            key = addValueControl(content, 'k', R.string.channel_mode_key,
                    snapshot, InputType.TYPE_CLASS_TEXT);
        }
        if (supportsValueMode(irc, 'l')) {
            limit = addValueControl(content, 'l', R.string.channel_mode_limit,
                    snapshot, InputType.TYPE_CLASS_NUMBER);
        }
        if (modes.isEmpty() && key == null && limit == null) {
            TextView empty = new TextView(activity);
            empty.setText(R.string.channel_modes_none);
            content.addView(empty);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        ValueControl finalKey = key;
        ValueControl finalLimit = limit;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.channel_modes_title, channel))
                .setView(scroll)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_apply, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    List<String> commands = new ArrayList<>();
                    List<String> summary = new ArrayList<>();
                    for (Map.Entry<Character, CheckBox> entry : boxes.entrySet()) {
                        if (!entry.getValue().isEnabled())
                            continue;
                        boolean before = snapshot.active.contains(entry.getKey());
                        boolean after = entry.getValue().isChecked();
                        if (before != after) {
                            String change = (after ? "+" : "-") + entry.getKey();
                            commands.add("MODE " + channel + " " + change);
                            summary.add(change + "  " + description(entry.getKey()));
                        }
                    }
                    if (!collectValueChange(finalKey, snapshot, commands, summary) ||
                            !collectValueChange(finalLimit, snapshot, commands, summary))
                        return;
                    if (commands.isEmpty()) {
                        Toast.makeText(activity, R.string.channel_modes_no_changes,
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        return;
                    }
                    confirmAndApply(commands, summary, dialog);
                }));
        dialog.show();
    }

    private ValueControl addValueControl(LinearLayout parent, char mode, int label,
                                         ChannelModeSnapshotHandler.Snapshot snapshot,
                                         int inputType) {
        CheckBox enabled = new CheckBox(activity);
        enabled.setText("+" + mode + " - " + activity.getString(label));
        enabled.setChecked(snapshot.active.contains(mode));
        EditText value = new EditText(activity);
        value.setSingleLine(true);
        value.setInputType(inputType);
        String current = snapshot.values.get(mode);
        if (current != null && !"*".equals(current))
            value.setText(current);
        value.setHint(mode == 'k' ? R.string.channel_mode_key_hint :
                R.string.channel_mode_limit_hint);
        value.setVisibility(enabled.isChecked() ? View.VISIBLE : View.GONE);
        enabled.setOnCheckedChangeListener((button, checked) ->
                value.setVisibility(checked ? View.VISIBLE : View.GONE));
        parent.addView(enabled);
        parent.addView(value);
        return new ValueControl(mode, enabled, value);
    }

    private boolean collectValueChange(ValueControl control,
                                       ChannelModeSnapshotHandler.Snapshot snapshot,
                                       List<String> commands, List<String> summary) {
        if (control == null)
            return true;
        boolean before = snapshot.active.contains(control.mode);
        boolean after = control.enabled.isChecked();
        String oldValue = snapshot.values.get(control.mode);
        String value = control.value.getText().toString().trim();
        if (after && (!before || (!value.isEmpty() && !value.equals(oldValue)))) {
            if (!safe(value)) {
                control.value.setError(activity.getString(R.string.channel_mode_value_required));
                return false;
            }
            commands.add("MODE " + channel + " +" + control.mode + " " + value);
            summary.add("+" + control.mode + " " + value);
        } else if (!after && before) {
            String suffix = control.mode == 'k' && safe(value) ? " " + value : "";
            commands.add("MODE " + channel + " -" + control.mode + suffix);
            summary.add("-" + control.mode);
        }
        return true;
    }

    private void confirmAndApply(List<String> commands, List<String> summary,
                                 AlertDialog editor) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.operator_confirm_title)
                .setMessage(android.text.TextUtils.join("\n", summary))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_apply, (d, which) -> {
                    IRCConnection irc = (IRCConnection) connection.getApiInstance();
                    for (String command : commands)
                        irc.sendCommandRaw(command, null, null);
                    Toast.makeText(activity, R.string.operator_command_sent,
                            Toast.LENGTH_SHORT).show();
                    editor.dismiss();
                }).show();
    }

    private boolean supportsValueMode(IRCConnection irc, char mode) {
        return irc.getServerConnectionData().getSupportList()
                .getSupportedValueExactUnsetChannelModes().contains(mode) ||
                irc.getServerConnectionData().getSupportList()
                        .getSupportedValueChannelModes().contains(mode);
    }

    private String description(char mode) {
        switch (mode) {
            case 'i': return activity.getString(R.string.channel_mode_desc_i);
            case 'm': return activity.getString(R.string.channel_mode_desc_m);
            case 'n': return activity.getString(R.string.channel_mode_desc_n);
            case 't': return activity.getString(R.string.channel_mode_desc_t);
            case 's': return activity.getString(R.string.channel_mode_desc_s);
            case 'p': return activity.getString(R.string.channel_mode_desc_p);
            default: break;
        }
        if (serverProfile == ServerProfile.INSPIRCD_3 ||
                serverProfile == ServerProfile.INSPIRCD_4) {
            switch (mode) {
                case 'A': return activity.getString(R.string.channel_mode_desc_A);
                case 'C': return activity.getString(R.string.channel_mode_desc_C);
                case 'D': return activity.getString(R.string.channel_mode_desc_D);
                case 'K': return activity.getString(R.string.channel_mode_desc_K);
                case 'M': return activity.getString(R.string.channel_mode_desc_M);
                case 'N': return activity.getString(R.string.channel_mode_desc_N);
                case 'O': return activity.getString(R.string.channel_mode_desc_O);
                case 'Q': return activity.getString(R.string.channel_mode_desc_Q);
                case 'R': return activity.getString(R.string.channel_mode_desc_R);
                case 'S': return activity.getString(R.string.channel_mode_desc_S);
                case 'T': return activity.getString(R.string.channel_mode_desc_T);
                case 'U': return activity.getString(R.string.channel_mode_desc_U);
                case 'c': return activity.getString(R.string.channel_mode_desc_c);
                case 'r': return activity.getString(R.string.channel_mode_desc_r) + " (" +
                        activity.getString(R.string.channel_mode_managed) + ")";
                case 'u': return activity.getString(R.string.channel_mode_desc_u);
                case 'z': return activity.getString(R.string.channel_mode_desc_z);
                default: break;
            }
        }
        return activity.getString(R.string.channel_mode_desc_unknown);
    }

    private boolean isEditable(char mode) {
        return !((serverProfile == ServerProfile.INSPIRCD_3 ||
                serverProfile == ServerProfile.INSPIRCD_4) && mode == 'r');
    }

    private ServerProfile detectServerProfile(IRCConnection irc) {
        String version = null;
        List<StatusMessageInfo> messages = irc.getServerConnectionData()
                .getServerStatusData().getMessages();
        synchronized (messages) {
            for (StatusMessageInfo message : messages) {
                if (message instanceof HostInfoMessageInfo)
                    version = ((HostInfoMessageInfo) message).getVersion();
            }
        }
        String probe = version != null ? version.toLowerCase(Locale.ROOT) : "";
        if (probe.contains("inspircd-4") || probe.contains("inspircd 4"))
            return ServerProfile.INSPIRCD_4;
        if (probe.contains("inspircd-3") || probe.contains("inspircd 3"))
            return ServerProfile.INSPIRCD_3;
        String address = connection.getServerAddress();
        if (address != null && address.toLowerCase(Locale.ROOT).contains("simosnap"))
            return ServerProfile.INSPIRCD_3;
        return ServerProfile.GENERIC;
    }

    private enum ServerProfile {
        GENERIC(R.string.channel_modes_profile_generic),
        INSPIRCD_3(R.string.channel_modes_profile_inspircd3),
        INSPIRCD_4(R.string.channel_modes_profile_inspircd4);

        final int label;
        ServerProfile(int label) {
            this.label = label;
        }
    }

    private static boolean safe(String value) {
        return value != null && !value.isEmpty() && !value.matches(".*[\\s\\r\\n].*");
    }

    private static class ValueControl {
        final char mode;
        final CheckBox enabled;
        final EditText value;
        ValueControl(char mode, CheckBox enabled, EditText value) {
            this.mode = mode; this.enabled = enabled; this.value = value;
        }
    }
}
