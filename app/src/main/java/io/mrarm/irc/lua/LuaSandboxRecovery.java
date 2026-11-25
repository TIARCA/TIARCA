package io.mrarm.irc.lua;

/** Main-looper retry policy. Monotonic time; reset only after a stable connection. */
final class LuaSandboxRecovery {
    private static final long[] DELAYS = {1000, 2000, 5000, 10000, 30000};
    private static final long STABLE_MS = 60000;
    private int failures;
    private long connectedAt = -1;
    private long retryAt;

    void connected(long now) {
        connectedAt = now;
        retryAt = 0;
    }

    void failed(long now) {
        if (connectedAt >= 0 && now - connectedAt >= STABLE_MS) failures = 0;
        connectedAt = -1;
        retryAt = now + DELAYS[Math.min(failures, DELAYS.length - 1)];
        if (failures < DELAYS.length) failures++;
    }

    long remaining(long now) { return Math.max(0, retryAt - now); }

    void reset() {
        failures = 0;
        connectedAt = -1;
        retryAt = 0;
    }
}
