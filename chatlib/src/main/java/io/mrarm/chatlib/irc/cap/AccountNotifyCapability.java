package io.mrarm.chatlib.irc.cap;

import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import java.util.List;
import java.util.Map;

/** IRCv3 account-notify support. */
public final class AccountNotifyCapability extends Capability {
    @Override public String[] getNames() { return new String[]{"account-notify"}; }
    @Override public Object[] getHandledCommands() { return new Object[]{"ACCOUNT"}; }
    @Override public void handle(ServerConnectionData connection, MessagePrefix sender,
            String command, List<String> params, Map<String, String> tags) {
        if (sender != null && !params.isEmpty())
            connection.getIdentityCache().note(sender.getNick(), null, null, params.get(0));
    }
}
