package io.mrarm.irc.dialog;

import android.content.Context;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.config.AppSettings;
import io.mrarm.irc.irc.AwayStateManager;

/** Manual controls for the local user's IRC AWAY state. */
public final class AwayDialog {

    private AwayDialog() {
    }

    public static void show(Context context, ServerConnectionInfo connection) {
        AwayStateManager manager = AwayStateManager.get(connection);
        boolean currentlyAway = manager.isAway();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(context, 24);
        root.setPadding(horizontal, dp(context, 8), horizontal, 0);

        SwitchCompat awaySwitch = new SwitchCompat(context);
        awaySwitch.setText(R.string.away_dialog_enable);
        awaySwitch.setChecked(currentlyAway);
        root.addView(awaySwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView messageLabel = new TextView(context);
        messageLabel.setText(R.string.away_dialog_message);
        messageLabel.setTypeface(messageLabel.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(context, 12);
        root.addView(messageLabel, labelParams);

        EditText messageInput = new EditText(context);
        messageInput.setSingleLine(true);
        String currentMessage = manager.getAwayMessage();
        messageInput.setText(currentlyAway && currentMessage != null && !currentMessage.isEmpty()
                ? currentMessage : AppSettings.getDefaultAwayMessage());
        messageInput.setSelection(messageInput.length());
        root.addView(messageInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        CheckBox nicknameAway = new CheckBox(context);
        nicknameAway.setText(R.string.away_dialog_nickname);
        nicknameAway.setChecked(currentlyAway
                ? manager.isAwayNickApplied() : AppSettings.isAwayNickEnabled());
        root.addView(nicknameAway, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Runnable updateEnabled = () -> {
            boolean enabled = awaySwitch.isChecked();
            messageLabel.setEnabled(enabled);
            messageInput.setEnabled(enabled);
            nicknameAway.setEnabled(enabled);
        };
        awaySwitch.setOnCheckedChangeListener((button, checked) -> updateEnabled.run());
        updateEnabled.run();

        new AlertDialog.Builder(context)
                .setTitle(R.string.action_away)
                .setView(root)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, which) ->
                        manager.requestAway(awaySwitch.isChecked(),
                                messageInput.getText().toString(), nicknameAway.isChecked()))
                .show();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
