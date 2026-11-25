package io.mrarm.irc.lua;

import android.app.ApplicationExitInfo;
import org.junit.Test;
import static org.junit.Assert.*;

public class LuaSandboxExitTest {
    @Test public void systemKillsDoNotStrikeTheActiveScript() {
        LuaCrashTracker tracker = new LuaCrashTracker();
        assertFalse(tracker.terminated("A", true));
        for (int reason : new int[] { ApplicationExitInfo.REASON_LOW_MEMORY,
                ApplicationExitInfo.REASON_OTHER, ApplicationExitInfo.REASON_USER_REQUESTED,
                ApplicationExitInfo.REASON_DEPENDENCY_DIED }) {
            LuaSandboxExit.Result result = LuaSandboxExit.classify(reason, 0);
            assertFalse(result.penalize);
            assertFalse(result.retryable);
            tracker.started(2);
            if (result.penalize) tracker.terminated("A", true);
            tracker.clearActive();
        }
        assertFalse(tracker.terminated("B", true));
    }

    @Test public void actualCrashesKeepTheExistingTwoStrikeProtection() {
        LuaCrashTracker tracker = new LuaCrashTracker();
        LuaSandboxExit.Result crash = LuaSandboxExit.classify(
                ApplicationExitInfo.REASON_CRASH_NATIVE, 0);
        assertTrue(crash.penalize);
        assertFalse(tracker.terminated("A", false));
        assertTrue(tracker.terminated("A", false));
        assertTrue(LuaSandboxExit.classify(ApplicationExitInfo.REASON_CRASH, 0).penalize);
        assertTrue(LuaSandboxExit.classify(ApplicationExitInfo.REASON_ANR, 0).penalize);
    }

    @Test public void missingOrAmbiguousReasonsRetainBoundedGuard() {
        LuaSandboxExit.Result unknown = LuaSandboxExit.classify(
                ApplicationExitInfo.REASON_UNKNOWN, 0);
        assertTrue(unknown.penalize);
        assertTrue(unknown.retryable);
        LuaSandboxExit.Result kill = LuaSandboxExit.classify(
                ApplicationExitInfo.REASON_SIGNALED, 9);
        assertTrue(kill.penalize);
        assertTrue(kill.label.contains("unverified"));
    }

    @Test public void rejectStaleReusedPidAndOtherProcessRecords() {
        LuaSandboxExit.Identity identity = new LuaSandboxExit.Identity(
                123, 99001, "pkg:lua_sandbox", 10000);
        assertTrue(LuaSandboxExit.matches(identity, 123, 99001, "pkg:lua_sandbox", 10100, 10200));
        assertFalse(LuaSandboxExit.matches(identity, 124, 99001, "pkg:lua_sandbox", 10100, 10200));
        assertFalse(LuaSandboxExit.matches(identity, 123, 99002, "pkg:lua_sandbox", 10100, 10200));
        assertFalse(LuaSandboxExit.matches(identity, 123, 99001, "pkg", 10100, 10200));
        assertFalse(LuaSandboxExit.matches(identity, 123, 99001, "pkg:lua_sandbox", 9999, 10200));
        assertFalse(LuaSandboxExit.matches(identity, 123, 99001, "pkg:lua_sandbox", 13000, 10200));
    }
}
