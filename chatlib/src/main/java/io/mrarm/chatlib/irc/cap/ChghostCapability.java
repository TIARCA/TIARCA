package io.mrarm.chatlib.irc.cap;

import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;

import java.util.List;
import java.util.Map;

/** IRCv3 chghost: keep the server-observed identity cache current. */
public final class ChghostCapability extends Capability {
    @Override public String[] getNames() { return new String[]{"chghost"}; }
    @Override public Object[] getHandledCommands() { return new Object[]{"CHGHOST"}; }

    @Override
    public void handle(ServerConnectionData connection, MessagePrefix sender,
                       String command, List<String> params, Map<String, String> tags) {
        if (sender == null || params.size() < 2) return;
        connection.getIdentityCache().changeHost(sender.getNick(), params.get(0), params.get(1));
        if (sender.getNick().equalsIgnoreCase(connection.getUserNick()))
            connection.setUserExtraInfo(params.get(0), params.get(1));
    }
}
