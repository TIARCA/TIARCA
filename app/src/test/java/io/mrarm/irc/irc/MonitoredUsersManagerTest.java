package io.mrarm.irc.irc;

import com.google.gson.Gson;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.handlers.ISupportCommandHandler;
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

    @Test public void observedNickChangesKeepOneEntryAndDoNotFollowReusedOldNick() throws Exception {
        ServerConnectionData data = supportedData("MONITOR=2", "CASEMAPPING=rfc1459");
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
        manager.onNickChanged(data, "Pippo", "PippoAway");
        manager.handle(data, null, "730", Arrays.asList("me", "Pippo!other@host"), Collections.emptyMap());
        assertEquals("PippoAway", manager.getMonitoredUsers().get(0).currentNick);
        assertFalse(manager.getMonitoredUsers().get(0).online);
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
        String[] params = new String[tokens.length + 2];
        params[0] = "me";
        System.arraycopy(tokens, 0, params, 1, tokens.length);
        params[params.length - 1] = "supported";
        new ISupportCommandHandler().handle(data, null, "005", Arrays.asList(params), Collections.emptyMap());
        return data;
    }
}
