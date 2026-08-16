package io.mrarm.irc.job;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import io.mrarm.chatlib.ChatApi;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.ServerConnectionManager;

public class ServerPingTask {

    private static final long PING_TASK_TIMEOUT_MS = 30 * 1000L;

    public static void pingServers(Context ctx, DoneCallback cb) {
        List<ServerConnectionInfo> servers =
                ServerConnectionManager.getInstance(ctx).getConnections();
        List<IRCConnection> serversToPing = new ArrayList<>();
        for (ServerConnectionInfo c : servers) {
            if (!c.isConnected())
                continue;
            ChatApi api = c.getApiInstance();
            if (api != null && api instanceof IRCConnection)
                serversToPing.add((IRCConnection) api);
        }
        if (serversToPing.size() == 0) {
            Log.d("ServerPingTask", "No servers to ping");
            cb.onDone();
            return;
        }
        Log.d("ServerPingTask", "Pinging " + serversToPing.size() + " servers");
        AtomicInteger countdownInteger = new AtomicInteger(serversToPing.size());
        AtomicBoolean completed = new AtomicBoolean(false);
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable finish = () -> {
            if (completed.compareAndSet(false, true)) {
                Log.d("ServerPingTask", "Task has been completed");
                cb.onDone();
            }
        };
        handler.postDelayed(() -> {
            if (!completed.get()) {
                Log.w("ServerPingTask", "Ping task timed out");
                finish.run();
            }
        }, PING_TASK_TIMEOUT_MS);
        for (IRCConnection api : serversToPing) {
            Runnable pingCompleteCb = () -> {
                Log.d("ServerPingTask", "Ping received from a server");
                if (!completed.get() && countdownInteger.decrementAndGet() == 0)
                    finish.run();
            };
            try {
                api.sendPing((Void v) -> pingCompleteCb.run(),
                        (Exception e) -> pingCompleteCb.run());
            } catch (RuntimeException e) {
                Log.w("ServerPingTask", "Could not send ping", e);
                pingCompleteCb.run();
            }
            // Sending the ping will either succeed or get us disconnected (we can't get an error
            // from the ping itself, and the disconnect will be handled by other code already).
        }
    }


    public interface DoneCallback {

        void onDone();

    }

}
