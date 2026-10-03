package io.mrarm.irc.util;

import android.os.Handler;
import android.os.Looper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import io.mrarm.chatlib.irc.CommandHandlerList;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.irc.WhoXAccountHandler;

/** Resolves the account names used by Simosnap's public avatar store. */
public final class SimosnapAvatarManager {

    private static final long REFRESH_INTERVAL_MS = 60_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_REQUEST_HISTORY = 512;
    private static final Map<String, Long> LAST_REQUEST =
            new LinkedHashMap<String, Long>(64, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_REQUEST_HISTORY;
                }
            };

    private SimosnapAvatarManager() { }

    public static boolean isSupported(ServerConnectionInfo connection) {
        return connection != null && isSupportedHost(connection.getServerAddress());
    }

    static boolean isSupportedHost(String address) {
        if (address == null)
            return false;
        String host = address.trim().toLowerCase(Locale.ROOT);
        return host.equals("simosnap.org") || host.endsWith(".simosnap.org") ||
                host.equals("simosnap.com") || host.endsWith(".simosnap.com");
    }

    public static String getAccount(ServerConnectionInfo connection, String nick) {
        // WHOX accounts also serve private-chat identity tracking on other networks.
        // They must never be interpreted as Simosnap avatar identities there.
        if (!isSupported(connection))
            return null;
        WhoXAccountHandler handler = getHandler(connection, false);
        return handler == null ? null : handler.getAccount(nick);
    }

    public static void rememberAccount(ServerConnectionInfo connection, String nick,
                                       String account) {
        if (!isSupported(connection) || account == null)
            return;
        WhoXAccountHandler handler = getHandler(connection, true);
        if (handler != null)
            handler.remember(nick, account);
    }

    /**
     * Prefers the account reported by WHOIS, but falls back to the account already learned via
     * WHOX. This keeps avatar resolution stable on servers that omit RPL_WHOISACCOUNT for some
     * users while their channel WHOX data is still available.
     */
    public static String resolveAccount(ServerConnectionInfo connection, String nick,
                                        String whoisAccount) {
        if (!isSupported(connection))
            return null;
        String account = chooseAccount(getAccount(connection, nick), whoisAccount);
        if (whoisAccount != null && !whoisAccount.trim().isEmpty())
            rememberAccount(connection, nick, whoisAccount);
        return account;
    }

    static String chooseAccount(String knownAccount, String whoisAccount) {
        if (whoisAccount != null && !whoisAccount.trim().isEmpty())
            return whoisAccount;
        return knownAccount;
    }

    public static void requestChannelAccounts(ServerConnectionInfo connection, String channel,
                                              Runnable callback) {
        if (!isSupported(connection) || channel == null ||
                !(connection.getApiInstance() instanceof IRCConnection))
            return;
        String key = connection.getUUID() + "\n" + channel.toLowerCase(Locale.ROOT);
        long now = android.os.SystemClock.elapsedRealtime();
        synchronized (LAST_REQUEST) {
            Long last = LAST_REQUEST.get(key);
            if (last != null && now - last < REFRESH_INTERVAL_MS)
                return;
            LAST_REQUEST.put(key, now);
        }
        WhoXAccountHandler handler = getHandler(connection, true);
        if (handler == null)
            return;
        String token = handler.begin(channel, () -> {
            if (callback != null)
                MAIN.post(callback);
        });
        ((IRCConnection) connection.getApiInstance()).sendCommandRaw(
                "WHO " + channel + " %tna," + token, null, null);
    }

    private static WhoXAccountHandler getHandler(ServerConnectionInfo connection,
                                                 boolean create) {
        if (connection == null || !(connection.getApiInstance() instanceof IRCConnection))
            return null;
        CommandHandlerList handlers = ((IRCConnection) connection.getApiInstance())
                .getServerConnectionData().getCommandHandlerList();
        WhoXAccountHandler handler = handlers.getHandler(WhoXAccountHandler.class);
        if (handler == null && create) {
            handler = new WhoXAccountHandler();
            handlers.registerHandler(handler);
        }
        return handler;
    }
}
