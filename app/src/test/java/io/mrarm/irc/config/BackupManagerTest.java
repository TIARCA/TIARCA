package io.mrarm.irc.config;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.mrarm.irc.ServerConnectionManager;
import io.mrarm.irc.irc.MonitoredUsersManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class BackupManagerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ServerConfigManager.getInstance(context).deleteAllServers(true);
        NotificationRuleManager.saveUserRuleSettings(context);
    }

    @Test
    public void roundTripBackupAndRestorePreservesMonitorListAndNetworkAssociations() throws Exception {
        ServerConfigManager configManager = ServerConfigManager.getInstance(context);

        // Network A
        ServerConfigData serverA = new ServerConfigData();
        serverA.name = "SimosNap";
        serverA.uuid = UUID.randomUUID();
        serverA.address = "irc.simosnap.org";
        serverA.port = 6697;

        MonitoredUsersManager managerA = new MonitoredUsersManager(serverA, () -> configManager.saveServerConfiguration(serverA));
        managerA.addMonitoredUser("Pippo", true, false);
        ServerConfigData.MonitoredUser userA2 = managerA.addMonitoredUser("Pluto", true, true);
        managerA.addAlias(null, userA2, "PlutoAway");
        configManager.saveServer(serverA);

        // Network B
        ServerConfigData serverB = new ServerConfigData();
        serverB.name = "Libera";
        serverB.uuid = UUID.randomUUID();
        serverB.address = "irc.libera.chat";
        serverB.port = 6697;

        MonitoredUsersManager managerB = new MonitoredUsersManager(serverB, () -> configManager.saveServerConfiguration(serverB));
        managerB.addMonitoredUser("Mario", false, true);
        configManager.saveServer(serverB);

        File backupFile = new File(temporaryFolder.getRoot(), "backup.zip");

        // Create Backup
        BackupManager.createBackup(context, backupFile, null);
        assertTrue(BackupManager.verifyBackupFile(backupFile));

        // Clean Install Simulation: delete all servers and disconnect
        ServerConnectionManager.getInstance(context).disconnectAndRemoveAllConnections(true);
        configManager.deleteAllServers(true);
        assertTrue(configManager.getServers().isEmpty());

        // Restore Backup
        BackupManager.restoreBackup(context, backupFile, null);

        List<ServerConfigData> restoredServers = configManager.getServers();
        assertEquals(2, restoredServers.size());

        ServerConfigData restoredA = configManager.findServer(serverA.uuid);
        assertNotNull(restoredA);
        assertEquals("SimosNap", restoredA.name);
        MonitoredUsersManager restoredManagerA = new MonitoredUsersManager(restoredA);
        assertEquals(2, restoredManagerA.getMonitoredUsers().size());

        ServerConfigData.MonitoredUser restoredA1 = restoredManagerA.getMonitoredUser(null, "Pippo");
        assertNotNull(restoredA1);
        assertTrue(restoredA1.notifyOnline);
        assertTrue(!restoredA1.notifyOffline);

        ServerConfigData.MonitoredUser restoredA2 = restoredManagerA.getMonitoredUser(null, "Pluto");
        assertNotNull(restoredA2);
        assertTrue(restoredA2.notifyOnline);
        assertTrue(restoredA2.notifyOffline);
        List<ServerConfigData.MonitoredAlias> aliasesA2 = restoredManagerA.getAliases(restoredA2);
        assertEquals(2, aliasesA2.size());
        assertEquals("Pluto", aliasesA2.get(0).nick);
        assertEquals("PlutoAway", aliasesA2.get(1).nick);

        ServerConfigData restoredB = configManager.findServer(serverB.uuid);
        assertNotNull(restoredB);
        assertEquals("Libera", restoredB.name);
        MonitoredUsersManager restoredManagerB = new MonitoredUsersManager(restoredB);
        assertEquals(1, restoredManagerB.getMonitoredUsers().size());

        ServerConfigData.MonitoredUser restoredB1 = restoredManagerB.getMonitoredUser(null, "Mario");
        assertNotNull(restoredB1);
        assertTrue(!restoredB1.notifyOnline);
        assertTrue(restoredB1.notifyOffline);
    }

    @Test
    public void restoresOldBackupWithoutMonitorListGracefully() throws Exception {
        File zipFile = new File(temporaryFolder.getRoot(), "old_backup.zip");
        StringWriter rulesWriter = new StringWriter();
        NotificationRuleManager.saveUserRuleSettings(context, rulesWriter);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("preferences.json"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("notification_rules.json"));
            zos.write(rulesWriter.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("command_aliases.json"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            UUID uuid = UUID.randomUUID();
            zos.putNextEntry(new ZipEntry("servers/server-" + uuid + ".json"));
            String oldServerJson = "{\"name\":\"LegacyServer\",\"uuid\":\"" + uuid + "\",\"address\":\"irc.legacy.org\",\"port\":6667}";
            zos.write(oldServerJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        assertTrue(BackupManager.verifyBackupFile(zipFile));

        ServerConfigManager configManager = ServerConfigManager.getInstance(context);
        configManager.deleteAllServers(true);

        BackupManager.restoreBackup(context, zipFile, null);

        List<ServerConfigData> restoredServers = configManager.getServers();
        assertEquals(1, restoredServers.size());

        ServerConfigData legacyServer = restoredServers.get(0);
        assertEquals("LegacyServer", legacyServer.name);
        MonitoredUsersManager manager = new MonitoredUsersManager(legacyServer);
        assertTrue(manager.getMonitoredUsers().isEmpty());
    }
}
