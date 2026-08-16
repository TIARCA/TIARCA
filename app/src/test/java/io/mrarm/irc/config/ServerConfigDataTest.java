package io.mrarm.irc.config;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerConfigDataTest {
    @Test public void migratesLegacyAddress() {
        ServerConfigData data = new ServerConfigData();
        data.address = "irc.example.net";
        data.migrateLegacyProperties();
        assertEquals(Arrays.asList("irc.example.net"), data.getConnectionAddresses());
    }

    @Test public void keepsOrderedUniqueFallbacks() {
        ServerConfigData data = new ServerConfigData();
        data.setConnectionAddresses(Arrays.asList("one.example", "two.example", "one.example"));
        assertEquals(Arrays.asList("one.example", "two.example"), data.getConnectionAddresses());
        assertEquals("one.example", data.address);
    }

    @Test public void replacingAddressesDoesNotRestoreOldPrimary() {
        ServerConfigData data = new ServerConfigData();
        data.address = "old.example";
        data.addresses = Arrays.asList("old.example", "fallback.example");

        data.setConnectionAddresses(Arrays.asList(" new-primary.example ",
                "new-fallback.example", "new-primary.example"));

        assertEquals(Arrays.asList("new-primary.example", "new-fallback.example"),
                data.getConnectionAddresses());
        assertEquals("new-primary.example", data.address);
    }

    @Test public void migratesLegacyJoinPartPreferencePerServer() {
        ServerConfigData hidden = new ServerConfigData();
        hidden.migrateLegacyProperties(true);
        assertTrue(hidden.shouldHideJoinPartMessages());

        ServerConfigData visible = new ServerConfigData();
        visible.migrateLegacyProperties(false);
        assertFalse(visible.shouldHideJoinPartMessages());

        visible.migrateLegacyProperties(true);
        assertFalse("A migrated per-server choice must not be overwritten",
                visible.shouldHideJoinPartMessages());
    }
}
