package io.mrarm.irc.irc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.mrarm.chatlib.irc.CommandDisconnectHandler;
import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;

/** Collects nick-to-account mappings from a channel-wide WHOX request. */
public final class WhoXAccountHandler implements CommandDisconnectHandler {

    private static final int RPL_WHOSPCRPL = 354;
    private static final int RPL_ENDOFWHO = 315;
    private static final AtomicInteger NEXT_TOKEN = new AtomicInteger(700);

    private static final int MAX_ACCOUNTS = 2048;
    private static final int MAX_REQUESTS = 32;
    private final Map<String, String> accounts = new LinkedHashMap<String, String>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_ACCOUNTS;
        }
    };
    private final Map<String, Request> requests = new LinkedHashMap<>();

    @Override public Object[] getHandledCommands() {
        return new Object[] { RPL_WHOSPCRPL, RPL_ENDOFWHO };
    }

    public synchronized String begin(String channel, Runnable callback) {
        int value = NEXT_TOKEN.getAndIncrement();
        if (value > 999) {
            NEXT_TOKEN.set(701);
            value = 700;
        }
        String token = String.valueOf(value);
        requests.put(token, new Request(channel, callback));
        while (requests.size() > MAX_REQUESTS)
            requests.remove(requests.keySet().iterator().next());
        return token;
    }

    public synchronized String getAccount(String nick) {
        return nick == null ? null : accounts.get(nick.toLowerCase(Locale.ROOT));
    }

    public synchronized void remember(String nick, String account) {
        if (nick == null || account == null || account.isEmpty() ||
                "0".equals(account) || "*".equals(account))
            return;
        accounts.put(nick.toLowerCase(Locale.ROOT), account);
    }

    @Override
    public void handle(ServerConnectionData connection, MessagePrefix sender, String command,
                       List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);
        if (numeric == RPL_WHOSPCRPL) {
            // WHO #channel %tna,NNN -> requester, token, nick, account
            if (params.size() < 4)
                return;
            String token = params.get(1);
            synchronized (this) {
                if (!requests.containsKey(token))
                    return;
            }
            remember(params.get(2), params.get(3));
            return;
        }
        if (numeric == RPL_ENDOFWHO && params.size() >= 2) {
            String channel = params.get(1);
            List<Runnable> callbacks = new ArrayList<>();
            synchronized (this) {
                List<String> finished = new ArrayList<>();
                for (Map.Entry<String, Request> entry : requests.entrySet()) {
                    if (entry.getValue().channel.equalsIgnoreCase(channel)) {
                        finished.add(entry.getKey());
                        if (entry.getValue().callback != null)
                            callbacks.add(entry.getValue().callback);
                    }
                }
                for (String token : finished)
                    requests.remove(token);
            }
            for (Runnable callback : callbacks)
                callback.run();
        }
    }

    @Override public synchronized void onDisconnected() {
        accounts.clear();
        requests.clear();
    }

    private static final class Request {
        final String channel;
        final Runnable callback;
        Request(String channel, Runnable callback) {
            this.channel = channel;
            this.callback = callback;
        }
    }
}
