package io.mrarm.irc.irc;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.IRCCaseMapping;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.irc.config.ServerConfigData;

/** Standard IRC MONITOR state grouped into persistent client-side nickname aliases. */
public final class MonitoredUsersManager implements CommandHandler {
    private static final String TAG = "MonitoredUsers";
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

    public static final class AliasConflictException extends IllegalArgumentException {
        private final ServerConfigData.MonitoredUser existingGroup;

        AliasConflictException(ServerConfigData.MonitoredUser existingGroup) {
            super("Alias already belongs to another monitored group");
            this.existingGroup = existingGroup;
        }

        public ServerConfigData.MonitoredUser getExistingGroup() { return existingGroup; }
    }

    private static final class AliasMatch {
        final ServerConfigData.MonitoredUser user;
        final ServerConfigData.MonitoredAlias alias;

        AliasMatch(ServerConfigData.MonitoredUser user, ServerConfigData.MonitoredAlias alias) {
            this.user = user;
            this.alias = alias;
        }
    }

    private final ServerConfigData config;
    private final ConfigPersister persister;
    private final List<Listener> listeners = new ArrayList<>();
    private final Set<ServerConfigData.MonitoredAlias> synchronizedAliases = new LinkedHashSet<>();
    private final Set<ServerConfigData.MonitoredAlias> aliasesOverLimit = new LinkedHashSet<>();
    private SyncState syncState = SyncState.UNINITIALIZED;
    private boolean serverLimitReached;
    private String lastError;

    public MonitoredUsersManager(ServerConfigData config) { this(config, null); }

    public MonitoredUsersManager(ServerConfigData config, ConfigPersister persister) {
        this.config = config;
        this.persister = persister;
        if (normalizeLegacyEntries()) persistConfiguration();
    }

    @Override public Object[] getHandledCommands() { return new Object[] {730, 731, 732, 733, 734}; }
    public boolean isSupported(ServerConnectionData data) {
        return data != null && data.getSupportList().getMonitorLimit() >= 0;
    }
    public int getLimit(ServerConnectionData data) { return data.getSupportList().getMonitorLimit(); }
    public SyncState getSyncState() { return syncState; }
    public String getLastError() { return lastError; }
    public boolean hasServerLimitReached() { return serverLimitReached; }

    public List<ServerConfigData.MonitoredUser> getMonitoredUsers() {
        return config.monitoredUsers == null ? Collections.emptyList() :
                Collections.unmodifiableList(config.monitoredUsers);
    }

    public List<ServerConfigData.MonitoredAlias> getAliases(ServerConfigData.MonitoredUser user) {
        ensureAliases(user);
        return Collections.unmodifiableList(user.aliases);
    }

    public List<ServerConfigData.MonitoredAlias> getOnlineAliases(ServerConfigData.MonitoredUser user) {
        ArrayList<ServerConfigData.MonitoredAlias> result = new ArrayList<>();
        for (ServerConfigData.MonitoredAlias alias : getAliases(user))
            if (alias.online) result.add(alias);
        return Collections.unmodifiableList(result);
    }

    public List<ServerConfigData.MonitoredUser> getSynchronizedUsers() {
        LinkedHashSet<ServerConfigData.MonitoredUser> result = new LinkedHashSet<>();
        for (ServerConfigData.MonitoredUser user : getMonitoredUsers())
            for (ServerConfigData.MonitoredAlias alias : getAliases(user))
                if (synchronizedAliases.contains(alias)) result.add(user);
        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    public List<ServerConfigData.MonitoredUser> getUsersOverLimit() {
        LinkedHashSet<ServerConfigData.MonitoredUser> result = new LinkedHashSet<>();
        for (ServerConfigData.MonitoredUser user : getMonitoredUsers())
            for (ServerConfigData.MonitoredAlias alias : getAliases(user))
                if (aliasesOverLimit.contains(alias)) result.add(user);
        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    public List<ServerConfigData.MonitoredAlias> getAliasesOverLimit(ServerConfigData.MonitoredUser user) {
        ArrayList<ServerConfigData.MonitoredAlias> result = new ArrayList<>();
        for (ServerConfigData.MonitoredAlias alias : getAliases(user))
            if (aliasesOverLimit.contains(alias)) result.add(alias);
        return Collections.unmodifiableList(result);
    }

    /** True only when every alias in the group is currently registered server-side. */
    public boolean isSynchronizedWithServer(ServerConfigData.MonitoredUser user) {
        List<ServerConfigData.MonitoredAlias> aliases = getAliases(user);
        return !aliases.isEmpty() && synchronizedAliases.containsAll(aliases);
    }

    public boolean isOnline(ServerConfigData.MonitoredUser user) {
        for (ServerConfigData.MonitoredAlias alias : getAliases(user))
            if (alias.online) return true;
        return false;
    }

    public String getPreferredNick(ServerConfigData.MonitoredUser user) {
        List<ServerConfigData.MonitoredAlias> online = getOnlineAliases(user);
        if (online.size() == 1) return online.get(0).nick;
        if (online.size() > 1) {
            for (ServerConfigData.MonitoredAlias alias : online)
                if (alias.nick.equals(user.nick)) return alias.nick;
            return online.get(0).nick;
        }
        return user.nick;
    }

    public boolean isMonitored(String nick) { return findAlias(nick, IRCCaseMapping.RFC1459) != null; }
    public boolean isMonitored(ServerConnectionData data, String nick) {
        return findAlias(nick, getCaseMapping(data)) != null;
    }

    public ServerConfigData.MonitoredUser getMonitoredUser(ServerConnectionData data, String nick) {
        AliasMatch match = findAlias(nick, getCaseMapping(data));
        return match == null ? null : match.user;
    }

    public ServerConfigData.MonitoredUser addMonitoredUser(String nick, boolean notifyOnline,
                                                            boolean notifyOffline) {
        return addMonitoredUser(null, nick, notifyOnline, notifyOffline);
    }

    public ServerConfigData.MonitoredUser addMonitoredUser(ServerConnectionData data, String nick,
                                                            boolean notifyOnline, boolean notifyOffline) {
        String normalizedNick = requireNick(nick);
        AliasMatch existing = findAlias(normalizedNick, getCaseMapping(data));
        if (existing != null) {
            existing.user.notifyOnline = notifyOnline;
            existing.user.notifyOffline = notifyOffline;
            persistConfiguration();
            notifyUserChanged(existing.user);
            return existing.user;
        }
        if (config.monitoredUsers == null) config.monitoredUsers = new ArrayList<>();
        ServerConfigData.MonitoredUser user = new ServerConfigData.MonitoredUser();
        user.nick = normalizedNick;
        user.currentNick = normalizedNick;
        user.notifyOnline = notifyOnline;
        user.notifyOffline = notifyOffline;
        user.aliases = new ArrayList<>();
        user.aliases.add(newAlias(normalizedNick, ServerConfigData.MonitoredAlias.ORIGIN_MANUAL));
        config.monitoredUsers.add(user);
        persistConfiguration();
        notifyUserChanged(user);
        if (data != null && syncState == SyncState.READY && isSupported(data))
            synchronizeAddedAliases(data, user, user.aliases);
        return user;
    }

    public ServerConfigData.MonitoredAlias addAlias(ServerConnectionData data,
                                                     ServerConfigData.MonitoredUser user,
                                                     String nick) {
        String normalizedNick = requireNick(nick);
        IRCCaseMapping mapping = getCaseMapping(data);
        AliasMatch existing = findAlias(normalizedNick, mapping);
        if (existing != null) {
            if (existing.user == user) return existing.alias;
            throw new AliasConflictException(existing.user);
        }
        ensureAliases(user);
        ServerConfigData.MonitoredAlias alias = newAlias(normalizedNick,
                ServerConfigData.MonitoredAlias.ORIGIN_MANUAL);
        user.aliases.add(alias);
        persistConfiguration();
        notifyUserChanged(user);
        if (data != null && syncState == SyncState.READY && isSupported(data))
            synchronizeAddedAliases(data, user, Collections.singletonList(alias));
        return alias;
    }

    public boolean removeAlias(ServerConnectionData data, ServerConfigData.MonitoredUser user,
                               String nick) {
        ensureAliases(user);
        IRCCaseMapping mapping = getCaseMapping(data);
        ServerConfigData.MonitoredAlias alias = findAliasInGroup(user, nick, mapping);
        if (alias == null) return false;
        if (user.aliases.size() == 1) return removeMonitoredUser(data, user.nick);
        boolean wasSynchronized = synchronizedAliases.remove(alias);
        aliasesOverLimit.remove(alias);
        user.aliases.remove(alias);
        if (mapping.equals(user.nick, alias.nick)) user.nick = user.aliases.get(0).nick;
        if (user.currentNick == null || mapping.equals(user.currentNick, alias.nick))
            user.currentNick = user.nick;
        refreshAggregateOnline(user);
        persistConfiguration();
        notifyUserChanged(user);
        if (wasSynchronized && data != null && isSupported(data)) send(data, "-", alias.nick);
        if (data != null && syncState == SyncState.READY && isSupported(data)) fillAvailableSlots(data);
        return true;
    }

    /** Applies an editor snapshot while preserving runtime state for unchanged aliases. */
    public void replaceAliases(ServerConnectionData data, ServerConfigData.MonitoredUser user,
                               String displayNick, List<String> aliases) {
        IRCCaseMapping mapping = getCaseMapping(data);
        ArrayList<String> normalized = new ArrayList<>();
        for (String candidate : aliases) {
            String nick = requireNick(candidate);
            if (contains(normalized, nick, mapping))
                throw new IllegalArgumentException("duplicate alias");
            AliasMatch existing = findAlias(nick, mapping);
            if (existing != null && existing.user != user) throw new AliasConflictException(existing.user);
            normalized.add(nick);
        }
        if (normalized.isEmpty()) throw new IllegalArgumentException("aliases");
        String primary = requireNick(displayNick);
        if (!contains(normalized, primary, mapping)) primary = normalized.get(0);

        ensureAliases(user);
        ArrayList<ServerConfigData.MonitoredAlias> removed = new ArrayList<>();
        for (ServerConfigData.MonitoredAlias old : user.aliases)
            if (!contains(normalized, old.nick, mapping)) removed.add(old);
        ArrayList<ServerConfigData.MonitoredAlias> added = new ArrayList<>();
        ArrayList<ServerConfigData.MonitoredAlias> replacement = new ArrayList<>();
        for (String nick : normalized) {
            ServerConfigData.MonitoredAlias old = findAliasInGroup(user, nick, mapping);
            if (old == null) {
                old = newAlias(nick, ServerConfigData.MonitoredAlias.ORIGIN_MANUAL);
                added.add(old);
            }
            replacement.add(old);
        }
        user.aliases = replacement;
        user.nick = primary;
        if (user.currentNick == null || !contains(normalized, user.currentNick, mapping))
            user.currentNick = primary;
        ArrayList<ServerConfigData.MonitoredAlias> removedFromServer = new ArrayList<>();
        for (ServerConfigData.MonitoredAlias alias : removed) {
            if (synchronizedAliases.remove(alias)) removedFromServer.add(alias);
            aliasesOverLimit.remove(alias);
        }
        persistConfiguration();
        notifyUserChanged(user);
        if (data != null && syncState == SyncState.READY && isSupported(data)) {
            for (ServerConfigData.MonitoredAlias alias : removedFromServer)
                send(data, "-", alias.nick);
            synchronizeAddedAliases(data, user, added);
            fillAvailableSlots(data);
        }
    }

    public boolean removeMonitoredUser(String nick) { return removeMonitoredUser(null, nick); }

    public boolean removeMonitoredUser(ServerConnectionData data, String nick) {
        AliasMatch match = findAlias(nick, getCaseMapping(data));
        if (match == null || config.monitoredUsers == null) return false;
        ensureAliases(match.user);
        ArrayList<ServerConfigData.MonitoredAlias> synchronizedCopy = new ArrayList<>();
        for (ServerConfigData.MonitoredAlias alias : match.user.aliases)
            if (synchronizedAliases.remove(alias)) synchronizedCopy.add(alias);
        aliasesOverLimit.removeAll(match.user.aliases);
        config.monitoredUsers.remove(match.user);
        persistConfiguration();
        notifyUserChanged(match.user);
        if (data != null && isSupported(data))
            for (ServerConfigData.MonitoredAlias alias : synchronizedCopy) send(data, "-", alias.nick);
        if (data != null && syncState == SyncState.READY && isSupported(data)) fillAvailableSlots(data);
        return true;
    }

    public void updateNotificationPreferences(String nick, boolean online, boolean offline) {
        updateNotificationPreferences(null, nick, online, offline);
    }

    public void updateNotificationPreferences(ServerConnectionData data, String nick, boolean online,
                                              boolean offline) {
        AliasMatch match = findAlias(nick, getCaseMapping(data));
        if (match == null) throw new IllegalArgumentException("nick");
        match.user.notifyOnline = online;
        match.user.notifyOffline = offline;
        persistConfiguration();
        notifyUserChanged(match.user);
    }

    /** Rebuilds runtime presence after registration. Server MONITOR state remains authoritative. */
    public void synchronize(ServerConnectionData data) {
        lastError = null;
        serverLimitReached = false;
        synchronizedAliases.clear();
        aliasesOverLimit.clear();
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
            for (ServerConfigData.MonitoredAlias alias : getAliases(user)) {
                if (alias.nick == null) continue;
                if (contains(nicks, alias.nick, mapping)) {
                    synchronizedAliases.add(alias);
                    continue;
                }
                if (nicks.size() >= limit) aliasesOverLimit.add(alias);
                else {
                    nicks.add(alias.nick);
                    synchronizedAliases.add(alias);
                }
            }
        }
        if (!nicks.isEmpty()) send(data, "+", join(nicks));
        send(data, "S");
        send(data, "L");
    }

    public void list(ServerConnectionData data) throws IOException {
        if (!isSupported(data)) { lastError = "This server does not support MONITOR."; return; }
        send(data, "L");
    }

    public void clear(ServerConnectionData data) throws IOException {
        if (!isSupported(data)) { lastError = "This server does not support MONITOR."; return; }
        send(data, "C");
        synchronizedAliases.clear();
        aliasesOverLimit.clear();
        clearRuntimePresence();
        setSyncState(SyncState.UNINITIALIZED);
    }

    public void onDisconnected() {
        synchronizedAliases.clear();
        aliasesOverLimit.clear();
        clearRuntimePresence();
        setSyncState(SyncState.UNINITIALIZED);
    }

    @Override public void handle(ServerConnectionData data, MessagePrefix sender, String command,
                                 List<String> params, java.util.Map<String, String> tags)
            throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);
        if (numeric == ERR_MONLISTFULL) {
            serverLimitReached = true;
            lastError = "The server MONITOR limit has been reached.";
            return;
        }
        if (numeric == RPL_ENDOFMONLIST) {
            if (syncState == SyncState.SYNCING) setSyncState(SyncState.READY);
            return;
        }
        if (numeric == RPL_MONLIST) return;
        if (numeric != RPL_MONONLINE && numeric != RPL_MONOFFLINE) return;
        String raw = CommandHandler.getParamOrNull(params, params.size() - 1);
        if (raw == null) return;
        boolean online = numeric == RPL_MONONLINE;
        IRCCaseMapping mapping = getCaseMapping(data);
        for (String item : raw.split(",")) {
            int bang = item.indexOf('!');
            String nick = bang < 0 ? item : item.substring(0, bang);
            AliasMatch match = findAlias(nick, mapping);
            if (match == null) continue;
            boolean groupWasOnline = isOnline(match.user);
            match.alias.online = online;
            refreshAggregateOnline(match.user);
            boolean groupIsOnline = match.user.online;
            notifyUserChanged(match.user);
            if (syncState == SyncState.SYNCING) {
                notifyPresence(match.user, PresenceUpdate.INITIAL_STATE);
            } else if (syncState == SyncState.READY && groupWasOnline != groupIsOnline) {
                PresenceUpdate update = groupIsOnline ? PresenceUpdate.BECAME_ONLINE :
                        PresenceUpdate.BECAME_OFFLINE;
                notifyPresence(match.user, update);
            }
        }
    }

    public void onNickChanged(ServerConnectionData data, String oldNick, String newNick) {
        IRCCaseMapping mapping = getCaseMapping(data);
        AliasMatch oldMatch = findAlias(oldNick, mapping);
        if (oldMatch == null || newNick == null || newNick.trim().isEmpty()) return;
        String normalizedNewNick = newNick.trim();
        AliasMatch conflict = findAlias(normalizedNewNick, mapping);
        boolean groupWasOnline = isOnline(oldMatch.user);
        oldMatch.alias.online = false;
        if (conflict != null && conflict.user != oldMatch.user) {
            refreshAggregateOnline(oldMatch.user);
            lastError = "Observed nickname already belongs to another monitored group.";
            notifyUserChanged(oldMatch.user);
            if (syncState == SyncState.READY && groupWasOnline && !oldMatch.user.online)
                notifyPresence(oldMatch.user, PresenceUpdate.BECAME_OFFLINE);
            return;
        }

        ServerConfigData.MonitoredAlias newAlias;
        if (conflict != null) {
            newAlias = conflict.alias;
        } else {
            newAlias = newAlias(normalizedNewNick,
                    ServerConfigData.MonitoredAlias.ORIGIN_OBSERVED_NICK_CHANGE);
            ensureAliases(oldMatch.user);
            oldMatch.user.aliases.add(newAlias);
        }
        newAlias.online = groupWasOnline;
        oldMatch.user.currentNick = normalizedNewNick;
        refreshAggregateOnline(oldMatch.user);
        persistConfiguration();
        notifyUserChanged(oldMatch.user);
        if (data != null && isSupported(data) && syncState == SyncState.READY &&
                !synchronizedAliases.contains(newAlias))
            synchronizeAddedAliases(data, oldMatch.user, Collections.singletonList(newAlias));
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }
    public void removeListener(Listener listener) { listeners.remove(listener); }

    private void synchronizeAddedAliases(ServerConnectionData data,
                                         ServerConfigData.MonitoredUser user,
                                         List<ServerConfigData.MonitoredAlias> aliases) {
        for (ServerConfigData.MonitoredAlias alias : aliases) {
            if (synchronizedAliases.contains(alias) || aliasesOverLimit.contains(alias)) continue;
            if (synchronizedAliases.size() >= getLimit(data)) aliasesOverLimit.add(alias);
            else {
                synchronizedAliases.add(alias);
                send(data, "+", alias.nick);
            }
        }
        notifyUserChanged(user);
    }

    private void fillAvailableSlots(ServerConnectionData data) {
        if (syncState != SyncState.READY) return;
        int available = getLimit(data) - synchronizedAliases.size();
        if (available <= 0) return;
        for (ServerConfigData.MonitoredUser user : getMonitoredUsers()) {
            for (ServerConfigData.MonitoredAlias alias : getAliases(user)) {
                if (available <= 0) return;
                if (!aliasesOverLimit.remove(alias)) continue;
                synchronizedAliases.add(alias);
                send(data, "+", alias.nick);
                notifyUserChanged(user);
                available--;
            }
        }
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

    private void clearRuntimePresence() {
        for (ServerConfigData.MonitoredUser user : getMonitoredUsers()) {
            for (ServerConfigData.MonitoredAlias alias : getAliases(user)) alias.online = false;
            user.online = false;
        }
    }

    private void refreshAggregateOnline(ServerConfigData.MonitoredUser user) {
        user.online = false;
        for (ServerConfigData.MonitoredAlias alias : getAliases(user))
            if (alias.online) { user.online = true; break; }
    }

    private boolean normalizeLegacyEntries() {
        if (config.monitoredUsers == null) return false;

        List<?> entries = config.monitoredUsers;
        ArrayList<ServerConfigData.MonitoredUser> normalized = new ArrayList<>();
        boolean changed = false;
        for (Object entry : entries) {
            ServerConfigData.MonitoredUser user = toMonitoredUser(entry);
            if (user == null) {
                Log.w(TAG, "Ignoring an unrecoverable malformed monitored-user entry");
                changed = true;
                continue;
            }
            if (user != entry) changed = true;
            changed |= ensureAliases(user);
            if (user.nick == null || user.aliases.isEmpty()) {
                Log.w(TAG, "Ignoring a monitored-user entry without a recoverable nickname");
                changed = true;
                continue;
            }
            normalized.add(user);
        }
        if (changed || normalized.size() != entries.size())
            config.monitoredUsers = normalized;
        return changed;
    }

    private ServerConfigData.MonitoredUser toMonitoredUser(Object entry) {
        if (entry instanceof ServerConfigData.MonitoredUser)
            return (ServerConfigData.MonitoredUser) entry;
        if (entry instanceof CharSequence) {
            String nick = normalizedString(entry);
            if (nick == null) return null;
            ServerConfigData.MonitoredUser user = new ServerConfigData.MonitoredUser();
            user.nick = nick;
            user.currentNick = nick;
            return user;
        }
        if (!(entry instanceof Map)) return null;

        Map<?, ?> values = (Map<?, ?>) entry;
        ServerConfigData.MonitoredUser user = new ServerConfigData.MonitoredUser();
        user.nick = firstString(values, "nick", "nickname", "displayNick");
        user.currentNick = firstString(values, "currentNick", "currentNickname");
        user.notifyOnline = booleanValue(values.get("notifyOnline"));
        user.notifyOffline = booleanValue(values.get("notifyOffline"));
        user.aliases = toAliases(values.get("aliases"));
        return user;
    }

    private boolean ensureAliases(ServerConfigData.MonitoredUser user) {
        boolean changed = false;
        String normalizedNick = normalizedString(user.nick);
        if (user.nick != normalizedNick &&
                (user.nick == null || !user.nick.equals(normalizedNick))) changed = true;
        user.nick = normalizedNick;
        String normalizedCurrentNick = normalizedString(user.currentNick);
        if (user.currentNick != normalizedCurrentNick &&
                (user.currentNick == null || !user.currentNick.equals(normalizedCurrentNick))) changed = true;
        user.currentNick = normalizedCurrentNick;

        List<?> existingAliases = user.aliases;
        if (existingAliases != null) {
            for (Object entry : existingAliases) {
                if (!(entry instanceof ServerConfigData.MonitoredAlias)) {
                    changed = true;
                    continue;
                }
                ServerConfigData.MonitoredAlias alias =
                        (ServerConfigData.MonitoredAlias) entry;
                if (alias.origin == null || normalizedString(alias.nick) == null ||
                        !alias.nick.equals(normalizedString(alias.nick))) changed = true;
            }
        }
        ArrayList<ServerConfigData.MonitoredAlias> aliases = toAliases(existingAliases);
        if (existingAliases == null || aliases.size() != existingAliases.size()) changed = true;
        if (existingAliases != null) {
            for (int i = 0; i < aliases.size() && !changed; i++)
                if (aliases.get(i) != existingAliases.get(i)) changed = true;
        }
        user.aliases = aliases;

        IRCCaseMapping mapping = IRCCaseMapping.RFC1459;
        if (user.nick != null && findAliasInGroup(user, user.nick, mapping) == null) {
            user.aliases.add(newAlias(user.nick, ServerConfigData.MonitoredAlias.ORIGIN_MANUAL));
            changed = true;
        }
        if (user.currentNick != null && findAliasInGroup(user, user.currentNick, mapping) == null) {
            user.aliases.add(newAlias(user.currentNick,
                    ServerConfigData.MonitoredAlias.ORIGIN_OBSERVED_NICK_CHANGE));
            changed = true;
        }
        if (user.nick == null && !user.aliases.isEmpty()) {
            user.nick = user.aliases.get(0).nick;
            changed = true;
        }
        if (user.currentNick == null && user.nick != null) {
            user.currentNick = user.nick;
            changed = true;
        }
        return changed;
    }

    private ArrayList<ServerConfigData.MonitoredAlias> toAliases(Object value) {
        ArrayList<ServerConfigData.MonitoredAlias> result = new ArrayList<>();
        if (value == null) return result;
        if (value instanceof Iterable) {
            for (Object entry : (Iterable<?>) value) addAliasIfValid(result, toAlias(entry));
        } else {
            addAliasIfValid(result, toAlias(value));
        }
        return result;
    }

    private ServerConfigData.MonitoredAlias toAlias(Object entry) {
        if (entry instanceof ServerConfigData.MonitoredAlias) {
            ServerConfigData.MonitoredAlias alias = (ServerConfigData.MonitoredAlias) entry;
            alias.nick = normalizedString(alias.nick);
            if (alias.origin == null)
                alias.origin = ServerConfigData.MonitoredAlias.ORIGIN_MANUAL;
            return alias;
        }
        String nick;
        String origin = null;
        if (entry instanceof Map) {
            Map<?, ?> values = (Map<?, ?>) entry;
            nick = firstString(values, "nick", "nickname");
            origin = normalizedString(values.get("origin"));
        } else {
            nick = normalizedString(entry);
        }
        if (nick == null) return null;
        return newAlias(nick, origin == null ? ServerConfigData.MonitoredAlias.ORIGIN_MANUAL : origin);
    }

    private void addAliasIfValid(List<ServerConfigData.MonitoredAlias> aliases,
                                 ServerConfigData.MonitoredAlias alias) {
        if (alias == null || alias.nick == null) return;
        for (ServerConfigData.MonitoredAlias existing : aliases)
            if (IRCCaseMapping.RFC1459.equals(existing.nick, alias.nick)) return;
        aliases.add(alias);
    }

    private static String firstString(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            String value = normalizedString(values.get(key));
            if (value != null) return value;
        }
        return null;
    }

    private static String normalizedString(Object value) {
        if (!(value instanceof CharSequence)) return null;
        String result = value.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        return value instanceof CharSequence && Boolean.parseBoolean(value.toString());
    }

    private AliasMatch findAlias(String nick, IRCCaseMapping mapping) {
        if (nick == null) return null;
        for (ServerConfigData.MonitoredUser user : getMonitoredUsers()) {
            ServerConfigData.MonitoredAlias alias = findAliasInGroup(user, nick, mapping);
            if (alias != null) return new AliasMatch(user, alias);
        }
        return null;
    }

    private ServerConfigData.MonitoredAlias findAliasInGroup(ServerConfigData.MonitoredUser user,
                                                              String nick,
                                                              IRCCaseMapping mapping) {
        if (nick == null || user.aliases == null) return null;
        for (ServerConfigData.MonitoredAlias alias : user.aliases)
            if (alias.nick != null && mapping.equals(alias.nick, nick)) return alias;
        return null;
    }

    private void persistConfiguration() {
        if (persister == null) return;
        try {
            persister.persist();
        } catch (IOException e) {
            lastError = "Could not save monitored users.";
        }
    }

    private void send(ServerConnectionData data, String... params) {
        if (data == null || data.getApi() == null) return;
        try {
            data.getApi().sendCommand("MONITOR", false, params, null, e -> {
                lastError = "Could not synchronize MONITOR.";
            });
        } catch (RuntimeException e) {
            lastError = "Could not synchronize MONITOR.";
        }
    }

    private static ServerConfigData.MonitoredAlias newAlias(String nick, String origin) {
        ServerConfigData.MonitoredAlias alias = new ServerConfigData.MonitoredAlias();
        alias.nick = nick;
        alias.origin = origin;
        return alias;
    }

    private static String requireNick(String nick) {
        if (nick == null || nick.trim().isEmpty()) throw new IllegalArgumentException("nick");
        return nick.trim();
    }

    private static boolean contains(List<String> nicks, String nick, IRCCaseMapping mapping) {
        for (String item : nicks) if (mapping.equals(item, nick)) return true;
        return false;
    }

    private static String join(List<String> nicks) {
        StringBuilder result = new StringBuilder();
        for (String nick : nicks) {
            if (result.length() > 0) result.append(',');
            result.append(nick);
        }
        return result.toString();
    }


    private static IRCCaseMapping getCaseMapping(ServerConnectionData data) {
        return data == null ? IRCCaseMapping.RFC1459 : data.getSupportList().getCaseMapping();
    }
}
