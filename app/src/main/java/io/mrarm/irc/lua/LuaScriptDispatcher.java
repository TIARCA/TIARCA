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

import io.mrarm.chatlib.NoSuchChannelException;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.StatusMessageInfo;
import java.util.Date;
import io.mrarm.chatlib.dto.NickChangeMessageInfo;
import io.mrarm.chatlib.irc.IrcIdentityCache;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.util.ChannelOperatorUtils;
import io.mrarm.irc.dialog.OperatorCommandComposer;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.chatlib.irc.ServerConnectionApi;
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
    private static final long MOD_INTERVAL_MS = 12000L;
    private static final long WHO_INTERVAL_MS = 60000L;
    private static final long TICK_INTERVAL_MS = 60000L;

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
    // Accessed only on ioThread; Java owns throttling, never the script.
    private final Map<String, Long> lastScriptModeration = new HashMap<>();
    private final Map<String, Long> lastServerModeration = new HashMap<>();
    private final Map<String, Long> lastWho = new HashMap<>();
    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            for (ServerConnectionInfo conn :
                    ServerConnectionManager.getInstance(context).getConnections()) {
                if (conn.isConnected())
                    enqueue("timer", conn.getUUID().toString(), "", "", "", "",
                            System.currentTimeMillis() / 1000);
            }
            main.postDelayed(this, TICK_INTERVAL_MS);
        }
    };
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
            main.removeCallbacks(timerTick);
            main.postDelayed(timerTick, TICK_INTERVAL_MS);
        });
    }

    public void stop() {
        main.post(() -> {
            running = false;
            main.removeCallbacks(timerTick);
            reconcileBinding();
        });
    }

    /** Called after a user enables/disables/imports/deletes a script. */
    public void reload() {
        main.post(() -> {
            running = true; // Allow on-demand script validation from Settings.
            reconcileBinding();
            main.removeCallbacks(timerTick);
            main.postDelayed(timerTick, TICK_INTERVAL_MS);
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
                channel == null || channel.isEmpty() ||
                info.isStatusMessage() || info.getBatch() != null) return;
        MessageInfo.MessageType type = info.getType();
        boolean content = type == MessageInfo.MessageType.NORMAL ||
                type == MessageInfo.MessageType.NOTICE ||
                type == MessageInfo.MessageType.ME;
        String event;
        if (content) event = "message";
        else if (type == MessageInfo.MessageType.JOIN) event = "join";
        else if (type == MessageInfo.MessageType.PART) event = "part";
        else if (type == MessageInfo.MessageType.QUIT) event = "quit";
        else if (type == MessageInfo.MessageType.NICK_CHANGE &&
                info instanceof NickChangeMessageInfo) event = "nick";
        else return;

        MessageSenderInfo sender = info.getSender();
        if (sender == null || sender.getNick() == null) return;
        if (content) {
            if (info.getMessage() == null) return;
            // Avoid echo/reply loops; presence events are still observable.
            try {
                if (sender.getNick().equalsIgnoreCase(connection.getUserNick())) return;
            } catch (RuntimeException ignored) {
                return;
            }
        }
        String value = "nick".equals(event) ?
                ((NickChangeMessageInfo) info).getNewNick() : info.getMessage();
        if (value == null) value = "";
        if (channel.length() > 64 || sender.getNick().length() > 64 ||
                value.length() > 1024 ||
                channel.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 96 ||
                sender.getNick().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 96 ||
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2048) return;
        String messageType = content ? type.name().toLowerCase(java.util.Locale.ROOT) : "";
        long timestamp = info.getDate() == null ?
                System.currentTimeMillis()/1000 : info.getDate().getTime()/1000;
        enqueue(event, connection.getUUID().toString(), channel, sender.getNick(),
                value, messageType, timestamp);
    }

    /**
     * Explicit local /lua invocation. Never forwards the command to the IRC
     * server; only enabled scripts on this connection receive the text.
     * False means the UI should retain the command for retry.
     */
    public boolean onCommand(ServerConnectionInfo connection, String channel, String command) {
        if (connection == null || !connection.isConnected() ||
                connection.getApiInstance() == null || command == null || command.isEmpty())
            return false;
        String target = channel == null ? "" : channel;
        String nick = connection.getUserNick();
        if (nick == null) nick = "";
        if (target.length() > 64 || nick.length() > 64 || command.length() > 1024 ||
                target.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 96 ||
                nick.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 96 ||
                command.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2048 ||
                command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 ||
                command.indexOf('\0') >= 0)
            return false;
        return enqueue("command", connection.getUUID().toString(), target, nick,
                command, "", System.currentTimeMillis() / 1000);
    }

    public void onConnectionChanged(ServerConnectionInfo connection, boolean connected) {
        if (connection == null) return;
        enqueue(connected ? "connected" : "disconnected",
                connection.getUUID().toString(), "", "", "", "",
                System.currentTimeMillis() / 1000);
    }

    private boolean enqueue(String event, String server, String channel,
                            String nick, String text, String messageType, long time) {
        if (remote == null || store.enabledOn(server).isEmpty()) return false;
        // Bound events *before* adding to HandlerThread, not only in-flight Binder
        // requests: otherwise a burst of IRC messages can grow the Java queue.
        if (queuedEvents.incrementAndGet() > MAX_PENDING_EVENTS) {
            queuedEvents.decrementAndGet();
            return false;
        }
        boolean accepted = io.post(() -> {
            try {
                if (remote == null) return;
                List<LuaScriptStore.Script> eligible = store.enabledOn(server);
                // Capture only names and cached counts from this approved IRC
                // connection. Do not block the chat/UI threads with the snapshot.
                LuaChannelSnapshot channels = LuaChannelSnapshot.empty();
                if (!"disconnected".equals(event) && !eligible.isEmpty()) {
                    try {
                        ServerConnectionInfo conn = ServerConnectionManager.getInstance(context)
                                .getConnection(UUID.fromString(server));
                        if (conn != null && conn.isConnected()
                                && conn.getApiInstance() instanceof ServerConnectionApi) {
                            channels = LuaChannelSnapshot.capture(
                                    ((ServerConnectionApi) conn.getApiInstance())
                                            .getServerConnectionData());
                        }
                    } catch (RuntimeException ignored) {
                        // Stale/disconnecting sessions provide an empty snapshot.
                    }
                }
                for (LuaScriptStore.Script script : eligible) {
                    LuaUserSnapshot users = LuaUserSnapshot.capture(null, null);
                    if (script.allowUserInfo && !"disconnected".equals(event)) {
                        try {
                            ServerConnectionInfo conn = ServerConnectionManager.getInstance(context)
                                    .getConnection(UUID.fromString(server));
                            if (conn != null && conn.isConnected() &&
                                    conn.getApiInstance() instanceof ServerConnectionApi)
                                users = LuaUserSnapshot.capture(
                                        ((ServerConnectionApi) conn.getApiInstance())
                                                .getServerConnectionData(), channel);
                        } catch (RuntimeException ignored) {
                            // A part/disconnect race returns an empty roster.
                        }
                    }
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
                    data.putStringArrayList(LuaDiagnosticService.EXTRA_CHANNEL_NAMES,
                            channels.names);
                    data.putIntArray(LuaDiagnosticService.EXTRA_CHANNEL_COUNTS, channels.counts);
                    data.putBoolean(LuaDiagnosticService.EXTRA_CHANNELS_TRUNCATED,
                            channels.truncated);
                    data.putStringArrayList(LuaDiagnosticService.EXTRA_USER_FIELDS,
                            users.fields);
                    data.putBoolean(LuaDiagnosticService.EXTRA_USERS_TRUNCATED,
                            users.truncated);
                    data.putBoolean(LuaDiagnosticService.EXTRA_CAN_READ_USERS,
                            script.allowUserInfo);
                    ArrayList<String> stored = new ArrayList<>();
                    if (script.allowStorage) {
                        try {
                            stored = store.storageSnapshot(script.id);
                        } catch (IOException error) {
                            store.fail(script.id, "Lua persistent data unavailable");
                            main.post(this::reload);
                            pendingEvents.decrementAndGet();
                            continue;
                        }
                    }
                    data.putStringArrayList(LuaDiagnosticService.EXTRA_STORAGE_PAIRS, stored);
                    data.putBoolean(LuaDiagnosticService.EXTRA_CAN_STORE, script.allowStorage);
                    request.setData(data);
                    sendMessage(request, new Pending(script.id, server, channel, event, true));
                }
            } finally {
                queuedEvents.decrementAndGet();
            }
        });
        if (!accepted) queuedEvents.decrementAndGet();
        return accepted;
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
            } else if ("store".equals(kind) || "erase".equals(kind)) {
                String operation = kind;
                io.post(() -> {
                    try {
                        store.applyStorage(script.id, request.server,
                                operation, target, value);
                    } catch (IOException error) {
                        store.fail(script.id, "Lua persistent data write failed");
                        main.post(this::reload);
                    }
                });
            } else if ("show".equals(kind) || "list".equals(kind)) {
                store.recordOutput(id, target, value, "list".equals(kind));
                if ("command".equals(request.event)) {
                    try {
                        ServerConnectionInfo conn = ServerConnectionManager.getInstance(context)
                                .getConnection(UUID.fromString(request.server));
                        if (conn != null && conn.isConnected() &&
                                conn.getApiInstance() instanceof ServerConnectionApi) {
                            String localText = "[Lua: " + script.name + "] " +
                                    target + ": " + ("list".equals(kind) ?
                                    value.replace('\n', ' ').replace('\r', ' ').substring(
                                            0, Math.min(180, value.length())) :
                                    value.replace('\n', ' ').replace('\r', ' '));
                            ((ServerConnectionApi) conn.getApiInstance())
                                    .getServerConnectionData().getServerStatusData()
                                    .addMessage(new StatusMessageInfo(null, new Date(),
                                            StatusMessageInfo.MessageType.NOTICE, localText));
                        }
                    } catch (RuntimeException ignored) {
                        // UI output is local only; disconnect must not interrupt scripts.
                    }
                }
            } else if ("mask".equals(kind)) {
                // Only a local user command may initiate a potentially large
                // channel WHO. The approved script cannot select another target.
                if ("command".equals(request.event) &&
                        LuaModerationPolicy.scoped(script, request.server,
                                request.channel, request.event, target, false) &&
                        MaskFinderMatcher.validQuery(value)) {
                    MaskFinderManager.get(context).startSearch(
                            script.id, request.server, target, value);
                }
            } else if ("who".equals(kind) || "kick".equals(kind) ||
                    "ban".equals(kind) || "mode".equals(kind) ||
                    "mute".equals(kind) || "tban".equals(kind)) {
                final String operation = kind;
                io.post(() -> performIrcAction(script.id, request, operation, target, value));
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

    /** Runs off the UI and IRC reader threads and rechecks consent at execution. */
    private void performIrcAction(String scriptId, Pending request, String kind,
                                  String channel, String value) {
        LuaScriptStore.Script script = store.find(scriptId);
        boolean who = "who".equals(kind);
        if (!LuaModerationPolicy.scoped(script, request.server, request.channel,
                request.event, channel, !who)) return;
        try {
            ServerConnectionInfo connection = ServerConnectionManager.getInstance(context)
                    .getConnection(UUID.fromString(request.server));
            if (connection == null || !connection.isConnected() ||
                    !(connection.getApiInstance() instanceof IRCConnection)) return;
            IRCConnection api = (IRCConnection) connection.getApiInstance();
            if (!api.getServerConnectionData().getSupportList()
                    .getSupportedChannelTypes().contains(channel.charAt(0))) return;
            try {
                api.getServerConnectionData().getJoinedChannelData(channel);
            } catch (NoSuchChannelException ignored) {
                return;
            }

            long now = SystemClock.elapsedRealtime();
            if (who) {
                if (!"refresh".equals(value)) return;
                if (now - lastWho.getOrDefault(script.id, -WHO_INTERVAL_MS)
                        < WHO_INTERVAL_MS) return;
                boolean whox = api.getServerConnectionData().getSupportList().isWhoxSupported();
                if (whox) api.getServerConnectionData().getIdentityCache().expectWhox();
                api.sendCommand("WHO", false, whox ?
                        new String[]{channel, "%tnuhaf," + IrcIdentityCache.whoxToken()} :
                        new String[]{channel}, null, null);
                lastWho.put(script.id, now);
                return;
            }

            if (now - lastScriptModeration.getOrDefault(script.id, -MOD_INTERVAL_MS)
                    < MOD_INTERVAL_MS ||
                    now - lastServerModeration.getOrDefault(request.server, -SERVER_INTERVAL_MS)
                            < SERVER_INTERVAL_MS) return;

            String nick;
            String reason = "";
            String mode = "";
            if ("kick".equals(kind) || "mode".equals(kind) || "tban".equals(kind)) {
                int tab = value == null ? -1 : value.indexOf('\t');
                if (tab <= 0) return;
                nick = value.substring(0, tab);
                if ("kick".equals(kind)) reason = value.substring(tab + 1);
                else mode = value.substring(tab + 1);
            } else if ("ban".equals(kind) || "mute".equals(kind)) {
                nick = value;
            } else return;
            if (!LuaModerationPolicy.liveTargetAllowed(
                    connection.isConnected(),
                    ChannelOperatorUtils.hasOperatorPrivileges(connection, channel, false),
                    ChannelOperatorUtils.isNickPresent(connection, channel, nick),
                    LuaModerationPolicy.isSelf(
                            api.getServerConnectionData().getSupportList().getCaseMapping(),
                            nick, connection.getUserNick()),
                    nick)) return;
            if ("kick".equals(kind)) {
                if (!LuaModerationPolicy.validReason(reason)) return;
                api.sendCommand("KICK", true, new String[]{channel, nick, reason},
                        null, null);
            } else if ("mode".equals(kind)) {
                if (!LuaModerationPolicy.permittedMode(mode)) return;
                api.sendCommand("MODE", false, new String[]{channel, mode, nick},
                        null, null);
            } else {
                IrcIdentityCache.Identity identity =
                        api.getServerConnectionData().getIdentityCache().get(nick);
                if (identity == null ||
                        !LuaModerationPolicy.validBanHost(identity.host)) return;
                String baseMask = "*!*@" + identity.host;
                OperatorCommandComposer.Dialect dialect =
                        new OperatorCommandComposer.Dialect(
                                api.getServerConnectionData().getSupportList().getExtban(),
                                api.getServerConnectionData().getSupportList().getAccountExtban());
                if ("mute".equals(kind)) {
                    String token = dialect.acting('m');
                    if (token == null) return; // Network has no known mute extban.
                    api.sendCommand("MODE", false,
                            new String[]{channel, "+b", token + baseMask}, null, null);
                } else if ("tban".equals(kind)) {
                    if (!mode.matches("(?i)[1-9][0-9]?[hmd]")) return;
                    String timed = dialect.timedCommand(channel, mode, baseMask);
                    if (timed == null) return; // Do not invent an unsupported syntax.
                    if (dialect.isInspStyle()) {
                        api.sendCommand("TBAN", false,
                                new String[]{channel, mode, baseMask}, null, null);
                    } else if (dialect.isUnrealStyle()) {
                        String timedMask = timed.substring(timed.lastIndexOf(' ') + 1);
                        api.sendCommand("MODE", false,
                                new String[]{channel, "+b", timedMask}, null, null);
                    } else return;
                } else {
                    api.sendCommand("MODE", false,
                            new String[]{channel, "+b", baseMask}, null, null);
                }
            }
            lastScriptModeration.put(script.id, now);
            lastServerModeration.put(request.server, now);
        } catch (RuntimeException ignored) {
            // IRC callbacks, disconnects and partial rosters must never crash TIARCA.
        }
    }

}
