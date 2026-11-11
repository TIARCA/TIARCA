package io.mrarm.irc.lua;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.ServerConnectionManager;

/**
 * Main-process gatekeeper for script capabilities.
 *
 * Lua never receives a Context, ChatApi, password, user@host or socket. Events
 * are copied into small IPC values. Even if a Lua script requests tiarca.send,
 * this class enforces current opt-in, server/target scope and a send rate limit.
 */
public final class LuaScriptDispatcher {
    private static LuaScriptDispatcher instance;
    private static final int MAX_PENDING_EVENTS = 32;
    private static final long SEND_INTERVAL_MS = 5000L;
    private static final long SERVER_INTERVAL_MS = 1500L;

    public static synchronized LuaScriptDispatcher get(Context context) {
        if (instance == null)
            instance = new LuaScriptDispatcher(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final LuaScriptStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread ioThread = new HandlerThread("tiarca-lua-ipc");
    private final Handler io;
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicInteger pendingEvents = new AtomicInteger();
    private final AtomicInteger queuedEvents = new AtomicInteger();
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, Long> lastScriptSend = new HashMap<>();
    private final Map<String, Long> lastServerSend = new HashMap<>();
    private volatile Messenger remote;
    private boolean bound; // main thread only
    private boolean running; // main thread only
    private final Messenger replies = new Messenger(
            new Handler(Looper.getMainLooper(), this::handleReply));

    private static final class Pending {
        final String scriptId;
        final String server;
        final String channel;
        final String event;
        final boolean isEvent;
        Pending(String id, String server, String channel, String event, boolean isEvent) {
            this.scriptId = id;
            this.server = server;
            this.channel = channel;
            this.event = event;
            this.isEvent = isEvent;
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            remote = new Messenger(service);
            io.post(LuaScriptDispatcher.this::loadAll);
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            disableInFlightAfterCrash();
            clearPending();
            remote = null;
            // With BIND_AUTO_CREATE Android may reconnect; then loadAll runs again.
        }

        @Override public void onBindingDied(ComponentName name) {
            disableInFlightAfterCrash();
            clearPending();
            remote = null;
            main.post(() -> {
                if (bound) {
                    context.unbindService(connection);
                    bound = false;
                }
                reconcileBinding();
            });
        }
    };

    private LuaScriptDispatcher(Context context) {
        this.context = context;
        this.store = LuaScriptStore.get(context);
        ioThread.start();
        io = new Handler(ioThread.getLooper());
    }

    public void start() {
        main.post(() -> {
            running = true;
            reconcileBinding();
        });
    }

    public void stop() {
        main.post(() -> {
            running = false;
            reconcileBinding();
        });
    }

    /** Called after a user enables/disables/imports/deletes a script. */
    public void reload() {
        main.post(() -> {
            running = true; // Allow on-demand script validation from Settings.
            reconcileBinding();
            if (remote != null) {
                // Discard replies from the old script generation. A reset may
                // invalidate in-flight events but must not disable new scripts.
                clearPending();
                io.post(this::loadAll);
            }
        });
    }

    private void reconcileBinding() {
        boolean needed = running && store.anyEnabled();
        if (needed && !bound) {
            try {
                bound = context.bindService(new Intent(context, LuaDiagnosticService.class),
                        connection, Context.BIND_AUTO_CREATE);
            } catch (RuntimeException error) {
                bound = false;
            }
        } else if (!needed && bound) {
            clearPending();
            remote = null;
            context.unbindService(connection);
            bound = false;
        }
    }

    private void disableInFlightAfterCrash() {
        // Only disable scripts actually executing when an isolated service dies;
        // unrelated, idle scripts are safe to reload after reconnection.
        for (Pending request : pending.values()) {
            // Includes script initialization: a top-level infinite loop must
            // also be disabled, rather than crashing repeatedly on reconnect.
            store.fail(request.scriptId,
                    "Lua process terminated while loading or executing this script");
        }
    }

    private void clearPending() {
        pending.clear();
        pendingEvents.set(0);
    }

    private void sendMessage(Message message, Pending identity) {
        Messenger target = remote;
        if (target == null) return;
        int id = sequence.incrementAndGet();
        message.replyTo = replies;
        message.getData().putInt(LuaDiagnosticService.EXTRA_REQUEST_ID, id);
        pending.put(id, identity);
        try {
            target.send(message);
        } catch (RemoteException error) {
            pending.remove(id);
            if (identity.isEvent) pendingEvents.decrementAndGet();
        }
    }

    /** Runs only on the coordinator worker; ordered before all subsequent events. */
    private void loadAll() {
        Messenger target = remote;
        if (target == null) return;
        try {
            target.send(Message.obtain(null, LuaDiagnosticService.REQUEST_RESET));
        } catch (RemoteException error) {
            return;
        }
        for (LuaScriptStore.Script script : store.list()) {
            if (!script.enabled) continue;
            byte[] source;
            try {
                source = store.sourceBytes(script);
            } catch (IOException error) {
                store.fail(script.id, "Lua source changed or unavailable");
                continue;
            }
            Message request = Message.obtain(null, LuaDiagnosticService.REQUEST_LOAD);
            Bundle data = new Bundle();
            data.putString(LuaDiagnosticService.EXTRA_SCRIPT_ID, script.id);
            data.putByteArray(LuaDiagnosticService.EXTRA_SOURCE, source);
            request.setData(data);
            sendMessage(request, new Pending(script.id, script.serverId, "", "", false));
        }
    }

    public void onMessage(ServerConnectionInfo connection, String channel, MessageInfo info) {
        if (connection == null || info == null || info.getType() == null ||
                channel == null || channel.isEmpty() || info.getMessage() == null ||
                info.isStatusMessage() || info.getBatch() != null) return;
        MessageInfo.MessageType type = info.getType();
        if (type != MessageInfo.MessageType.NORMAL &&
                type != MessageInfo.MessageType.NOTICE &&
                type != MessageInfo.MessageType.ME) return;

        MessageSenderInfo sender = info.getSender();
        if (sender == null || sender.getNick() == null) return;
        // Avoid automatic reply loops from our own messages, including server echo.
        try {
            if (sender.getNick().equalsIgnoreCase(connection.getUserNick())) return;
        } catch (RuntimeException ignored) {
            return;
        }
        String text = info.getMessage();
        if (channel.length() > 64 || sender.getNick().length() > 64 || text.length() > 1024 ||
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2048) return;
        String serverId = connection.getUUID().toString();
        String name = type.name().toLowerCase(java.util.Locale.ROOT);
        long timestamp = info.getDate() == null ?
                System.currentTimeMillis()/1000 : info.getDate().getTime()/1000;
        enqueue("message", serverId, channel, sender.getNick(), text, name, timestamp);
    }

    public void onConnectionChanged(ServerConnectionInfo connection, boolean connected) {
        if (connection == null) return;
        enqueue(connected ? "connected" : "disconnected",
                connection.getUUID().toString(), "", "", "", "",
                System.currentTimeMillis() / 1000);
    }

    private void enqueue(String event, String server, String channel,
                         String nick, String text, String messageType, long time) {
        if (remote == null || store.enabledOn(server).isEmpty()) return;
        // Bound events *before* adding to HandlerThread, not only in-flight Binder
        // requests: otherwise a burst of IRC messages can grow the Java queue.
        if (queuedEvents.incrementAndGet() > MAX_PENDING_EVENTS) {
            queuedEvents.decrementAndGet();
            return;
        }
        if (!io.post(() -> {
            try {
                if (remote == null) return;
                List<LuaScriptStore.Script> eligible = store.enabledOn(server);
                for (LuaScriptStore.Script script : eligible) {
                    if (pendingEvents.incrementAndGet() > MAX_PENDING_EVENTS) {
                        pendingEvents.decrementAndGet();
                        return; // drop event; never block IRC threads
                    }
                    Message request = Message.obtain(null, LuaDiagnosticService.REQUEST_EVENT);
                    Bundle data = new Bundle();
                    data.putString(LuaDiagnosticService.EXTRA_SCRIPT_ID, script.id);
                    data.putString(LuaDiagnosticService.EXTRA_EVENT, event);
                    data.putString(LuaDiagnosticService.EXTRA_SERVER, server);
                    data.putString(LuaDiagnosticService.EXTRA_CHANNEL, channel);
                    data.putString(LuaDiagnosticService.EXTRA_NICK, nick);
                    data.putString(LuaDiagnosticService.EXTRA_TEXT, text);
                    data.putString(LuaDiagnosticService.EXTRA_MESSAGE_TYPE, messageType);
                    data.putLong(LuaDiagnosticService.EXTRA_TIME, time);
                    request.setData(data);
                    sendMessage(request, new Pending(script.id, server, channel, event, true));
                }
            } finally {
                queuedEvents.decrementAndGet();
            }
        })) queuedEvents.decrementAndGet();
    }

    private boolean handleReply(Message message) {
        if (message.what != LuaDiagnosticService.RESPONSE_SCRIPT) return true;
        Bundle data = message.getData();
        Pending request = pending.remove(data.getInt(LuaDiagnosticService.EXTRA_REQUEST_ID));
        if (request == null) return true;
        if (request.isEvent) pendingEvents.decrementAndGet();
        String id = data.getString(LuaDiagnosticService.EXTRA_SCRIPT_ID);
        if (!request.scriptId.equals(id)) return true;
        LuaScriptStore.Script script = store.find(id);
        if (script == null || !script.enabled || !script.serverId.equals(request.server))
            return true;
        String status = data.getString(LuaDiagnosticService.EXTRA_STATUS, "");
        if (!"OK".equals(status)) {
            store.fail(id, status);
            reload();
            return true;
        }
        if (!request.isEvent) return true;
        ArrayList<String> actions = data.getStringArrayList(LuaDiagnosticService.EXTRA_ACTIONS);
        if (actions == null || actions.size() > 36 || actions.size() % 3 != 0) {
            store.fail(id, "Lua returned invalid actions");
            reload();
            return true;
        }
        for (int i = 0; i < actions.size(); i += 3) {
            String kind = actions.get(i);
            String target = actions.get(i + 1);
            String value = actions.get(i + 2);
            if ("log".equals(kind)) {
                store.recordLog(id, value);
            } else if ("send".equals(kind)) {
                sendAuthorized(script, request, target, value);
            } else {
                store.fail(id, "Lua returned an unsupported action");
                reload();
                return true;
            }
        }
        return true;
    }

    private void sendAuthorized(LuaScriptStore.Script snapshot, Pending request,
                                String target, String text) {
        LuaScriptStore.Script current = store.find(snapshot.id);
        if (!LuaActionPolicy.permitsSend(current, request.server, request.channel,
                request.event, target, text)) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastScriptSend.getOrDefault(snapshot.id, -SEND_INTERVAL_MS) <
                SEND_INTERVAL_MS ||
                now - lastServerSend.getOrDefault(request.server, -SERVER_INTERVAL_MS) <
                        SERVER_INTERVAL_MS) return;
        try {
            ServerConnectionInfo conn = ServerConnectionManager.getInstance(context)
                    .getConnection(UUID.fromString(request.server));
            if (conn == null || !conn.isConnected() || conn.getApiInstance() == null) return;
            // No raw IRC commands and no arbitrary target: only reply to the
            // incoming channel/private conversation on this explicitly approved server.
            conn.getApiInstance().sendMessage(target, text, null, null);
            lastScriptSend.put(snapshot.id, now);
            lastServerSend.put(request.server, now);
        } catch (RuntimeException ignored) {
            // Scripts must never crash or block the client.
        }
    }
}
