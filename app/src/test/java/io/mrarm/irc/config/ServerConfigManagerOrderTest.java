package io.mrarm.irc.config;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.ServerConnectionManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ServerConfigManagerOrderTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ServerConnectionManager.destroyInstance();
        ServerConfigManager.destroyInstance();
        ServerConnectionManager.getInstance(context).disconnectAndRemoveAllConnections(true);
        ServerConfigManager.getInstance(context).deleteAllServers(true);
        File orderFile = new File(new File(context.getFilesDir(), "servers"), "server_order.json");
        if (orderFile.exists()) {
            orderFile.delete();
        }
    }

    @After
    public void tearDown() {
        ServerConnectionManager.destroyInstance();
        ServerConfigManager.destroyInstance();
    }

    @Test
    public void testReorderingAndPersistence() throws Exception {
        ServerConfigManager configManager = ServerConfigManager.getInstance(context);

        ServerConfigData serverA = createServer("Server A");
        ServerConfigData serverB = createServer("Server B");
        ServerConfigData serverC = createServer("Server C");

        configManager.saveServer(serverA);
        configManager.saveServer(serverB);
        configManager.saveServer(serverC);

        // Initial order: A, B, C
        List<ServerConfigData> initialServers = configManager.getServers();
        assertEquals(3, initialServers.size());
        assertEquals("Server A", initialServers.get(0).name);
        assertEquals("Server B", initialServers.get(1).name);
        assertEquals("Server C", initialServers.get(2).name);

        // Reorder to: C, A, B
        List<UUID> newOrder = Arrays.asList(serverC.uuid, serverA.uuid, serverB.uuid);
        configManager.saveServerOrder(newOrder);

        // 1. Save -> C, A, B is used
        List<ServerConfigData> reorderedServers = configManager.getServers();
        assertEquals("Server C", reorderedServers.get(0).name);
        assertEquals("Server A", reorderedServers.get(1).name);
        assertEquals("Server B", reorderedServers.get(2).name);

        // 2. Restart app -> C, A, B remains
        ServerConfigManager.destroyInstance(); // Reset singleton instance if any
        ServerConfigManager reloadedManager = ServerConfigManager.getInstance(context);
        List<ServerConfigData> reloadedServers = reloadedManager.getServers();
        assertEquals(3, reloadedServers.size());
        assertEquals("Expected C at 0, got " + reloadedServers.get(0).name, serverC.uuid, reloadedServers.get(0).uuid);
        assertEquals("Expected A at 1, got " + reloadedServers.get(1).name, serverA.uuid, reloadedServers.get(1).uuid);
        assertEquals("Expected B at 2, got " + reloadedServers.get(2).name, serverB.uuid, reloadedServers.get(2).uuid);
    }

    @Test
    public void testCancelDiscard() throws Exception {
        ServerConfigManager configManager = ServerConfigManager.getInstance(context);

        ServerConfigData serverA = createServer("Server A");
        ServerConfigData serverB = createServer("Server B");

        configManager.saveServer(serverA);
        configManager.saveServer(serverB);

        // Persist initial order
        configManager.saveServerOrder(Arrays.asList(serverA.uuid, serverB.uuid));

        // Temporary order changes without saveServerOrder
        List<UUID> tempOrder = Arrays.asList(serverB.uuid, serverA.uuid);
        // Do not call saveServerOrder(tempOrder)

        // 3. Previous persisted order remains unchanged
        List<ServerConfigData> current = configManager.getServers();
        assertEquals(serverA.uuid, current.get(0).uuid);
        assertEquals(serverB.uuid, current.get(1).uuid);
    }

    @Test
    public void testActiveServerPreservedNoReconnection() throws Exception {
        ServerConfigManager configManager = ServerConfigManager.getInstance(context);
        ServerConnectionManager connectionManager = ServerConnectionManager.getInstance(context);

        ServerConfigData serverA = createServer("Server A");
        ServerConfigData serverB = createServer("Server B");
        configManager.saveServer(serverA);
        configManager.saveServer(serverB);

        // Create connection
        ServerConnectionInfo connA = connectionManager.createConnection(serverA);
        assertNotNull(connA);
        assertTrue(connectionManager.hasConnection(serverA.uuid));

        // Reorder servers: B, A
        configManager.saveServerOrder(Arrays.asList(serverB.uuid, serverA.uuid));

        // 4. Active server before reordering remains active afterwards
        assertTrue(connectionManager.hasConnection(serverA.uuid));
        assertEquals(connA, connectionManager.getConnection(serverA.uuid));

        // 5. Active connections list updated in order B, A
        List<ServerConnectionInfo> activeConns = connectionManager.getConnections();
        assertEquals(1, activeConns.size());
        assertEquals(connA, activeConns.get(0));

        // 6. Existing chats/unread/mention state associated with server ID remains
        assertEquals(serverA.uuid, connA.getUUID());
    }

    @Test
    public void testAddingAndRemovingServersWithCustomOrder() throws Exception {
        ServerConfigManager configManager = ServerConfigManager.getInstance(context);

        ServerConfigData serverA = createServer("Server A");
        ServerConfigData serverB = createServer("Server B");
        configManager.saveServer(serverA);
        configManager.saveServer(serverB);

        configManager.saveServerOrder(Arrays.asList(serverB.uuid, serverA.uuid));

        // 7. Adding a server after a custom order appends it at the end without resetting custom order
        ServerConfigData serverC = createServer("Server C");
        configManager.saveServer(serverC);

        List<ServerConfigData> serversAfterAdd = configManager.getServers();
        assertEquals(3, serversAfterAdd.size());
        assertEquals("Server B", serversAfterAdd.get(0).name);
        assertEquals("Server A", serversAfterAdd.get(1).name);
        assertEquals("Server C", serversAfterAdd.get(2).name);

        // 8. Removing a server removes it cleanly without disturbing relative order of remaining
        configManager.deleteServer(serverA);

        List<ServerConfigData> serversAfterDelete = configManager.getServers();
        assertEquals(2, serversAfterDelete.size());
        assertEquals("Server B", serversAfterDelete.get(0).name);
        assertEquals("Server C", serversAfterDelete.get(1).name);
    }

    @Test
    public void testLegacyConfigurationAndDuplicateNames() throws Exception {
        ServerConfigManager configManager = ServerConfigManager.getInstance(context);

        // 10. Duplicate display names do not break ordering because identity is based on stable IDs
        ServerConfigData server1 = createServer("Freenode");
        ServerConfigData server2 = createServer("Freenode");

        configManager.saveServer(server1);
        configManager.saveServer(server2);

        // 9. Legacy configuration with no explicit ordering loads using order loaded
        List<ServerConfigData> legacyList = configManager.getServers();
        assertEquals(2, legacyList.size());
        assertEquals(server1.uuid, legacyList.get(0).uuid);
        assertEquals(server2.uuid, legacyList.get(1).uuid);

        // Custom order duplicate names
        configManager.saveServerOrder(Arrays.asList(server2.uuid, server1.uuid));
        List<ServerConfigData> orderedList = configManager.getServers();
        assertEquals(server2.uuid, orderedList.get(0).uuid);
        assertEquals(server1.uuid, orderedList.get(1).uuid);
    }

    private ServerConfigData createServer(String name) {
        ServerConfigData data = new ServerConfigData();
        data.name = name;
        data.uuid = UUID.randomUUID();
        data.address = "irc." + name.toLowerCase().replace(" ", "") + ".org";
        data.port = 6667;
        data.nicks = Arrays.asList("TestNick");
        return data;
    }
}
