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
    public static final int RESPONSE_SCRIPT_STARTED = 7;
    public static final String EXTRA_SCRIPT_ID = "script_id";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_EVENT = "event";
    public static final String EXTRA_SERVER = "server";
    public static final String EXTRA_CHANNEL = "channel";
    public static final String EXTRA_NICK = "nick";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_MESSAGE_TYPE = "message_type";
    public static final String EXTRA_TIME = "time";
    public static final String EXTRA_CHANNEL_NAMES = "channel_names";
    public static final String EXTRA_CHANNEL_COUNTS = "channel_counts";
    public static final String EXTRA_CHANNELS_TRUNCATED = "channels_truncated";
    public static final String EXTRA_USER_FIELDS = "user_fields";
    public static final String EXTRA_USERS_TRUNCATED = "users_truncated";
    public static final String EXTRA_CAN_READ_USERS = "can_read_users";
    public static final String EXTRA_STORAGE_PAIRS = "storage_pairs";
    public static final String EXTRA_CAN_STORE = "can_store";
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
    // Only the isolated worker accesses these overrides; scripts on other
    // networks continue using their original VM without interruption.
    private final java.util.Map<String, String> scopedRuntimeIds =
            new java.util.HashMap<>();

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
            scopedRuntimeIds.clear();
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
        // Confirm execution, not mere Binder queueing, for accurate crash
        // attribution in the main process.
        Message started = Message.obtain(null, RESPONSE_SCRIPT_STARTED);
        Bundle startedData = new Bundle();
        startedData.putString(EXTRA_SCRIPT_ID,
                message.getData().getString(EXTRA_SCRIPT_ID));
        startedData.putInt(EXTRA_REQUEST_ID,
                message.getData().getInt(EXTRA_REQUEST_ID));
        started.setData(startedData);
        try {
            message.replyTo.send(started);
        } catch (RemoteException ignored) {
            return;
        }
        // Lua runs on a worker. The independent main looper is the watchdog:
        // a non-returning native function cannot bypass this by missing Lua
        // instruction hooks. Only this isolated process is terminated.
        final int generation = activeCall.incrementAndGet();
        Runnable timeout = () -> {
            if (activeCall.get() != generation) return;
            // Best-effort precise timeout result before killing the stuck
            // isolated process. The native call itself cannot be interrupted.
            Message response = Message.obtain(null, RESPONSE_SCRIPT);
            Bundle payload = new Bundle();
            payload.putString(EXTRA_SCRIPT_ID,
                    message.getData().getString(EXTRA_SCRIPT_ID));
            payload.putInt(EXTRA_REQUEST_ID,
                    message.getData().getInt(EXTRA_REQUEST_ID));
            payload.putString(EXTRA_STATUS, "ERROR: Lua execution exceeded 3-second watchdog");
            payload.putStringArrayList(EXTRA_ACTIONS, new java.util.ArrayList<>());
            response.setData(payload);
            try {
                message.replyTo.send(response);
            } catch (RemoteException ignored) {
                // Main process is gone; still terminate the isolated service.
            } finally {
                Process.killProcess(Process.myPid());
            }
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
                String network = request.getString(EXTRA_SERVER, "");
                boolean scoped = !network.isEmpty();
                String runtimeId = scriptId;
                if (scoped) {
                    try {
                        if (!java.util.UUID.fromString(network).toString()
                                .equalsIgnoreCase(network))
                            throw new IllegalArgumentException("invalid network");
                        // Deterministic, 36-character VM ID accepted by the existing JNI.
                        runtimeId = java.util.UUID.nameUUIDFromBytes(
                                (scriptId + ":" + network).getBytes(
                                        java.nio.charset.StandardCharsets.UTF_8)).toString();
                    } catch (IllegalArgumentException invalid) {
                        status = "ERROR: invalid restart network";
                        returnReply(message, scriptId, status, actions);
                        return;
                    }
                }
                status = source == null || source.length > LuaScriptStore.MAX_SOURCE_BYTES
                        ? "ERROR: source too large or missing"
                        : NativeLuaRuntime.decode(NativeLuaRuntime.load(runtimeId, source));
                if (scoped) {
                    if ("OK".equals(status)) scopedRuntimeIds.put(scriptId + ":" + network, runtimeId);
                    else scopedRuntimeIds.remove(scriptId + ":" + network);
                }
            } else {
                String event = request.getString(EXTRA_EVENT, "");
                String server = request.getString(EXTRA_SERVER, "");
                String channel = request.getString(EXTRA_CHANNEL, "");
                String nick = request.getString(EXTRA_NICK, "");
                String text = request.getString(EXTRA_TEXT, "");
                String messageType = request.getString(EXTRA_MESSAGE_TYPE, "");
                java.util.ArrayList<String> names =
                        request.getStringArrayList(EXTRA_CHANNEL_NAMES);
                int[] memberCounts = request.getIntArray(EXTRA_CHANNEL_COUNTS);
                java.util.ArrayList<String> members =
                        request.getStringArrayList(EXTRA_USER_FIELDS);
                java.util.ArrayList<String> stored =
                        request.getStringArrayList(EXTRA_STORAGE_PAIRS);
                if (server.length() != 36 || channel.length() > 64 ||
                        nick.length() > 64 || text.length() > 1024 ||
                        channel.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 96 ||
                        nick.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 96 ||
                        text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2048 ||
                        !("message".equals(event) || "connected".equals(event) ||
                                "disconnected".equals(event) || "command".equals(event) ||
                                "join".equals(event) || "part".equals(event) ||
                                "quit".equals(event) || "nick".equals(event) ||
                                "timer".equals(event))) {
                    status = "ERROR: invalid event";
                } else if (names == null || memberCounts == null ||
                        names.size() != memberCounts.length ||
                        names.size() > LuaChannelSnapshot.MAX_CHANNELS ||
                        members == null ||
                        members.size() > LuaUserSnapshot.MAX_USERS * LuaUserSnapshot.FIELDS ||
                        members.size() % LuaUserSnapshot.FIELDS != 0 ||
                        stored == null || stored.size() > LuaScriptStorage.MAX_KEYS * 2 ||
                        stored.size() % 2 != 0 ||
                        (!request.getBoolean(EXTRA_CAN_STORE, false) && !stored.isEmpty())) {
                    status = "ERROR: invalid channel snapshot";
                } else {
                    byte[][] channels = new byte[names.size()][];
                    boolean valid = true;
                    for (int i = 0; i < names.size(); i++) {
                        String name = names.get(i);
                        if (name == null || name.isEmpty() || name.length() > 64 ||
                                memberCounts[i] < 0 || name.indexOf('\0') >= 0 ||
                                name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
                            valid = false;
                            break;
                        }
                        channels[i] = NativeLuaRuntime.utf8(name);
                        if (channels[i].length > 96) {
                            valid = false;
                            break;
                        }
                    }
                    if (!valid) {
                        status = "ERROR: invalid channel snapshot";
                    } else {
                        byte[][] userFields = new byte[members.size()][];
                        for (int i = 0; i < members.size(); i++) {
                            String value = members.get(i);
                            int field = i % LuaUserSnapshot.FIELDS;
                            int limit = field == 3 ? 256 : field == 1 ? 32 : 96;
                            if (value == null || value.indexOf('\0') >= 0 ||
                                    value.indexOf('\r') >= 0 ||
                                    value.indexOf('\n') >= 0 ||
                                    NativeLuaRuntime.utf8(value).length >= limit ||
                                    (field == 0 && value.isEmpty())) {
                                valid = false;
                                break;
                            }
                            userFields[i] = NativeLuaRuntime.utf8(value);
                        }
                        if (!valid) {
                            status = "ERROR: invalid user snapshot";
                        } else {
                            byte[][] storagePairs = new byte[stored.size()][];
                            int total = 0;
                            for (int i = 0; i < stored.size(); i += 2) {
                                String key = stored.get(i), value = stored.get(i + 1);
                                if (!LuaScriptStorage.validKey(key) ||
                                        !LuaScriptStorage.validValue(value) ||
                                        value.isEmpty()) {
                                    valid = false;
                                    break;
                                }
                                byte[] keyBytes = NativeLuaRuntime.utf8(key);
                                byte[] valueBytes = NativeLuaRuntime.utf8(value);
                                total += keyBytes.length + valueBytes.length;
                                if (total > LuaScriptStorage.MAX_TOTAL_BYTES) {
                                    valid = false;
                                    break;
                                }
                                storagePairs[i] = keyBytes;
                                storagePairs[i + 1] = valueBytes;
                            }
                            if (!valid) {
                                status = "ERROR: invalid Lua storage snapshot";
                            } else {
                                String runtimeId = scopedRuntimeIds.getOrDefault(
                                        scriptId + ":" + server, scriptId);
                                byte[][] nativeActions = NativeLuaRuntime.dispatch(runtimeId, event,
                                        NativeLuaRuntime.utf8(server), NativeLuaRuntime.utf8(channel),
                                        NativeLuaRuntime.utf8(nick), NativeLuaRuntime.utf8(text),
                                        NativeLuaRuntime.utf8(messageType), request.getLong(EXTRA_TIME),
                                        channels, memberCounts,
                                        request.getBoolean(EXTRA_CHANNELS_TRUNCATED, false),
                                        userFields,
                                        request.getBoolean(EXTRA_USERS_TRUNCATED, false),
                                        request.getBoolean(EXTRA_CAN_READ_USERS, false),
                                        storagePairs, request.getBoolean(EXTRA_CAN_STORE, false));
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
                        }
                    }
                }
        } catch (RuntimeException error) {
            status = "ERROR: Lua runtime exception";
        }

        returnReply(message, scriptId, status, actions);
    }

    private void returnReply(Message message, String scriptId, String status,
                             java.util.ArrayList<String> actions) {
        Message response = Message.obtain(null, RESPONSE_SCRIPT);
        Bundle payload = new Bundle();
        payload.putString(EXTRA_SCRIPT_ID, scriptId);
        payload.putInt(EXTRA_REQUEST_ID, message.getData().getInt(EXTRA_REQUEST_ID));
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
