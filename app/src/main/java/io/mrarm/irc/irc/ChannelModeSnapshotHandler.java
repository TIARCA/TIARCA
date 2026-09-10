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
    private final Set<Character> activeUserModes = new HashSet<>();
    private final Set<Character> knownUserModes = new HashSet<>();
    private boolean hasFullUserModeSnapshot;

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

    /** Returns whether a mode is currently known to be active for our own IRC user. */
    public synchronized boolean isUserModeActive(char mode) {
        return activeUserModes.contains(mode);
    }

    /** Returns whether the session has enough state to decide this user mode without a query. */
    public synchronized boolean isUserModeKnown(char mode) {
        return hasFullUserModeSnapshot || knownUserModes.contains(mode);
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
            updateCurrentUserModes(connection, params);
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
        String text = findModeText(params);
        if (text == null)
            throw new InvalidMessageException();
        Snapshot snapshot = parseUserModes(text);
        Callback callback = null;
        synchronized (this) {
            activeUserModes.clear();
            activeUserModes.addAll(snapshot.active);
            knownUserModes.clear();
            hasFullUserModeSnapshot = true;
            if (userCallback != null && (requestedUserNick == null || requestedUserNick.equalsIgnoreCase(nick))) {
                callback = userCallback;
                userCallback = null;
                requestedUserNick = null;
            }
        }
        if (callback != null) callback.onModes(snapshot);
    }

    private void updateCurrentUserModes(ServerConnectionData connection, List<String> params) {
        if (connection == null || params == null || params.size() < 2)
            return;
        String ownNick = connection.getUserNick();
        if (ownNick == null || !ownNick.equalsIgnoreCase(params.get(0)))
            return;
        String text = params.get(1);
        if (text == null || text.isEmpty())
            return;
        boolean adding = true;
        synchronized (this) {
            for (int i = 0; i < text.length(); i++) {
                char mode = text.charAt(i);
                if (mode == '+') { adding = true; continue; }
                if (mode == '-') { adding = false; continue; }
                if (!Character.isLetter(mode)) continue;
                knownUserModes.add(mode);
                if (adding) activeUserModes.add(mode); else activeUserModes.remove(mode);
            }
        }
    }

    private static Snapshot parseUserModes(String text) {
        Snapshot snapshot = new Snapshot();
        boolean adding = true;
        for (int i = 0; i < text.length(); i++) {
            char mode = text.charAt(i);
            if (mode == '+') { adding = true; continue; }
            if (mode == '-') { adding = false; continue; }
            if (!Character.isLetter(mode)) continue;
            if (adding) snapshot.active.add(mode); else snapshot.active.remove(mode);
        }
        return snapshot;
    }

    private static String findModeText(List<String> params) {
        if (params == null) return null;
        for (int i = 1; i < params.size(); i++) {
            String value = params.get(i);
            if (value != null && !value.isEmpty() &&
                    (value.charAt(0) == '+' || value.charAt(0) == '-'))
                return value;
        }
        return null;
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
