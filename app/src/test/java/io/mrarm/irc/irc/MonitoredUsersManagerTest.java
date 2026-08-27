package io.mrarm.irc.irc;

import com.google.gson.Gson;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import io.mrarm.chatlib.ResponseCallback;
import io.mrarm.chatlib.ResponseErrorCallback;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.handlers.ISupportCommandHandler;
import io.mrarm.chatlib.test.TestApiImpl;
import io.mrarm.irc.config.ServerConfigData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MonitoredUsersManagerTest {
    @Test public void parsesMonitorSupportAndIrcCaseMapping() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=2", "CASEMAPPING=ascii");
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData());
        manager.addMonitoredUser(data, "[Nick", false, false);
        assertTrue(manager.isSupported(data));
        assertEquals(2, manager.getLimit(data));
        assertFalse(manager.isMonitored(data, "{nick"));

        new ISupportCommandHandler().handle(data, null, "005",
                Arrays.asList("me", "CASEMAPPING=rfc1459", "supported"), Collections.emptyMap());
        assertTrue(manager.isMonitored(data, "{nick"));
        assertTrue(manager.isMonitored(data, "[NICK"));

        new ISupportCommandHandler().handle(data, null, "005",
                Arrays.asList("me", "CASEMAPPING=strict-rfc1459", "supported"), Collections.emptyMap());
        manager.addMonitoredUser(data, "^Nick", false, false);
        assertFalse(manager.isMonitored(data, "~nick"));
    }

    @Test public void persistsConfigurationButNotRuntimePresence() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        ServerConfigData config = new ServerConfigData();
        MonitoredUsersManager manager = new MonitoredUsersManager(config, saves::incrementAndGet);
        manager.addMonitoredUser("Pippo", true, false);
        manager.updateNotificationPreferences("Pippo", false, true);
        manager.onNickChanged(new ServerConnectionData(), "Pippo", "PippoAway");
        assertTrue(manager.removeMonitoredUser("PippoAway"));
        assertEquals(4, saves.get());

        manager.addMonitoredUser("Pippo", true, true);
        config.monitoredUsers.get(0).online = true;
        ServerConfigData restored = new Gson().fromJson(new Gson().toJson(config), ServerConfigData.class);
        assertEquals(1, restored.monitoredUsers.size());
        assertEquals("Pippo", restored.monitoredUsers.get(0).nick);
        assertTrue(restored.monitoredUsers.get(0).notifyOnline);
        assertTrue(restored.monitoredUsers.get(0).notifyOffline);
        assertFalse(restored.monitoredUsers.get(0).online);
    }

    @Test public void syncSeparatesInitialStateRealtimeTransitionsAndMonitorList() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=2");
        ServerConfigData config = new ServerConfigData();
        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        manager.addMonitoredUser(data, "Pippo", true, false);
        AtomicInteger initial = new AtomicInteger();
        AtomicInteger online = new AtomicInteger();
        AtomicInteger offline = new AtomicInteger();
        manager.addListener(new MonitoredUsersManager.Listener() {
            @Override public void onPresenceUpdated(ServerConfigData.MonitoredUser user, MonitoredUsersManager.PresenceUpdate update) {
                if (update == MonitoredUsersManager.PresenceUpdate.INITIAL_STATE) initial.incrementAndGet();
                if (update == MonitoredUsersManager.PresenceUpdate.BECAME_ONLINE) online.incrementAndGet();
                if (update == MonitoredUsersManager.PresenceUpdate.BECAME_OFFLINE) offline.incrementAndGet();
            }
            @Override public void onSyncStateChanged(MonitoredUsersManager.SyncState state) { }
        });
        manager.synchronize(data);
        assertEquals(MonitoredUsersManager.SyncState.SYNCING, manager.getSyncState());
        manager.handle(data, null, "730", Arrays.asList("me", "Pippo!id@host"), Collections.emptyMap());
        assertTrue(config.monitoredUsers.get(0).online);
        assertEquals(1, initial.get());
        manager.handle(data, null, "732", Arrays.asList("me", "Pippo"), Collections.emptyMap());
        assertTrue(config.monitoredUsers.get(0).online);
        assertEquals(1, initial.get());
        manager.handle(data, null, "733", Arrays.asList("me", "end"), Collections.emptyMap());
        assertEquals(MonitoredUsersManager.SyncState.READY, manager.getSyncState());
        manager.handle(data, null, "731", Arrays.asList("me", "Pippo"), Collections.emptyMap());
        assertFalse(config.monitoredUsers.get(0).online);
        assertEquals(1, offline.get());
        manager.handle(data, null, "730", Arrays.asList("me", "Pippo!id@host"), Collections.emptyMap());
        assertEquals(1, online.get());
    }

    @Test public void reconnectClearsRuntimeStateAndRetainsConfiguredUsers() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=1");
        ServerConfigData config = new ServerConfigData();
        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        manager.addMonitoredUser(data, "Pippo", false, false);
        manager.synchronize(data);
        manager.handle(data, null, "730", Arrays.asList("me", "Pippo!id@host"), Collections.emptyMap());
        manager.handle(data, null, "733", Arrays.asList("me", "end"), Collections.emptyMap());
        manager.onDisconnected();
        assertEquals(MonitoredUsersManager.SyncState.UNINITIALIZED, manager.getSyncState());
        assertFalse(config.monitoredUsers.get(0).online);
        assertEquals(1, manager.getMonitoredUsers().size());
        manager.synchronize(data);
        assertEquals(MonitoredUsersManager.SyncState.SYNCING, manager.getSyncState());
    }

    @Test public void observedNickChangesKeepOneGroupAndAllAliases() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=5", "CASEMAPPING=rfc1459");
        ServerConfigData config = new ServerConfigData();
        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        manager.addMonitoredUser(data, "Pippo", false, false);
        manager.synchronize(data);
        manager.handle(data, null, "733", Arrays.asList("me", "end"), Collections.emptyMap());
        manager.onNickChanged(data, "Pippo", "PippoAway");
        manager.onNickChanged(data, "PippoAway", "PippoCena");
        manager.onNickChanged(data, "PippoCena", "Pippo");
        assertEquals(1, manager.getMonitoredUsers().size());
        assertEquals("Pippo", manager.getMonitoredUsers().get(0).currentNick);
        assertEquals(3, manager.getAliases(manager.getMonitoredUsers().get(0)).size());
        manager.onNickChanged(data, "Pippo", "PippoAway");
        assertEquals("PippoAway", manager.getMonitoredUsers().get(0).currentNick);
        assertEquals(3, manager.getAliases(manager.getMonitoredUsers().get(0)).size());
    }

    @Test public void observedNickChangePersistsCurrentNickAndUpdatesMonitorTarget() throws Exception {
        RecordingApi api = new RecordingApi();
        ServerConnectionData data = api.getServerConnectionData();
        applySupport(data, "MONITOR=2");
        ServerConfigData config = new ServerConfigData();
        AtomicInteger saves = new AtomicInteger();
        MonitoredUsersManager manager = new MonitoredUsersManager(config, saves::incrementAndGet);
        manager.addMonitoredUser(data, "Pippo", true, false);
        manager.synchronize(data);
        manager.handle(data, null, "733", Arrays.asList("me", "end"), Collections.emptyMap());
        api.commands.clear();
        manager.onNickChanged(data, "Pippo", "PippoAway");
        assertEquals("PippoAway", config.monitoredUsers.get(0).currentNick);
        assertEquals(Arrays.asList("MONITOR + PippoAway"), api.commands);
        assertEquals(2, manager.getAliases(config.monitoredUsers.get(0)).size());
        ServerConfigData restored = new Gson().fromJson(new Gson().toJson(config), ServerConfigData.class);
        assertEquals("PippoAway", restored.monitoredUsers.get(0).currentNick);
        assertTrue(saves.get() >= 2);
    }

    @Test public void editingUsersWhileReadyUpdatesMonitorWithoutChangingConnectionState() throws Exception {
        RecordingApi api = new RecordingApi();
        ServerConnectionData data = api.getServerConnectionData();
        applySupport(data, "MONITOR=2");
        AtomicInteger saves = new AtomicInteger();
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData(), saves::incrementAndGet);
        manager.synchronize(data);
        manager.handle(data, null, "733", Arrays.asList("me", "end"), Collections.emptyMap());
        assertEquals(MonitoredUsersManager.SyncState.READY, manager.getSyncState());

        api.commands.clear();
        manager.addMonitoredUser(data, "Pippo", true, false);
        assertEquals(Arrays.asList("MONITOR + Pippo"), api.commands);
        assertEquals(MonitoredUsersManager.SyncState.READY, manager.getSyncState());

        api.commands.clear();
        manager.updateNotificationPreferences(data, "Pippo", false, true);
        assertTrue(api.commands.isEmpty());
        assertEquals(MonitoredUsersManager.SyncState.READY, manager.getSyncState());

        manager.removeMonitoredUser(data, "Pippo");
        assertEquals(Arrays.asList("MONITOR - Pippo"), api.commands);
        assertEquals(MonitoredUsersManager.SyncState.READY, manager.getSyncState());
        assertEquals(3, saves.get());
    }

    @Test public void offlineAdditionIsPersistedAndAppliedByTheNextSync() throws Exception {
        RecordingApi api = new RecordingApi();
        ServerConnectionData data = api.getServerConnectionData();
        applySupport(data, "MONITOR=2");
        AtomicInteger saves = new AtomicInteger();
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData(), saves::incrementAndGet);

        manager.addMonitoredUser("Pippo", false, false);
        assertEquals(1, saves.get());
        assertTrue(api.commands.isEmpty());

        manager.synchronize(data);
        assertEquals(Arrays.asList("MONITOR + Pippo", "MONITOR S", "MONITOR L"), api.commands);
    }

    @Test public void uiAddRemoveAndRenameQueueNetworkWritesInOrder() throws Exception {
        QueuedRecordingApi api = new QueuedRecordingApi();
        ServerConnectionData data = api.getServerConnectionData();
        applySupport(data, "MONITOR=2");
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData());
        manager.synchronize(data);
        manager.handle(data, null, "733", Arrays.asList("me", "end"), Collections.emptyMap());
        api.clearQueued();

        manager.addMonitoredUser(data, "Pippo", false, false);
        assertTrue(api.commands.isEmpty());
        assertEquals(1, api.queued.size());
        api.runQueued();
        assertEquals(Arrays.asList("MONITOR + Pippo"), api.commands);

        api.commands.clear();
        manager.onNickChanged(data, "Pippo", "PippoAway");
        assertTrue(api.commands.isEmpty());
        assertEquals(1, api.queued.size());
        api.runQueued();
        assertEquals(Arrays.asList("MONITOR + PippoAway"), api.commands);

        api.commands.clear();
        manager.updateNotificationPreferences(data, "PippoAway", true, true);
        assertTrue(api.queued.isEmpty());
        assertTrue(api.commands.isEmpty());

        manager.removeMonitoredUser(data, "PippoAway");
        assertTrue(api.commands.isEmpty());
        assertEquals(2, api.queued.size());
        api.runQueued();
        assertEquals(Arrays.asList("MONITOR - Pippo", "MONITOR - PippoAway"), api.commands);
    }

    @Test public void keepsEntriesOverMonitorLimitAndExposesServerError() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=2");
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData());
        manager.addMonitoredUser(data, "One", false, false);
        manager.addMonitoredUser(data, "Two", false, false);
        manager.addMonitoredUser(data, "Three", false, false);
        manager.synchronize(data);
        assertEquals(2, manager.getSynchronizedUsers().size());
        assertEquals("Three", manager.getUsersOverLimit().get(0).nick);
        manager.handle(data, null, "734", Arrays.asList("me", "2", "Three"), Collections.emptyMap());
        assertTrue(manager.hasServerLimitReached());
        assertTrue(manager.getLastError().contains("limit"));
    }

    @Test public void migratesLegacyEntryToSingleAliasWithoutLosingPreferences() {
        String legacy = "{\"monitoredUsers\":[{\"nick\":\"Legacy\",\"currentNick\":\"Legacy\"," +
                "\"notifyOnline\":true,\"notifyOffline\":true}]}";
        ServerConfigData config = new Gson().fromJson(legacy, ServerConfigData.class);
        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        ServerConfigData.MonitoredUser user = manager.getMonitoredUsers().get(0);
        assertEquals("Legacy", user.nick);
        assertEquals(1, manager.getAliases(user).size());
        assertEquals("Legacy", manager.getAliases(user).get(0).nick);
        assertEquals(ServerConfigData.MonitoredAlias.ORIGIN_MANUAL,
                manager.getAliases(user).get(0).origin);
        assertTrue(user.notifyOnline);
        assertTrue(user.notifyOffline);
    }

    @Test public void manualAliasesRejectDuplicatesAndCrossGroupConflictsUsingCaseMapping()
            throws Exception {
        ServerConnectionData data = supportedData("MONITOR=10", "CASEMAPPING=rfc1459");
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData());
        ServerConfigData.MonitoredUser first = manager.addMonitoredUser(data, "[Nick", false, false);
        manager.addAlias(data, first, "Second");
        manager.addAlias(data, first, "second");
        assertEquals(2, manager.getAliases(first).size());

        ServerConfigData.MonitoredUser other = manager.addMonitoredUser(data, "Other", false, false);
        boolean conflict = false;
        try {
            manager.addAlias(data, other, "{nick");
        } catch (MonitoredUsersManager.AliasConflictException expected) {
            conflict = expected.getExistingGroup() == first;
        }
        assertTrue(conflict);

        boolean duplicateSnapshot = false;
        try {
            manager.replaceAliases(data, first, "[Nick", Arrays.asList("[Nick", "{nick"));
        } catch (IllegalArgumentException expected) {
            duplicateSnapshot = true;
        }
        assertTrue(duplicateSnapshot);
    }

    @Test public void groupPresenceNotifiesOnlyAggregateZeroOneTransitions() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=5");
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData());
        ServerConfigData.MonitoredUser user = manager.addMonitoredUser(data, "Pippo", true, true);
        manager.addAlias(data, user, "PippoAway");
        AtomicInteger online = new AtomicInteger();
        AtomicInteger offline = new AtomicInteger();
        manager.addListener(new MonitoredUsersManager.Listener() {
            @Override public void onPresenceUpdated(ServerConfigData.MonitoredUser changed,
                                                    MonitoredUsersManager.PresenceUpdate update) {
                if (update == MonitoredUsersManager.PresenceUpdate.BECAME_ONLINE) online.incrementAndGet();
                if (update == MonitoredUsersManager.PresenceUpdate.BECAME_OFFLINE) offline.incrementAndGet();
            }
            @Override public void onSyncStateChanged(MonitoredUsersManager.SyncState state) { }
        });
        manager.synchronize(data);
        manager.handle(data, null, "733", Arrays.asList("me", "end"), Collections.emptyMap());
        manager.handle(data, null, "730", Arrays.asList("me", "Pippo"), Collections.emptyMap());
        manager.handle(data, null, "730", Arrays.asList("me", "PippoAway"), Collections.emptyMap());
        assertEquals(1, online.get());
        assertEquals(2, manager.getOnlineAliases(user).size());
        manager.handle(data, null, "731", Arrays.asList("me", "Pippo"), Collections.emptyMap());
        assertEquals(0, offline.get());
        assertEquals("PippoAway", manager.getPreferredNick(user));
        manager.handle(data, null, "731", Arrays.asList("me", "PippoAway"), Collections.emptyMap());
        assertEquals(1, offline.get());
        assertFalse(manager.isOnline(user));
    }

    @Test public void monitorLimitCountsAliasesAndKeepsExcessAliasesPersisted() throws Exception {
        RecordingApi api = new RecordingApi();
        ServerConnectionData data = api.getServerConnectionData();
        applySupport(data, "MONITOR=2");
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData());
        ServerConfigData.MonitoredUser user = manager.addMonitoredUser(data, "One", false, false);
        manager.addAlias(data, user, "Two");
        manager.addAlias(data, user, "Three");
        manager.synchronize(data);
        assertEquals(3, manager.getAliases(user).size());
        assertEquals(1, manager.getAliasesOverLimit(user).size());
        assertFalse(manager.isSynchronizedWithServer(user));
        assertEquals("Three", manager.getAliasesOverLimit(user).get(0).nick);
        assertEquals("MONITOR + One,Two", api.commands.get(0));
    }

    @Test public void readyAliasAddAndRemoveUseMonitorWithoutReconnectOrPreferenceCommands()
            throws Exception {
        QueuedRecordingApi api = new QueuedRecordingApi();
        ServerConnectionData data = api.getServerConnectionData();
        applySupport(data, "MONITOR=5");
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData());
        ServerConfigData.MonitoredUser user = manager.addMonitoredUser(data, "Pippo", false, false);
        manager.synchronize(data);
        manager.handle(data, null, "733", Arrays.asList("me", "end"), Collections.emptyMap());
        api.clearQueued();

        manager.addAlias(data, user, "PippoAway");
        assertEquals(1, api.queued.size());
        api.runQueued();
        assertEquals(Arrays.asList("MONITOR + PippoAway"), api.commands);

        api.commands.clear();
        manager.updateNotificationPreferences(data, "Pippo", true, true);
        assertTrue(api.commands.isEmpty());
        assertTrue(api.queued.isEmpty());

        manager.removeAlias(data, user, "PippoAway");
        assertEquals(1, api.queued.size());
        api.runQueued();
        assertEquals(Arrays.asList("MONITOR - PippoAway"), api.commands);
        assertEquals(MonitoredUsersManager.SyncState.READY, manager.getSyncState());
    }

    @Test public void observedNickAddsPersistentAliasAndKeepsAggregateOnline() throws Exception {
        RecordingApi api = new RecordingApi();
        ServerConnectionData data = api.getServerConnectionData();
        applySupport(data, "MONITOR=5");
        ServerConfigData config = new ServerConfigData();
        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        ServerConfigData.MonitoredUser user = manager.addMonitoredUser(data, "Pippo", false, false);
        manager.synchronize(data);
        manager.handle(data, null, "730", Arrays.asList("me", "Pippo"), Collections.emptyMap());
        manager.handle(data, null, "733", Arrays.asList("me", "end"), Collections.emptyMap());
        api.commands.clear();

        manager.onNickChanged(data, "Pippo", "PippoAway");
        assertEquals(2, manager.getAliases(user).size());
        assertEquals("Pippo", manager.getAliases(user).get(0).nick);
        assertEquals("PippoAway", manager.getAliases(user).get(1).nick);
        assertEquals(ServerConfigData.MonitoredAlias.ORIGIN_OBSERVED_NICK_CHANGE,
                manager.getAliases(user).get(1).origin);
        assertTrue(manager.isOnline(user));
        assertEquals("PippoAway", manager.getPreferredNick(user));
        assertEquals(Arrays.asList("MONITOR + PippoAway"), api.commands);

        ServerConfigData restored = new Gson().fromJson(new Gson().toJson(config), ServerConfigData.class);
        MonitoredUsersManager restoredManager = new MonitoredUsersManager(restored);
        assertEquals(2, restoredManager.getAliases(restored.monitoredUsers.get(0)).size());
        assertFalse(restoredManager.isOnline(restored.monitoredUsers.get(0)));
    }

    @Test public void observedNickConflictDoesNotMergeGroups() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=5");
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData());
        ServerConfigData.MonitoredUser first = manager.addMonitoredUser(data, "Pippo", false, false);
        ServerConfigData.MonitoredUser second = manager.addMonitoredUser(data, "PippoAway", false, false);
        manager.onNickChanged(data, "Pippo", "PippoAway");
        assertEquals(2, manager.getMonitoredUsers().size());
        assertEquals(1, manager.getAliases(first).size());
        assertEquals(1, manager.getAliases(second).size());
        assertTrue(manager.getLastError().contains("another monitored group"));
    }

    @Test public void reconnectSynchronizesEveryPersistedAlias() throws Exception {
        RecordingApi api = new RecordingApi();
        ServerConnectionData data = api.getServerConnectionData();
        applySupport(data, "MONITOR=5");
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData());
        ServerConfigData.MonitoredUser user = manager.addMonitoredUser(data, "Pippo", false, false);
        manager.addAlias(data, user, "PippoAway");
        manager.synchronize(data);
        manager.onDisconnected();
        api.commands.clear();
        manager.synchronize(data);
        assertEquals(Arrays.asList("MONITOR + Pippo,PippoAway", "MONITOR S", "MONITOR L"),
                api.commands);
    }

    @Test public void serverWithoutMonitorRemainsUnsupported() {
        ServerConnectionData data = new ServerConnectionData();
        MonitoredUsersManager manager = new MonitoredUsersManager(new ServerConfigData());
        manager.addMonitoredUser("Pippo", false, false);
        manager.synchronize(data);
        assertFalse(manager.isSupported(data));
        assertEquals(MonitoredUsersManager.SyncState.UNINITIALIZED, manager.getSyncState());
        assertTrue(manager.getLastError().contains("does not support"));
    }

    private static ServerConnectionData supportedData(String... tokens) throws Exception {
        ServerConnectionData data = new ServerConnectionData();
        applySupport(data, tokens);
        return data;
    }

    private static void applySupport(ServerConnectionData data, String... tokens) throws Exception {
        String[] params = new String[tokens.length + 2];
        params[0] = "me";
        System.arraycopy(tokens, 0, params, 1, tokens.length);
        params[params.length - 1] = "supported";
        new ISupportCommandHandler().handle(data, null, "005", Arrays.asList(params), Collections.emptyMap());
    }

    private static class RecordingApi extends TestApiImpl {
        final List<String> commands = new ArrayList<>();
        RecordingApi() { super("me"); }
        @Override public void sendCommand(String command, boolean isLastArgFullLine, String... args) {
            throw new AssertionError("MONITOR must use the queued sendCommand overload");
        }
        @Override public Future<Void> sendCommand(String command, boolean isLastArgFullLine,
                                                   String[] args, ResponseCallback<Void> callback,
                                                   ResponseErrorCallback errorCallback) {
            commands.add(command + " " + String.join(" ", args));
            if (callback != null) callback.onResponse(null);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class QueuedRecordingApi extends RecordingApi {
        final List<Runnable> queued = new ArrayList<>();
        @Override public Future<Void> sendCommand(String command, boolean isLastArgFullLine,
                                                   String[] args, ResponseCallback<Void> callback,
                                                   ResponseErrorCallback errorCallback) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            queued.add(() -> {
                commands.add(command + " " + String.join(" ", args));
                if (callback != null) callback.onResponse(null);
                future.complete(null);
            });
            return future;
        }
        void clearQueued() { queued.clear(); commands.clear(); }
        void runQueued() {
            List<Runnable> copy = new ArrayList<>(queued);
            queued.clear();
            for (Runnable runnable : copy) runnable.run();
        }
    }
}
