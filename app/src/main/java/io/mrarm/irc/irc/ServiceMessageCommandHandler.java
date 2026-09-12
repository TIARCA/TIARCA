package io.mrarm.irc.irc;

import java.util.Date;
import java.util.List;
import java.util.Map;

import io.mrarm.chatlib.dto.StatusMessageInfo;
import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.irc.ServerConnectionInfo;

/** Routes trusted IRC service queries to the server status tab. */
public final class ServiceMessageCommandHandler implements CommandHandler {

    private final CommandHandler delegate;
    private final ServerConnectionInfo connection;

    public ServiceMessageCommandHandler(CommandHandler delegate,
                                        ServerConnectionInfo connection) {
        this.delegate = delegate;
        this.connection = connection;
    }

    @Override
    public Object[] getHandledCommands() {
        return new Object[]{"PRIVMSG", "NOTICE"};
    }

    @Override
    public void handle(ServerConnectionData data, MessagePrefix prefix, String command,
                       List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        String target = CommandHandler.getParamOrNull(params, 0);
        String text = CommandHandler.getParamOrNull(params, 1);
        String nick = prefix == null ? null : prefix.getNick();
        boolean direct = target != null && target.equalsIgnoreCase(data.getUserNick());
        boolean ctcp = text != null && text.length() > 1 && text.charAt(0) == '\u0001';

        // InspIRCd confirms successful ACCEPT add/remove operations using server NOTICEs.
        // Observe those before normal message routing so ACCEPT state does not depend on
        // which server-status view is currently visible.
        boolean serverNotice = "NOTICE".equalsIgnoreCase(command) && direct && !ctcp &&
                prefix != null && prefix.getUser() == null && prefix.getHost() == null;
        if (serverNotice)
            CallerIdAcceptManager.observeServerNotice(connection, text);

        if (direct && !ctcp && !connection.hasOpenConversation(nick) &&
                connection.isTrustedService(nick, prefix.getUser(), prefix.getHost())) {
            connection.rememberServiceNick(nick);
            data.getServerStatusData().addMessage(new StatusMessageInfo(nick, new Date(),
                    StatusMessageInfo.MessageType.NOTICE, text));
            return;
        }
        delegate.handle(data, prefix, command, params, tags);
    }
}
