package io.mrarm.irc.irc;

import java.util.List;
import java.util.Map;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;

/** Handles the standard IRC confirmations for the local user's AWAY state. */
public final class SelfAwayCommandHandler implements CommandHandler {

    public static final int RPL_UNAWAY = 305;
    public static final int RPL_NOWAWAY = 306;

    public interface Listener {
        void onSelfAwayState(boolean away);
    }

    private final Listener listener;

    public SelfAwayCommandHandler(Listener listener) {
        this.listener = listener;
    }

    @Override
    public Object[] getHandledCommands() {
        return new Object[] { RPL_UNAWAY, RPL_NOWAWAY };
    }

    @Override
    public void handle(ServerConnectionData connection, MessagePrefix sender, String command,
                       List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);
        if (numeric == RPL_NOWAWAY)
            listener.onSelfAwayState(true);
        else if (numeric == RPL_UNAWAY)
            listener.onSelfAwayState(false);
    }
}
