package io.mrarm.irc.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SimosnapAvatarBehaviorTest {

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
