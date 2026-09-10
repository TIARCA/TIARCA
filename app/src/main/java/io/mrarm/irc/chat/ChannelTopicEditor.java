package io.mrarm.irc.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import io.mrarm.chatlib.irc.ServerConnectionApi;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;

final class ChannelTopicEditor {

    private ChannelTopicEditor() {
    }

    static void show(Context context, ServerConnectionInfo connection, String channel,
                     String currentTopic) {
        if (context == null || connection == null || channel == null || channel.length() == 0)
            return;
        if (!(connection.getApiInstance() instanceof ServerConnectionApi))
            return;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_text, null);
        EditText input = view.findViewById(R.id.edit_text);
        input.setSingleLine(false);
        input.setText(currentTopic == null ? "" : currentTopic);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.channel_topic_edit_title, channel))
                .setView(view)
                .setPositiveButton(R.string.action_continue, (dialog, which) ->
                        confirm(context, connection, channel, input.getText().toString()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static void confirm(Context context, ServerConnectionInfo connection, String channel,
                                String newTopic) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.channel_topic_edit_confirm_title)
                .setMessage(context.getString(R.string.channel_topic_edit_confirm_message, channel))
                .setPositiveButton(R.string.action_edit, (dialog, which) ->
                        sendTopic(context, connection, channel, newTopic))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static void sendTopic(Context context, ServerConnectionInfo connection, String channel,
                                  String newTopic) {
        if (!(connection.getApiInstance() instanceof ServerConnectionApi))
            return;
        ServerConnectionApi api = (ServerConnectionApi) connection.getApiInstance();
        api.sendCommand("TOPIC", true, new String[] { channel, newTopic }, null, error -> {
            android.os.Handler handler = new android.os.Handler(context.getMainLooper());
            handler.post(() -> Toast.makeText(context,
                    R.string.channel_topic_edit_send_failed, Toast.LENGTH_SHORT).show());
        });
    }
}
