package io.mrarm.irc.irc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.IRCCaseMapping;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.irc.config.ServerConfigData;

/** Standard IRC MONITOR state. Configuration is persisted; presence is connection runtime state. */
public final class MonitoredUsersManager implements CommandHandler {
    private static final String DEBUG_TAG = "TIARCA-MONITOR-DEBUG";
    public static final int RPL_MONONLINE = 730, RPL_MONOFFLINE = 731, RPL_MONLIST = 732,
            RPL_ENDOFMONLIST = 733, ERR_MONLISTFULL = 734;

    public enum SyncState { UNINITIALIZED, SYNCING, READY }
    public enum PresenceUpdate { INITIAL_STATE, BECAME_ONLINE, BECAME_OFFLINE }

    public interface Listener {
        void onPresenceUpdated(ServerConfigData.MonitoredUser user, PresenceUpdate update);
        void onSyncStateChanged(SyncState state);
        default void onMonitoredUserChanged(ServerConfigData.MonitoredUser user) { }
    }

    public interface ConfigPersister { void persist() throws IOException; }

    private final ServerConfigData config;
    private final ConfigPersister persister;
    private final List<Listener> listeners = new ArrayList<>();
    private final Set<ServerConfigData.MonitoredUser> synchronizedUsers = new LinkedHashSet<>();
    private final Set<ServerConfigData.MonitoredUser> usersOverLimit = new LinkedHashSet<>();
    private SyncState syncState = SyncState.UNINITIALIZED;
    private boolean serverLimitReached;
    private String lastError;

    public MonitoredUsersManager(ServerConfigData config) { this(config, null); }

    public MonitoredUsersManager(ServerConfigData config, ConfigPersister persister) {
        this.config = config;
        this.persister = persister;
    }

    @Override public Object[] getHandledCommands() { return new Object[] {730, 731, 732, 733, 734}; }
    public boolean isSupported(ServerConnectionData data) { return data.getSupportList().getMonitorLimit() >= 0; }
    public int getLimit(ServerConnectionData data) { return data.getSupportList().getMonitorLimit(); }
    public SyncState getSyncState() { return syncState; }
    public String getLastError() { return lastError; }
    public boolean hasServerLimitReached() { return serverLimitReached; }

    public List<ServerConfigData.MonitoredUser> getMonitoredUsers() {
        return config.monitoredUsers == null ? Collections.emptyList() : Collections.unmodifiableList(config.monitoredUsers);
    }

    public List<ServerConfigData.MonitoredUser> getSynchronizedUsers() {
        return Collections.unmodifiableList(new ArrayList<>(synchronizedUsers));
    }

    public List<ServerConfigData.MonitoredUser> getUsersOverLimit() {
        return Collections.unmodifiableList(new ArrayList<>(usersOverLimit));
    }

    public boolean isSynchronizedWithServer(ServerConfigData.MonitoredUser user) {
        return synchronizedUsers.contains(user);
    }

    public boolean isMonitored(String nick) { return find(nick, IRCCaseMapping.RFC1459) != null; }
    public boolean isMonitored(ServerConnectionData data, String nick) { return find(nick, getCaseMapping(data)) != null; }

    public ServerConfigData.MonitoredUser addMonitoredUser(String nick, boolean notifyOnline, boolean notifyOffline) {
        return addMonitoredUser(null, nick, notifyOnline, notifyOffline);
    }

    public ServerConfigData.MonitoredUser addMonitoredUser(ServerConnectionData data, String nick,
                                                            boolean notifyOnline, boolean notifyOffline) {
        if (nick == null || nick.trim().isEmpty()) throw new IllegalArgumentException("nick");
        ServerConfigData.MonitoredUser existing = find(nick, getCaseMapping(data));
        if (existing != null) {
            existing.notifyOnline = notifyOnline;
            existing.notifyOffline = notifyOffline;
            persistConfiguration();
            notifyUserChanged(existing);
            return existing;
        }
        if (config.monitoredUsers == null) config.monitoredUsers = new ArrayList<>();
        ServerConfigData.MonitoredUser user = new ServerConfigData.MonitoredUser();
        user.nick = nick.trim();
        user.currentNick = user.nick;
        user.notifyOnline = notifyOnline;
        user.notifyOffline = notifyOffline;
        config.monitoredUsers.add(user);
        debug("add nick=" + user.currentNick + " sync=" + syncState +
                " supported=" + (data != null && isSupported(data)));
        persistConfiguration();
        notifyUserChanged(user);
        if (data != null && syncState == SyncState.READY && isSupported(data)) synchronizeAddedUser(data, user);
        return user;
    }

    public boolean removeMonitoredUser(String nick) { return removeMonitoredUser(null, nick); }

    public boolean removeMonitoredUser(ServerConnectionData data, String nick) {
        ServerConfigData.MonitoredUser user = find(nick, getCaseMapping(data));
        if (user == null || config.monitoredUsers == null) return false;
        boolean wasSynchronized = synchronizedUsers.remove(user);
        usersOverLimit.remove(user);
        config.monitoredUsers.remove(user);
        debug("remove nick=" + (user.currentNick == null ? user.nick : user.currentNick) +
                " sync=" + syncState + " serverSynchronized=" + wasSynchronized);
        persistConfiguration();
        notifyUserChanged(user);
        if (wasSynchronized && data != null && isSupported(data))
            send(data, "-", user.currentNick == null ? user.nick : user.currentNick);
        return true;
    }

    public void updateNotificationPreferences(String nick, boolean online, boolean offline) {
        updateNotificationPreferences(null, nick, online, offline);
    }

    public void updateNotificationPreferences(ServerConnectionData data, String nick, boolean online, boolean offline) {
        ServerConfigData.MonitoredUser user = find(nick, getCaseMapping(data));
        if (user == null) throw new IllegalArgumentException("nick");
        user.notifyOnline = online;
        user.notifyOffline = offline;
        debug("notification-preferences nick=" + nick + " online=" + online +
                " offline=" + offline + " sync=" + syncState);
        persistConfiguration();
        notifyUserChanged(user);
    }

    /** Rebuilds runtime presence after registration. Server MONITOR state remains authoritative. */
    public void synchronize(ServerConnectionData data) {
        lastError = null;
        serverLimitReached = false;
        synchronizedUsers.clear();
        usersOverLimit.clear();
        clearRuntimePresence();
        if (!isSupported(data)) {
            lastError = "This server does not support MONITOR.";
            setSyncState(SyncState.UNINITIALIZED);
            return;
        }
        setSyncState(SyncState.SYNCING);
        int limit = getLimit(data);
        IRCCaseMapping mapping = getCaseMapping(data);
        List<String> nicks = new ArrayList<>();
        for (ServerConfigData.MonitoredUser user : getMonitoredUsers()) {
            String nick = user.currentNick == null ? user.nick : user.currentNick;
            if (nick == null || contains(nicks, nick, mapping)) continue;
            if (nicks.size() >= limit) usersOverLimit.add(user);
            else { nicks.add(nick); synchronizedUsers.add(user); }
        }
        if (!nicks.isEmpty()) send(data, "+", join(nicks));
        // S reports presence; the following L/733 pair is the ordered completion marker.
        send(data, "S");
        send(data, "L");
    }

    public void list(ServerConnectionData data) throws IOException {
        if (!isSupported(data)) { lastError = "This server does not support MONITOR."; return; }
        data.getApi().sendCommand("MONITOR", false, "L");
    }

    public void clear(ServerConnectionData data) throws IOException {
        if (!isSupported(data)) { lastError = "This server does not support MONITOR."; return; }
        data.getApi().sendCommand("MONITOR", false, "C");
        synchronizedUsers.clear();
        usersOverLimit.clear();
        clearRuntimePresence();
        setSyncState(SyncState.UNINITIALIZED);
    }

    /** Clears connection runtime data on transport loss without touching saved configuration. */
    public void onDisconnected() {
        synchronizedUsers.clear();
        usersOverLimit.clear();
        clearRuntimePresence();
        setSyncState(SyncState.UNINITIALIZED);
    }

    @Override
    public void handle(ServerConnectionData data, MessagePrefix sender, String command, List<String> params,
                       java.util.Map<String, String> tags) throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);
        debug("numeric=" + numeric + " sync=" + syncState + " params=" + params.size());
        if (numeric == ERR_MONLISTFULL) {
            serverLimitReached = true;
            lastError = "The server MONITOR limit has been reached.";
            return;
        }
        if (numeric == RPL_ENDOFMONLIST) {
            if (syncState == SyncState.SYNCING) setSyncState(SyncState.READY);
            return;
        }
        if (numeric == RPL_MONLIST) return; // 732 lists MONITOR registrations, not presence.
        if (numeric != RPL_MONONLINE && numeric != RPL_MONOFFLINE) return;
        String raw = CommandHandler.getParamOrNull(params, params.size() - 1);
        if (raw == null) return;
        boolean online = numeric == RPL_MONONLINE;
        IRCCaseMapping mapping = getCaseMapping(data);
        for (String item : raw.split(",")) {
            int bang = item.indexOf('!');
            String nick = bang < 0 ? item : item.substring(0, bang);
            ServerConfigData.MonitoredUser user = find(nick, mapping);
            if (user == null) continue;
            boolean changed = user.online != online;
            user.online = online;
            user.currentNick = nick;
            notifyUserChanged(user);
            if (syncState == SyncState.SYNCING) notifyPresence(user, PresenceUpdate.INITIAL_STATE);
            else if (syncState == SyncState.READY && changed)
                notifyPresence(user, online ? PresenceUpdate.BECAME_ONLINE : PresenceUpdate.BECAME_OFFLINE);
        }
    }

    public void onNickChanged(ServerConnectionData data, String oldNick, String newNick) {
        ServerConfigData.MonitoredUser user = find(oldNick, getCaseMapping(data));
        if (user == null || newNick == null || newNick.trim().isEmpty()) return;
        String previousNick = user.currentNick == null ? user.nick : user.currentNick;
        user.currentNick = newNick;
        persistConfiguration();
        notifyUserChanged(user);
        if (!isSupported(data) || !synchronizedUsers.contains(user)) return;
        send(data, "-", previousNick);
        send(data, "+", newNick);
    }

    public void addListener(Listener listener) { if (listener != null && !listeners.contains(listener)) listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }

    private void synchronizeAddedUser(ServerConnectionData data, ServerConfigData.MonitoredUser user) {
        if (synchronizedUsers.size() >= getLimit(data)) { usersOverLimit.add(user); return; }
        synchronizedUsers.add(user);
        send(data, "+", user.currentNick == null ? user.nick : user.currentNick);
    }

    private void setSyncState(SyncState state) {
        if (syncState == state) return;
        syncState = state;
        for (Listener listener : new ArrayList<>(listeners)) listener.onSyncStateChanged(state);
    }

    private void notifyPresence(ServerConfigData.MonitoredUser user, PresenceUpdate update) {
        for (Listener listener : new ArrayList<>(listeners)) listener.onPresenceUpdated(user, update);
    }

    private void notifyUserChanged(ServerConfigData.MonitoredUser user) {
        for (Listener listener : new ArrayList<>(listeners)) listener.onMonitoredUserChanged(user);
    }

    private void clearRuntimePresence() { for (ServerConfigData.MonitoredUser user : getMonitoredUsers()) user.online = false; }

    private void persistConfiguration() {
        if (persister == null) return;
        try {
            debug("persist-start server=" + config.uuid);
            persister.persist();
            debug("persist-complete server=" + config.uuid);
        } catch (IOException e) {
            lastError = "Could not save monitored users.";
            debug("persist-failed server=" + config.uuid + " exception=" +
                    e.getClass().getSimpleName() + " message=" + e.getMessage());
        }
    }

    private void send(ServerConnectionData data, String... params) {
        if (data == null || data.getApi() == null) return;
        try {
            debug("send MONITOR " + joinParams(params) + " sync=" + syncState);
            data.getApi().sendCommand("MONITOR", false, params);
        } catch (IOException e) {
            lastError = "Could not synchronize MONITOR.";
            debug("send-failed MONITOR " + joinParams(params) + " exception=" +
                    e.getClass().getSimpleName() + " message=" + e.getMessage());
        }
    }

    private ServerConfigData.MonitoredUser find(String nick, IRCCaseMapping mapping) {
        if (nick == null) return null;
        for (ServerConfigData.MonitoredUser user : getMonitoredUsers()) {
            String currentNick = user.currentNick == null ? user.nick : user.currentNick;
            if (currentNick != null && mapping.equals(currentNick, nick)) return user;
        }
        return null;
    }

    private static boolean contains(List<String> nicks, String nick, IRCCaseMapping mapping) {
        for (String item : nicks) if (mapping.equals(item, nick)) return true;
        return false;
    }

    private static String join(List<String> nicks) {
        StringBuilder result = new StringBuilder();
        for (String nick : nicks) { if (result.length() > 0) result.append(','); result.append(nick); }
        return result.toString();
    }

    private static String joinParams(String[] params) {
        StringBuilder result = new StringBuilder();
        if (params != null) for (String param : params) {
            if (result.length() > 0) result.append(' ');
            result.append(param);
        }
        return result.toString();
    }

    private static void debug(String message) {
        System.out.println(DEBUG_TAG + " " + message);
    }

    private static IRCCaseMapping getCaseMapping(ServerConnectionData data) {
        return data == null ? IRCCaseMapping.RFC1459 : data.getSupportList().getCaseMapping();
    }
}
