package io.mrarm.irc.irc;

import java.util.Locale;

/** Helpers for InspIRCd's SECURELIST channel-list protection. */
public final class SecureListSupport {

    private static final String WAIT_NOTICE = "cannot view the channel list right now";
    private static final String AUTH_NOTICE = "must be logged into an account to view the channel list";

    private SecureListSupport() {
    }

    public static boolean isBlockingNotice(String text) {
        if (text == null)
            return false;
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains(WAIT_NOTICE) || normalized.contains(AUTH_NOTICE);
    }

    /**
     * Returns a safe retry instant. InspIRCd allows LIST only after signon + waittime, so keep a
     * one-second guard to avoid retrying on the exact boundary.
     */
    public static long getRetryAtMillis(int waitSeconds, long registrationTimeMillis,
                                        long nowMillis) {
        if (waitSeconds <= 0)
            return -1L;
        long base = registrationTimeMillis > 0L ? registrationTimeMillis : nowMillis;
        long target = base + waitSeconds * 1000L + 1000L;
        return Math.max(target, nowMillis + 1000L);
    }

    public static int getRemainingSeconds(long retryAtMillis, long nowMillis) {
        if (retryAtMillis <= nowMillis)
            return 0;
        return (int) Math.max(1L, (retryAtMillis - nowMillis + 999L) / 1000L);
    }
}
