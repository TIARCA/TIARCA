package io.mrarm.irc.irc;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.handlers.WhoisCommandHandler;

/** Collects complete WHOWAS batches, including the shared 312 server/time reply. */
public class WhowasCommandHandler implements CommandHandler {

    public interface Callback {
        void onResult(List<Result> results);
        void onError(String message);
    }

    public static class Result {
        public final String nick;
        public final String user;
        public final String host;
        public final String realName;
        public String server;
        public String serverInfo;
        public Date disconnectTime;

        Result(String nick, String user, String host, String realName) {
            this.nick = nick;
            this.user = user;
            this.host = host;
            this.realName = realName;
        }
    }

    private static class RequestState {
        final Callback callback;
        final List<Result> results = new ArrayList<>();
        RequestState(Callback callback) { this.callback = callback; }
    }

    private static final int RPL_WHOWASUSER = 314;
    private static final int RPL_ENDOFWHOWAS = 369;
    private static final int ERR_WASNOSUCHNICK = 406;
    private static final int RPL_TARGUMODEG = 718;
    private final Map<String, RequestState> requests = new HashMap<>();

    public static WhowasCommandHandler getOrInstall(ServerConnectionData data) {
        WhowasCommandHandler handler = data.getCommandHandlerList().getHandler(WhowasCommandHandler.class);
        if (handler == null) {
            handler = new WhowasCommandHandler();
            data.getCommandHandlerList().registerHandler(handler);
        }
        WhoisCommandHandler whois = data.getCommandHandlerList().getHandler(WhoisCommandHandler.class);
        if (whois != null)
            whois.setOrphanServerReplyListener(handler::handleOrphanServerReply);
        return handler;
    }

    public synchronized void request(String nick, Callback callback) {
        requests.put(key(nick), new RequestState(callback));
    }

    @Override
    public Object[] getHandledCommands() {
        return new Object[] {
                RPL_WHOWASUSER, RPL_ENDOFWHOWAS, ERR_WASNOSUCHNICK, RPL_TARGUMODEG
        };
    }

    @Override
    public synchronized void handle(ServerConnectionData connection, MessagePrefix sender,
                                    String command, List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);

        // InspIRCd caller-id (+g) uses numeric 718 to notify the protected user that somebody
        // is trying to send a private message. The caller nickname is the first parameter after
        // our own nick; the following parameter is the caller's ident/host mask. Do not parse
        // the human-readable trailing text as it is server/localisation dependent.
        if (numeric == RPL_TARGUMODEG) {
            String callerNick = getCallerIdNick(params);
            if (callerNick != null) {
                String source = params.size() > 2 ? params.get(2) : null;
                connection.getServerStatusData().addMessage(
                        new CallerIdStatusMessageInfo(callerNick, source));
            }
            return;
        }

        String nick = CommandHandler.getParamWithCheck(params, 1);
        String requestKey = key(nick);
        RequestState state = requests.get(requestKey);

        // WHOWAS can be sent from the Server overflow dialog, from a configured/raw command,
        // or by typing /WHOWAS.  The 0.9.1.2 collector was installed only by the dialog, so
        // replies from the other entry points fell through as raw 314/369 lines.  Once this
        // handler is installed for the connection, a 314 starts a passive batch as well.
        if (numeric == RPL_WHOWASUSER && state == null) {
            state = new RequestState(null);
            requests.put(requestKey, state);
        }
        if (state == null)
            return;

        if (numeric == RPL_WHOWASUSER) {
            state.results.add(new Result(nick,
                    CommandHandler.getParamWithCheck(params, 2),
                    CommandHandler.getParamWithCheck(params, 3),
                    CommandHandler.getParamOrNull(params, 5)));
        } else if (numeric == RPL_ENDOFWHOWAS) {
            requests.remove(requestKey);
            if (!state.results.isEmpty()) {
                List<Result> completed = new ArrayList<>(state.results);
                if (state.callback != null) {
                    state.callback.onResult(completed);
                } else {
                    for (Result result : completed)
                        connection.getServerStatusData().addMessage(new WhowasStatusMessageInfo(result));
                }
            } else if (state.callback != null) {
                state.callback.onError("WHOWAS data unavailable");
            }
        } else if (numeric == ERR_WASNOSUCHNICK) {
            requests.remove(requestKey);
            if (state.callback != null)
                state.callback.onError(CommandHandler.getParamOrDefault(params, 2,
                        "WHOWAS data unavailable"));
        }
    }

    static String getCallerIdNick(List<String> params) {
        // InspIRCd/SimosNap wire form:
        // 718 <me> <nick> <nick!ident@host-or-ident@host> :is messaging you ...
        if (params == null || params.size() < 2)
            return null;
        String nick = params.get(1);
        if (nick == null)
            return null;
        nick = nick.trim();
        return nick.isEmpty() ? null : nick;
    }

    /** Called by WhoisCommandHandler for numeric 312 when there is no active WHOIS reply. */
    public synchronized void handleOrphanServerReply(List<String> params) {
        if (params.size() < 3)
            return;
        String requestKey = key(params.get(1));
        RequestState state = requests.get(requestKey);
        if (state == null || state.results.isEmpty())
            return;
        Result result = state.results.get(state.results.size() - 1);
        result.server = params.get(2);
        String info = params.size() > 3 ? params.get(3) : null;
        Date time = parseDisconnectTime(info);
        if (time != null)
            result.disconnectTime = time;
        else
            result.serverInfo = info;
    }

    static Date parseDisconnectTime(String value) {
        if (value == null || value.trim().isEmpty())
            return null;
        String input = value.trim();
        if (input.matches("[0-9]{10}")) {
            try { return new Date(Long.parseLong(input) * 1000L); }
            catch (NumberFormatException ignored) { return null; }
        }
        String[] patterns = {
                "EEE MMM dd yyyy HH:mm:ss",
                "EEE MMM dd HH:mm:ss yyyy",
                "EEE MMM d yyyy HH:mm:ss",
                "EEE MMM d HH:mm:ss yyyy"
        };
        for (String pattern : patterns) {
            SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
            parser.setLenient(false);
            try { return parser.parse(input); }
            catch (ParseException ignored) { }
        }
        return null;
    }

    private static String key(String nick) {
        return nick.toLowerCase(Locale.ROOT);
    }
}
