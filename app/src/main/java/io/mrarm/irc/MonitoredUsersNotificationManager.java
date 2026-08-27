package io.mrarm.irc;

import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.irc.MonitoredUsersManager;

import java.util.IdentityHashMap;
import java.util.Map;

/** Posts only confirmed MONITOR transitions; initial synchronization is explicitly ignored. */
final class MonitoredUsersNotificationManager implements MonitoredUsersManager.Listener {
    private static final String CHANNEL_ID = "monitored_users";
    private static final int NOTIFICATION_ID_BASE = 25000;
    private static final long ALIAS_TRANSITION_GRACE_MS = 1000L;
    private final Context context;
    private final ServerConnectionInfo connection;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<ServerConfigData.MonitoredUser, Runnable> pendingOffline =
            new IdentityHashMap<>();

    MonitoredUsersNotificationManager(Context context, ServerConnectionInfo connection) {
        this.context = context.getApplicationContext();
        this.connection = connection;
        createChannel();
    }

    @Override public void onPresenceUpdated(ServerConfigData.MonitoredUser user,
                                            MonitoredUsersManager.PresenceUpdate update) {
        handler.post(() -> handlePresenceUpdated(user, update));
    }

    private void handlePresenceUpdated(ServerConfigData.MonitoredUser user,
                                       MonitoredUsersManager.PresenceUpdate update) {
        if (update == MonitoredUsersManager.PresenceUpdate.INITIAL_STATE)
            return;
        if (update == MonitoredUsersManager.PresenceUpdate.BECAME_ONLINE) {
            Runnable pending = pendingOffline.remove(user);
            if (pending != null) {
                handler.removeCallbacks(pending);
                return;
            }
            if (user.notifyOnline) postPresenceNotification(user, true);
            return;
        }
        if (pendingOffline.containsKey(user)) return;
        Runnable pending = () -> {
            pendingOffline.remove(user);
            if (user.notifyOffline) postPresenceNotification(user, false);
        };
        pendingOffline.put(user, pending);
        handler.postDelayed(pending, ALIAS_TRANSITION_GRACE_MS);
    }

    private void postPresenceNotification(ServerConfigData.MonitoredUser user, boolean online) {
        String nick = connection.getMonitoredUsersManager().getPreferredNick(user);
        if (nick == null) return;
        PendingIntent intent = PendingIntent.getActivity(context, notificationId(user),
                MainActivity.getLaunchIntent(context, connection, nick),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int text = online ? R.string.monitor_notification_online : R.string.monitor_notification_offline;
        String displayNick = user.nick == null ? nick : user.nick;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_tiarca)
                .setContentTitle(displayNick)
                .setContentText(context.getString(text, displayNick))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorNotificationMention));
        NotificationManager.postNotification(context, notificationId(user), notification.build());
    }

    @Override public void onSyncStateChanged(MonitoredUsersManager.SyncState state) {
        if (state != MonitoredUsersManager.SyncState.SYNCING) return;
        handler.post(this::clearPendingOffline);
    }

    @Override public void onMonitoredUserChanged(ServerConfigData.MonitoredUser user) {
        if (connection.getMonitoredUsersManager().getMonitoredUsers().contains(user)) return;
        handler.post(() -> {
            Runnable pending = pendingOffline.remove(user);
            if (pending != null) handler.removeCallbacks(pending);
        });
    }

    private void clearPendingOffline() {
        for (Runnable pending : pendingOffline.values()) handler.removeCallbacks(pending);
        pendingOffline.clear();
    }

    private int notificationId(ServerConfigData.MonitoredUser user) {
        return NOTIFICATION_ID_BASE + ((connection.getUUID().toString() + ":" + user.nick).hashCode()
                & 0x7fffffff) % 100000;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.monitor_notification_channel),
                android.app.NotificationManager.IMPORTANCE_DEFAULT);
        channel.setGroup(NotificationManager.getSystemNotificationChannelGroup(context));
        android.app.NotificationManager manager = (android.app.NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }
}
