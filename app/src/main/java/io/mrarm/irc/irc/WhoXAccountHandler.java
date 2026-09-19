package io.mrarm.irc.irc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.mrarm.chatlib.irc.CommandDisconnectHandler;
import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;

/**
 * Collects nick-to-account mappings from channel-wide WHOX requests.
 *
 * Historical mappings deliberately survive a transport reconnect so TIARCA can reconcile a
 * private query whose user changed nickname while the client was offline. The separate current
 * map is cleared on disconnect and contains only identities observed on the active connection.
 */
public final class WhoXAccountHandler implements CommandDisconnectHandler {

    private static final int RPL_WHOSPCRPL = 354;
    private static final int RPL_ENDOFWHO = 315;
    private static final AtomicInteger NEXT_TOKEN = new AtomicInteger(700);

    private static final int MAX_ACCOUNTS = 2048;
    private static final int MAX_REQUESTS = 32;
    private final Map<String, String> accounts = newAccountMap();
    private final Map<String, String> currentAccounts = newAccountMap();
    private final Map<String, Request> requests = new LinkedHashMap<>();

    private static Map<String, String> newAccountMap() {
        return new LinkedHashMap<String, String>(128, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > MAX_ACCOUNTS;
            }
        };
    }

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
        return getCaseInsensitive(accounts, nick);
    }

    public synchronized void remember(String nick, String account) {
        if (nick == null || account == null || account.isEmpty() ||
                "0".equals(account) || "*".equals(account))
            return;
        putCaseInsensitive(accounts, nick, account);
        putCaseInsensitive(currentAccounts, nick, account);
    }

    /** Keeps account identity attached to an observed live NICK change. */
    public synchronized void renameNick(String oldNick, String newNick) {
        if (oldNick == null || newNick == null || oldNick.equalsIgnoreCase(newNick))
            return;
        String account = getCaseInsensitive(accounts, oldNick);
        removeCaseInsensitive(currentAccounts, oldNick);
        if (account == null)
            return;
        putCaseInsensitive(accounts, newNick, account);
        putCaseInsensitive(currentAccounts, newNick, account);
    }

    public synchronized Map<String, String> snapshotKnownAccounts() {
        return new LinkedHashMap<>(accounts);
    }

    public synchronized Map<String, String> snapshotCurrentAccounts() {
        return new LinkedHashMap<>(currentAccounts);
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
        // Keep historical nick -> account identity for reconnect reconciliation, but never treat it
        // as current presence. Current identities are rebuilt by WHOX on the next connection.
        currentAccounts.clear();
        requests.clear();
    }

    private static String getCaseInsensitive(Map<String, String> values, String nick) {
        if (nick == null)
            return null;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(nick))
                return entry.getValue();
        }
        return null;
    }

    private static void putCaseInsensitive(Map<String, String> values, String nick, String account) {
        removeCaseInsensitive(values, nick);
        values.put(nick, account);
    }

    private static void removeCaseInsensitive(Map<String, String> values, String nick) {
        if (nick == null)
            return;
        String found = null;
        for (String key : values.keySet()) {
            if (key.equalsIgnoreCase(nick)) {
                found = key;
                break;
            }
        }
        if (found != null)
            values.remove(found);
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
