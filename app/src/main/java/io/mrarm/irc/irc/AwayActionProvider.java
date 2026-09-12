package io.mrarm.irc.irc;

import android.content.Context;
import android.view.View;

import androidx.core.view.ActionProvider;
import androidx.fragment.app.Fragment;

import io.mrarm.irc.MainActivity;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.chat.ChatFragment;
import io.mrarm.irc.dialog.AwayDialog;

/** Keeps the Away overflow action scoped to the connected server-status tab. */
public final class AwayActionProvider extends ActionProvider {

    private final Context context;

    public AwayActionProvider(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public View onCreateActionView() {
        return null;
    }

    @Override
    public boolean overridesItemVisibility() {
        return true;
    }

    @Override
    public boolean isVisible() {
        ServerConnectionInfo connection = getServerStatusConnection();
        return connection != null && connection.isConnected();
    }

    @Override
    public boolean onPerformDefaultAction() {
        ServerConnectionInfo connection = getServerStatusConnection();
        if (connection == null || !connection.isConnected())
            return false;
        AwayDialog.show(context, connection);
        return true;
    }

    private ServerConnectionInfo getServerStatusConnection() {
        if (!(context instanceof MainActivity))
            return null;
        Fragment current = ((MainActivity) context).getCurrentFragment();
        if (!(current instanceof ChatFragment))
            return null;
        ChatFragment chat = (ChatFragment) current;
        if (chat.getCurrentChannel() != null)
            return null;
        return chat.getConnectionInfo();
    }
}
