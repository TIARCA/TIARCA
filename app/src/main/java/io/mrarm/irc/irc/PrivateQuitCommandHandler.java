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
    private final QuitCommandHandler delegate;
    private final ServerConnectionInfo owner;

    public PrivateQuitCommandHandler(QuitCommandHandler delegate, ServerConnectionInfo owner) {
        this.delegate = delegate;
        this.owner = owner;
    }

    @Override public Object[] getHandledCommands() {
        return delegate.getHandledCommands();
    }

    @Override public void handle(ServerConnectionData connection, MessagePrefix sender,
                                 String command, List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        // First retain chatlib's existing channel member removal and channel event.
        delegate.handle(connection, sender, command, params, tags);
        if (sender != null && sender.getNick() != null)
            owner.onPrivateQuit(connection, sender, CommandHandler.getParamOrNull(params, 0));
    }
}
