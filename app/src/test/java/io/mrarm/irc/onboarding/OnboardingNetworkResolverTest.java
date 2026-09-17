package io.mrarm.irc.onboarding;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import io.mrarm.irc.config.ServerConfigData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class OnboardingNetworkResolverTest {

    @Test
    public void sameNameAndServerReusesExistingNetwork() {
        ServerConfigData existing = server("Simosnap", "irc.simosnap.com");
        OnboardingNetworkResolver.Resolution resolution = OnboardingNetworkResolver.resolve(
                Collections.singletonList(existing), "Simosnap",
                Collections.singletonList("irc.simosnap.com"));
        assertSame(existing, resolution.existing);
        assertEquals("Simosnap", resolution.name);
    }

    @Test
    public void deprecatedSimosnapHostStillReusesExistingOfficialNetwork() {
        ServerConfigData existing = server("Simosnap", "irc.simosnap.com");
        OnboardingNetworkResolver.Resolution resolution = OnboardingNetworkResolver.resolve(
                Collections.singletonList(existing), "Simosnap",
                Collections.singletonList("irc.simosnap.org"));
        assertSame(existing, resolution.existing);
        assertEquals("Simosnap", resolution.name);
    }

    @Test
    public void sameServerWithDifferentNameCreatesRequestedNetwork() {
        ServerConfigData existing = server("Simosnap pippo", "irc.simosnap.com");
        OnboardingNetworkResolver.Resolution resolution = OnboardingNetworkResolver.resolve(
                Collections.singletonList(existing), "Simosnap",
                Collections.singletonList("irc.simosnap.com"));
        assertNull(resolution.existing);
        assertEquals("Simosnap", resolution.name);
    }

    @Test
    public void sameNameWithDifferentServerUsesHostAsNewName() {
        ServerConfigData existing = server("Simosnap", "irc.simosnap.com");
        OnboardingNetworkResolver.Resolution resolution = OnboardingNetworkResolver.resolve(
                Collections.singletonList(existing), "Simosnap",
                Collections.singletonList("pippo.simosnap.org"));
        assertNull(resolution.existing);
        assertEquals("pippo.simosnap.org", resolution.name);
    }

    @Test
    public void bothDifferentCreateRequestedNetwork() {
        ServerConfigData existing = server("IRCNet", "irc.ircnet.example");
        OnboardingNetworkResolver.Resolution resolution = OnboardingNetworkResolver.resolve(
                Collections.singletonList(existing), "Libera.Chat",
                Collections.singletonList("irc.libera.chat"));
        assertNull(resolution.existing);
        assertEquals("Libera.Chat", resolution.name);
    }

    @Test
    public void matchingUsesAnyConfiguredAddress() {
        ServerConfigData existing = new ServerConfigData();
        existing.uuid = UUID.randomUUID();
        existing.name = "Libera.Chat";
        existing.setConnectionAddresses(Arrays.asList("irc.eu.libera.chat", "irc.libera.chat"));
        OnboardingNetworkResolver.Resolution resolution = OnboardingNetworkResolver.resolve(
                Collections.singletonList(existing), "Libera.Chat",
                Collections.singletonList("irc.libera.chat"));
        assertSame(existing, resolution.existing);
    }

    private static ServerConfigData server(String name, String address) {
        ServerConfigData data = new ServerConfigData();
        data.uuid = UUID.randomUUID();
        data.name = name;
        data.setConnectionAddresses(Collections.singletonList(address));
        return data;
    }
}
