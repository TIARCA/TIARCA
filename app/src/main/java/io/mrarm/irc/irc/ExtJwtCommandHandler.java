package io.mrarm.irc.irc;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;

/** Collects the one or more EXTJWT reply lines used by the SimosNap uploader. */
public class ExtJwtCommandHandler implements CommandHandler {

    public interface Callback {
        void onToken(String token);
    }

    private final Queue<Callback> callbacks = new ArrayDeque<>();
    private final StringBuilder token = new StringBuilder();

    public synchronized void request(Callback callback) {
        callbacks.add(callback);
        if (callbacks.size() == 1)
            token.setLength(0);
    }

    public synchronized void cancel(Callback callback) {
        callbacks.remove(callback);
        if (callbacks.isEmpty())
            token.setLength(0);
    }

    @Override
    public Object[] getHandledCommands() {
        return new Object[] { "EXTJWT" };
    }

    @Override
    public synchronized void handle(ServerConnectionData connection, MessagePrefix sender,
                                    String command, List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        if (callbacks.isEmpty() || params.isEmpty())
            return;
        token.append(params.get(params.size() - 1));
        // EXTJWT v1 uses four parameters when another token fragment follows.
        if (params.size() == 4)
            return;
        String value = token.toString();
        token.setLength(0);
        while (!callbacks.isEmpty())
            callbacks.remove().onToken(value);
    }
}
