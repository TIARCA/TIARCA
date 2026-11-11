package io.mrarm.irc.irc;

import java.util.List;
import java.util.Map;

import io.mrarm.chatlib.irc.CommandDisconnectHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;

/** Collects a single EXTJWT response from the active IRC connection. */
public class ExtJwtCommandHandler implements CommandDisconnectHandler {

    public interface Callback {
        void onToken(String token);
    }

    private static final int MAX_TOKEN_LENGTH = 8192;
    private Callback pending;
    private final StringBuilder token = new StringBuilder();

    /** Only one EXTJWT request may be in-flight on a connection. */
    public synchronized boolean request(Callback callback) {
        if (callback == null || pending != null)
            return false;
        pending = callback;
        token.setLength(0);
        return true;
    }

    public synchronized void cancel(Callback callback) {
        if (pending == callback) {
            pending = null;
            token.setLength(0);
        }
    }

    @Override
    public Object[] getHandledCommands() {
        return new Object[] { "EXTJWT" };
    }

    @Override
    public void handle(ServerConnectionData connection, MessagePrefix sender,
                       String command, List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        Callback callback;
        String result;
        synchronized (this) {
            if (pending == null || params == null || params.isEmpty())
                return;
            String part = params.get(params.size() - 1);
            if (part == null || part.length() > MAX_TOKEN_LENGTH - token.length()) {
                result = null;
            } else {
                token.append(part);
                // EXTJWT v1 uses four parameters when a token fragment follows.
                if (params.size() == 4)
                    return;
                result = token.toString();
            }
            callback = pending;
            pending = null;
            token.setLength(0);
        }
        callback.onToken(result);
    }

    @Override
    public void onDisconnected() {
        Callback callback;
        synchronized (this) {
            callback = pending;
            pending = null;
            token.setLength(0);
        }
        if (callback != null)
            callback.onToken(null);
    }
}
