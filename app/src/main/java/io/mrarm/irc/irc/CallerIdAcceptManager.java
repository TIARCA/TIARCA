package io.mrarm.irc.irc;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.CommandHandlerList;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.handlers.ModeCommandHandler;
import io.mrarm.irc.ServerConnectionInfo;

/** Sends an implicit ACCEPT when the current user is protected by caller-id mode (+g). */
public final class CallerIdAcceptManager {

    private CallerIdAcceptManager() { }

    public static void acceptIfCallerIdEnabled(ServerConnectionInfo connection, String targetNick) {
        if (connection == null || targetNick == null || targetNick.trim().isEmpty() ||
                !(connection.getApiInstance() instanceof IRCConnection))
            return;

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

        if (handler.isUserModeActive('g'))
            irc.sendCommandRaw("ACCEPT +" + targetNick.trim(), null, null);
    }
}
