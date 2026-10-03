package io.mrarm.irc.util;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;

import java.util.Collections;
import java.util.UUID;

import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.ServerConnectionManager;
import io.mrarm.irc.config.ServerConfigData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class SimosnapAvatarBehaviorTest {

    @Test
    public void acceptsOnlySimosnapDomainsAndTheirSubdomains() {
        for (String host : new String[] { "simosnap.com", "irc.simosnap.com",
                "resurrection.simosnap.com", "irc.simosnap.org", "simosnap.org",
                " IRC.SIMOSNAP.COM " })
            assertTrue(host, SimosnapAvatarManager.isSupportedHost(host));
        for (String host : new String[] { "irc.libera.chat", "simosnap.example.org",
                "irc.simosnap.com.example.org", "notsimosnap.com", "", "127.0.0.1" })
            assertFalse(host, SimosnapAvatarManager.isSupportedHost(host));
        assertFalse(SimosnapAvatarManager.isSupportedHost(null));
        assertFalse(SimosnapAvatarManager.isSupported(null));
    }

    @Test
    public void otherNetworksCannotResolveWhoisOrKnownAccountsForAvatars() {
        ServerConnectionInfo connection = connectionFor("irc.libera.chat");
        assertNull(SimosnapAvatarManager.resolveAccount(connection, "Nick", "shared-account"));
        assertNull(SimosnapAvatarManager.getAccount(connection, "Nick"));
        assertNull(SimosnapAvatarManager.resolveAccount(null, "Nick", "shared-account"));
    }

    @Test
    public void simosnapWhoisAccountStillResolves() {
        assertEquals("shared-account", SimosnapAvatarManager.resolveAccount(
                connectionFor("irc.simosnap.com"), "Nick", "shared-account"));
    }

    @Test
    public void unsupportedNetworkClearsRecycledAvatarBeforeAnyLoad() {
        ImageView view = new ImageView(RuntimeEnvironment.getApplication());
        view.setImageDrawable(new ColorDrawable(Color.RED));
        view.setVisibility(View.VISIBLE);
        view.setTag(R.id.tag_simosnap_avatar_url, "previous-simosnap-request");
        int[] callbacks = { 0 };
        SimosnapAvatarLoader.load(view, connectionFor("irc.libera.chat"),
                "shared-account", true, loaded -> {
                    assertFalse(loaded);
                    callbacks[0]++;
                });
        assertEquals(1, callbacks[0]);
        assertNull(view.getDrawable());
        assertNull(view.getTag(R.id.tag_simosnap_avatar_url));
        assertEquals(View.GONE, view.getVisibility());
    }

    private static ServerConnectionInfo connectionFor(String host) {
        ServerConfigData config = new ServerConfigData();
        config.uuid = UUID.randomUUID();
        config.address = host;
        return new ServerConnectionInfo(
                new ServerConnectionManager(RuntimeEnvironment.getApplication()), config,
                null, null, Collections.emptyList());
    }

    @Test
    public void whoisAccountOverridesKnownWhoxAccount() {
        assertEquals("whois-account",
                SimosnapAvatarManager.chooseAccount("whox-account", "whois-account"));
    }

    @Test
    public void missingWhoisAccountKeepsKnownWhoxAccount() {
        assertEquals("whox-account",
                SimosnapAvatarManager.chooseAccount("whox-account", null));
        assertEquals("whox-account",
                SimosnapAvatarManager.chooseAccount("whox-account", ""));
    }

    @Test
    public void largeAvatarFallsBackToThumbnailForSameAccount() {
        String[] urls = SimosnapAvatarLoader.getCandidateUrls("ExampleAccount", true);

        assertEquals(2, urls.length);
        assertTrue(urls[0].contains("/uploads/avatars/default/"));
        assertTrue(urls[1].contains("/uploads/avatars/40/"));
        assertEquals(urls[0].substring(urls[0].lastIndexOf('/') + 1),
                urls[1].substring(urls[1].lastIndexOf('/') + 1));
    }

    @Test
    public void thumbnailRequestDoesNotUseLargeEndpoint() {
        String[] urls = SimosnapAvatarLoader.getCandidateUrls("ExampleAccount", false);

        assertEquals(1, urls.length);
        assertTrue(urls[0].contains("/uploads/avatars/40/"));
    }

    @Test
    public void onlyConfirmedNotFoundIsNegativeCached() {
        assertTrue(SimosnapAvatarLoader.isPermanentMissingStatus(404));
        assertFalse(SimosnapAvatarLoader.isPermanentMissingStatus(500));
        assertFalse(SimosnapAvatarLoader.isPermanentMissingStatus(429));
        assertFalse(SimosnapAvatarLoader.isPermanentMissingStatus(200));
    }
}
