package io.mrarm.irc.lua;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.system.OsConstants;

/** Best-effort attribution of an isolated worker death, never a guess from a queued request. */
final class LuaSandboxExit {
    private LuaSandboxExit() { }

    static final class Identity {
        final int pid;
        final int uid;
        final String process;
        final long startedAt;

        Identity(int pid, int uid, String process, long startedAt) {
            this.pid = pid;
            this.uid = uid;
            this.process = process;
            this.startedAt = startedAt;
        }

        static Identity fromStarted(Bundle data) {
            int pid = data.getInt(LuaDiagnosticService.EXTRA_PID, -1);
            int uid = data.getInt(LuaDiagnosticService.EXTRA_PROCESS_UID, -1);
            String name = data.getString(LuaDiagnosticService.EXTRA_PROCESS_NAME);
            long started = data.getLong(LuaDiagnosticService.EXTRA_STARTED_AT, 0);
            return pid > 0 && uid >= 0 && name != null && !name.isEmpty() && started > 0
                    ? new Identity(pid, uid, name, started) : null;
        }
    }

    static final class Result {
        final String label;
        final boolean penalize;
        final boolean retryable;
        final LuaEngineMetrics.Count category;

        Result(String label, boolean penalize, boolean retryable) {
            this.label = label;
            this.penalize = penalize;
            this.retryable = retryable;
            this.category = "LOW_MEMORY".equals(label) ? LuaEngineMetrics.Count.EXIT_LOW_MEMORY
                    : !penalize ? LuaEngineMetrics.Count.EXIT_SYSTEM
                    : ("CRASH".equals(label) || "CRASH_NATIVE".equals(label) || "ANR".equals(label))
                    ? LuaEngineMetrics.Count.EXIT_CRASH : LuaEngineMetrics.Count.EXIT_UNKNOWN;
        }
    }

    static boolean matches(Identity expected, int pid, int uid, String name,
                           long timestamp, long observedAt) {
        return expected != null && expected.pid == pid && expected.uid == uid
                && expected.process.equals(name)
                && timestamp >= expected.startedAt && timestamp <= observedAt + 2000L;
    }

    static Result classify(int reason, int status) {
        switch (reason) {
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return new Result("LOW_MEMORY", false, false);
            case ApplicationExitInfo.REASON_OTHER:
            case ApplicationExitInfo.REASON_USER_REQUESTED:
            case ApplicationExitInfo.REASON_USER_STOPPED:
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED:
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE:
            case ApplicationExitInfo.REASON_PACKAGE_UPDATED:
            case ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE:
            case ApplicationExitInfo.REASON_FREEZER:
                return new Result("SYSTEM (reason " + reason + ")", false, false);
            case ApplicationExitInfo.REASON_CRASH:
                return new Result("CRASH", true, false);
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return new Result("CRASH_NATIVE", true, false);
            case ApplicationExitInfo.REASON_ANR:
                return new Result("ANR", true, false);
            case ApplicationExitInfo.REASON_SIGNALED:
                // SIGKILL may mean memory pressure on devices without LMK reporting.
                // Never label it a confirmed crash or a confirmed low-memory kill.
                return new Result("SIGNAL " + status + " (cause unverified)", true,
                        status == OsConstants.SIGKILL);
            default:
                return new Result("UNKNOWN (reason " + reason + ")", true,
                        reason == ApplicationExitInfo.REASON_UNKNOWN);
        }
    }

    static Result lookup(Context context, Identity identity, long observedAt) {
        if (Build.VERSION.SDK_INT < 30)
            return new Result("UNKNOWN (Android < 11)", true, false);
        if (identity == null)
            return new Result("UNKNOWN (worker PID unavailable)", true, false);
        return Api30.lookup(context, identity, observedAt);
    }

    @android.annotation.TargetApi(30)
    private static final class Api30 {
        static Result lookup(Context context, Identity identity, long observedAt) {
            try {
                ActivityManager manager = context.getSystemService(ActivityManager.class);
                if (manager != null) {
                    for (ApplicationExitInfo info : manager.getHistoricalProcessExitReasons(
                            context.getPackageName(), identity.pid, 4)) {
                        if (info.getPackageUid() == android.os.Process.myUid()
                                && matches(identity, info.getPid(), info.getRealUid(),
                                info.getProcessName(), info.getTimestamp(), observedAt)) {
                            return classify(info.getReason(), info.getStatus());
                        }
                    }
                }
            } catch (RuntimeException unavailable) {
                // OEM restrictions or a transient ActivityManager failure.
                return new Result("UNKNOWN (exit history unavailable)", true, true);
            }
            return new Result("UNKNOWN (matching exit record unavailable)", true, true);
        }
    }
}
