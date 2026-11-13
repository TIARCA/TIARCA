package io.mrarm.chatlib.irc.handlers;

import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.dto.WhoisInfo;
import io.mrarm.chatlib.dto.WhoisStatusMessageInfo;
import io.mrarm.chatlib.irc.*;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class WhoisCommandHandler extends RequestResponseCommandHandler<String, WhoisInfo> {

    public static final int RPL_WHOISUSER = 311;
    public static final int RPL_WHOISSERVER = 312;
    public static final int RPL_WHOISOPERATOR = 313;
    public static final int RPL_WHOISIDLE = 317;
    public static final int RPL_ENDOFWHOIS = 318;
    public static final int RPL_WHOISCHANNELS = 319;
    public static final int RPL_WHOISACCOUNT = 330;
    public static final int RPL_WHOISBOT = 335;
    // Optional InspIRCd geoban numeric: <client> <nick> <alpha2> :country name.
    public static final int RPL_WHOISCOUNTRY = 344;
    public static final int RPL_WHOISSECURE = 671;

    public static final int ERR_NOSUCHNICK = 401;

    private static final long WHOIS_TIMEOUT_MILLIS = 15000L;
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "IRC WHOIS timeout");
                thread.setDaemon(true);
                return thread;
            });

    public interface OrphanServerReplyListener {
        void onOrphanServerReply(List<String> params);
    }

    private final Map<String, WhoisInfo.Builder> currentReply = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TimeoutRequest> pendingTimeouts = new ConcurrentHashMap<>();
    private final long timeoutMillis;
    private volatile OrphanServerReplyListener orphanServerReplyListener;

    private static final class TimeoutRequest {
        volatile ScheduledFuture<?> future;
    }

    public void setOrphanServerReplyListener(OrphanServerReplyListener listener) {
        orphanServerReplyListener = listener;
    }

    public WhoisCommandHandler(ErrorCommandHandler handler) {
        this(handler, WHOIS_TIMEOUT_MILLIS);
    }

    // Package-private timeout override allows fast, deterministic regression tests.
    WhoisCommandHandler(ErrorCommandHandler handler, long timeoutMillis) {
        super(handler, true);
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public Object[] getHandledCommands() {
        return new Object[] { RPL_WHOISUSER, RPL_WHOISSERVER, RPL_WHOISOPERATOR,
                RPL_WHOISIDLE, RPL_ENDOFWHOIS, RPL_WHOISCHANNELS, RPL_WHOISACCOUNT,
                RPL_WHOISBOT, RPL_WHOISSECURE, RPL_WHOISCOUNTRY };
    }

    @Override
    public int[] getHandledErrors() {
        return new int[] { ERR_NOSUCHNICK, NickCommandHandler.ERR_NONICKNAMEGIVEN };
    }

    @Override
    public void handle(ServerConnectionData connection, MessagePrefix sender, String command,
                       List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);
        String replyNick = CommandHandler.getParamWithCheck(params, 1);
        String nick = replyNick.toLowerCase(Locale.ROOT);
        if (numeric == RPL_WHOISBOT) {
            AutomatedSenderRegistry.rememberBot(connection, nick);
            return;
        }
        WhoisInfo.Builder builder = currentReply.get(nick);
        if (builder == null) {
            if (numeric == RPL_WHOISUSER) {
                builder = new WhoisInfo.Builder();
                currentReply.put(nick, builder);
            } else if (numeric == RPL_WHOISSERVER) {
                OrphanServerReplyListener listener = orphanServerReplyListener;
                if (listener != null)
                    listener.onOrphanServerReply(params);
                return;
            } else {
                throw new InvalidMessageException("Whois data not started with a RPL_WHOISUSER");
            }
        }
        if (numeric == RPL_WHOISUSER) {
            builder.setUserInfo(replyNick, CommandHandler.getParamWithCheck(params, 2),
                    CommandHandler.getParamWithCheck(params, 3), CommandHandler.getParamOrNull(params, 5));
        } else if (numeric == RPL_WHOISSERVER) {
            builder.setServerInfo(CommandHandler.getParamWithCheck(params, 2),
                    CommandHandler.getParamWithCheck(params, 3));
        } else if (numeric == RPL_WHOISOPERATOR) {
            builder.setOperator(true);
        } else if (numeric == RPL_WHOISIDLE) {
            try {
                builder.setIdle(Integer.parseInt(CommandHandler.getParamWithCheck(params, 2)));
            } catch (NumberFormatException e) {
                throw new InvalidMessageException("Bad idle time");
            }
        } else if (numeric == RPL_WHOISCHANNELS) {
            for (String channel : CommandHandler.getParamWithCheck(params, 2).split(" ")) {
                NickWithPrefix p = connection.getNickPrefixParser().parse(connection, channel);
                builder.addChannel(new WhoisInfo.ChannelWithNickPrefixes(p.getNick(), p.getNickPrefixes()));
            }
        } else if (numeric == RPL_WHOISACCOUNT) {
            builder.setAccount(CommandHandler.getParamWithCheck(params, 2));
        } else if (numeric == RPL_WHOISCOUNTRY) {
            // 344 is not universal. Accept only the explicit two-letter value attached
            // to this ongoing WHOIS, not the country name or a guessed DNS suffix.
            String country = CommandHandler.getParamOrNull(params, 2);
            if (country != null && country.matches("(?i)[A-Z]{2}"))
                builder.setCountryCode(country.toUpperCase(Locale.ROOT));
        } else if (numeric == RPL_WHOISSECURE) {
            builder.setSecure(true);
        } else if (numeric == RPL_ENDOFWHOIS) {
            cancelTimeout(nick);
            currentReply.remove(nick);
            WhoisInfo whoisInfo = builder.build();
            // A completed WHOIS is the freshest server-confirmed away state. Clear stale
            // away flags as well when no RPL_AWAY (301) was included in this reply.
            connection.setUserAway(whoisInfo.getNick(), whoisInfo.getUser(), whoisInfo.getHost(),
                    whoisInfo.getAwayMessage() != null, whoisInfo.getAwayMessage());
            onResponse(nick, whoisInfo);
            connection.getServerStatusData().addMessage(new WhoisStatusMessageInfo(
                    sender != null ? sender.getServerName() : null, new Date(), whoisInfo));
        }
    }

    @Override
    public boolean onError(int commandId, List<String> params) {
        if (commandId == NickCommandHandler.ERR_NONICKNAMEGIVEN)
            return onError(null, commandId, CommandHandler.getParamOrNull(params, 1), false);
        if (params.size() <= 1)
            return false;
        String nick = params.get(1).toLowerCase(Locale.ROOT);
        boolean handled = onError(nick, commandId, CommandHandler.getParamOrNull(params, 2), false);
        if (handled)
            clearRequestState(nick);
        return handled;
    }

    @Override
    public void onDisconnected() {
        for (String nick : pendingTimeouts.keySet())
            cancelTimeout(nick);
        super.onDisconnected();
        currentReply.clear();
    }

    @Override
    public boolean onRequested(String i, Callback<WhoisInfo> callback,
                               ErrorCallback<String> errorCallback) {
        String nick = i.toLowerCase(Locale.ROOT);
        boolean shouldSend = super.onRequested(nick, callback, errorCallback);
        if (shouldSend) {
            TimeoutRequest timeout = new TimeoutRequest();
            pendingTimeouts.put(nick, timeout);
            timeout.future = TIMEOUT_EXECUTOR.schedule(
                    () -> onWhoisTimeout(nick, timeout), timeoutMillis, TimeUnit.MILLISECONDS);
        }
        return shouldSend;
    }

    private void onWhoisTimeout(String nick, TimeoutRequest timeout) {
        // A delayed timeout must never cancel a newer WHOIS for the same nickname.
        if (!pendingTimeouts.remove(nick, timeout))
            return;
        currentReply.remove(nick);
        onError(nick, -1, "WHOIS timed out", true);
    }

    private void clearRequestState(String nick) {
        cancelTimeout(nick);
        currentReply.remove(nick);
    }

    private void cancelTimeout(String nick) {
        TimeoutRequest timeout = pendingTimeouts.remove(nick);
        if (timeout != null && timeout.future != null)
            timeout.future.cancel(false);
    }

    public void onAwayMessage(String nick, String message) {
        WhoisInfo.Builder builder = currentReply.get(nick.toLowerCase(Locale.ROOT));
        if (builder != null)
            builder.setAway(message);
    }
}
