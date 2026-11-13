package io.mrarm.irc.lua;

/** Tracks which request the isolated Lua worker actually started. */
final class LuaCrashTracker {
    private int activeRequestId = -1;
    private String failedScriptId;
    private boolean failedDuringEvent;
    private int deaths;

    void started(int requestId) {
        if (requestId > 0) activeRequestId = requestId;
    }

    int activeRequestId() {
        return activeRequestId;
    }

    void completed(int requestId, String scriptId, boolean isEvent) {
        if (activeRequestId == requestId) activeRequestId = -1;
        if (scriptId != null && scriptId.equals(failedScriptId)
                && isEvent == failedDuringEvent) {
            failedScriptId = null;
            deaths = 0;
        }
    }

    /** First unexpected death gets a recovery attempt; second disables the culprit. */
    boolean terminated(String scriptId, boolean isEvent) {
        activeRequestId = -1;
        if (scriptId == null) return false;
        if (scriptId.equals(failedScriptId) && isEvent == failedDuringEvent) {
            deaths++;
        } else {
            failedScriptId = scriptId;
            failedDuringEvent = isEvent;
            deaths = 1;
        }
        return deaths >= 2;
    }

    void clearActive() { activeRequestId = -1; }

    void reset() {
        clearActive();
        failedScriptId = null;
        deaths = 0;
    }
}
