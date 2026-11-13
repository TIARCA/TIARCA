package io.mrarm.chatlib.irc.cap;

import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import java.util.List;
import java.util.Map;

/** IRCv3 extended-join support. */
public final class ExtendedJoinCapability extends Capability {
    @Override public String[] getNames() { return new String[]{"extended-join"}; }
    @Override public Object[] getHandledCommands() { return new Object[]{}; }
    @Override public void handle(ServerConnectionData connection, MessagePrefix sender,
            String command, List<String> params, Map<String, String> tags) {
    }
}
