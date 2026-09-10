package io.mrarm.irc.irc;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.CommandHandlerList;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.handlers.ModeCommandHandler;
import io.mrarm.irc.ServerConnectionInfo;

/** Sends an implicit ACCEPT when the user deliberately starts a private conversation while +g is active. */
public final class CallerIdAcceptManager {

    private CallerIdAcceptManager() { }

    public static void acceptOutgoingPrivateConversation(ServerConnectionInfo connection, String targetNick) {
        if (connection == null || targetNick == null || targetNick.trim().isEmpty() ||
                !(connection.getApiInstance() instanceof IRCConnection))
            return;

        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        String ownNick = irc.getServerConnectionData().getUserNick();
        if (ownNick == null || ownNick.isEmpty())
            return;

        CommandHandlerList handlers = irc.getServerConnectionData().getCommandHandlerList();
        ChannelModeSnapshotHandler modeHandler = handlers.getHandler(ChannelModeSnapshotHandler.class);
        if (modeHandler == null) {
            CommandHandler delegate = handlers.getHandler(ModeCommandHandler.class);
            modeHandler = new ChannelModeSnapshotHandler(delegate);
            if (delegate != null) handlers.unregisterHandler(delegate);
            handlers.registerHandler(modeHandler);
        }

        String nick = targetNick.trim();
        if (modeHandler.isUserModeKnown('g')) {
            if (modeHandler.isUserModeActive('g'))
                irc.sendCommandRaw("ACCEPT +" + nick, null, null);
            return;
        }

        final ChannelModeSnapshotHandler finalModeHandler = modeHandler;
        ChannelModeSnapshotHandler.Callback callback = snapshot -> {
            if (snapshot.active.contains('g'))
                irc.sendCommandRaw("ACCEPT +" + nick, null, null);
        };
        modeHandler.requestUserModes(ownNick, callback);
        irc.sendCommandRaw("MODE " + ownNick, null,
                error -> finalModeHandler.cancelUserModes(callback));
    }
}
