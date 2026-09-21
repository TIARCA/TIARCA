package io.mrarm.irc.irc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SecureListSupportTest {

    @Test
    public void recognizesInspIrcdBlockingNotices() {
        assertTrue(SecureListSupport.isBlockingNotice(
                "*** You cannot view the channel list right now. Please try again in 45 seconds."));
        assertTrue(SecureListSupport.isBlockingNotice(
                "*** You must be logged into an account to view the channel list."));
        assertFalse(SecureListSupport.isBlockingNotice(
                "*** Maintenance will start shortly."));
    }

    @Test
    public void retryTimeUsesRegistrationAndBoundaryGuard() {
        long now = 1_030_000L;
        long registered = 1_000_000L;
        long retryAt = SecureListSupport.getRetryAtMillis(60, registered, now);

        assertEquals(1_061_000L, retryAt);
        assertEquals(31, SecureListSupport.getRemainingSeconds(retryAt, now));
    }

    @Test
    public void zeroWaitHasNoTimedRetry() {
        assertEquals(-1L, SecureListSupport.getRetryAtMillis(0, 1_000L, 2_000L));
    }
}
