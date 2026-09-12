package io.mrarm.irc.irc;

import android.content.Context;
import android.content.ContextWrapper;
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
        MainActivity activity = findMainActivity(context);
        if (connection == null || activity == null || !connection.isConnected())
            return false;
        AwayDialog.show(activity, connection);
        return true;
    }

    private ServerConnectionInfo getServerStatusConnection() {
        MainActivity activity = findMainActivity(context);
        if (activity == null)
            return null;
        Fragment current = activity.getCurrentFragment();
        if (!(current instanceof ChatFragment))
            return null;
        ChatFragment chat = (ChatFragment) current;
        if (chat.getCurrentChannel() != null)
            return null;
        return chat.getConnectionInfo();
    }

    private static MainActivity findMainActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof MainActivity)
                return (MainActivity) current;
            Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current)
                break;
            current = base;
        }
        return current instanceof MainActivity ? (MainActivity) current : null;
    }
}
