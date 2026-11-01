package io.mrarm.irc.irc;

import android.util.Log;

import java.util.List;
import java.util.Map;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.handlers.QuitCommandHandler;
import io.mrarm.irc.ServerConnectionInfo;

/** Saves the QUIT in existing PVTs without interfering with normal channel QUIT handling. */
public final class PrivateQuitCommandHandler implements CommandHandler {
    private static final String TAG = "PrivateQuitHandler";

    interface PrivateQuitListener {
        void onPrivateQuit(ServerConnectionData connection, MessagePrefix sender, String reason);
    }

    private final QuitCommandHandler delegate;
    private final PrivateQuitListener listener;

    public PrivateQuitCommandHandler(QuitCommandHandler delegate, ServerConnectionInfo owner) {
        this(delegate, owner::onPrivateQuit);
    }

    /** Package-private for network event tests. */
    PrivateQuitCommandHandler(QuitCommandHandler delegate, PrivateQuitListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override public Object[] getHandledCommands() {
        return delegate.getHandledCommands();
    }

    @Override public void handle(ServerConnectionData connection, MessagePrefix sender,
                                 String command, List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        // Channel state may be out of sync after a reconnect. A failure to remove a
        // member from the channel must not suppress a genuine remote QUIT in the PVT.
        if (sender != null && sender.getNick() != null) {
            try {
                listener.onPrivateQuit(connection, sender,
                        CommandHandler.getParamOrNull(params, 0));
            } catch (RuntimeException error) {
                // Never let a local PVT persistence failure break the IRC command pipeline.
                Log.w(TAG, "Could not process remote QUIT in private conversation", error);
            }
        }
        // Always retain chatlib's normal channel member removal and channel event.
        delegate.handle(connection, sender, command, params, tags);
    }
}
