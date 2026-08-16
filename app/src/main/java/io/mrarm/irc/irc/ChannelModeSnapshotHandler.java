package io.mrarm.irc.irc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;

/** Captures RPL_CHANNELMODEIS while preserving chatlib's normal mode handling. */
public class ChannelModeSnapshotHandler implements CommandHandler {

    public interface Callback { void onModes(Snapshot snapshot); }

    public static class Snapshot {
        public final Set<Character> active = new HashSet<>();
        public final Map<Character, String> values = new HashMap<>();
    }

    private final CommandHandler delegate;
    private final Map<String, Callback> callbacks = new HashMap<>();

    public ChannelModeSnapshotHandler(CommandHandler delegate) {
        this.delegate = delegate;
    }

    public synchronized void request(String channel, Callback callback) {
        if (channel != null && callback != null)
            callbacks.put(channel.toLowerCase(Locale.ROOT), callback);
    }

    public synchronized void cancel(String channel, Callback callback) {
        if (channel == null)
            return;
        String key = channel.toLowerCase(Locale.ROOT);
        if (callbacks.get(key) == callback)
            callbacks.remove(key);
    }

    @Override public Object[] getHandledCommands() { return new Object[] { "MODE", 324 }; }

    @Override
    public void handle(ServerConnectionData connection, MessagePrefix sender, String command,
                       List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);
        // Capture the reply before forwarding it. Some IRCds advertise channel modes
        // unknown to this old chatlib version; its delegate can reject those modes,
        // but the operator dialog should still receive the raw 324 snapshot.
        if (numeric != 324) {
            if (delegate != null)
                delegate.handle(connection, sender, command, params, tags);
            return;
        }
        String channel = CommandHandler.getParamWithCheck(params, 1);
        String modeText = CommandHandler.getParamWithCheck(params, 2);
        Snapshot snapshot = new Snapshot();
        ModeList listModes = connection.getSupportList().getSupportedListChannelModes();
        ModeList alwaysValue = connection.getSupportList()
                .getSupportedValueExactUnsetChannelModes();
        ModeList setValue = connection.getSupportList().getSupportedValueChannelModes();
        boolean adding = true;
        int valueIndex = 3;
        for (int i = 0; i < modeText.length(); i++) {
            char mode = modeText.charAt(i);
            if (mode == '+') { adding = true; continue; }
            if (mode == '-') { adding = false; continue; }
            boolean takesValue = listModes.contains(mode) || alwaysValue.contains(mode) ||
                    (adding && setValue.contains(mode));
            String value = takesValue && valueIndex < params.size()
                    ? params.get(valueIndex++) : null;
            if (adding) {
                snapshot.active.add(mode);
                if (value != null)
                    snapshot.values.put(mode, value);
            }
        }
        Callback callback;
        synchronized (this) {
            callback = callbacks.remove(channel.toLowerCase(Locale.ROOT));
        }
        if (callback != null)
            callback.onModes(snapshot);
        try {
            if (delegate != null)
                delegate.handle(connection, sender, command, params, tags);
        } catch (RuntimeException ignored) {
            // The snapshot above remains usable even when the legacy mode parser does not
            // understand a server-specific channel mode.
        }
    }
}
