package io.mrarm.irc.irc;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.CommandHandlerList;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.handlers.ModeCommandHandler;
import io.mrarm.irc.ServerConnectionInfo;

/** Handles caller-ID (+g) ACCEPT state for private conversations. */
public final class CallerIdAcceptManager {

    private static final Pattern ACCEPT_ADDED = Pattern.compile(
            "^([^\\s:]+)\\s+is now on your accept list(?:\\.|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCEPT_REMOVED = Pattern.compile(
            "^([^\\s:]+)\\s+is no longer on your accept list(?:\\.|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Map<IRCConnection, Set<String>> ACCEPTED = new WeakHashMap<>();

    private CallerIdAcceptManager() { }

    public static boolean isCallerIdActive(ServerConnectionInfo connection) {
        if (connection == null || !(connection.getApiInstance() instanceof IRCConnection))
            return false;
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        ChannelModeSnapshotHandler modeHandler = irc.getServerConnectionData()
                .getCommandHandlerList().getHandler(ChannelModeSnapshotHandler.class);
        return modeHandler != null && modeHandler.isUserModeKnown('g') &&
                modeHandler.isUserModeActive('g');
    }

    public static synchronized boolean isAccepted(ServerConnectionInfo connection, String targetNick) {
        IRCConnection irc = getIrc(connection);
        if (irc == null || targetNick == null)
            return false;
        Set<String> accepted = ACCEPTED.get(irc);
        return accepted != null && accepted.contains(normalizeNick(targetNick));
    }

    /** Sends an ACCEPT change; local state is updated only after a server confirmation. */
    public static void setAccepted(ServerConnectionInfo connection, String targetNick, boolean accepted) {
        IRCConnection irc = getIrc(connection);
        if (irc == null || targetNick == null || targetNick.trim().isEmpty())
            return;
        String nick = targetNick.trim();
        irc.sendCommandRaw("ACCEPT " + (accepted ? "+" : "-") + nick, null, null);
    }

    public static synchronized void noteAccepted(ServerConnectionInfo connection, String targetNick,
                                                  boolean accepted) {
        IRCConnection irc = getIrc(connection);
        if (irc == null || targetNick == null || targetNick.trim().isEmpty())
            return;
        Set<String> users = ACCEPTED.get(irc);
        if (users == null) {
            users = new HashSet<>();
            ACCEPTED.put(irc, users);
        }
        String nick = normalizeNick(targetNick);
        if (accepted)
            users.add(nick);
        else
            users.remove(nick);
    }

    /**
     * Applies the authoritative InspIRCd ACCEPT success notices to the session state.
     * Returns true when the notice was recognized as an ACCEPT-list confirmation.
     */
    public static boolean observeServerNotice(ServerConnectionInfo connection, String rawMessage) {
        AcceptNotice notice = parseAcceptNotice(rawMessage);
        if (notice == null)
            return false;
        noteAccepted(connection, notice.nick, notice.accepted);
        return true;
    }

    /** Returns whether a status line has the form of an ACCEPT success confirmation. */
    public static boolean isAcceptServerNotice(String rawMessage) {
        return parseAcceptNotice(rawMessage) != null;
    }

    static AcceptNotice parseAcceptNotice(String rawMessage) {
        if (rawMessage == null)
            return null;
        Matcher added = ACCEPT_ADDED.matcher(rawMessage.trim());
        if (added.find())
            return new AcceptNotice(added.group(1), true);
        Matcher removed = ACCEPT_REMOVED.matcher(rawMessage.trim());
        if (removed.find())
            return new AcceptNotice(removed.group(1), false);
        return null;
    }

    static final class AcceptNotice {
        final String nick;
        final boolean accepted;

        AcceptNotice(String nick, boolean accepted) {
            this.nick = nick;
            this.accepted = accepted;
        }
    }

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
                setAccepted(connection, nick, true);
            return;
        }

        final ChannelModeSnapshotHandler finalModeHandler = modeHandler;
        ChannelModeSnapshotHandler.Callback callback = snapshot -> {
            if (snapshot.active.contains('g'))
                setAccepted(connection, nick, true);
        };
        modeHandler.requestUserModes(ownNick, callback);
        irc.sendCommandRaw("MODE " + ownNick, null,
                error -> finalModeHandler.cancelUserModes(callback));
    }

    private static IRCConnection getIrc(ServerConnectionInfo connection) {
        return connection != null && connection.getApiInstance() instanceof IRCConnection ?
                (IRCConnection) connection.getApiInstance() : null;
    }

    private static String normalizeNick(String nick) {
        return nick.trim().toLowerCase(Locale.ROOT);
    }
}
