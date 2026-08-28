package io.mrarm.irc;

import org.junit.Test;

import io.mrarm.irc.config.ServerConfigData;

import static org.junit.Assert.assertEquals;

public class MonitoredUserDialogTest {

    @Test
    public void contextualAddUsesSelectedCurrentNickname() {
        assertEquals("PippoAway", MonitoredUserDialog.resolveInitialNickname(
                null, "  PippoAway  "));
    }

    @Test
    public void manualAddStartsWithEmptyNickname() {
        assertEquals("", MonitoredUserDialog.resolveInitialNickname(null, null));
    }

    @Test
    public void existingAliasOpensItsConfiguredGroup() {
        ServerConfigData.MonitoredUser existing = new ServerConfigData.MonitoredUser();
        existing.nick = "Pippo";
        assertEquals("Pippo", MonitoredUserDialog.resolveInitialNickname(
                existing, "PippoAway"));
    }
}
