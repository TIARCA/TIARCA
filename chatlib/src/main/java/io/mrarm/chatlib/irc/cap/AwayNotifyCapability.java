package io.mrarm.chatlib.irc.cap;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;

import java.util.List;
import java.util.Map;

/** IRCv3 away-notify state updates for users sharing a channel with us. */
public class AwayNotifyCapability extends Capability {

    @Override
    public String[] getNames() {
        return new String[] { "away-notify" };
    }

    @Override
    public Object[] getHandledCommands() {
        return new Object[] { "AWAY" };
    }

    @Override
    public void handle(ServerConnectionData connection, MessagePrefix sender, String command,
                       List<String> params, Map<String, String> tags) throws InvalidMessageException {
        if (sender == null)
            return;
        String message = CommandHandler.getParamOrNull(params, 0);
        connection.setUserAway(sender.getNick(), sender.getUser(), sender.getHost(),
                message != null, message);
    }
}
