package io.mrarm.irc.lua;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;

/** Diagnostics only. No raw user script, file, network or IRC API is exposed. */
public final class LuaDiagnosticService extends Service {
    public static final int REQUEST_PROBE = 1;
    public static final int RESPONSE_PROBE = 2;
    public static final int REQUEST_LOAD = 3;
    public static final int REQUEST_EVENT = 4;
    public static final int REQUEST_RESET = 5;
    public static final int RESPONSE_SCRIPT = 6;
    public static final String EXTRA_SCRIPT_ID = "script_id";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_EVENT = "event";
    public static final String EXTRA_SERVER = "server";
    public static final String EXTRA_CHANNEL = "channel";
    public static final String EXTRA_NICK = "nick";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_MESSAGE_TYPE = "message_type";
    public static final String EXTRA_TIME = "time";
    public static final String EXTRA_REQUEST_ID = "request_id";
    public static final String EXTRA_ACTIONS = "actions";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_SCENARIO = "scenario";
    public static final String EXTRA_RESULT = "result";
    public static final String EXTRA_ISOLATED = "isolated";
    public static final String EXTRA_PID = "pid";

    private static final long SCRIPT_TIMEOUT_MS = 3000L;
    private final Handler watchdog = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.atomic.AtomicInteger activeCall =
            new java.util.concurrent.atomic.AtomicInteger();
    private HandlerThread worker;
    private Messenger messenger;

    @Override public void onCreate() {
        super.onCreate();
        if (!Process.isIsolated())
            throw new SecurityException("Lua must run in an isolated process");
        worker = new HandlerThread("tiarca-lua-experimental");
        worker.start();
        messenger = new Messenger(new Handler(worker.getLooper(), this::handle));
    }

    @Override public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private boolean handle(Message message) {
        if (message.what == REQUEST_RESET) {
            NativeLuaRuntime.clear();
            return true;
        }
        if (message.what == REQUEST_LOAD || message.what == REQUEST_EVENT) {
            handleUserScript(message);
            return true;
        }
        if (message.what != REQUEST_PROBE || message.replyTo == null)
            return true;
        String scenario = message.getData().getString(EXTRA_SCENARIO, "");
        String result;
        switch (scenario) {
            case "arithmetic":
                result = NativeLuaProbe.runProbe("result = 6 * 7");
                break;
            case "denied":
                result = NativeLuaProbe.runProbe(
                        "result = type(io)..':'..type(os)..':'..type(package)..':'..type(debug)");
                break;
            case "loop":
                result = NativeLuaProbe.runProbe("while true do end");
                break;
            case "memory":
                result = NativeLuaProbe.runProbe("result = string.rep('a', 32 * 1024 * 1024)");
                break;
            case "crash":
                // Kills *only* this isolated Android process. The IRC client must survive.
                Process.killProcess(Process.myPid());
                return true;
            default:
                result = "ERROR: unsupported diagnostic";
        }
        Message response = Message.obtain(null, RESPONSE_PROBE);
        Bundle payload = new Bundle();
        payload.putString(EXTRA_RESULT, result);
        payload.putBoolean(EXTRA_ISOLATED, Process.isIsolated());
        payload.putInt(EXTRA_PID, Process.myPid());
        response.setData(payload);
        try {
            message.replyTo.send(response);
        } catch (RemoteException ignored) {
            // The requesting Activity stopped.
        }
        return true;
    }

    private void handleUserScript(Message message) {
        if (message.replyTo == null) return;
        // Lua runs on a worker. The independent main looper is the watchdog:
        // a non-returning native function cannot bypass this by missing Lua
        // instruction hooks. Only this isolated process is terminated.
        final int generation = activeCall.incrementAndGet();
        Runnable timeout = () -> {
            if (activeCall.get() == generation)
                Process.killProcess(Process.myPid());
        };
        watchdog.postDelayed(timeout, SCRIPT_TIMEOUT_MS);
        try {
            handleScriptWithinBudget(message);
        } finally {
            activeCall.incrementAndGet();
            watchdog.removeCallbacks(timeout);
        }
    }

    private void handleScriptWithinBudget(Message message) {
        Bundle request = message.getData();
        String scriptId = request.getString(EXTRA_SCRIPT_ID);
        if (scriptId == null || scriptId.length() != 36) return;

        String status;
        java.util.ArrayList<String> actions = new java.util.ArrayList<>();
        try {
            if (message.what == REQUEST_LOAD) {
                byte[] source = request.getByteArray(EXTRA_SOURCE);
                status = source == null || source.length > LuaScriptStore.MAX_SOURCE_BYTES
                        ? "ERROR: source too large or missing"
                        : NativeLuaRuntime.decode(NativeLuaRuntime.load(scriptId, source));
            } else {
                String event = request.getString(EXTRA_EVENT, "");
                String server = request.getString(EXTRA_SERVER, "");
                String channel = request.getString(EXTRA_CHANNEL, "");
                String nick = request.getString(EXTRA_NICK, "");
                String text = request.getString(EXTRA_TEXT, "");
                String messageType = request.getString(EXTRA_MESSAGE_TYPE, "");
                if (server.length() != 36 || channel.length() > 64 ||
                        nick.length() > 64 || text.length() > 1024 ||
                        (int) text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2048 ||
                        !("message".equals(event) || "connected".equals(event) ||
                                "disconnected".equals(event))) {
                    status = "ERROR: invalid event";
                } else {
                    byte[][] nativeActions = NativeLuaRuntime.dispatch(scriptId, event,
                            NativeLuaRuntime.utf8(server), NativeLuaRuntime.utf8(channel),
                            NativeLuaRuntime.utf8(nick), NativeLuaRuntime.utf8(text),
                            NativeLuaRuntime.utf8(messageType), request.getLong(EXTRA_TIME));
                    if (nativeActions == null || nativeActions.length < 1 ||
                            (nativeActions.length - 1) % 3 != 0 ||
                            nativeActions.length > 37) {
                        status = "ERROR: Lua script not loaded";
                    } else {
                        status = NativeLuaRuntime.decode(nativeActions[0]);
                        if ("OK".equals(status))
                            for (int i = 1; i < nativeActions.length; i++)
                                actions.add(NativeLuaRuntime.decode(nativeActions[i]));
                    }
                }
            }
        } catch (RuntimeException error) {
            status = "ERROR: Lua runtime exception";
        }

        Message response = Message.obtain(null, RESPONSE_SCRIPT);
        Bundle payload = new Bundle();
        payload.putString(EXTRA_SCRIPT_ID, scriptId);
        payload.putInt(EXTRA_REQUEST_ID, request.getInt(EXTRA_REQUEST_ID));
        payload.putString(EXTRA_STATUS, status);
        payload.putStringArrayList(EXTRA_ACTIONS, actions);
        response.setData(payload);
        try {
            message.replyTo.send(response);
        } catch (RemoteException ignored) {
            // Requesting process has already gone away.
        }
    }

    @Override public void onDestroy() {
        if (worker != null) worker.quitSafely();
        super.onDestroy();
    }
}
