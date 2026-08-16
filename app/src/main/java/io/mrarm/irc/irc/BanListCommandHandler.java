package io.mrarm.irc.irc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;

/** Collects the standard 367/368 channel ban-list numerics. */
public class BanListCommandHandler implements CommandHandler {

    public interface Callback { void onBanList(List<Entry> entries); }

    public static class Entry {
        public final String mask;
        public final String setter;
        public final long timestamp;
        public Entry(String mask, String setter, long timestamp) {
            this.mask = mask;
            this.setter = setter;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, Callback> callbacks = new HashMap<>();
    private final Map<String, List<Entry>> results = new HashMap<>();

    public synchronized void request(String channel, Callback callback) {
        String key = channel.toLowerCase(Locale.ROOT);
        callbacks.put(key, callback);
        results.put(key, new ArrayList<>());
    }

    @Override public Object[] getHandledCommands() { return new Object[] { 367, 368 }; }

    @Override
    public synchronized void handle(ServerConnectionData connection, MessagePrefix sender,
                                    String command, List<String> params,
                                    Map<String, String> tags) throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);
        String channel = CommandHandler.getParamWithCheck(params, 1);
        String key = channel.toLowerCase(Locale.ROOT);
        if (!callbacks.containsKey(key))
            return;
        if (numeric == 367) {
            String mask = CommandHandler.getParamWithCheck(params, 2);
            String setter = CommandHandler.getParamOrDefault(params, 3, "");
            long timestamp = 0;
            try { timestamp = Long.parseLong(CommandHandler.getParamOrDefault(params, 4, "0")); }
            catch (NumberFormatException ignored) { }
            results.get(key).add(new Entry(mask, setter, timestamp));
        } else {
            Callback callback = callbacks.remove(key);
            List<Entry> entries = results.remove(key);
            callback.onBanList(entries == null ? new ArrayList<>() : entries);
        }
    }
}
