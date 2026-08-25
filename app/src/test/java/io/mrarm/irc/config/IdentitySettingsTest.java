package io.mrarm.irc.config;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IdentitySettingsTest {

    @Test
    public void automaticIdentityUsesTheExpectedIrcSafeFormat() {
        assertTrue(IdentitySettings.createAutomaticIdentity().matches("TIARCA\\d{4}"));
    }

    @Test
    public void nicknameConfigurationRequiresNonBlankNickname() {
        assertFalse(IdentitySettings.hasNickname(null));
        assertFalse(IdentitySettings.hasNickname(new String[] { "", "  " }));
        assertTrue(IdentitySettings.hasNickname(new String[] { "  nick  " }));
    }
}
