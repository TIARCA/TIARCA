package io.mrarm.irc.irc;

import com.google.gson.Gson;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.handlers.ISupportCommandHandler;
import io.mrarm.irc.config.ServerConfigData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MonitoredUsersManagerTest {
    @Test public void parsesMonitorSupportAndTracksOnlineOffline() throws Exception {
        ServerConnectionData data = new ServerConnectionData();
        new ISupportCommandHandler().handle(data, null, "005", Arrays.asList("me", "MONITOR=2", "supported"), Collections.emptyMap());
        ServerConfigData config = new ServerConfigData(); MonitoredUsersManager manager = new MonitoredUsersManager(config);
        manager.addMonitoredUser("Pippo", true, false);
        assertTrue(manager.isSupported(data)); assertEquals(2, manager.getLimit(data));
        manager.handle(data, new MessagePrefix("server"), "730", Arrays.asList("me", "Pippo!id@host"), Collections.emptyMap());
        assertTrue(manager.getMonitoredUsers().get(0).online);
        manager.handle(data, new MessagePrefix("server"), "731", Arrays.asList("me", "Pippo"), Collections.emptyMap());
        assertFalse(manager.getMonitoredUsers().get(0).online);
    }
    @Test public void persistsEntriesAndHandlesLimitError() throws Exception {
        ServerConfigData config = new ServerConfigData(); MonitoredUsersManager manager = new MonitoredUsersManager(config);
        manager.addMonitoredUser("Pippo", true, true); manager.addMonitoredUser("Pluto", false, true);
        String json = new Gson().toJson(config); ServerConfigData restored = new Gson().fromJson(json, ServerConfigData.class);
        assertEquals(2, restored.monitoredUsers.size()); assertTrue(restored.monitoredUsers.get(0).notifyOnline);
        manager.handle(new ServerConnectionData(), null, "734", Arrays.asList("me", "limit"), Collections.emptyMap());
        assertTrue(manager.getLastError().contains("limit")); assertTrue(manager.removeMonitoredUser("Pluto"));
    }
    @Test public void serverWithoutMonitorIsUnsupportedAndNickChangesAreRetained() {
        ServerConnectionData data = new ServerConnectionData(); ServerConfigData config = new ServerConfigData();
        MonitoredUsersManager manager = new MonitoredUsersManager(config); manager.addMonitoredUser("Pippo", false, false);
        assertFalse(manager.isSupported(data)); manager.onNickChanged(data, "Pippo", "PippoAway");
        assertEquals("PippoAway", manager.getMonitoredUsers().get(0).currentNick);
    }
}
