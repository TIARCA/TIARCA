package io.mrarm.irc.irc;

import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import io.mrarm.chatlib.ChatApi;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.config.AppSettings;

/**
 * Per-connection state for the local user's manual AWAY status.
 *
 * The visible state changes only after the server confirms AWAY with numeric 306 or 305.
 * The requested away message is kept locally because those confirmation numerics do not echo it.
 */
public final class AwayStateManager implements ServerConnectionInfo.InfoChangeListener {

    public interface Listener {
        void onAwayStateChanged(AwayStateManager manager);
    }

    private static final long NICK_REQUEST_TIMEOUT_MS = 10_000L;
    private static final WeakHashMap<ServerConnectionInfo, AwayStateManager> INSTANCES =
            new WeakHashMap<>();

    public static synchronized AwayStateManager get(ServerConnectionInfo connection) {
        AwayStateManager manager = INSTANCES.get(connection);
        if (manager == null) {
            manager = new AwayStateManager(connection);
            INSTANCES.put(connection, manager);
        }
        manager.attachIfNeeded();
        return manager;
    }

    private final WeakReference<ServerConnectionInfo> connectionRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();

    private IRCConnection attachedConnection;
    private boolean away;
    private String awayMessage;
    private String pendingAwayMessage;
    private Boolean pendingNicknameAway;
    private String pendingAwayNickname;

    private boolean awayNickApplied;
    private String nickBeforeAway;
    private String awayNickCurrent;
    private String pendingNickTarget;
    private boolean pendingNickRestore;

    private AwayStateManager(ServerConnectionInfo connection) {
        connectionRef = new WeakReference<>(connection);
        connection.addOnChannelInfoChangeListener(this);
    }

    private synchronized void attachIfNeeded() {
        ServerConnectionInfo connection = connectionRef.get();
        if (connection == null)
            return;
        ChatApi api = connection.getApiInstance();
        if (!(api instanceof IRCConnection))
            return;
        IRCConnection irc = (IRCConnection) api;
        if (attachedConnection == irc)
            return;

        SelfAwayCommandHandler handler = new SelfAwayCommandHandler(this::onServerAwayState);
        irc.getServerConnectionData().getCommandHandlerList().registerHandler(handler);
        irc.getUserInfoApi().subscribeNickChanges((info, oldNick, newNick) ->
                onNickChanged(oldNick, newNick), null, null);
        attachedConnection = irc;
    }

    public synchronized boolean isAway() {
        return away;
    }

    public synchronized String getAwayMessage() {
        return awayMessage;
    }

    public synchronized boolean isAwayNickApplied() {
        return awayNickApplied;
    }

    /** Nickname shown in the Away dialog before a nickname-away request is sent. */
    public String getSuggestedAwayNickname() {
        ServerConnectionInfo connection = connectionRef.get();
        if (connection == null || connection.getApiInstance() == null)
            return "";
        synchronized (this) {
            if (awayNickApplied && awayNickCurrent != null && !awayNickCurrent.isEmpty())
                return awayNickCurrent;
        }
        return buildAwayNickname(connection.getUserNick(), AppSettings.getAwayNickSuffix());
    }

    public void addListener(Listener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /** Keeps the old call path used by the status bar and other simple AWAY actions. */
    public boolean requestAway(boolean enable, String message, boolean useAwayNickname) {
        return requestAway(enable, message, useAwayNickname, null);
    }

    /** Sends AWAY/UNAWAY without changing visible state until 306/305 arrives. */
    public boolean requestAway(boolean enable, String message, boolean useAwayNickname,
                               String requestedAwayNickname) {
        attachIfNeeded();
        ServerConnectionInfo connection = connectionRef.get();
        if (connection == null || !connection.isConnected() ||
                !(connection.getApiInstance() instanceof IRCConnection))
            return false;

        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        if (enable) {
            String normalized = sanitizeMessage(message);
            if (normalized.isEmpty())
                normalized = sanitizeMessage(AppSettings.getDefaultAwayMessage());
            if (normalized.isEmpty())
                normalized = "Away";
            String normalizedNickname = useAwayNickname
                    ? sanitizeNickname(requestedAwayNickname) : null;
            synchronized (this) {
                pendingAwayMessage = normalized;
                pendingNicknameAway = useAwayNickname;
                pendingAwayNickname = normalizedNickname;
            }
            irc.sendCommandRaw("AWAY :" + normalized, null, null);
        } else {
            synchronized (this) {
                pendingAwayMessage = null;
                pendingNicknameAway = false;
                pendingAwayNickname = null;
            }
            irc.sendCommandRaw("AWAY", null, null);
        }
        return true;
    }

    private void onServerAwayState(boolean newAway) {
        boolean shouldUseAwayNick = false;
        boolean shouldRestoreNick = false;
        String requestedAwayNickname = null;
        synchronized (this) {
            away = newAway;
            if (newAway) {
                if (pendingAwayMessage != null)
                    awayMessage = pendingAwayMessage;
                shouldUseAwayNick = pendingNicknameAway != null
                        ? pendingNicknameAway : awayNickApplied;
                requestedAwayNickname = pendingAwayNickname;
                shouldRestoreNick = !shouldUseAwayNick && awayNickApplied;
            } else {
                awayMessage = null;
                shouldRestoreNick = awayNickApplied;
            }
            pendingAwayMessage = null;
            pendingNicknameAway = null;
            pendingAwayNickname = null;
        }

        syncOwnUserAwayState(newAway);
        if (newAway && shouldUseAwayNick)
            applyAwayNickname(requestedAwayNickname);
        else if (shouldRestoreNick)
            restoreNickname();
        notifyListeners();
    }

    /** Reuses the existing user-away model so our own nick is dimmed in channel member lists too. */
    private void syncOwnUserAwayState(boolean newAway) {
        ServerConnectionInfo connection = connectionRef.get();
        if (connection == null || !(connection.getApiInstance() instanceof IRCConnection))
            return;
        ServerConnectionData data = ((IRCConnection) connection.getApiInstance())
                .getServerConnectionData();
        String nick = data.getUserNick();
        if (nick == null || nick.isEmpty())
            return;
        try {
            data.setUserAway(nick, data.getUserUser(), data.getUserHost(), newAway,
                    newAway ? getAwayMessage() : null);
        } catch (RuntimeException ignored) {
            // The connection may confirm AWAY before the local user has been resolved in NAMES.
        }
    }

    private void applyAwayNickname(String requestedNickname) {
        ServerConnectionInfo connection = connectionRef.get();
        if (connection == null || !(connection.getApiInstance() instanceof IRCConnection))
            return;
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        ServerConnectionData data = irc.getServerConnectionData();
        String currentNick = data.getUserNick();
        if (currentNick == null || currentNick.isEmpty())
            return;

        String target = sanitizeNickname(requestedNickname);
        if (target.isEmpty())
            target = buildAwayNickname(currentNick, AppSettings.getAwayNickSuffix());
        if (target.isEmpty() || target.equalsIgnoreCase(currentNick))
            return;

        synchronized (this) {
            if (pendingNickTarget != null)
                return;
            if (!awayNickApplied)
                nickBeforeAway = currentNick;
            pendingNickTarget = target;
            pendingNickRestore = false;
        }
        irc.sendCommandRaw("NICK " + target, null, null);
        scheduleNickRequestTimeout(target, false);
    }

    private void restoreNickname() {
        ServerConnectionInfo connection = connectionRef.get();
        if (connection == null || !(connection.getApiInstance() instanceof IRCConnection))
            return;
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        String target;
        synchronized (this) {
            if (!awayNickApplied || nickBeforeAway == null || nickBeforeAway.isEmpty() ||
                    pendingNickTarget != null)
                return;
            String currentNick = irc.getServerConnectionData().getUserNick();
            if (currentNick != null && currentNick.equalsIgnoreCase(nickBeforeAway)) {
                clearAwayNickTrackingLocked();
                return;
            }
            target = nickBeforeAway;
            pendingNickTarget = target;
            pendingNickRestore = true;
        }
        irc.sendCommandRaw("NICK " + target, null, null);
        scheduleNickRequestTimeout(target, true);
    }

    /**
     * Nick validation remains server-authoritative. If a network rejects the requested suffix or
     * restore nickname, release the pending state so a later attempt is not permanently blocked.
     */
    private void scheduleNickRequestTimeout(String target, boolean restoring) {
        mainHandler.postDelayed(() -> {
            boolean changed = false;
            synchronized (AwayStateManager.this) {
                if (pendingNickTarget == null || !pendingNickTarget.equalsIgnoreCase(target) ||
                        pendingNickRestore != restoring)
                    return;
                pendingNickTarget = null;
                pendingNickRestore = false;
                if (!restoring && !awayNickApplied)
                    nickBeforeAway = null;
                changed = true;
            }
            if (changed)
                notifyListeners();
        }, NICK_REQUEST_TIMEOUT_MS);
    }

    private void onNickChanged(String oldNick, String newNick) {
        boolean changed = false;
        synchronized (this) {
            if (pendingNickTarget != null && newNick != null &&
                    newNick.equalsIgnoreCase(pendingNickTarget)) {
                if (pendingNickRestore) {
                    clearAwayNickTrackingLocked();
                } else {
                    awayNickApplied = true;
                    awayNickCurrent = newNick;
                    pendingNickTarget = null;
                    pendingNickRestore = false;
                }
                changed = true;
            } else if (awayNickApplied && oldNick != null && awayNickCurrent != null &&
                    oldNick.equalsIgnoreCase(awayNickCurrent)) {
                // A manual or network-driven nick change while away must cancel auto-restore.
                clearAwayNickTrackingLocked();
                changed = true;
            } else if (pendingNickTarget != null && oldNick != null) {
                // A different successful NICK means our pending target was not the applied change.
                pendingNickTarget = null;
                pendingNickRestore = false;
                if (!awayNickApplied)
                    nickBeforeAway = null;
            }
        }
        if (changed)
            notifyListeners();
    }

    private void clearAwayNickTrackingLocked() {
        awayNickApplied = false;
        nickBeforeAway = null;
        awayNickCurrent = null;
        pendingNickTarget = null;
        pendingNickRestore = false;
    }

    @Override
    public void onConnectionInfoChanged(ServerConnectionInfo connection) {
        attachIfNeeded();
        if (connection.isConnected())
            return;
        boolean changed;
        synchronized (this) {
            changed = away || awayMessage != null || awayNickApplied;
            away = false;
            awayMessage = null;
            pendingAwayMessage = null;
            pendingNicknameAway = null;
            pendingAwayNickname = null;
            clearAwayNickTrackingLocked();
        }
        if (changed)
            notifyListeners();
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            List<Listener> snapshot;
            synchronized (listeners) {
                snapshot = new ArrayList<>(listeners);
            }
            for (Listener listener : snapshot)
                listener.onAwayStateChanged(this);
        });
    }

    static String sanitizeMessage(String value) {
        if (value == null)
            return "";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    static String sanitizeSuffix(String value) {
        if (value == null)
            return "";
        String suffix = value.replace("\r", "").replace("\n", "").trim();
        for (int i = 0; i < suffix.length(); i++) {
            if (Character.isWhitespace(suffix.charAt(i)))
                return "";
        }
        return suffix;
    }

    static String sanitizeNickname(String value) {
        if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)
            return "";
        String nick = value.trim();
        for (int i = 0; i < nick.length(); i++) {
            if (Character.isWhitespace(nick.charAt(i)))
                return "";
        }
        return nick;
    }

    static String buildAwayNickname(String currentNick, String suffix) {
        String nick = sanitizeNickname(currentNick);
        String cleanSuffix = sanitizeSuffix(suffix);
        if (nick.isEmpty() || cleanSuffix.isEmpty())
            return "";
        return nick + cleanSuffix;
    }
}
