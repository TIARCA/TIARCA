package io.mrarm.irc.irc;

import java.util.ArrayList;
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

/** Captures channel/user MODE snapshots while preserving chatlib's normal MODE handling. */
public class ChannelModeSnapshotHandler implements CommandHandler {

    public interface Callback { void onModes(Snapshot snapshot); }

    public interface ModeListener {
        void onModeCommand(ServerConnectionData connection, MessagePrefix sender, List<String> params);
    }

    public static class Snapshot {
        public final Set<Character> active = new HashSet<>();
        public final Map<Character, String> values = new HashMap<>();
    }

    private final CommandHandler delegate;
    private final Map<String, Callback> callbacks = new HashMap<>();
    private Callback userCallback;
    private String requestedUserNick;
    private final List<ModeListener> modeListeners = new ArrayList<>();

    public ChannelModeSnapshotHandler(CommandHandler delegate) {
        this.delegate = delegate;
    }

    public void addModeListener(ModeListener listener) {
        if (listener == null) return;
        synchronized (modeListeners) {
            if (!modeListeners.contains(listener))
                modeListeners.add(listener);
        }
    }

    public void removeModeListener(ModeListener listener) {
        if (listener == null) return;
        synchronized (modeListeners) {
            modeListeners.remove(listener);
        }
    }

    public synchronized void request(String channel, Callback callback) {
        if (channel != null && callback != null)
            callbacks.put(channel.toLowerCase(Locale.ROOT), callback);
    }

    public synchronized void cancel(String channel, Callback callback) {
        if (channel == null) return;
        String key = channel.toLowerCase(Locale.ROOT);
        if (callbacks.get(key) == callback) callbacks.remove(key);
    }

    public synchronized void requestUserModes(String nick, Callback callback) {
        requestedUserNick = nick;
        userCallback = callback;
    }

    public synchronized void cancelUserModes(Callback callback) {
        if (userCallback == callback) {
            userCallback = null;
            requestedUserNick = null;
        }
    }

    @Override public Object[] getHandledCommands() { return new Object[] { "MODE", 221, 324 }; }

    @Override
    public void handle(ServerConnectionData connection, MessagePrefix sender, String command,
                       List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);
        if (numeric == 221) {
            handleUserModeReply(params);
            return;
        }
        if (numeric != 324) {
            notifyModeListeners(connection, sender, params);
            if (delegate != null) delegate.handle(connection, sender, command, params, tags);
            return;
        }
        String channel = CommandHandler.getParamWithCheck(params, 1);
        String modeText = CommandHandler.getParamWithCheck(params, 2);
        Snapshot snapshot = new Snapshot();
        ModeList listModes = connection.getSupportList().getSupportedListChannelModes();
        ModeList alwaysValue = connection.getSupportList().getSupportedValueExactUnsetChannelModes();
        ModeList setValue = connection.getSupportList().getSupportedValueChannelModes();
        boolean adding = true;
        int valueIndex = 3;
        for (int i = 0; i < modeText.length(); i++) {
            char mode = modeText.charAt(i);
            if (mode == '+') { adding = true; continue; }
            if (mode == '-') { adding = false; continue; }
            boolean takesValue = listModes.contains(mode) || alwaysValue.contains(mode) ||
                    (adding && setValue.contains(mode));
            String value = takesValue && valueIndex < params.size() ? params.get(valueIndex++) : null;
            if (adding) {
                snapshot.active.add(mode);
                if (value != null) snapshot.values.put(mode, value);
            }
        }
        Callback callback;
        synchronized (this) { callback = callbacks.remove(channel.toLowerCase(Locale.ROOT)); }
        if (callback != null) callback.onModes(snapshot);
        try {
            if (delegate != null) delegate.handle(connection, sender, command, params, tags);
        } catch (RuntimeException ignored) {
            // Snapshot is still valid if the legacy parser does not know a server-specific mode.
        }
    }

    private void handleUserModeReply(List<String> params) throws InvalidMessageException {
        String nick = CommandHandler.getParamWithCheck(params, 0);
        String text = CommandHandler.getParamWithCheck(params, 1);
        Snapshot snapshot = new Snapshot();
        boolean adding = true;
        for (int i = 0; i < text.length(); i++) {
            char mode = text.charAt(i);
            if (mode == '+') { adding = true; continue; }
            if (mode == '-') { adding = false; continue; }
            if (adding) snapshot.active.add(mode); else snapshot.active.remove(mode);
        }
        Callback callback = null;
        synchronized (this) {
            if (userCallback != null && (requestedUserNick == null || requestedUserNick.equalsIgnoreCase(nick))) {
                callback = userCallback;
                userCallback = null;
                requestedUserNick = null;
            }
        }
        if (callback != null) callback.onModes(snapshot);
    }

    private void notifyModeListeners(ServerConnectionData connection, MessagePrefix sender,
                                     List<String> params) {
        List<ModeListener> listenersCopy;
        synchronized (modeListeners) {
            if (modeListeners.isEmpty()) return;
            listenersCopy = new ArrayList<>(modeListeners);
        }
        for (ModeListener listener : listenersCopy) {
            try { listener.onModeCommand(connection, sender, params); } catch (Exception ignored) { }
        }
    }
}
