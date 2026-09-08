package io.mrarm.irc.dialog;

import android.content.Context;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.List;

import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.irc.WhowasCommandHandler;
import io.mrarm.irc.irc.WhowasStatusMessageInfo;

/** Entry point for manual WHOWAS queries from the Server-tab overflow menu. */
public final class WhowasQueryDialog {
    private WhowasQueryDialog() { }

    public static void show(Context context, ServerConnectionInfo connection) {
        if (context == null || connection == null ||
      !(connection.getApiInstance() instanceof IRCConnection))
  return;
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.whowas_nickname_prompt);
        AlertDialog dialog = new AlertDialog.Builder(context)
      .setTitle(R.string.action_whowas)
      .setView(input)
      .setNegativeButton(R.string.action_cancel, null)
      .setPositiveButton(R.string.action_ok, null)
      .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
      .setOnClickListener(v -> {
          String nick = input.getText().toString().trim();
          if (nick.isEmpty() || nick.matches(".*[\\s\\r\\n].*")) {
              input.setError(context.getString(R.string.whowas_nickname_prompt));
              return;
          }
          query(context, connection, nick);
          dialog.dismiss();
      }));
        dialog.show();
        if (context instanceof MainActivity)
  ((MainActivity) context).setFragmentDialog(dialog);
    }

    private static void query(Context context, ServerConnectionInfo connection, String nick) {
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        ServerConnectionData data = irc.getServerConnectionData();
        WhowasCommandHandler handler = WhowasCommandHandler.getOrInstall(data);
        handler.request(nick, new WhowasCommandHandler.Callback() {
  @Override public void onResult(List<WhowasCommandHandler.Result> results) {
      android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
      main.post(() -> {
          for (WhowasCommandHandler.Result result : results)
              data.getServerStatusData().addMessage(new WhowasStatusMessageInfo(result));
      });
  }
  @Override public void onError(String message) {
      new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
              Toast.makeText(context, message, Toast.LENGTH_LONG).show());
  }
        });
        irc.sendCommandRaw("WHOWAS " + nick, null, null);
    }
}
