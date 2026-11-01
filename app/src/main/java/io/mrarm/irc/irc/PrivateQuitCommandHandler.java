package io.mrarm.irc.irc;

import java.util.List;
import java.util.Map;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.handlers.QuitCommandHandler;
import io.mrarm.irc.ServerConnectionInfo;

/** Preserves channel QUIT processing while writing the same event into an existing PVT. */
public final class PrivateQuitCommandHandler implements CommandHandler {
    /** Delivery is independent of channel member cleanup. */
    interface PrivateQuitObserver {
        void onQuit(ServerConnectionData connection, MessagePrefix sender, String reason);
    }

    private final QuitCommandHandler delegate;
    private final PrivateQuitObserver observer;

    public PrivateQuitCommandHandler(QuitCommandHandler delegate, ServerConnectionInfo owner) {
        this(delegate, owner::onPrivateQuit);
    }

    PrivateQuitCommandHandler(QuitCommandHandler delegate, PrivateQuitObserver observer) {
        this.delegate = delegate;
        this.observer = observer;
    }

    @Override public Object[] getHandledCommands() {
        return delegate.getHandledCommands();
    }

    @Override public void handle(ServerConnectionData connection, MessagePrefix sender,
                                 String command, List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        // Record the real QUIT in existing PVTs before cleaning up joined-channel
        // members. The default handler can fail while looking up a now-missing
        // member; that should not suppress an otherwise valid private QUIT.
        if (sender != null && sender.getNick() != null)
            observer.onQuit(connection, sender, CommandHandler.getParamOrNull(params, 0));
        delegate.handle(connection, sender, command, params, tags);
    }
}
