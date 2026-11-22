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
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.mrarm.chatlib.NoSuchChannelException;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.StatusMessageInfo;
import java.util.Date;
import io.mrarm.chatlib.dto.NickChangeMessageInfo;
import io.mrarm.chatlib.dto.KickMessageInfo;
import io.mrarm.chatlib.dto.ChannelModeMessageInfo;
import io.mrarm.chatlib.dto.MessageList;
import io.mrarm.chatlib.dto.MessageFilterOptions;
import java.util.Arrays;
import io.mrarm.chatlib.irc.IrcIdentityCache;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.util.ChannelOperatorUtils;
import io.mrarm.irc.dialog.OperatorCommandComposer;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.chatlib.dto.NickWithPrefix;
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
    private static final long WHO_INTERVAL_MS = 60000L;
    private static final long TICK_INTERVAL_MS = 60000L;
    private static final long BIND_RETRY_GRACE_MS = 30000L;

    public static synchronized LuaScriptDispatcher get(Context context) {
        if (instance == null)
            instance = new LuaScriptDispatcher(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final LuaScriptStore store;
    // Generic journal: no moderation rules or subscriptions in the Android host.
    private final LuaScriptJournal journal;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread ioThread = new HandlerThread("tiarca-lua-ipc");
    private final Handler io;
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicInteger pendingEvents = new AtomicInteger();
    private final AtomicInteger queuedEvents = new AtomicInteger();
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    // Main-looper only: one worker may queue many requests but execute just one.
    private final LuaCrashTracker crashTracker = new LuaCrashTracker();
    private final Map<String, Long> lastScriptSend = new HashMap<>();
    private final Map<String, Long> lastServerSend = new HashMap<>();
    // Main-thread only. Tabs opened by script PVTs, never pre-existing conversations.
    private final LuaPrivateTabTracker privateTabs = new LuaPrivateTabTracker();
    private final Map<String, PrivateTabWatcher> privateTabWatchers = new HashMap<>();
    // Accessed only on ioThread; scripts cannot bypass the ordered, bounded pacing.
    private final LuaModerationPacer moderationPacer = new LuaModerationPacer();
    private final LuaConsoleState console = new LuaConsoleState();
    // Main thread only. Scoped restarts do not replace other network VMs.
    private final Map<String, RestartBatch> restarts = new HashMap<>();
    private final Map<String, Long> lastWho = new HashMap<>();
    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            recoverMissingSandbox();
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
    private long lastBindAttemptMs; // main thread only
    private final Messenger replies = new Messenger(
            new Handler(Looper.getMainLooper(), this::handleReply));

    private static final class PrivateTabWatcher {
        final ServerConnectionInfo connection;
        final ServerConnectionInfo.ChannelListChangeListener listener;

        PrivateTabWatcher(ServerConnectionInfo connection,
                          ServerConnectionInfo.ChannelListChangeListener listener) {
            this.connection = connection;
            this.listener = listener;
        }
    }

    private static final class Pending {
        final String scriptId;
        final String server;
        final String channel;
        final String nick;
        final String event;
        final String commandText;
        final String eventText;
        final long eventTimestamp;
        final boolean isEvent;
        final long generation;

        Pending(String id, String server, String channel, String nick,
                String event, boolean isEvent, long generation) {
            this(id, server, channel, nick, event, isEvent, generation, "");
        }

        Pending(String id, String server, String channel, String nick,
                String event, boolean isEvent, long generation, String commandText) {
            this.scriptId = id;
            this.server = server;
            this.channel = channel;
            this.nick = nick;
            this.event = event;
            this.commandText = commandText;
            this.eventText = "";
            this.eventTimestamp = 0L;
            this.isEvent = isEvent;
            this.generation = generation;
        }

        Pending(String id, String server, String channel, String nick,
                String event, boolean isEvent, long generation, String commandText,
                String eventText, long eventTimestamp) {
            this.scriptId = id;
            this.server = server;
            this.channel = channel;
            this.nick = nick;
            this.event = event;
            this.commandText = commandText;
            this.eventText = eventText;
            this.eventTimestamp = eventTimestamp;
            this.isEvent = isEvent;
            this.generation = generation;
        }
    }

    private static final class RestartBatch {
        final long generation;
        int remaining;
        int failures;

        RestartBatch(long generation, int remaining) {
            this.generation = generation;
            this.remaining = remaining;
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            remote = new Messenger(service);
            io.post(LuaScriptDispatcher.this::loadAll);
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            lastBindAttemptMs = SystemClock.elapsedRealtime();
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
        this.journal = new LuaScriptJournal(context);
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
            io.post(moderationPacer::clear);
            abortRestarts();
            reconcileBinding();
        });
    }

    /** Called after a user enables/disables/imports/deletes a script. */
    public void reload() {
        main.post(() -> {
            running = true; // Allow on-demand script validation from Settings.
            crashTracker.reset();
            io.post(moderationPacer::clear); // Invalidate queued actions on permission/script changes.
            abortRestarts();
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

    /**
     * BIND_AUTO_CREATE normally reconnects a dead isolated process. Some device
     * implementations can leave the binding without a live Messenger. On a
     * connected, unpaused network, retry at the next minute tick after a grace
     * period; never revive a script disabled by the crash guard or /lua stop.
     */
    private void recoverMissingSandbox() {
        if (!running || remote != null || !store.anyEnabled()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastBindAttemptMs < BIND_RETRY_GRACE_MS) return;
        boolean needed = false;
        for (ServerConnectionInfo conn :
                ServerConnectionManager.getInstance(context).getConnections()) {
            if (conn.isConnected() && !console.isPaused(conn.getUUID().toString())
                    && !store.enabledOn(conn.getUUID().toString()).isEmpty()) {
                needed = true;
                break;
            }
        }
        if (!needed) return;
        if (bound) {
            context.unbindService(connection);
            bound = false;
        }
        reconcileBinding();
    }

    private void reconcileBinding() {
        boolean needed = running && store.anyEnabled();
        if (needed && !bound) {
            lastBindAttemptMs = SystemClock.elapsedRealtime();
            try {
                bound = context.bindService(new Intent(context, LuaDiagnosticService.class),
                        connection, Context.BIND_AUTO_CREATE);
            } catch (RuntimeException error) {
                bound = false;
            }
        } else if (!needed && bound) {
            crashTracker.reset();
            clearPending();
            remote = null;
            context.unbindService(connection);
            bound = false;
        }
    }

    private void disableInFlightAfterCrash() {
        // A request waiting for a Binder reply is not necessarily executing.
        // Attribute process death only to a worker-acknowledged request.
        Pending active = pending.get(crashTracker.activeRequestId());
        if (active == null) {
            crashTracker.clearActive(); // Idle sandbox may be reclaimed by Android.
            return;
        }
        if (crashTracker.terminated(active.scriptId, active.isEvent)) {
            store.fail(active.scriptId,
                    "Lua sandbox terminated twice while loading or executing this script");
        } else {
            store.recordLog(active.scriptId,
                    "Lua sandbox restarted after an unexpected termination; retrying");
            console.log(active.server, active.scriptId,
                    "Isolated Lua process terminated; recovering after first failure");
        }
    }

    private void abortRestarts() {
        for (Map.Entry<String, RestartBatch> entry : restarts.entrySet())
            console.failRestart(entry.getKey(), entry.getValue().generation);
        restarts.clear();
    }

    private void clearPending() {
        crashTracker.clearActive();
        pending.clear();
        pendingEvents.set(0);
        abortRestarts();
    }

    private void discardPendingForNetwork(String server) {
        for (Map.Entry<Integer, Pending> entry : pending.entrySet()) {
            Pending value = entry.getValue();
            if (server.equals(value.server) && pending.remove(entry.getKey(), value)
                    && value.isEvent) pendingEvents.decrementAndGet();
        }
    }

    private boolean sendMessage(Message message, Pending identity) {
        Messenger target = remote;
        if (target == null) return false;
        int id = sequence.incrementAndGet();
        message.replyTo = replies;
        message.getData().putInt(LuaDiagnosticService.EXTRA_REQUEST_ID, id);
        pending.put(id, identity);
        try {
            target.send(message);
            return true;
        } catch (RemoteException error) {
            pending.remove(id);
            if (identity.isEvent) pendingEvents.decrementAndGet();
            return false;
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
            sendMessage(request, new Pending(script.id, script.serverId, "", "", "", false,
                    console.generation(script.serverId)));
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
        else if (type == MessageInfo.MessageType.KICK &&
                info instanceof KickMessageInfo) event = "kick";
        else if (type == MessageInfo.MessageType.MODE &&
                info instanceof ChannelModeMessageInfo) {
            // A MODE may contain several +b masks. Emit one event per added ban.
            MessageSenderInfo modeSender = info.getSender();
            if (modeSender == null || modeSender.getNick() == null) return;
            long modeTime = info.getDate() == null ? System.currentTimeMillis()/1000 :
                    info.getDate().getTime()/1000;
            for (ChannelModeMessageInfo.Entry entry :
                    ((ChannelModeMessageInfo) info).getEntries()) {
                if (entry.getMode() != 'b' || entry.isRemoved() ||
                        entry.getParam() == null || entry.getParam().isEmpty()) continue;
                String mask = entry.getParam();
                if (mask.length() > 1024 ||
                        mask.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2048)
                    continue;
                enqueue("ban", connection.getUUID().toString(), channel,
                        modeSender.getNick(), mask, "", modeTime);

            }
            return;
        }
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
        // For kick, message_type carries the kicked target; nick is the actor.
        String messageType = "kick".equals(event) ?
                ((KickMessageInfo) info).getKickedNick() :
                content ? type.name().toLowerCase(java.util.Locale.ROOT) : "";
        if (messageType == null || messageType.length() > 64 ||
                messageType.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 96)
            return;
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
        if (connection == null || command == null || command.isEmpty())
            return false;
        if (!connection.isConnected() || connection.getApiInstance() == null)
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
        String builtin = command.trim().toLowerCase(Locale.ROOT);
        String server = connection.getUUID().toString();
        if ("stop".equals(builtin)) {
            console.stop(server);
            main.post(() -> {
                discardPendingForNetwork(server);
                restarts.remove(server);
                io.post(() -> moderationPacer.clearServer(server));
                emitConsole(connection, "[Lua] Script sospesi su questa rete. /lua start per riprendere.");
            });
            return true;
        }
        if ("start".equals(builtin)) {
            if (console.start(server) < 0) {
                emitConsole(connection, "[Lua] Riavvio in corso; /lua status per dettagli.");
                return true;
            }
            main.post(() -> {
                discardPendingForNetwork(server);
                restarts.remove(server);
                emitConsole(connection, "[Lua] Script ripresi su questa rete.");
            });
            return true;
        }
        if ("restart".equals(builtin)) {
            long generation = console.beginRestart(server);
            if (generation < 0) {
                emitConsole(connection, "[Lua] Riavvio gia' in corso.");
                return true;
            }
            main.post(() -> restartNetwork(connection, server, generation));
            return true;
        }
        if ("list".equals(builtin) || "help".equals(builtin) ||
                "status".equals(builtin) || "log".equals(builtin)) {
            showBuiltinCommand(connection, builtin);
            return true;
        }
        if (console.isPaused(server)) {
            emitConsole(connection, "[Lua] Script sospesi: /lua start o /lua restart.");
            return true;
        }
        return enqueue("command", server, target, nick,
                command, "", System.currentTimeMillis() / 1000);
    }

    private void emitConsole(ServerConnectionInfo connection, String text) {
        try {
            if (connection == null || !(connection.getApiInstance() instanceof ServerConnectionApi))
                return;
            ((ServerConnectionApi) connection.getApiInstance())
                    .getServerConnectionData().getServerStatusData()
                    .addMessage(new StatusMessageInfo(null, new Date(),
                            StatusMessageInfo.MessageType.NOTICE, text));
        } catch (RuntimeException ignored) {
            // Local diagnostics must not affect IRC.
        }
    }

    /** Local server-tab audit: the command was sent, not confirmed by IRC. */
    private void logModerationSent(ServerConnectionInfo connection,
                                   LuaScriptStore.Script script, String channel,
                                   String action, String nick, String detail) {
        if (script == null) return;
        String text = "[Lua: " + script.name + "] " + channel + " - " + action +
                " " + nick + (detail.isEmpty() ? "" : " (" + detail + ")") +
                " - comando inviato";
        main.post(() -> emitConsole(connection, text));
    }

    private void showBuiltinCommand(ServerConnectionInfo connection, String builtin) {
        String server = connection.getUUID().toString();
        List<String> lines = new ArrayList<>();
        if ("help".equals(builtin)) {
            lines.add("[Lua] /lua help - guida generale");
            lines.add("[Lua] /lua list - script attivi sulla rete");
            lines.add("[Lua] /lua status - motore, script, errori e coda");
            lines.add("[Lua] /lua stop | start - sospende o riprende sulla rete");
            lines.add("[Lua] /lua restart - reinizializza gli script di questa rete");
            lines.add("[Lua] /lua log - ultimi messaggi degli script sulla rete");
            lines.add("[Lua] /lua mask help | mod help - guida degli script");
        } else if ("list".equals(builtin)) {
            List<LuaScriptStore.Script> scripts = store.enabledOn(server);
            lines.add("[Lua] " + connection.getName() + " - script abilitati: " +
                    scripts.size() + (console.isRestarting(server) ?
                            " (riavvio in corso)" :
                            console.isPaused(server) ? " (sospesi)" : ""));
            for (LuaScriptStore.Script script : scripts)
                lines.add("[Lua] " + script.name);
        } else if ("log".equals(builtin)) {
            List<LuaConsoleState.LogLine> history = console.logs(server);
            lines.add("[Lua] " + connection.getName() + " - ultimi log: " + history.size());
            if (history.isEmpty()) lines.add("[Lua] Nessun log disponibile");
            for (LuaConsoleState.LogLine line : history)
                lines.add("[Lua] " + line.script + ": " + line.message);
        } else if ("status".equals(builtin)) {
            io.post(() -> {
                int queued = moderationPacer.pendingCount(server);
                main.post(() -> showNetworkStatus(connection, queued));
            });
            return;
        }
        for (String line : lines) emitConsole(connection, line);
    }

    private void showNetworkStatus(ServerConnectionInfo connection, int queued) {
        String server = connection.getUUID().toString();
        List<LuaScriptStore.Script> installed = store.list();
        List<LuaScriptStore.Script> active = store.enabledOn(server);
        boolean paused = console.isPaused(server);
        emitConsole(connection, "[Lua] Stato - " + connection.getName() + ": " +
                (console.isRestarting(server) ? "RIAVVIO IN CORSO" :
                        paused ? "SOSPESO" :
                        remote == null ? "MOTORE NON DISPONIBILE" : "ATTIVO"));
        emitConsole(connection, "[Lua] Script sulla rete: " + active.size() +
                "; moderazioni in coda: " + queued);
        for (LuaScriptStore.Script script : installed) {
            String status = script.isEnabledOn(server) ?
                    (paused ? "sospeso" : remote == null ? "motore assente" : "attivo") :
                    script.enabled ? "abilitato su altre reti" : "disattivato";
            if (script.error != null && !script.error.isEmpty())
                status += " - errore: " + script.error;
            emitConsole(connection, "[Lua] " + script.name + ": " + status);
        }
        emitConsole(connection, "[Lua] Memoria effettiva: non disponibile; limite VM 2 MiB, globale 32 MiB");
    }

    private void restartNetwork(ServerConnectionInfo connection, String server,
                                long generation) {
        if (!console.isPaused(server) || console.generation(server) != generation) return;
        discardPendingForNetwork(server);
        io.post(() -> moderationPacer.clearServer(server));
        List<LuaScriptStore.Script> scripts = store.enabledOn(server);
        if (scripts.isEmpty()) {
            console.completeRestart(server, generation);
            emitConsole(connection, "[Lua] Nessuno script abilitato da riavviare.");
            return;
        }
        if (remote == null) {
            console.failRestart(server, generation);
            emitConsole(connection, "[Lua] Motore non disponibile; riavvio non eseguito.");
            return; // Remain paused until explicit /lua start or retry.
        }
        restarts.put(server, new RestartBatch(generation, scripts.size()));
        io.post(() -> {
            for (LuaScriptStore.Script script : scripts) {
                if (!console.isPaused(server) || console.generation(server) != generation)
                    return;
                try {
                    Message load = Message.obtain(null, LuaDiagnosticService.REQUEST_LOAD);
                    Bundle data = new Bundle();
                    data.putString(LuaDiagnosticService.EXTRA_SCRIPT_ID, script.id);
                    data.putString(LuaDiagnosticService.EXTRA_SERVER, server);
                    data.putByteArray(LuaDiagnosticService.EXTRA_SOURCE, store.sourceBytes(script));
                    load.setData(data);
                    if (!sendMessage(load, new Pending(script.id, server, "", "",
                            "restart", false, generation)))
                        main.post(() -> restartResult(connection, server, generation,
                                script.name, "ERROR: motor IPC unavailable"));
                } catch (IOException error) {
                    main.post(() -> restartResult(connection, server, generation,
                            script.name, "ERROR: script source unavailable"));
                }
            }
        });
    }

    private void restartResult(ServerConnectionInfo connection, String server,
                               long generation, String script, String status) {
        RestartBatch batch = restarts.get(server);
        if (batch == null || batch.generation != generation) return;
        if (!"OK".equals(status)) {
            batch.failures++;
            console.log(server, script, status);
        }
        if (--batch.remaining > 0) return;
        restarts.remove(server);
        if (batch.failures == 0 && console.completeRestart(server, generation)) {
            emitConsole(connection, "[Lua] Riavviati " +
                    (store.enabledOn(server).size()) + " script sulla rete " +
                    connection.getName() + ". Memoria temporanea azzerata.");
        } else if (batch.failures > 0) {
            console.failRestart(server, generation);
            emitConsole(connection, "[Lua] Riavvio incompleto (" + batch.failures +
                    " errori). Script sospesi: /lua log per i dettagli.");
        }
    }

    public void onConnectionChanged(ServerConnectionInfo connection, boolean connected) {
        if (connection == null) return;
        if (!connected) {
            String serverId = connection.getUUID().toString();
            io.post(() -> moderationPacer.clearServer(serverId));
        }
        enqueue(connected ? "connected" : "disconnected",
                connection.getUUID().toString(), "", "", "", "",
                System.currentTimeMillis() / 1000);
        if (connected) main.post(this::recoverMissingSandbox);
    }

    private boolean enqueue(String event, String server, String channel,
                            String nick, String text, String messageType, long time) {
        return enqueueOnly(event, server, channel, nick, text, messageType, time, null);
    }

    /** Optional single-script delivery for asynchronous history results. */
    private boolean enqueueOnly(String event, String server, String channel,
                                String nick, String text, String messageType, long time,
                                String onlyScriptId) {
        return enqueueOnlyWithIdentity(event, server, channel, nick, text, messageType,
                time, onlyScriptId, null, null);
    }

    /** Sender fields from the actual stored chat message, not a guessed current identity. */
    private boolean enqueueOnlyWithIdentity(String event, String server, String channel,
            String nick, String text, String messageType, long time, String onlyScriptId,
            String historyIdent, String historyHost) {
        if (remote == null || console.isPaused(server) ||
                store.enabledOn(server).isEmpty()) return false;
        long currentGeneration = console.generation(server);
        // Bound events *before* adding to HandlerThread, not only in-flight Binder
        // requests: otherwise a burst of IRC messages can grow the Java queue.
        if (queuedEvents.incrementAndGet() > MAX_PENDING_EVENTS) {
            queuedEvents.decrementAndGet();
            return false;
        }
        boolean accepted = io.post(() -> {
            try {
                if (remote == null || console.isPaused(server) ||
                        console.generation(server) != currentGeneration) return;
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
                    if (onlyScriptId != null && !onlyScriptId.equals(script.id)) continue;
                    LuaUserSnapshot users = LuaUserSnapshot.capture(null, null);
                    if (script.allowUserInfo && !"disconnected".equals(event)) {
                        try {
                            ServerConnectionInfo conn = ServerConnectionManager.getInstance(context)
                                    .getConnection(UUID.fromString(server));
                            if (conn != null && conn.isConnected() &&
                                    conn.getApiInstance() instanceof ServerConnectionApi)
                                users = LuaUserSnapshot.capture(
                                        ((ServerConnectionApi) conn.getApiInstance())
                                                .getServerConnectionData(), channel, nick);
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
                    if ("history".equals(event)) {
                        data.putString(LuaDiagnosticService.EXTRA_HISTORY_IDENT,
                                historyIdent == null ? "" : historyIdent);
                        data.putString(LuaDiagnosticService.EXTRA_HISTORY_HOST,
                                historyHost == null ? "" : historyHost);
                    }
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
                    sendMessage(request, new Pending(script.id, server, channel, nick, event, true,
                            console.generation(server), "command".equals(event) ? text : "",
                            "message".equals(event) ? text : "", time));
                }
            } finally {
                queuedEvents.decrementAndGet();
            }
        });
        if (!accepted) queuedEvents.decrementAndGet();
        return accepted;
    }

    private static String safeHistoricIdentity(String value, int limit) {
        return value != null && value.length() <= limit &&
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= limit &&
                value.indexOf('\n') < 0 && value.indexOf('\r') < 0 &&
                value.indexOf('\t') < 0 && value.indexOf('\0') < 0 ?
                value : "";
    }

    /** Read the bounded existing SQLite history only after an authorized Lua
     * callback explicitly requests it. No automatic collector queries. */
    private void lookupHistoryForScript(LuaScriptStore.Script script, Pending request,
                                        String channel, String value) {
        if (!script.allowStorage || !script.allowUserInfo ||
                !channel.equals(request.channel) ||
                !("kick".equals(request.event) || "ban".equals(request.event) ||
                  "moderation_log".equals(request.event)) ||
                channel.length() > 64 || value == null) return;
        String[] parts = value.split("\\t", -1);
        if (parts.length != 2 || parts[0].isEmpty() || parts[0].length() > 64 ||
                !parts[0].matches("[^\\s,!:]+") ||
                !parts[1].matches("[a-z0-9]{1,20}")) return;
        final String nick = parts[0], token = parts[1];
        final String server = request.server;
        final long cutoff = request.eventTimestamp;
        final java.util.function.BiConsumer<String, Long> respond = (body, when) ->
                enqueueOnly("history", server, channel, nick,
                        body == null ? "" : body, token, when, script.id);
        try {
            ServerConnectionInfo conn = ServerConnectionManager.getInstance(context)
                    .getConnection(UUID.fromString(server));
            if (conn == null || !(conn.getApiInstance() instanceof ServerConnectionApi)) {
                respond.accept(null, 0L);
                return;
            }
            ServerConnectionApi api = (ServerConnectionApi) conn.getApiInstance();
            // The event already scopes the channel; the target may have just been kicked.
            MessageFilterOptions filter = new MessageFilterOptions();
            filter.restrictToMessageTypes = Arrays.asList(
                    MessageInfo.MessageType.NORMAL, MessageInfo.MessageType.ME);
            api.getMessageStorageApi().getMessages(channel, 200, filter, null,
                    (MessageList history) -> {
                        if (history != null && history.getMessages() != null) {
                            List<MessageInfo> messages = history.getMessages();
                            for (int i = messages.size() - 1; i >= 0; i--) {
                                MessageInfo m = messages.get(i);
                                if (m == null || m.getSender() == null ||
                                        m.getSender().getNick() == null ||
                                        !nick.equalsIgnoreCase(m.getSender().getNick()) ||
                                        m.getMessage() == null || m.getDate() == null ||
                                        m.getDate().getTime() / 1000 > cutoff) continue;
                                String text = m.getMessage();
                                // JNI and Binder limits: preserve a valid bounded prefix.
                                if (text.length() > 512 ||
                                        text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2048)
                                    text = text.substring(0, Math.min(512, text.length()));
                                enqueueOnlyWithIdentity("history", server, channel, nick,
                                        text, token, m.getDate().getTime() / 1000,
                                        script.id, safeHistoricIdentity(m.getSender().getUser(), 96),
                                        safeHistoricIdentity(m.getSender().getHost(), 256));
                                return;
                            }
                        }
                        respond.accept(null, 0L);
                    }, error -> respond.accept(null, 0L));
        } catch (RuntimeException ignored) {
            respond.accept(null, 0L);
        }
    }

    private boolean handleReply(Message message) {
        Bundle data = message.getData();
        int requestId = data.getInt(LuaDiagnosticService.EXTRA_REQUEST_ID);
        if (message.what == LuaDiagnosticService.RESPONSE_SCRIPT_STARTED) {
            Pending started = pending.get(requestId);
            if (started != null && started.scriptId.equals(
                    data.getString(LuaDiagnosticService.EXTRA_SCRIPT_ID)))
                crashTracker.started(requestId);
            return true;
        }
        if (message.what != LuaDiagnosticService.RESPONSE_SCRIPT) return true;
        Pending request = pending.remove(requestId);
        if (request == null) return true;
        crashTracker.completed(requestId, request.scriptId, request.isEvent);
        if (request.isEvent) pendingEvents.decrementAndGet();
        String id = data.getString(LuaDiagnosticService.EXTRA_SCRIPT_ID);
        if (!request.scriptId.equals(id)) return true;
        String status = data.getString(LuaDiagnosticService.EXTRA_STATUS, "");
        if ("restart".equals(request.event)) {
            LuaScriptStore.Script restartScript = store.find(id);
            ServerConnectionInfo conn = null;
            try {
                conn = ServerConnectionManager.getInstance(context)
                        .getConnection(UUID.fromString(request.server));
            } catch (RuntimeException ignored) { }
            if (conn != null)
                restartResult(conn, request.server, request.generation,
                        restartScript == null ? id : restartScript.name, status);
            return true;
        }
        if (request.isEvent && (console.isPaused(request.server) ||
                console.generation(request.server) != request.generation))
            return true;
        LuaScriptStore.Script script = store.find(id);
        if (script == null || !script.isEnabledOn(request.server))
            return true;
        if (!"OK".equals(status)) {
            console.log(request.server, script.name, status);
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
                console.log(request.server, script.name, value);
                // Automatic moderation decisions used to remain in /lua log only.
                // Show a bounded, sender/channel-verified notice on the Server tab
                // even when a warning is private or a requested action is denied.
                String report = LuaAutoModReport.fromEventLog(
                        request.event, request.server, request.channel, request.nick, value);
                if (report != null) {
                    // Publish a generic observed moderation report to enabled Lua
                    // scripts; only an external script decides whether to archive it.
                    if (!"moderation_log".equals(request.event))
                        enqueue("moderation_log", request.server, request.channel,
                                request.nick, value, script.name, request.eventTimestamp);
                    try {
                        ServerConnectionInfo connection = ServerConnectionManager
                                .getInstance(context).getConnection(UUID.fromString(request.server));
                        emitConsole(connection, "[Lua: " + script.name + "] " + report);
                    } catch (RuntimeException ignored) {
                        // Local reporting must not interfere with IRC or moderation.
                    }
                }
            } else if ("send".equals(kind)) {
                // Queue behind preceding persistent writes in this reply.
                io.post(() -> main.post(() ->
                        sendAuthorized(script, request, target, value)));
            } else if ("closepm".equals(kind)) {
                // Only a locally typed /lua mod close may clean up this script's tabs.
                if ("command".equals(request.event) &&
                        "mod close".equals(request.commandText.trim().toLowerCase(Locale.ROOT)) &&
                        target.isEmpty() && "1".equals(value))
                    closeScriptPrivateTabs(script, request.server);
            } else if ("pm".equals(kind) || "notice".equals(kind)) {
                boolean notice = "notice".equals(kind);
                io.post(() -> main.post(() ->
                        sendDirectAuthorized(script, request, target, value, notice)));
            } else if ("journal".equals(kind)) {
                if (!script.allowStorage || !target.isEmpty()) continue;
                io.post(() -> {
                    try { journal.append(script.id, request.server, value); }
                    catch (IOException error) {
                        console.log(request.server, script.name,
                                "Lua journal append failed: " + error.getMessage());
                    }
                });
            } else if ("export".equals(kind)) {
                if (!script.allowStorage || !"command".equals(request.event) ||
                        !target.isEmpty() || !"1".equals(value)) continue;
                io.post(() -> {
                    try {
                        java.io.File snapshot = journal.snapshot(script.id, request.server);
                        main.post(() -> {
                            try { journal.share(snapshot); }
                            catch (RuntimeException error) {
                                console.log(request.server, script.name,
                                        "Lua journal export unavailable");
                            }
                        });
                    } catch (IOException error) {
                        console.log(request.server, script.name,
                                "Lua journal export failed: " + error.getMessage());
                    }
                });
            } else if ("rotate".equals(kind) || "archive".equals(kind)) {
                // Explicit, script-controlled local command; no Collector-specific
                // moderation or collection logic runs in the Android host.
                if (!script.allowStorage || !"command".equals(request.event) ||
                        !target.isEmpty() || !"1".equals(value)) continue;
                io.post(() -> {
                    try {
                        java.io.File snapshot = "rotate".equals(kind)
                                ? journal.rotate(script.id, request.server)
                                : journal.snapshotLastArchive(script.id, request.server);
                        main.post(() -> {
                            try { journal.share(snapshot); }
                            catch (RuntimeException error) {
                                console.log(request.server, script.name,
                                        "Lua archived journal share unavailable; archive retained");
                            }
                        });
                    } catch (IOException error) {
                        console.log(request.server, script.name,
                                "Lua archived journal operation failed: " + error.getMessage());
                    }
                });
            } else if ("history".equals(kind)) {
                lookupHistoryForScript(script, request, target, value);
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
                // Permit an explicitly named joined channel on this same network,
                // never an unrequested channel, pattern or remote event.
                if (MaskFinderCommandPolicy.permits(script, request.server,
                        request.event, request.channel, request.commandText, target, value)) {
                    MaskFinderManager.get(context).startSearch(
                            script.id, request.server, target, value);
                }
            } else if ("who".equals(kind) || "kick".equals(kind) ||
                    "ban".equals(kind) || "mode".equals(kind) ||
                    "mute".equals(kind) || "tban".equals(kind) || "tmute".equals(kind) ||
                    "kban".equals(kind) || "ktban".equals(kind) ||
                    "blkmod".equals(kind) || "eject".equals(kind)) {
                final String operation = kind;
                if ("who".equals(operation)) {
                    io.post(() -> performIrcAction(script.id, request, operation, target, value));
                } else {
                    // Reserve ordered server-local slots; never drop the second part
                    // of a legitimate ban + kick merely because it is too soon.
                    io.post(() -> scheduleModeration(script.id, request,
                            operation, target, value));
                }
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
        if (console.isPaused(request.server) ||
                console.generation(request.server) != request.generation) return;
        LuaScriptStore.Script current = store.find(snapshot.id);
        if (!LuaActionPolicy.permitsSend(current, request.server, request.channel,
                request.event, target, text)) return;

        long now = SystemClock.elapsedRealtime();
        String scriptNetwork = snapshot.id + ":" + request.server;
        if (now - lastScriptSend.getOrDefault(scriptNetwork, -SEND_INTERVAL_MS) <
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
            lastScriptSend.put(scriptNetwork, now);
            lastServerSend.put(request.server, now);
        } catch (RuntimeException ignored) {
            // Scripts must never crash or block the client.
        }
    }

    /** Restricted outgoing PVT/NOTICE to the sender of a channel message. */
    private void sendDirectAuthorized(LuaScriptStore.Script snapshot, Pending request,
                                      String target, String text, boolean notice) {
        if (console.isPaused(request.server) ||
                console.generation(request.server) != request.generation) return;
        LuaScriptStore.Script current = store.find(snapshot.id);
        try {
            ServerConnectionInfo conn = ServerConnectionManager.getInstance(context)
                    .getConnection(UUID.fromString(request.server));
            if (conn == null || !conn.isConnected() ||
                    !(conn.getApiInstance() instanceof IRCConnection)) return;
            IRCConnection api = (IRCConnection) conn.getApiInstance();
            if (!LuaActionPolicy.permitsDirectReply(current, request.server,
                    request.channel, request.event, request.nick, target, text,
                    api.getServerConnectionData().getSupportList().getCaseMapping()))
                return;
            if (!api.getServerConnectionData().getSupportList()
                    .getSupportedChannelTypes().contains(request.channel.charAt(0)) ||
                    !ChannelOperatorUtils.isNickPresent(conn, request.channel, target))
                return;
            long now = SystemClock.elapsedRealtime();
            String scriptNetwork = snapshot.id + ":" + request.server;
            if (now - lastScriptSend.getOrDefault(scriptNetwork, -SEND_INTERVAL_MS) <
                    SEND_INTERVAL_MS ||
                    now - lastServerSend.getOrDefault(request.server, -SERVER_INTERVAL_MS) <
                    SERVER_INTERVAL_MS) return;
            if (notice) {
                api.sendNotice(target, text, null, null);
            } else if (conn.hasOpenConversation(target)) {
                // A manually opened PVT is never claimed by Lua.
                api.sendMessage(target, text, null, null);
            } else {
                api.sendMessage(target, text, ignored -> main.post(() -> {
                    if (conn.hasOpenConversation(target)) {
                        privateTabs.track(request.server, snapshot.id, target);
                        watchScriptPrivateTabs(conn, request.server);
                    }
                }), ignored -> { /* A failed send must not claim a tab. */ });
            }
            lastScriptSend.put(scriptNetwork, now);
            lastServerSend.put(request.server, now);
        } catch (RuntimeException ignored) {
            // A stale nick or disconnected server must not crash TIARCA.
        }
    }

    /** Keep ownership accurate when the user closes a private tab manually. */
    private void watchScriptPrivateTabs(ServerConnectionInfo connection, String server) {
        PrivateTabWatcher old = privateTabWatchers.get(server);
        if (old != null && old.connection == connection) return;
        if (old != null)
            old.connection.removeOnChannelListChangeListener(old.listener);
        ServerConnectionInfo.ChannelListChangeListener listener = (changed, channels) ->
                main.post(() -> {
                    privateTabs.prune(server, channels);
                    if (!privateTabs.hasTracked(server)) unwatchScriptPrivateTabs(server);
                });
        connection.addOnChannelListChangeListener(listener);
        privateTabWatchers.put(server, new PrivateTabWatcher(connection, listener));
    }

    private void unwatchScriptPrivateTabs(String server) {
        PrivateTabWatcher watcher = privateTabWatchers.remove(server);
        if (watcher != null)
            watcher.connection.removeOnChannelListChangeListener(watcher.listener);
    }

    /** Local-only cleanup. Does not send IRC messages or touch moderation history. */
    private void closeScriptPrivateTabs(LuaScriptStore.Script script, String server) {
        try {
            ServerConnectionInfo connection = ServerConnectionManager.getInstance(context)
                    .getConnection(UUID.fromString(server));
            if (connection == null) return;
            List<String> owned = privateTabs.take(server, script.id, connection.getChannels());
            if (!privateTabs.hasTracked(server)) unwatchScriptPrivateTabs(server);
            for (String nick : owned)
                if (connection.hasOpenConversation(nick))
                    connection.closePrivateConversation(nick, "");
            emitConsole(connection, "[Lua: " + script.name + "] PVT aperti dallo script chiusi: " +
                    owned.size());
        } catch (RuntimeException ignored) {
            // Closing an obsolete/disconnected tab must not interrupt Lua.
        }
    }

    /**
     * Runs on ioThread. Bounded outstanding actions prevent untrusted scripts from
     * building an unbounded backlog. Every queued action is revalidated on delivery.
     */
    private void scheduleModeration(String scriptId, Pending request, String kind,
                                    String channel, String value) {
        if (console.isPaused(request.server) ||
                console.generation(request.server) != request.generation) return;
        LuaScriptStore.Script script = store.find(scriptId);
        if ("eject".equals(kind) ?
                !LuaModerationPolicy.scopedEject(script, request.server, request.channel,
                        request.event, channel) :
                !LuaModerationPolicy.scoped(script, request.server, request.channel,
                        request.event, channel, true)) return;
        LuaModerationPacer.Slot slot = moderationPacer.reserve(
                request.server, scriptId, SystemClock.elapsedRealtime());
        if (slot == null) {
            store.recordLog(scriptId, "Moderation queue full or too old; action not scheduled");
            return;
        }
        boolean accepted = io.postDelayed(() -> {
            if (!moderationPacer.isCurrent(slot) ||
                    console.isPaused(request.server) ||
                    console.generation(request.server) != request.generation) return;
            try {
                // Android may pause the worker past its scheduled time.
                // Never punish a user for an obsolete, delayed event.
                if (moderationPacer.isExpired(slot, SystemClock.elapsedRealtime())) {
                    store.recordLog(scriptId, "Moderation action expired in queue");
                    return;
                }
                performIrcAction(scriptId, request, kind, channel, value);
            } finally {
                moderationPacer.complete(slot);
            }
        }, slot.delayMs);
        if (!accepted) moderationPacer.complete(slot);
    }

    /**
     * Manual, atomic preflight of a multi-ban. All masks are derived from the
     * currently observed identity while the target is still present; after KICK
     * another membership lookup would lose the user.
     */
    private void performEject(ServerConnectionInfo connection, IRCConnection api,
                              LuaScriptStore.Script script, String channel, String value) {
        if (value == null) return;
        String[] parts = value.split("\\t", -1);
        if (parts.length != 2) return;
        String nick = parts[0], reason = parts[1];
        if (!LuaModerationPolicy.validNick(nick) ||
                !LuaModerationPolicy.validReason(reason)) return;
        NickWithPrefix member = ChannelOperatorUtils.findMember(connection, channel, nick);
        if (!LuaModerationPolicy.liveTargetAllowed(connection.isConnected(),
                ChannelOperatorUtils.hasOperatorPrivileges(connection, channel, false),
                member != null,
                LuaModerationPolicy.isSelf(
                        api.getServerConnectionData().getSupportList().getCaseMapping(),
                        nick, connection.getUserNick()), nick) ||
                !LuaModerationPolicy.ejectableTarget(member)) return;

        IrcIdentityCache.Identity identity =
                api.getServerConnectionData().getIdentityCache().get(nick);
        String suffix = identity == null ? null :
                LuaModerationPolicy.revisedHost(identity.host);
        if (suffix == null) {
            store.recordLog(script.id, "Eject rejected: target host absent or not safely parseable");
            return;
        }
        String account = identity.accountKnown ? identity.account : null;
        if (account != null && ("*".equals(account) || "0".equals(account)))
            account = null;
        if (account != null && !LuaModerationPolicy.validNick(account)) {
            store.recordLog(script.id, "Eject rejected: invalid account");
            return;
        }
        OperatorCommandComposer.Dialect dialect = new OperatorCommandComposer.Dialect(
                api.getServerConnectionData().getSupportList().getExtban(),
                api.getServerConnectionData().getSupportList().getAccountExtban());
        if (!dialect.isInspStyle() || !"m:".equals(dialect.acting('m')) ||
                !"U:".equals(dialect.matching('U')) ||
                (account != null && !"R:".equals(dialect.matching('R')))) {
            store.recordLog(script.id, "Eject rejected: network extban syntax unsupported");
            return;
        }
        // Explicitly requested: U: matching unauthenticated users,
        // m: acting mute; R: targets authenticated NickServ accounts.
        List<String> masks = new ArrayList<>();
        masks.add("m:U:*!*@" + suffix);
        if (account != null) masks.add("R:" + account);
        masks.add(nick + "!*@*");
        if (account != null && !account.equalsIgnoreCase(nick))
            masks.add(account + "!*@*");

        // Do not send ANY command before the live privileges, host, account,
        // prefixes and extban dialect have passed validation.
        api.sendCommand("MODE", false, new String[]{channel, "+N"}, null, null);
        boolean hadVoice = LuaModerationPolicy.targetHasVoice(member);
        if (hadVoice) api.sendCommand("MODE", false,
                new String[]{channel, "-v", nick}, null, null);
        api.sendCommand("KICK", true, new String[]{channel, nick, reason}, null, null);
        for (String mask : masks)
            api.sendCommand("MODE", false,
                    new String[]{channel, "+b", mask}, null, null);
        String result = "[Lua: " + script.name + "] " + channel + " - " +
                nick + ": +N" + (hadVoice ? ", -v" : "") + ", KICK (" + reason +
                "), ban " + String.join(" / ", masks) + " - comandi inviati";
        main.post(() -> emitConsole(connection, result));
    }

    /** Runs off the UI and IRC reader threads and rechecks consent at execution. */
    private void performIrcAction(String scriptId, Pending request, String kind,
                                  String channel, String value) {
        if (console.isPaused(request.server) ||
                console.generation(request.server) != request.generation) return;
        LuaScriptStore.Script script = store.find(scriptId);
        boolean who = "who".equals(kind);
        if ("eject".equals(kind) ?
                !LuaModerationPolicy.scopedEject(script, request.server, request.channel,
                        request.event, channel) :
                !LuaModerationPolicy.scoped(script, request.server, request.channel,
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
            String scriptNetwork = script.id + ":" + request.server;
            if (who) {
                if (!"refresh".equals(value)) return;
                if (now - lastWho.getOrDefault(scriptNetwork, -WHO_INTERVAL_MS)
                        < WHO_INTERVAL_MS) return;
                boolean whox = api.getServerConnectionData().getSupportList().isWhoxSupported();
                if (whox) api.getServerConnectionData().getIdentityCache().expectWhox();
                api.sendCommand("WHO", false, whox ?
                        new String[]{channel, "%tnuhaf," + IrcIdentityCache.whoxToken()} :
                        new String[]{channel}, null, null);
                lastWho.put(scriptNetwork, now);
                return;
            }

            if ("eject".equals(kind)) {
                performEject(connection, api, script, channel, value);
                return;
            }

            String nick = "";
            String reason = "";
            String mode = "";
            boolean blacklistOverride = "blkmod".equals(kind);
            String claimedAccount = "";
            if (blacklistOverride) {
                if (value == null) return;
                String[] fields = value.split("\\t", -1);
                if (fields.length != 4) return;
                nick = fields[0];
                claimedAccount = fields[1];
                mode = fields[3];
                switch (fields[2]) {
                    case "kick": kind = "kick"; break;
                    case "ban": kind = "ban"; break;
                    case "mute": kind = "mute"; break;
                    case "tban": kind = "tban"; break;
                    case "bankick": kind = "kban"; break;
                    case "tbankick": kind = "ktban"; break;
                    default: return;
                }
                reason = "non ripetere";
            }
            boolean compound = "kban".equals(kind) || "ktban".equals(kind);
            if (blacklistOverride) {
                // Parsed and validated above; permission checked against the
                // live account below, never against only the Lua claim.
            } else if (compound) {
                if (value == null) return;
                String[] fields = value.split("\\t", -1);
                if (fields.length != ("kban".equals(kind) ? 2 : 3)) return;
                nick = fields[0];
                if ("ktban".equals(kind)) {
                    mode = fields[1];
                    reason = fields[2];
                } else reason = fields[1];
                if (!LuaModerationPolicy.validReason(reason)) return;
            } else if ("kick".equals(kind) || "mode".equals(kind) ||
                    "tban".equals(kind) || "tmute".equals(kind)) {
                int tab = value == null ? -1 : value.indexOf('\t');
                if (tab <= 0) return;
                nick = value.substring(0, tab);
                if ("kick".equals(kind)) reason = value.substring(tab + 1);
                else mode = value.substring(tab + 1);
            } else if ("ban".equals(kind) || "mute".equals(kind)) {
                nick = value;
            } else return;
            NickWithPrefix targetMember =
                    ChannelOperatorUtils.findMember(connection, channel, nick);
            if (!LuaModerationPolicy.liveTargetAllowed(
                    connection.isConnected(),
                    ChannelOperatorUtils.hasOperatorPrivileges(connection, channel, false),
                    targetMember != null,
                    LuaModerationPolicy.isSelf(
                            api.getServerConnectionData().getSupportList().getCaseMapping(),
                            nick, connection.getUserNick()),
                    nick)) return;
            // Ordinary scripts cannot moderate prefixed users. An explicit
            // account blacklist request is the sole exception, and only when
            // the target is the event sender AND a live observed IRC account
            // matches the claim, with user-info and moderation consent.
            IrcIdentityCache.Identity liveIdentity =
                    api.getServerConnectionData().getIdentityCache().get(nick);
            if (blacklistOverride &&
                    !LuaModerationPolicy.blacklistOverrideAllowed(script,
                            request.server, request.event, request.nick, nick,
                            claimedAccount, liveIdentity == null ? null :
                            liveIdentity.account, liveIdentity != null &&
                            liveIdentity.accountKnown,
                            api.getServerConnectionData().getSupportList()
                                    .getCaseMapping())) return;
            if (!"mode".equals(kind) && !blacklistOverride &&
                    !LuaModerationPolicy.unprivilegedTarget(targetMember)) return;
            if (compound) {
                // Validate both commands while the target is still in the channel.
                // The cached host is captured BEFORE the KICK.
                IrcIdentityCache.Identity identity =
                        api.getServerConnectionData().getIdentityCache().get(nick);
                if (identity == null ||
                        !LuaModerationPolicy.validBanHost(identity.host)) return;
                String baseMask = "*!*@" + identity.host;
                String banVerb;
                String[] banArgs;
                if ("ktban".equals(kind)) {
                    if (!mode.matches("(?i)[1-9][0-9]?[hmd]")) return;
                    OperatorCommandComposer.Dialect dialect =
                            new OperatorCommandComposer.Dialect(
                                    api.getServerConnectionData().getSupportList().getExtban(),
                                    api.getServerConnectionData().getSupportList().getAccountExtban());
                    String timed = dialect.timedCommand(channel, mode, baseMask);
                    if (timed == null) return;
                    if (dialect.isInspStyle()) {
                        banVerb = "TBAN";
                        banArgs = new String[]{channel, mode, baseMask};
                    } else if (dialect.isUnrealStyle()) {
                        banVerb = "MODE";
                        banArgs = new String[]{channel, "+b",
                                timed.substring(timed.lastIndexOf(' ') + 1)};
                    } else return;
                } else {
                    banVerb = "MODE";
                    banArgs = new String[]{channel, "+b", baseMask};
                }
                // Send in the requested order. A second membership lookup
                // after KICK would incorrectly suppress the BAN.
                api.sendCommand("KICK", true,
                        new String[]{channel, nick, reason}, null, null);
                logModerationSent(connection, script, channel, "KICK", nick,
                        "motivo: " + reason);
                api.sendCommand(banVerb, false, banArgs, null, null);
                logModerationSent(connection, script, channel,
                        "ktban".equals(kind) ? "BAN temporaneo" : "BAN",
                        nick, "ktban".equals(kind) ? "durata: " + mode : "");
            } else if ("kick".equals(kind)) {
                if (!LuaModerationPolicy.validReason(reason)) return;
                api.sendCommand("KICK", true, new String[]{channel, nick, reason},
                        null, null);
                logModerationSent(connection, script, channel, "KICK", nick,
                        "motivo: " + reason);
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
                    logModerationSent(connection, script, channel, "MUTE", nick, "");
                } else if ("tmute".equals(kind)) {
                    String timed = LuaModerationPolicy.timedMuteCommand(
                            dialect, channel, mode, identity.host);
                    if (timed == null) return;
                    String mask = dialect.acting('m') + baseMask;
                    if (dialect.isInspStyle()) {
                        api.sendCommand("TBAN", false,
                                new String[]{channel, mode, mask}, null, null);
                    } else if (dialect.isUnrealStyle()) {
                        api.sendCommand("MODE", false,
                                new String[]{channel, "+b",
                                        timed.substring(timed.lastIndexOf(' ') + 1)},
                                null, null);
                    } else return;
                    logModerationSent(connection, script, channel, "MUTE temporaneo",
                            nick, "durata: " + mode);
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
                    logModerationSent(connection, script, channel, "BAN temporaneo",
                            nick, "durata: " + mode);
                } else {
                    api.sendCommand("MODE", false,
                            new String[]{channel, "+b", baseMask}, null, null);
                    logModerationSent(connection, script, channel, "BAN", nick, "");
                }
            }
        } catch (RuntimeException ignored) {
            // IRC callbacks, disconnects and partial rosters must never crash TIARCA.
        }
    }

}
