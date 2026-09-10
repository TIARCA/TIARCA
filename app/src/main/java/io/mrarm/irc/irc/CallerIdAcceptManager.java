package io.mrarm.irc.irc;

import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.ServerConnectionInfo;

/** Sends an implicit ACCEPT when the user deliberately starts a private conversation. */
public final class CallerIdAcceptManager {

    private CallerIdAcceptManager() { }

    public static void acceptOutgoingPrivateConversation(ServerConnectionInfo connection, String targetNick) {
        if (connection == null || targetNick == null || targetNick.trim().isEmpty() ||
                !(connection.getApiInstance() instanceof IRCConnection))
            return;

        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        irc.sendCommandRaw("ACCEPT +" + targetNick.trim(), null, null);
    }
}
