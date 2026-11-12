package io.mrarm.irc.lua;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.mrarm.chatlib.NoSuchChannelException;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.WhoSnapshotCollector;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.ServerConnectionManager;

/**
 * Explicit, read-only Mask Finder searches. WHO replies are gathered by the
 * IRC core, not by the 128-user Lua event snapshot or the 512-entry identity
 * cache. Only the user-requested channel and a consented script are allowed.
 *
 * All session state lives on the main thread and never persists to backups.
 * No search result is transmitted as an IRC message.
 */
public final class MaskFinderManager {
    private static final int BATCH_SIZE = 50;
    private static final long TIMEOUT_MS = 30_000L;
    private static final long MIN_WHO_INTERVAL_MS = 5_000L;
    private static MaskFinderManager instance;

    public static synchronized MaskFinderManager get(Context context) {
        if (instance == null) instance = new MaskFinderManager(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<String, Long> lastWho = new HashMap<>();
    private long nextId = 1;

    private MaskFinderManager(Context context) {
        this.context = context;
    }

    private static final class Session {
        final String server;
        final String scriptId;
        final String channel;
        final String query;
        final long id;
        final int memberCount;
        final ServerConnectionData data;
        final List<WhoSnapshotCollector.User> matches = new ArrayList<>();
        int cursor;
        MaskFinderStatusMessageInfo prompt;
        Runnable timeout;
        boolean waiting;

        Session(String server, String scriptId, String channel, String query,
                long id, int memberCount, ServerConnectionData data) {
            this.server = server;
            this.scriptId = scriptId;
            this.channel = channel;
            this.query = query;
            this.id = id;
            this.memberCount = memberCount;
            this.data = data;
        }
    }

    /** Invoked only from a user-entered /lua command after the host's policy gate. */
    public void startSearch(String scriptId, String server, String channel, String query) {
        main.post(() -> startOnMain(scriptId, server, channel, query));
    }

    private ServerConnectionInfo connected(String server, String channel) {
        try {
            ServerConnectionInfo connection = ServerConnectionManager.getInstance(context)
                    .getConnection(UUID.fromString(server));
            if (connection == null || !connection.isConnected() ||
                    !(connection.getApiInstance() instanceof IRCConnection)) return null;
            IRCConnection api = (IRCConnection) connection.getApiInstance();
            if (channel == null || channel.isEmpty() ||
                    !api.getServerConnectionData().getSupportList()
                            .getSupportedChannelTypes().contains(channel.charAt(0)))
                return null;
            api.getServerConnectionData().getJoinedChannelData(channel);
            return connection;
        } catch (NoSuchChannelException | RuntimeException error) {
            return null;
        }
    }

    private boolean approved(Session session) {
        LuaScriptStore.Script script = LuaScriptStore.get(context).find(session.scriptId);
        return script != null && script.enabled && script.allowUserInfo &&
                session.server.equals(script.serverId);
    }

    private void notice(ServerConnectionInfo connection,
                        MaskFinderStatusMessageInfo.Kind kind, Session session,
                        String nick, String message) {
        IRCConnection api = (IRCConnection) connection.getApiInstance();
        api.getServerConnectionData().getServerStatusData().addMessage(
                new MaskFinderStatusMessageInfo(kind, session.channel,
                        nick, session.id, message));
    }

    private void startOnMain(String scriptId, String server, String channel, String query) {
        ServerConnectionInfo conn = connected(server, channel);
        LuaScriptStore.Script script = LuaScriptStore.get(context).find(scriptId);
        if (conn == null || script == null || !script.enabled || !script.allowUserInfo ||
                !server.equals(script.serverId) || !MaskFinderMatcher.validQuery(query) ||
                query.getBytes(StandardCharsets.UTF_8).length > 256) return;

        IRCConnection api = (IRCConnection) conn.getApiInstance();
        ServerConnectionData data = api.getServerConnectionData();
        int count;
        try {
            count = data.getJoinedChannelData(channel).getMemberCount();
        } catch (NoSuchChannelException error) {
            return;
        }

        // One active search per network: stop an older WHO before starting a
        // new one, but never invalidate already printed server-tab rows.
        Session previous = sessions.remove(server);
        if (previous != null && previous.waiting) {
            previous.data.getWhoSnapshotCollector().cancel();
            main.removeCallbacks(previous.timeout);
        }
        Session session = new Session(server, scriptId, channel, query,
                nextId++, count, data);
        sessions.put(server, session);

        long now = SystemClock.elapsedRealtime();
        if (now - lastWho.getOrDefault(server, -MIN_WHO_INTERVAL_MS) <
                MIN_WHO_INTERVAL_MS) {
            notice(conn, MaskFinderStatusMessageInfo.Kind.INFO, session, null,
                    "Mask Finder: attendi qualche secondo prima di una nuova ricerca WHO.");
            sessions.remove(server);
            return;
        }

        boolean whox = data.getSupportList().isWhoxSupported();
        // Distinct from the C3 042 and WhoXAccountHandler's 700-999 tokens.
        String token = whox ? "642" : "";
        if (!data.getWhoSnapshotCollector().begin(channel, token,
                result -> main.post(() -> complete(session, result)))) {
            notice(conn, MaskFinderStatusMessageInfo.Kind.INFO, session, null,
                    "Mask Finder: un'altra ricerca WHO è già in corso.");
            sessions.remove(server);
            return;
        }
        session.waiting = true;
        session.timeout = () -> timeout(session);
        main.postDelayed(session.timeout, TIMEOUT_MS);
        try {
            api.sendCommand("WHO", false, whox ?
                    new String[]{channel, "%tnuhaf," + token} :
                    new String[]{channel}, null, null);
            lastWho.put(server, now);
        } catch (RuntimeException error) {
            data.getWhoSnapshotCollector().cancel();
            main.removeCallbacks(session.timeout);
            session.waiting = false;
            sessions.remove(server);
            notice(conn, MaskFinderStatusMessageInfo.Kind.INFO, session, null,
                    "Mask Finder: impossibile richiedere WHO al server.");
        }
    }

    private void timeout(Session session) {
        if (sessions.get(session.server) != session || !session.waiting) return;
        session.waiting = false;
        session.data.getWhoSnapshotCollector().cancel();
        ServerConnectionInfo conn = connected(session.server, session.channel);
        if (conn != null && approved(session))
            notice(conn, MaskFinderStatusMessageInfo.Kind.INFO, session, null,
                    "Mask Finder: risposta WHO incompleta (timeout). Nessun totale affidabile.");
        sessions.remove(session.server);
    }

    private void complete(Session session, WhoSnapshotCollector.Result result) {
        if (sessions.get(session.server) != session || !session.waiting) return;
        session.waiting = false;
        main.removeCallbacks(session.timeout);
        ServerConnectionInfo conn = connected(session.server, session.channel);
        if (conn == null || !approved(session)) {
            sessions.remove(session.server);
            return;
        }
        for (WhoSnapshotCollector.User user : result.users)
            if (MaskFinderMatcher.matches(session.query, user.mask()))
                session.matches.add(user);
        session.matches.sort(Comparator.comparing(
                u -> u.nick.toLowerCase(Locale.ROOT)));

        boolean incomplete = result.truncated ||
                result.users.size() < session.memberCount;
        String summary = "Mask Finder · " + session.channel + " · " + session.query +
                " · " + session.matches.size() + " utenti trovati su " +
                session.memberCount + " presenti";
        notice(conn, MaskFinderStatusMessageInfo.Kind.HEADER, session, null,
                summary + (incomplete ?
                        " · RICERCA INCOMPLETA (" + result.users.size() +
                        " identità WHO disponibili)" : ""));
        if (session.matches.isEmpty()) {
            notice(conn, MaskFinderStatusMessageInfo.Kind.INFO, session, null,
                    incomplete ? "Nessuna corrispondenza certa; dati incompleti." :
                            "Nessuna corrispondenza.");
            sessions.remove(session.server);
            return;
        }
        appendBatch(session, conn);
    }

    private void appendBatch(Session session, ServerConnectionInfo conn) {
        int end = Math.min(session.cursor + BATCH_SIZE, session.matches.size());
        for (int i = session.cursor; i < end; i++) {
            WhoSnapshotCollector.User user = session.matches.get(i);
            notice(conn, MaskFinderStatusMessageInfo.Kind.USER, session,
                    user.nick, user.mask());
        }
        session.cursor = end;
        if (end == session.matches.size()) {
            notice(conn, MaskFinderStatusMessageInfo.Kind.INFO, session, null,
                    "Fine risultati: " + end + " di " + end + ".");
            sessions.remove(session.server);
        } else {
            session.prompt = new MaskFinderStatusMessageInfo(
                    MaskFinderStatusMessageInfo.Kind.CONTINUE, session.channel,
                    null, session.id, "Visualizzati " + end + " di " +
                    session.matches.size() + ". Continua? [SI] [NO]");
            ((IRCConnection) conn.getApiInstance()).getServerConnectionData()
                    .getServerStatusData().addMessage(session.prompt);
        }
    }

    /** Triggered only by a tap on the matching server-tab continuation row. */
    public void advance(String server, long searchId, boolean yes) {
        main.post(() -> {
            Session session = sessions.get(server);
            if (session == null || session.id != searchId ||
                    session.waiting || session.prompt == null ||
                    session.prompt.consumed) return;
            session.prompt.consumed = true;
            session.prompt = null;
            if (!yes) {
                sessions.remove(server);
                return;
            }
            ServerConnectionInfo conn = connected(server, session.channel);
            if (conn == null || !approved(session)) {
                sessions.remove(server);
                return;
            }
            appendBatch(session, conn);
        });
    }
}
