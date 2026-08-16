package io.mrarm.irc.irc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;

/** Minimal WHOWAS response collector for operator actions on recently disconnected users. */
public class WhowasCommandHandler implements CommandHandler {

    public interface Callback {
        void onResult(Result result);
        void onError(String message);
    }

    public static class Result {
        public final String nick;
        public final String user;
        public final String host;
        public final String realName;

        Result(String nick, String user, String host, String realName) {
            this.nick = nick;
            this.user = user;
            this.host = host;
            this.realName = realName;
        }
    }

    private static final int RPL_WHOWASUSER = 314;
    private static final int RPL_ENDOFWHOWAS = 369;
    private static final int ERR_WASNOSUCHNICK = 406;
    private final Map<String, Callback> callbacks = new HashMap<>();
    private final Map<String, Result> replies = new HashMap<>();

    public synchronized void request(String nick, Callback callback) {
        callbacks.put(nick.toLowerCase(), callback);
        replies.remove(nick.toLowerCase());
    }

    @Override
    public Object[] getHandledCommands() {
        return new Object[] { RPL_WHOWASUSER, RPL_ENDOFWHOWAS, ERR_WASNOSUCHNICK };
    }

    @Override
    public synchronized void handle(ServerConnectionData connection, MessagePrefix sender,
                                    String command, List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);
        String nick = CommandHandler.getParamWithCheck(params, 1);
        String key = nick.toLowerCase();
        if (!callbacks.containsKey(key))
            return;
        if (numeric == RPL_WHOWASUSER) {
            replies.put(key, new Result(nick,
                    CommandHandler.getParamWithCheck(params, 2),
                    CommandHandler.getParamWithCheck(params, 3),
                    CommandHandler.getParamOrNull(params, 5)));
        } else if (numeric == RPL_ENDOFWHOWAS) {
            Callback callback = callbacks.remove(key);
            Result result = replies.remove(key);
            if (result != null)
                callback.onResult(result);
            else
                callback.onError("WHOWAS data unavailable");
        } else if (numeric == ERR_WASNOSUCHNICK) {
            Callback callback = callbacks.remove(key);
            replies.remove(key);
            callback.onError(CommandHandler.getParamOrDefault(params, 2, "WHOWAS data unavailable"));
        }
    }
}
