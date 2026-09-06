package io.mrarm.irc.irc;

import com.google.gson.Gson;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test public void migratesGenericMapAndMixedLegacyEntriesWithoutDataLoss() {
        String json = "{\"monitoredUsers\":[" +
                "\"SingleNick\"," +
                "{\"nick\":\"OldNick\",\"currentNickname\":\"OldAway\"," +
                "\"notifyOnline\":true,\"notifyOffline\":false}," +
                "{\"nick\":\"Grouped\",\"currentNick\":\"GroupedAway\"," +
                "\"notifyOnline\":false,\"notifyOffline\":true,\"aliases\":[" +
                "{\"nick\":\"Grouped\",\"origin\":\"manual\"}," +
                "{\"nick\":\"GroupedAway\",\"origin\":\"observed_nick_change\"}]}]}";
        Map root = new Gson().fromJson(json, Map.class);
        List genericEntries = (List) root.get("monitoredUsers");
        assertTrue(genericEntries.get(1) instanceof Map);

        ServerConfigData config = new ServerConfigData();
        config.monitoredUsers = (List<ServerConfigData.MonitoredUser>) (List<?>) genericEntries;
        AtomicInteger saves = new AtomicInteger();
        MonitoredUsersManager manager = new MonitoredUsersManager(config, saves::incrementAndGet);

        assertEquals(1, saves.get());
        assertEquals(3, manager.getMonitoredUsers().size());
        ServerConfigData.MonitoredUser single = manager.getMonitoredUsers().get(0);
        assertEquals("SingleNick", single.nick);
        assertEquals(1, manager.getAliases(single).size());

        ServerConfigData.MonitoredUser old = manager.getMonitoredUsers().get(1);
        assertEquals("OldNick", old.nick);
        assertEquals("OldAway", old.currentNick);
        assertTrue(old.notifyOnline);
        assertFalse(old.notifyOffline);
        assertEquals(2, manager.getAliases(old).size());

        ServerConfigData.MonitoredUser grouped = manager.getMonitoredUsers().get(2);
        assertEquals("Grouped", grouped.nick);
        assertEquals("GroupedAway", grouped.currentNick);
        assertFalse(grouped.notifyOnline);
        assertTrue(grouped.notifyOffline);
        assertEquals(2, manager.getAliases(grouped).size());
        assertEquals(ServerConfigData.MonitoredAlias.ORIGIN_OBSERVED_NICK_CHANGE,
                manager.getAliases(grouped).get(1).origin);

        ServerConfigData restored = new Gson().fromJson(new Gson().toJson(config),
                ServerConfigData.class);
        AtomicInteger secondSaves = new AtomicInteger();
        MonitoredUsersManager restoredManager = new MonitoredUsersManager(restored,
                secondSaves::incrementAndGet);
        assertEquals(0, secondSaves.get());
        assertEquals(3, restoredManager.getMonitoredUsers().size());
        assertEquals("OldAway", restoredManager.getMonitoredUsers().get(1).currentNick);
        assertTrue(restoredManager.getMonitoredUsers().get(1).notifyOnline);
        assertTrue(restoredManager.getMonitoredUsers().get(2).notifyOffline);
        assertEquals(2, restoredManager.getAliases(
                restoredManager.getMonitoredUsers().get(2)).size());
    }

    @Test public void missingOrEmptyMonitorListNeedsNoMigration() {
        ServerConfigData missing = new Gson().fromJson("{}", ServerConfigData.class);
        assertTrue(new MonitoredUsersManager(missing).getMonitoredUsers().isEmpty());

        ServerConfigData empty = new ServerConfigData();
        empty.monitoredUsers = new ArrayList<>();
        AtomicInteger saves = new AtomicInteger();
        assertTrue(new MonitoredUsersManager(empty, saves::incrementAndGet)
                .getMonitoredUsers().isEmpty());
        assertEquals(0, saves.get());
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

    @Test public void tracksLastKnownStateAndTimestampWithoutRepeatedRefresh() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=5");
        ServerConfigData config = new ServerConfigData();
        AtomicInteger saves = new AtomicInteger();
        MonitoredUsersManager manager = new MonitoredUsersManager(config, saves::incrementAndGet);
        ServerConfigData.MonitoredUser user = manager.addMonitoredUser(data, "Marco", false, false);

        assertEquals(ServerConfigData.MonitoredUser.STATE_UNKNOWN, user.lastKnownState);
        assertNull(user.onlineSince);
        assertNull(user.lastSeen);
        assertEquals(0, user.lastStateTimestamp);

        int initialSaves = saves.get();
        manager.synchronize(data);
        manager.handle(data, null, "730", Arrays.asList("me", "Marco!id@host"), Collections.emptyMap());

        assertEquals(ServerConfigData.MonitoredUser.STATE_ONLINE, user.lastKnownState);
        assertEquals("Marco", user.lastKnownNick);
        assertNotNull(user.onlineSince);
        assertNull(user.lastSeen);
        long initialOnlineTime = user.onlineSince;
        assertTrue(initialOnlineTime > 0);
        assertEquals(initialOnlineTime, user.lastStateTimestamp);
        assertTrue(saves.get() > initialSaves);

        int saveCountBeforeDuplicate = saves.get();
        Thread.sleep(10);
        manager.handle(data, null, "730", Arrays.asList("me", "Marco!id@host"), Collections.emptyMap());
        assertEquals(initialOnlineTime, (long) user.onlineSince);
        assertEquals(initialOnlineTime, user.lastStateTimestamp);
        assertEquals(saveCountBeforeDuplicate, saves.get());

        // Nick change Marco -> Resilienza
        manager.onNickChanged(data, "Marco", "Resilienza");
        assertEquals(ServerConfigData.MonitoredUser.STATE_ONLINE, user.lastKnownState);
        assertEquals("Resilienza", user.lastKnownNick);
        assertEquals(initialOnlineTime, (long) user.onlineSince);
        assertEquals(initialOnlineTime, user.lastStateTimestamp);

        // Disconnect does not reset last known state or timestamp
        manager.onDisconnected();
        assertEquals(ServerConfigData.MonitoredUser.STATE_ONLINE, user.lastKnownState);
        assertEquals("Resilienza", user.lastKnownNick);
        assertEquals(initialOnlineTime, (long) user.onlineSince);
        assertEquals(initialOnlineTime, user.lastStateTimestamp);

        // Observed online -> offline transition in continuous session
        manager.synchronize(data);
        manager.handle(data, null, "730", Arrays.asList("me", "Resilienza!id@host"), Collections.emptyMap());
        Thread.sleep(10);
        manager.handle(data, null, "731", Arrays.asList("me", "Resilienza!id@host"), Collections.emptyMap());
        assertEquals(ServerConfigData.MonitoredUser.STATE_OFFLINE, user.lastKnownState);
        assertEquals("Resilienza", user.lastKnownNick);
        assertNotNull(user.lastSeen);
        long offlineTime = user.lastSeen;
        assertTrue(offlineTime > initialOnlineTime);
        assertEquals(offlineTime, user.lastStateTimestamp);

        // Repeated offline event does not refresh timestamp
        Thread.sleep(10);
        manager.handle(data, null, "731", Arrays.asList("me", "Resilienza!id@host"), Collections.emptyMap());
        assertEquals(offlineTime, (long) user.lastSeen);
        assertEquals(offlineTime, user.lastStateTimestamp);
    }

    @Test public void testA_addMonitoredUserAlreadyOffline() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=5");
        ServerConfigData config = new ServerConfigData();
        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        ServerConfigData.MonitoredUser user = manager.addMonitoredUser(data, "MBAREEE", false, false);
        assertEquals(ServerConfigData.MonitoredUser.STATE_UNKNOWN, user.lastKnownState);
        assertNull(user.lastSeen);
        assertEquals(0, user.lastStateTimestamp);

        manager.synchronize(data);
        manager.handle(data, null, "731", Arrays.asList("me", "MBAREEE!id@host"), Collections.emptyMap());

        assertEquals(ServerConfigData.MonitoredUser.STATE_OFFLINE, user.lastKnownState);
        assertNull(user.lastSeen);
        assertNull(user.onlineSince);
        assertEquals(0, user.lastStateTimestamp);
    }

    @Test public void testB_addMultipleMonitoredUsersAlreadyOffline() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=10");
        ServerConfigData config = new ServerConfigData();
        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        List<ServerConfigData.MonitoredUser> users = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            users.add(manager.addMonitoredUser(data, "User" + i, false, false));
        }
        manager.synchronize(data);
        manager.handle(data, null, "731", Arrays.asList("me", "User1!id@host,User2!id@host,User3!id@host,User4!id@host,User5!id@host"), Collections.emptyMap());

        for (ServerConfigData.MonitoredUser user : users) {
            assertEquals(ServerConfigData.MonitoredUser.STATE_OFFLINE, user.lastKnownState);
            assertNull(user.lastSeen);
            assertNull(user.onlineSince);
            assertEquals(0, user.lastStateTimestamp);
        }
    }

    @Test public void testC_observedOnlineTransitionToOfflineSetsLastSeen() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=5");
        ServerConfigData config = new ServerConfigData();
        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        ServerConfigData.MonitoredUser user = manager.addMonitoredUser(data, "Alice", false, false);

        manager.synchronize(data);
        manager.handle(data, null, "730", Arrays.asList("me", "Alice!id@host"), Collections.emptyMap());
        assertEquals(ServerConfigData.MonitoredUser.STATE_ONLINE, user.lastKnownState);
        assertNotNull(user.onlineSince);
        long onlineTime = user.onlineSince;
        assertNull(user.lastSeen);

        Thread.sleep(10);
        manager.handle(data, null, "731", Arrays.asList("me", "Alice!id@host"), Collections.emptyMap());
        assertEquals(ServerConfigData.MonitoredUser.STATE_OFFLINE, user.lastKnownState);
        assertNotNull(user.lastSeen);
        assertTrue(user.lastSeen > onlineTime);
        assertNull(user.onlineSince);
        assertEquals((long) user.lastSeen, user.lastStateTimestamp);
    }

    @Test public void testD_offlineWithValidLastSeenPreservedAcrossRestartAndServerConfirm() throws Exception {
        ServerConfigData config = new ServerConfigData();
        ServerConfigData.MonitoredUser user = new ServerConfigData.MonitoredUser();
        user.nick = "Bob";
        user.currentNick = "Bob";
        user.lastKnownState = ServerConfigData.MonitoredUser.STATE_OFFLINE;
        user.lastKnownNick = "Bob";
        user.lastSeen = 1700000000000L;
        user.lastStateTimestamp = 1700000000000L;
        config.monitoredUsers = new ArrayList<>(Collections.singletonList(user));

        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        ServerConnectionData data = supportedData("MONITOR=5");
        manager.synchronize(data);
        manager.handle(data, null, "731", Arrays.asList("me", "Bob!id@host"), Collections.emptyMap());

        ServerConfigData.MonitoredUser loaded = manager.getMonitoredUsers().get(0);
        assertEquals(ServerConfigData.MonitoredUser.STATE_OFFLINE, loaded.lastKnownState);
        assertEquals(Long.valueOf(1700000000000L), loaded.lastSeen);
        assertEquals(1700000000000L, loaded.lastStateTimestamp);
    }

    @Test public void testE_offlineWithoutLastSeenPreservedAcrossRestartAndServerConfirm() throws Exception {
        ServerConfigData config = new ServerConfigData();
        ServerConfigData.MonitoredUser user = new ServerConfigData.MonitoredUser();
        user.nick = "Charlie";
        user.currentNick = "Charlie";
        user.lastKnownState = ServerConfigData.MonitoredUser.STATE_OFFLINE;
        user.lastKnownNick = "Charlie";
        user.lastSeen = null;
        user.lastStateTimestamp = 0;
        config.monitoredUsers = new ArrayList<>(Collections.singletonList(user));

        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        ServerConnectionData data = supportedData("MONITOR=5");
        manager.synchronize(data);
        manager.handle(data, null, "731", Arrays.asList("me", "Charlie!id@host"), Collections.emptyMap());

        ServerConfigData.MonitoredUser loaded = manager.getMonitoredUsers().get(0);
        assertEquals(ServerConfigData.MonitoredUser.STATE_OFFLINE, loaded.lastKnownState);
        assertNull(loaded.lastSeen);
        assertEquals(0, loaded.lastStateTimestamp);
    }

    @Test public void testH_persistedOnlineThenReconnectOfflineDoesNotAssignReconnectTimeAsLastSeen() throws Exception {
        ServerConfigData config = new ServerConfigData();
        ServerConfigData.MonitoredUser user = new ServerConfigData.MonitoredUser();
        user.nick = "Dave";
        user.currentNick = "Dave";
        user.lastKnownState = ServerConfigData.MonitoredUser.STATE_ONLINE;
        user.lastKnownNick = "Dave";
        user.onlineSince = 1699999000000L;
        user.lastSeen = null;
        user.lastStateTimestamp = 1699999000000L;
        config.monitoredUsers = new ArrayList<>(Collections.singletonList(user));

        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        ServerConnectionData data = supportedData("MONITOR=5");

        // Reconnect happens: synchronize is called
        manager.synchronize(data);
        // Server initial status reports Dave is offline
        manager.handle(data, null, "731", Arrays.asList("me", "Dave!id@host"), Collections.emptyMap());

        ServerConfigData.MonitoredUser loaded = manager.getMonitoredUsers().get(0);
        assertEquals(ServerConfigData.MonitoredUser.STATE_OFFLINE, loaded.lastKnownState);
        assertNull(loaded.lastSeen);
        assertNull(loaded.onlineSince);
        assertEquals(0, loaded.lastStateTimestamp);
    }

    @Test public void testI_onlineObservedInSessionThenOfflineSetsCorrectLastSeen() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=5");
        ServerConfigData config = new ServerConfigData();
        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        ServerConfigData.MonitoredUser user = manager.addMonitoredUser(data, "Eve", false, false);

        manager.synchronize(data);
        manager.handle(data, null, "730", Arrays.asList("me", "Eve!id@host"), Collections.emptyMap());
        assertEquals(ServerConfigData.MonitoredUser.STATE_ONLINE, user.lastKnownState);

        Thread.sleep(10);
        long beforeOffline = System.currentTimeMillis();
        manager.handle(data, null, "731", Arrays.asList("me", "Eve!id@host"), Collections.emptyMap());
        assertEquals(ServerConfigData.MonitoredUser.STATE_OFFLINE, user.lastKnownState);
        assertNotNull(user.lastSeen);
        assertTrue(user.lastSeen >= beforeOffline);
        assertEquals((long) user.lastSeen, user.lastStateTimestamp);
    }

    @Test public void testJ_localDisconnectDoesNotInventLastSeen() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=5");
        ServerConfigData config = new ServerConfigData();
        MonitoredUsersManager manager = new MonitoredUsersManager(config);
        ServerConfigData.MonitoredUser user = manager.addMonitoredUser(data, "Frank", false, false);

        manager.synchronize(data);
        manager.handle(data, null, "730", Arrays.asList("me", "Frank!id@host"), Collections.emptyMap());
        long onlineTime = user.onlineSince;
        assertNull(user.lastSeen);

        manager.onDisconnected();
        assertEquals(ServerConfigData.MonitoredUser.STATE_ONLINE, user.lastKnownState);
        assertEquals(Long.valueOf(onlineTime), user.onlineSince);
        assertNull(user.lastSeen);
        assertEquals(onlineTime, user.lastStateTimestamp);
    }

    @Test public void deserializesLegacyAndNewLastKnownStateConfig() {
        String oldJson = "{\"monitoredUsers\":[{\"nick\":\"OldUser\",\"currentNick\":\"OldUser\"}]}";
        ServerConfigData oldConfig = new Gson().fromJson(oldJson, ServerConfigData.class);
        MonitoredUsersManager oldManager = new MonitoredUsersManager(oldConfig);
        ServerConfigData.MonitoredUser oldUser = oldManager.getMonitoredUsers().get(0);
        assertEquals(ServerConfigData.MonitoredUser.STATE_UNKNOWN, oldUser.lastKnownState);
        assertEquals("OldUser", oldUser.lastKnownNick);
        assertNull(oldUser.onlineSince);
        assertNull(oldUser.lastSeen);
        assertEquals(0, oldUser.lastStateTimestamp);

        // Legacy format from commit 47b32b3 with lastStateTimestamp
        String legacyJson = "{\"monitoredUsers\":[{\"nick\":\"Marco\",\"currentNick\":\"Resilienza\"," +
                "\"lastKnownState\":\"online\",\"lastKnownNick\":\"Resilienza\",\"lastStateTimestamp\":1700000000000}]}";
        ServerConfigData legacyConfig = new Gson().fromJson(legacyJson, ServerConfigData.class);
        MonitoredUsersManager legacyManager = new MonitoredUsersManager(legacyConfig);
        ServerConfigData.MonitoredUser legacyUser = legacyManager.getMonitoredUsers().get(0);
        assertEquals(ServerConfigData.MonitoredUser.STATE_ONLINE, legacyUser.lastKnownState);
        assertEquals("Resilienza", legacyUser.lastKnownNick);
        assertEquals(Long.valueOf(1700000000000L), legacyUser.onlineSince);
        assertEquals(1700000000000L, legacyUser.lastStateTimestamp);

        // New format with explicit onlineSince and lastSeen
        String newJson = "{\"monitoredUsers\":[{\"nick\":\"Alice\",\"currentNick\":\"Alice\"," +
                "\"lastKnownState\":\"offline\",\"lastKnownNick\":\"Alice\",\"lastSeen\":1700000005000}]}";
        ServerConfigData newConfig = new Gson().fromJson(newJson, ServerConfigData.class);
        MonitoredUsersManager newManager = new MonitoredUsersManager(newConfig);
        ServerConfigData.MonitoredUser newUser = newManager.getMonitoredUsers().get(0);
        assertEquals(ServerConfigData.MonitoredUser.STATE_OFFLINE, newUser.lastKnownState);
        assertEquals("Alice", newUser.lastKnownNick);
        assertEquals(Long.valueOf(1700000005000L), newUser.lastSeen);
        assertNull(newUser.onlineSince);
        assertEquals(1700000005000L, newUser.lastStateTimestamp);

        String reserialized = new Gson().toJson(newConfig);
        assertTrue(reserialized.contains("\"lastKnownState\":\"offline\""));
        assertTrue(reserialized.contains("\"lastKnownNick\":\"Alice\""));
        assertTrue(reserialized.contains("\"lastSeen\":1700000005000"));
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
